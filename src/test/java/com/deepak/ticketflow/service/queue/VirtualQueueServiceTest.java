package com.deepak.ticketflow.service.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.deepak.ticketflow.Enum.UserType;
import com.deepak.ticketflow.handlers.EventNotFoundException;
import com.deepak.ticketflow.handlers.InvalidReservationException;
import com.deepak.ticketflow.model.Event;
import com.deepak.ticketflow.repository.EventRepository;

public class VirtualQueueServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AdmissionControllerService admissionControllerService;

    @Mock
    private EventRepository eventRepository;

    private VirtualQueueService queueService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redis.opsForValue()).thenReturn(valueOperations);
        queueService = new VirtualQueueService(redis, admissionControllerService, eventRepository);
    }

    @Test
    public void testGenerateBookingToken_WhenEventNotFound_ThrowsEventNotFoundException() {
        when(eventRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> {
            queueService.generateBookingToken(1L, 100, UserType.NORMAL, 5);
        });
    }

    @Test
    public void testGenerateBookingToken_WhenSalesNotStarted_ThrowsInvalidReservationException() {
        Event event = new Event();
        event.setEventId(1L);
        event.setSaleStartTime(LocalDateTime.now().plusDays(1)); // Starts tomorrow
        event.setEventDate(LocalDateTime.now().plusDays(2));

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        InvalidReservationException ex = assertThrows(InvalidReservationException.class, () -> {
            queueService.generateBookingToken(1L, 100, UserType.NORMAL, 5);
        });
        assertEquals("Event sales have not started yet", ex.getMessage());
    }

    @Test
    public void testGenerateBookingToken_WhenSalesEnded_ThrowsInvalidReservationException() {
        Event event = new Event();
        event.setEventId(1L);
        event.setSaleStartTime(LocalDateTime.now().minusDays(2));
        event.setEventDate(LocalDateTime.now().minusDays(1)); // Ended yesterday

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        InvalidReservationException ex = assertThrows(InvalidReservationException.class, () -> {
            queueService.generateBookingToken(1L, 100, UserType.NORMAL, 5);
        });
        assertEquals("Event sales have ended", ex.getMessage());
    }

    @Test
    public void testGenerateBookingToken_WhenSalesActive_GeneratesToken() {
        Event event = new Event();
        event.setEventId(1L);
        event.setSaleStartTime(LocalDateTime.now().minusHours(1));
        event.setEventDate(LocalDateTime.now().plusHours(2));

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        String token = queueService.generateBookingToken(1L, 100, UserType.NORMAL, 5);
        assertNotNull(token);
        verify(admissionControllerService).registerActiveBooking(1L, 100);
    }
}
