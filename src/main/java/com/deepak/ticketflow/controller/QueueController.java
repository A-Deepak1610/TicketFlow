package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.dto.queue.QueueJoinRequest;
import com.deepak.ticketflow.dto.queue.QueueJoinResponse;
import com.deepak.ticketflow.dto.queue.QueuePositionResponse;
import com.deepak.ticketflow.dto.queue.QueueDecision;
import com.deepak.ticketflow.filters.CustomUserPrincipal;
import com.deepak.ticketflow.Enum.UserType;
import com.deepak.ticketflow.service.queue.QueueDecisionService;
import com.deepak.ticketflow.service.queue.VirtualQueueService;
import com.deepak.ticketflow.model.Event;
import com.deepak.ticketflow.repository.EventRepository;
import com.deepak.ticketflow.handlers.EventNotFoundException;
import com.deepak.ticketflow.handlers.InvalidReservationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Tag(name = "Virtual Queue", description = "APIs for joining the virtual queue and checking user's queue position")
public class QueueController {

    private final QueueDecisionService decisionService;
    private final VirtualQueueService queueService;
    private final EventRepository eventRepository;

    @Operation(
        summary = "Join Queue",
        description = "Allows the user to join the virtual queue for a specific event. Returns a booking token if load is low (DIRECT mode), or a queue status response if a queue is active."
    )
    @PostMapping("/join")
    public ResponseEntity<QueueJoinResponse> joinQueue(
            @RequestBody QueueJoinRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        request.setUserId(principal.getUserId());
        
        // Verify event sales status
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + request.getEventId()));

        LocalDateTime now = LocalDateTime.now();
        if (event.getSaleStartTime() != null && now.isBefore(event.getSaleStartTime())) {
            throw new InvalidReservationException("Event sales have not started yet");
        }
        if (event.getEventDate() != null && now.isAfter(event.getEventDate())) {
            throw new InvalidReservationException("Event sales have ended");
        }

        // Determine if queue is needed
        QueueDecision decision = decisionService.decide(
                request.getEventId(),
                principal.getUserId(),
                request.getUserType()
        );

        if (decision == QueueDecision.NO_QUEUE) {
            // Direct booking mode - generate immediate token
            String token = queueService.generateBookingToken(
                request.getEventId(),
                    principal.getUserId(),
                    request.getUserType(),
                    10 // 5 minutes expiry
            );

            return ResponseEntity.ok(QueueJoinResponse.builder()
                    .mode("DIRECT")
                    .token(token)
                    .expiresIn(300)
                    .build());
        } else {
            // Queue mode
            QueueJoinResponse response = queueService.joinQueue(
                    request.getEventId(),
                    request.getUserId(),
                    request.getUserType()
            );
            response.setMode(decision == QueueDecision.SOFT_QUEUE ? "SOFT_QUEUE" : "HARD_QUEUE");
            return ResponseEntity.ok(response);
        }
    }

    @Operation(
        summary = "Get Queue Position",
        description = "Retrieves the current queue position, status, and estimated wait time for the authenticated user."
    )
    @GetMapping("/position")
    public ResponseEntity<QueuePositionResponse> getPosition(
            @RequestParam Long eventId,
            @RequestParam UserType userType,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        QueuePositionResponse response = queueService.getPosition(
                eventId,
                principal.getUserId(),
                userType
        );

        return ResponseEntity.ok(response);
    }
}