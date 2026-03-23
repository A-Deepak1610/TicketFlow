package com.deepak.ticketflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/events")
    public String createEvent() {
        return "Event created";
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
