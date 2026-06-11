package com.deepak.ticketflow.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.deepak.ticketflow.event.BookingSlotFreedEvent;
import com.deepak.ticketflow.service.queue.QueueAdmissionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Event listener that reacts to BookingSlotFreedEvent.
 * 
 * When a booking slot becomes available, this listener immediately
 * attempts to admit the next waiting user from the queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueueAdmissionListener {

    private final QueueAdmissionService queueAdmissionService;

    /**
     * Handle booking slot freed event by attempting to admit next user.
     * 
     * This is executed asynchronously to avoid blocking the booking flow.
     * 
     * @param event the booking slot freed event
     */
    @EventListener
    @Async
    public void handleSlotFreed(BookingSlotFreedEvent event) {
        try {
            log.debug("Booking slot freed for event {}. Attempting to admit next user...", 
                    event.getEventId());
            queueAdmissionService.tryAdmitNextUser(event.getEventId());
        } catch (Exception ex) {
            log.error("Error admitting next user for event {}: {}", 
                    event.getEventId(), ex.getMessage(), ex);
        }
    }
}
