package com.deepak.ticketflow.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deepak.ticketflow.dto.BookingResponse;
import com.deepak.ticketflow.dto.ConfirmBookingRequest;
import com.deepak.ticketflow.dto.ReservationRequest;
import com.deepak.ticketflow.dto.ReservationResponse;
import com.deepak.ticketflow.service.TicketBookingService;

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

        Integer finalUserId = userId != null ? userId : 1;
        ReservationResponse response = ticketBookingService.reserveSeats(
                request.getEventId(),
                request.getSeatNumbers(),
                finalUserId
        );
        return ResponseEntity.ok(response);
    }
    /**
     * Step 2 — Confirm booking after payment (idempotent via header)
     * Accepts multiple reservation IDs — processes all in one atomic transaction
     * POST /api/bookings/confirm
     * Header: Idempotency-Key: <uuid>
     * Body: { "reservationIds": [123, 124, 125], "paymentRequest": { "amount": 5000.00, "paymentMethod": "UPI" } }
     */
    @PostMapping("/bookings/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @RequestBody ConfirmBookingRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Integer userId) {

        BookingResponse response = ticketBookingService.confirmBooking(
                request.getReservationIds(),
                request.getPaymentRequest(),
                idempotencyKey
        );
        return ResponseEntity.ok(response);
    }
}