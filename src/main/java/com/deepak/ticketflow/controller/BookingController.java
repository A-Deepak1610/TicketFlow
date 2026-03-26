package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.dto.*;
import com.deepak.ticketflow.service.TicketBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BookingController{
    @Autowired
    private TicketBookingService ticketBookingService;
    /**
     * Step 1 — Reserve seats (authenticated user)
     * POST /api/reservations
     */
    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserveSeats(
            @RequestBody ReservationRequest request,
            @AuthenticationPrincipal Integer userId) {

        ReservationResponse response = ticketBookingService.reserveSeats(
                request.getEventId(),
                request.getSeatNumbers(),
            userId != null ? userId : Integer.valueOf(1)
        );
        return ResponseEntity.ok(response);
    }
    /**
     * Step 2 — Confirm booking after payment (idempotent via header)
     * POST /api/bookings/confirm
     * Header: Idempotency-Key: <uuid>
     */
    @PostMapping("/bookings/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @RequestBody ConfirmBookingRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Long userId) {

        BookingResponse response = ticketBookingService.confirmBooking(
                request.getReservationId(),
                request.getPaymentRequest(),
                idempotencyKey
        );
        return ResponseEntity.ok(response);
    }
}