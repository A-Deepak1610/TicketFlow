package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.filters.CustomUserPrincipal;
import com.deepak.ticketflow.service.queue.VirtualQueueService;  // ← ADD THIS IMPORT
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;  // ← ADD THIS IMPORT
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

import java.util.HashMap;  
import java.util.Map;     

@RestController
@RequestMapping("/api")
public class BookingController{

    @Autowired
    private TicketBookingService ticketBookingService;

    @Autowired
    private VirtualQueueService queueService;  

    @PostMapping("/reservations")
    public ResponseEntity<?> reserveSeats(
                                            @RequestBody ReservationRequest request,
                                            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
                                            @AuthenticationPrincipal CustomUserPrincipal principal) {

        Integer userId = principal.getUserId();
        // ← START: ADD QUEUE VALIDATION
        // Validate queue token if present
        if (queueToken == null || queueToken.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Queue token required");
            error.put("message", "Please join the queue first");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        if (!queueService.validateToken(queueToken, request.getEventId(), userId)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid or expired queue token");
            error.put("message", "Please rejoin the queue");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        // ← END: ADD QUEUE VALIDATION

        ReservationResponse response = ticketBookingService.reserveSeats(
                request.getEventId(),
                request.getSeatNumbers(),
                userId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2 — Confirm booking after payment (idempotent via header)
     * Accepts multiple reservation IDs — processes all in one atomic transaction
     * POST /api/bookings/confirm
     * Header: Idempotency-Key: <uuid>
     * Header: X-Queue-Token: <token-from-queue>
     * Body: { "reservationIds": [123, 124, 125], "paymentRequest": { "amount": 5000.00, "paymentMethod": "UPI" } }
     */
    @PostMapping("/bookings/confirm")
    public ResponseEntity<?> confirmBooking(  // ← CHANGE ResponseEntity<BookingResponse> to ResponseEntity<?>
                                              @RequestBody ConfirmBookingRequest request,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,  // ← ADD THIS PARAMETER
                                              @AuthenticationPrincipal CustomUserPrincipal principal) {

        // ← START: ADD QUEUE VALIDATION
        // Validate queue token
        if (queueToken == null || queueToken.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Queue token required");
            error.put("message", "Please join the queue first");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        // Queue token validation is performed inside the booking service because
        // the event/user pair is derived from the reservation IDs.

        BookingResponse response = ticketBookingService.confirmBooking(
                request.getReservationIds(),
                request.getPaymentRequest(),
                idempotencyKey,
                queueToken
        );

        return ResponseEntity.ok(response);
    }
}