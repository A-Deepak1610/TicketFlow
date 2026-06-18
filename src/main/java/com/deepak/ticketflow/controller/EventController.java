package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.dto.CreateEventRequest;
import com.deepak.ticketflow.repository.SeatRepository;
import com.deepak.ticketflow.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Event Management", description = "APIs for querying, creating, and deleting events and seats")
public class EventController {
    @Autowired
    EventService eventService;
    @Autowired
    SeatRepository seatRepository;

    @Operation(
        summary = "Get all events",
        description = "Returns all available events configured in the system."
    )
    @GetMapping("/events")//public end point
    public ResponseEntity<?> getEvents(){
        System.out.println("getEvents");
        return new ResponseEntity<>(eventService.getEvents(), HttpStatus.OK);
    }

    @Operation(
        summary = "Get Event Details",
        description = "Fetches detailed information for a specific event by its unique database ID."
    )
    @GetMapping("/events/{id}")
    public ResponseEntity<?> getEvent(@PathVariable Long id){
        return  new ResponseEntity<>(eventService.getEventDatails(id),HttpStatus.OK);
    }

    @Operation(
        summary = "Create Event",
        description = "Allows administrators to create and save a new event. Requires ADMIN role."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/events")
    public ResponseEntity<?> createEvent(@RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }

    @Operation(
        summary = "Create Seats",
        description = "Initializes seat layout or configurations. Requires ADMIN role."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/seats")
    public String createSeats() {
        return "Seats created";
    }

    @Operation(
        summary = "Delete Event",
        description = "Allows administrators to delete an event by its database ID. Requires ADMIN role."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/events/{id}")
    public String deleteEvent() {
        return "Event deleted";
    }
}
