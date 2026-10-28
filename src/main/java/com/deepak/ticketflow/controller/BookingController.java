package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.filters.CustomUserPrincipal;
import com.deepak.ticketflow.service.queue.VirtualQueueService;  // ← ADD THIS IMPORT
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;  // ← ADD THIS IMPORT
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
@Tag(name = "Booking & Reservations", description = "APIs for reserving seats and confirming ticket bookings")
public class BookingController{

    @Autowired
    private TicketBookingService ticketBookingService;

    @Autowired
    private VirtualQueueService queueService;

    @Autowired
    private com.deepak.ticketflow.service.security.AntiScalpingService antiScalpingService;

    @Operation(
        summary = "Reserve Seats",
        description = "Temporarily reserves the requested seat numbers for a specific event. Requires a valid 'X-Queue-Token' header and checks Anti-Scalping Shield (with optional X-Captcha-Token)."
    )
    @PostMapping("/reservations")
    public ResponseEntity<?> reserveSeats(
                                            @RequestBody ReservationRequest request,
                                            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
                                            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
                                            @AuthenticationPrincipal CustomUserPrincipal principal,
                                            jakarta.servlet.http.HttpServletRequest servletRequest) {

        Integer userId = principal.getUserId();

        // 1. Anti-Scalping & Bot Shield Validation
        String clientIp = servletRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = servletRequest.getRemoteAddr();
        }
        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        String userAgent = servletRequest.getHeader("User-Agent");

        try {
            antiScalpingService.validateReservationRequest(
                    request.getEventId(),
                    request.getSeatNumbers(),
                    userId,
                    clientIp,
                    userAgent,
                    captchaToken
            );
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Anti-Scalping Protection Triggered");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // 2. Queue Token Validation
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

        ReservationResponse response = ticketBookingService.reserveSeats(
                request.getEventId(),
                request.getSeatNumbers(),
                userId
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Confirm Booking",
        description = "Finalizes the ticket purchase/booking after payment processing. Requires a valid 'Idempotency-Key' to prevent double transactions, and a valid 'X-Queue-Token' header if virtual queue is active."
    )
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

    @Operation(
        summary = "Get User Bookings",
        description = "Retrieves all confirmed and previous bookings for the currently authenticated user."
    )
    @GetMapping("/user/bookings")
    public ResponseEntity<?> getUserBookings(@AuthenticationPrincipal CustomUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(ticketBookingService.getUserBookings(principal.getUserId()));
    }

    @Operation(
        summary = "Get All Fleet Bookings (Admin)",
        description = "Retrieves all bookings across all events for admin analytics."
    )
    @GetMapping("/admin/bookings")
    public ResponseEntity<?> getAllBookings() {
        return ResponseEntity.ok(ticketBookingService.getAllBookings());
    }
}