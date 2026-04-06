package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.dto.queue.QueueJoinRequest;
import com.deepak.ticketflow.dto.queue.QueueJoinResponse;
import com.deepak.ticketflow.dto.queue.QueuePositionResponse;
import com.deepak.ticketflow.dto.queue.QueueDecision;
import com.deepak.ticketflow.filters.CustomUserPrincipal;
import com.deepak.ticketflow.model.queue.UserType;
import com.deepak.ticketflow.service.queue.QueueDecisionService;
import com.deepak.ticketflow.service.queue.VirtualQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueDecisionService decisionService;
    private final VirtualQueueService queueService;

    @PostMapping("/join")
    public ResponseEntity<QueueJoinResponse> joinQueue(
            @RequestBody QueueJoinRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        // Verify user matches
        if (!request.getUserId().equals(principal.getUserId())) {
            return ResponseEntity.status(403).build();
        }

        // Determine if queue is needed
        QueueDecision decision = decisionService.decide(
                request.getEventId(),
                request.getUserId(),
                request.getUserType()
        );

        if (decision == QueueDecision.NO_QUEUE) {
            // Direct booking mode - generate immediate token
            String token = queueService.generateBookingToken(
                    request.getUserId(),
                    request.getUserType(),
                    5  // 5 minutes expiry
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