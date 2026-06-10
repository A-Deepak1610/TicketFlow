package com.deepak.ticketflow.event;

import org.springframework.context.ApplicationEvent;

/**
 * Domain event published whenever a booking slot becomes available
 * (user booking succeeds, cancelled, or token expires).
 * 
 * This event triggers admission of the next waiting user from the queue.
 */
public class BookingSlotFreedEvent extends ApplicationEvent {

    private final Long eventId;

    public BookingSlotFreedEvent(Object source, Long eventId) {
        super(source);
        this.eventId = eventId;
    }

    public Long getEventId() {
        return eventId;
    }

    @Override
    public String toString() {
        return "BookingSlotFreedEvent{" +
                "eventId=" + eventId +
                '}';
    }
}
