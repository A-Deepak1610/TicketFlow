package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.dto.CreateEventRequest;
import com.deepak.ticketflow.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class EventController {
    @Autowired
    EventService eventService;
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/seats")
    public String createSeats() {
        return "Seats created";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/events/{id}")
    public String deleteEvent() {
        return "Event deleted";
    }
}
