package com.deepak.ticketflow.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import com.deepak.ticketflow.Enum.SeatStatus;
import com.deepak.ticketflow.dto.ReservationResponse;
import com.deepak.ticketflow.model.Seat;
import com.deepak.ticketflow.model.Reservation;
import com.deepak.ticketflow.repository.BookingRepository;
import com.deepak.ticketflow.repository.BookingSeatRepository;
import com.deepak.ticketflow.repository.EventRepository;
import com.deepak.ticketflow.repository.ReservationRepository;
import com.deepak.ticketflow.repository.SeatRepository;
import com.deepak.ticketflow.service.queue.SseNotificationService;
import com.deepak.ticketflow.service.queue.VirtualQueueService;

public class TicketBookingServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private EventRepository eventRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private PaymentService paymentService;
    @Mock private VirtualQueueService queueService;
    @Mock private SseNotificationService sseNotificationService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TicketBookingService ticketBookingService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testReserveSeats_BroadcastsStatus() {
        Long eventId = 1L;
        List<String> seatNumbers = List.of("A1");
        Integer userId = 42;

        Seat seat = new Seat();
        seat.setSeatId(101L);
        seat.setSeatNumber("A1");
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setPrice(BigDecimal.valueOf(100.00));

        when(seatRepository.findByEventIdAndSeatNumber(eventId, "A1")).thenReturn(Optional.of(seat));
        when(redisLockService.acquireSeatsLock(eq(eventId), eq(seatNumbers), anyString(), any())).thenReturn(List.of("lock"));
        when(seatRepository.save(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = ticketBookingService.reserveSeats(eventId, seatNumbers, userId);

        assertNotNull(response);
        // Verify that the status change was broadcasted
        verify(sseNotificationService, times(1)).sendSeatStatusUpdate(eventId, "A1", "RESERVED");
    }
}
