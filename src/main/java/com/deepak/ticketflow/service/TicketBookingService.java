package com.deepak.ticketflow.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deepak.ticketflow.Enum.BookingStatus;
import com.deepak.ticketflow.Enum.ReservationStatus;
import com.deepak.ticketflow.Enum.SeatStatus;
import com.deepak.ticketflow.dto.BookingResponse;
import com.deepak.ticketflow.dto.PaymentRequest;
import com.deepak.ticketflow.dto.PaymentResponse;
import com.deepak.ticketflow.dto.ReservationResponse;
import com.deepak.ticketflow.handlers.InvalidReservationException;
import com.deepak.ticketflow.handlers.InvalidSeatStateException;
import com.deepak.ticketflow.handlers.PaymentFailedException;
import com.deepak.ticketflow.handlers.ReservationExpiredException;
import com.deepak.ticketflow.handlers.ReservationNotFoundException;
import com.deepak.ticketflow.handlers.SeatNotAvailableException;
import com.deepak.ticketflow.handlers.SeatNotFoundException;
import com.deepak.ticketflow.model.Booking;
import com.deepak.ticketflow.model.BookingSeat;
import com.deepak.ticketflow.model.Event;
import com.deepak.ticketflow.model.Reservation;
import com.deepak.ticketflow.model.Seat;
import com.deepak.ticketflow.repository.BookingRepository;
import com.deepak.ticketflow.repository.BookingSeatRepository;
import com.deepak.ticketflow.repository.EventRepository;
import com.deepak.ticketflow.repository.ReservationRepository;
import com.deepak.ticketflow.repository.SeatRepository;
import com.deepak.ticketflow.service.queue.VirtualQueueService;
import com.deepak.ticketflow.service.queue.SseNotificationService;
import com.deepak.ticketflow.event.BookingSlotFreedEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TicketBookingService {
    private static final int  RESERVATION_TIMEOUT_MINUTES = 5;
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    @Autowired private SeatRepository        seatRepository;
    @Autowired private BookingRepository     bookingRepository;
    @Autowired private BookingSeatRepository bookingSeatRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private EventRepository       eventRepository;
    @Autowired private RedisLockService      redisLockService;
    @Autowired private PaymentService        paymentService;
    @Autowired private VirtualQueueService   queueService;
    @Autowired private SseNotificationService sseNotificationService;
    @Autowired private ApplicationEventPublisher applicationEventPublisher;
    @Transactional
    public ReservationResponse reserveSeats(Long eventId,
                                            List<String> seatNumbers,
                                            Integer userId) {
        String lockValue = UUID.randomUUID().toString();
        List<String> acquiredLocks = new ArrayList<>();
        try {
            // Acquire distributed locks (sorted to avoid deadlock)
            acquiredLocks = redisLockService.acquireSeatsLock(
                    eventId, seatNumbers, lockValue, LOCK_TTL);

            LocalDateTime reservedUntil = LocalDateTime.now()
                    .plusMinutes(RESERVATION_TIMEOUT_MINUTES);

            List<Reservation> reservations = new ArrayList<>();
            for (String seatNumber : seatNumbers) {
                // Fetch with optimistic lock (@Version on Seat)
                Seat seat = seatRepository
                        .findByEventIdAndSeatNumber(eventId, seatNumber)
                        .orElseThrow(() -> new SeatNotFoundException(
                                "Seat not found: " + seatNumber));

                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    throw new SeatNotAvailableException(
                            "Seat " + seatNumber + " is not available");
                }

                // Mark as RESERVED in DB
                seat.setStatus(SeatStatus.RESERVED);
                seat.setReservedBy(userId);
                seat.setReservedUntil(reservedUntil);
                seatRepository.save(seat);  // @Version bumped here

                // Broadcast update
                sseNotificationService.sendSeatStatusUpdate(eventId, seatNumber, SeatStatus.RESERVED.name());

                // Persist reservation record
                Reservation reservation = new Reservation();
                reservation.setSeatId(seat.getSeatId());
                reservation.setEventId(eventId);
                reservation.setUserId(userId);
                reservation.setExpiresAt(reservedUntil);
                reservation.setStatus(ReservationStatus.ACTIVE);
                reservations.add(reservationRepository.save(reservation));
            }

            return new ReservationResponse(reservations, reservedUntil);

        } finally {
            // Always release locks — even if an exception was thrown
            redisLockService.releaseAll(acquiredLocks, lockValue);
        }
    }
    // STEP 2 — Confirm booking (idempotent)
    // Accepts MULTIPLE reservation IDs — processes all atomically
    // If ANY validation/payment fails, ALL reservations are rolled back
    @Transactional
    public BookingResponse confirmBooking(List<Long> reservationIds,
                                          PaymentRequest paymentRequest,
                                          String idempotencyKey,
                                          String queueToken) {

        if (reservationIds == null || reservationIds.isEmpty()) {
            throw new InvalidReservationException("At least one reservation ID required");
        }

        // Idempotency check — if we already processed this key, return existing booking
        return bookingRepository.findByIdempotencyKey(idempotencyKey)
            .map(existing -> {
                log.info("Idempotent replay — returning existing booking {}",
                    existing.getBookingReference());
                return new BookingResponse(existing);
            })
            .orElseGet(() -> processNewMultiSeatBooking(
                reservationIds, paymentRequest, idempotencyKey, queueToken));
    }

    private BookingResponse processNewMultiSeatBooking(List<Long> reservationIds,
                                                        PaymentRequest paymentRequest,
                                String idempotencyKey,
                                String queueToken) {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> reservations = new ArrayList<>();
        List<Seat> seats = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Integer userId = null;
        Long eventId = null;

        // ─── STEP 1: Validate all reservations
        for (Long reservationId : reservationIds) {
            Reservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(
                            "Reservation not found: " + reservationId));

            // Check expiry
            if (now.isAfter(reservation.getExpiresAt())) {
                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);
                throw new ReservationExpiredException(
                        "Reservation " + reservationId + " expired. Please select seats again.");
            }

            // Check active status
            if (reservation.getStatus() != ReservationStatus.ACTIVE) {
                throw new InvalidReservationException(
                        "Reservation " + reservationId + " is not active");
            }

            Seat seat = seatRepository.findById(reservation.getSeatId())
                    .orElseThrow(() -> new SeatNotFoundException(
                            "Seat not found for reservation: " + reservationId));

            // Check seat state
            if (seat.getStatus() != SeatStatus.RESERVED
                    || !reservation.getUserId().equals(seat.getReservedBy())) {
                throw new InvalidSeatStateException(
                        "Seat " + seat.getSeatNumber() + " state has changed");
            }
            
            // Accumulate
            reservations.add(reservation);
            seats.add(seat);
            totalAmount = totalAmount.add(seat.getPrice());
            // Ensure all reservations are for same event & user
            if (userId == null) {
                userId = reservation.getUserId();
                eventId = reservation.getEventId();
            } else if (eventId != null && (!userId.equals(reservation.getUserId())
                    || !eventId.equals(reservation.getEventId()))) {
                throw new InvalidReservationException(
                        "All reservations must be for same event and user");
            }
        }

        log.info("Validated {} reservations for user {}, total amount: {}",
                reservationIds.size(), userId, totalAmount);

        if (queueToken == null || queueToken.isBlank()) {
            throw new InvalidReservationException("Queue token required");
        }

        if (!queueService.validateToken(queueToken, eventId, userId)) {
            throw new InvalidReservationException("Invalid or expired queue token");
        }

        // ─── STEP 2: Process payment (single payment for all seats)
        //      If this fails, everything rolls back
        PaymentResponse payment = paymentService.processPayment(paymentRequest);
        if (!payment.isSuccess()) {
            throw new PaymentFailedException("Payment failed: " + payment.getErrorMessage());
        }
        log.info("Payment processed: {} {}", payment.getPaymentId(), totalAmount);
        // ─── STEP 3: Create ONE booking for all seats
        Booking booking = new Booking();
        booking.setEventId(eventId);
        booking.setUserId(userId);
        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(payment.getPaymentId());
        booking.setPaymentStatus("SUCCESS");
        booking.setBookingReference(generateBookingReference());
        booking.setIdempotencyKey(idempotencyKey);
        booking.setConfirmedAt(now);
        booking = bookingRepository.save(booking);
        log.info("Booking created: {}", booking.getBookingReference());
        // ─── STEP 4: Link all seats to this booking
        for (Seat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBookingId(booking.getBookingId());
            bookingSeat.setSeatId(seat.getSeatId());
            bookingSeat.setPrice(seat.getPrice());
            bookingSeatRepository.save(bookingSeat);
        }

        // ─── STEP 5: Update all seats to BOOKED
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
            seat.setBookingId(booking.getBookingId());
            seat.setReservedBy(null);
            seat.setReservedUntil(null);
            seatRepository.save(seat);
            sseNotificationService.sendSeatStatusUpdate(eventId, seat.getSeatNumber(), SeatStatus.BOOKED.name());
        }

        // ─── STEP 6: Update all reservations to CONFIRMED
        for (Reservation reservation : reservations) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);
        }

        // ─── STEP 7: Decrement available seat count
        updateAvailableSeats(eventId, -seats.size());

        queueService.invalidateToken(queueToken, eventId, userId);

        // Publish event to trigger admission of next waiting user
        applicationEventPublisher.publishEvent(
                new BookingSlotFreedEvent(this, eventId)
        );
        //Notification Service-Email
        
        log.info("Booking confirmed: {} for user {} ({} seats). Published BookingSlotFreedEvent for event {}",
                booking.getBookingReference(), userId, seats.size(), eventId);

        return new BookingResponse(booking);
    }
    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private void updateAvailableSeats(Long eventId, int delta) {
        //I use optimistic locking with a version field to detect concurrent updates.
        //If an update fails due to a version mismatch, I retry the operation with the latest data to ensure consistency without using heavy database locks.
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) return;

            int newCount = Math.max(0, event.getAvailableSeats() + delta);
            int updated  = eventRepository.updateAvailableSeats(
                    eventId, event.getVersion(), newCount);

            if (updated > 0) return; // optimistic update succeeded
            log.warn("Optimistic lock retry {}/{} for event {}", attempt + 1, maxRetries, eventId);
        }
        log.error("Failed to update available seat count for event {} after {} retries",
                eventId, maxRetries);
    }

    private String generateBookingReference() {
        return "TF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 3 — Cleanup expired reservations
    // ─────────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 60_000) // every 60 seconds
    public void cleanupExpiredReservations() {
        List<Reservation> expired = reservationRepository
                .findExpiredReservations(LocalDateTime.now());

        log.info("Cleanup job: found {} expired reservations", expired.size());

        for (Reservation reservation : expired) {
            try {
                releaseExpiredReservation(reservation);
            } catch (Exception e) {
                log.error("Failed to release reservation {}: {}",
                        reservation.getReservationId(), e.getMessage());
            }
        }
    }

    @Transactional
    protected void releaseExpiredReservation(Reservation reservation) {
        String seatNumber = seatRepository
                .findById(reservation.getSeatId())
                .map(Seat::getSeatNumber)
                .orElse(null);

        if (seatNumber == null) return;

        String lockKey   = redisLockService.buildKey(
                reservation.getEventId(), seatNumber);
        String lockValue = UUID.randomUUID().toString();

        // Best-effort lock — if someone is currently booking this seat, skip it
        if (!redisLockService.tryAcquire(lockKey, lockValue, Duration.ofSeconds(5))) {
            log.debug("Skipping seat {} — lock held by active booking", seatNumber);
            return;
        }

        try {
            Seat seat = seatRepository.findById(reservation.getSeatId()).orElse(null);
            if (seat == null) return;

            // Only release if still RESERVED and truly past expiry
            if (seat.getStatus() == SeatStatus.RESERVED
                    && LocalDateTime.now().isAfter(seat.getReservedUntil())) {

                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setReservedBy(null);
                seat.setReservedUntil(null);
                seatRepository.save(seat);

                sseNotificationService.sendSeatStatusUpdate(reservation.getEventId(), seatNumber, SeatStatus.AVAILABLE.name());

                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);

//                updateAvailableSeats(reservation.getEventId(), +1);

                log.info("Released expired reservation {} for seat {}",
                        reservation.getReservationId(), seatNumber);
            }
        } finally {
            redisLockService.release(lockKey, lockValue);
        }
    }

    /**
     * Cancel a confirmed booking and free up the seats.
     * 
     * Publishes BookingSlotFreedEvent to trigger admission of next waiting users.
     * 
     * @param bookingId the booking ID to cancel
     */
    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed bookings can be cancelled");
        }

        Long eventId = booking.getEventId();
        Integer userId = booking.getUserId();

        // Get all seats for this booking
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        
        // Release all seats back to AVAILABLE
        for (BookingSeat bookingSeat : bookingSeats) {
            Seat seat = seatRepository.findById(bookingSeat.getSeatId())
                    .orElseThrow(() -> new SeatNotFoundException("Seat not found"));
            
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setBookingId(null);
            seat.setReservedBy(null);
            seat.setReservedUntil(null);
            seatRepository.save(seat);
            sseNotificationService.sendSeatStatusUpdate(eventId, seat.getSeatNumber(), SeatStatus.AVAILABLE.name());
        }

        // Update booking status
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Increment available seats
        updateAvailableSeats(eventId, bookingSeats.size());

        log.info("Booking {} cancelled for user {}. Released {} seats", 
                bookingId, userId, bookingSeats.size());

        // Publish event to trigger admission of waiting users
        applicationEventPublisher.publishEvent(new BookingSlotFreedEvent(this, eventId));
    }
}
