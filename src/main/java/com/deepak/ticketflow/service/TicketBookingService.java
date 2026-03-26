package com.deepak.ticketflow.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TicketBookingService {

    private static final int  RESERVATION_TIMEOUT_MINUTES = 10;
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    @Autowired private SeatRepository        seatRepository;
    @Autowired private BookingRepository     bookingRepository;
    @Autowired private BookingSeatRepository bookingSeatRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private EventRepository       eventRepository;
    @Autowired private RedisLockService      redisLockService;
    @Autowired private PaymentService        paymentService;

    // ─────────────────────────────────────────────────────────────
    // STEP 1 — Reserve seats
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // STEP 2 — Confirm booking (idempotent)
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public BookingResponse confirmBooking(Long reservationId,
                                          PaymentRequest paymentRequest,
                                          String idempotencyKey) {

        // Idempotency check — if we already processed this key, return existing booking
        return bookingRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.info("Idempotent replay — returning existing booking {}",
                            existing.getBookingReference());
                    return new BookingResponse(existing);
                })
                .orElseGet(() -> processNewBooking(
                        reservationId, paymentRequest, idempotencyKey));
    }

    private BookingResponse processNewBooking(Long reservationId,
                                              PaymentRequest paymentRequest,
                                              String idempotencyKey) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + reservationId));

        // Guard: expired?
        if (LocalDateTime.now().isAfter(reservation.getExpiresAt())) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            throw new ReservationExpiredException(
                    "Reservation expired. Please select your seats again.");
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidReservationException("Reservation is not active");
        }

        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new SeatNotFoundException("Seat not found"));

        // Double-check seat is still reserved by this user
        if (seat.getStatus() != SeatStatus.RESERVED
                || !userId(reservation).equals(seat.getReservedBy())) {
            throw new InvalidSeatStateException("Seat state has changed");
        }

        // Process payment — if this throws, @Transactional rolls everything back
        PaymentResponse payment = paymentService.processPayment(paymentRequest);
        if (!payment.isSuccess()) {
            throw new PaymentFailedException("Payment failed: " + payment.getErrorMessage());
        }

        // Create booking
        Booking booking = new Booking();
        booking.setEventId(reservation.getEventId());
        booking.setUserId(reservation.getUserId());
        booking.setTotalAmount(seat.getPrice());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(payment.getPaymentId());
        booking.setPaymentStatus("SUCCESS");
        booking.setBookingReference(generateBookingReference());
        booking.setIdempotencyKey(idempotencyKey);
        booking.setConfirmedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        // Create booking-seat link (price snapshot)
        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setBookingId(booking.getBookingId());
        bookingSeat.setSeatId(seat.getSeatId());
        bookingSeat.setPrice(seat.getPrice());
        bookingSeatRepository.save(bookingSeat);

        // Update seat → BOOKED
        seat.setStatus(SeatStatus.BOOKED);
        seat.setBookingId(booking.getBookingId());
        seat.setReservedBy(null);
        seat.setReservedUntil(null);
        seatRepository.save(seat);

        // Update reservation → CONFIRMED
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        // Decrement available seat count (with optimistic retry)
        updateAvailableSeats(reservation.getEventId(), -1);

        log.info("Booking confirmed: {} for user {}",
                booking.getBookingReference(), reservation.getUserId());

        return new BookingResponse(booking);
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 3 — Cleanup expired reservations
    // ─────────────────────────────────────────────────────────────
    @Scheduled(fixedRate = 60_000) // every 60 seconds
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

                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);

                updateAvailableSeats(reservation.getEventId(), +1);

                log.info("Released expired reservation {} for seat {}",
                        reservation.getReservationId(), seatNumber);
            }
        } finally {
            redisLockService.release(lockKey, lockValue);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private void updateAvailableSeats(Long eventId, int delta) {
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

    private Integer userId(Reservation r) {
        return r.getUserId();
    }
}