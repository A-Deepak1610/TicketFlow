package com.deepak.ticketflow.service;

import com.deepak.ticketflow.Enum.EventStatus;
import com.deepak.ticketflow.dto.CreateEventRequest;
import com.deepak.ticketflow.model.Event;
import com.deepak.ticketflow.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EventService {
    @Autowired
    EventRepository eventRepository;
    public Event createEvent(CreateEventRequest request) {
        Event event = new Event();
        event.setEventName(request.getEventName());
        event.setEventDate(request.getEventDate());
        event.setVenueName(request.getVenueName());
        event.setTotalSeats(request.getTotalSeats());
        event.setAvailableSeats(request.getTotalSeats());
        event.setSaleStartTime(request.getSaleStartTime());
        event.setStatus(EventStatus.UPCOMING);
        return eventRepository.save(event);
    }
}
