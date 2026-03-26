package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.dto.CreateEventRequest;
import com.deepak.ticketflow.repository.SeatRepository;
import com.deepak.ticketflow.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventController {
    @Autowired
    EventService eventService;
    @Autowired
    SeatRepository seatRepository;
    @GetMapping("/events")//public end point
    public ResponseEntity<?> getEvents(){
        System.out.println("getEvents");
        return new ResponseEntity<>(eventService.getEvents(), HttpStatus.OK);
    }
    @GetMapping("/events/{id}")
    public ResponseEntity<?> getEvent(@PathVariable Long id){
        return  new ResponseEntity<>(eventService.getEventDatails(id),HttpStatus.OK);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/events")
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
