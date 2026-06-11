package com.deepak.ticketflow.service.queue;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.deepak.ticketflow.Enum.UserType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for admitting the next waiting user when a booking slot becomes available.
 * 
 * This service is triggered by BookingSlotFreedEvent whenever:
 * - A booking succeeds
 * - A booking is cancelled
 * - A booking token expires
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueueAdmissionService {

    private static final String VIP_QUEUE_KEY_TEMPLATE = "queue:%d:vip";
    private static final String NORMAL_QUEUE_KEY_TEMPLATE = "queue:%d:normal";

    private final StringRedisTemplate redis;
    private final VirtualQueueService queueService;
    private final SseNotificationService sseNotificationService;
    private final AdmissionControllerService admissionControllerService;

    /**
     * Attempt to admit the next waiting user from the event's queue.
     * 
     * Priority: VIP users are admitted before normal users.
     * 
     * @param eventId the event ID for which to admit a user
     */
    public void tryAdmitNextUser(Long eventId) {
        if (eventId == null) {
            return;
        }

        // Check if there are available slots
        int availableSlots = admissionControllerService.getAvailableSlots(eventId);
        if (availableSlots <= 0) {
            log.debug("No available slots for event {}, skipping admission", eventId);
            return;
        }

        // Try VIP queue first
        String vipQueueKey = getQueueKey(eventId, UserType.VIP);
        String queueEntry = redis.opsForList().leftPop(vipQueueKey);
        UserType userType = UserType.VIP;

        if (queueEntry == null) {
            // Fall back to normal queue
            String normalQueueKey = getQueueKey(eventId, UserType.NORMAL);
            queueEntry = redis.opsForList().leftPop(normalQueueKey);
            userType = UserType.NORMAL;
        }

        if (queueEntry == null) {
            log.debug("No waiting users in queue for event {}", eventId);
            return;
        }

        // Parse queue entry: eventId:userId
        String[] parts = queueEntry.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid queue entry format: {}", queueEntry);
            return;
        }

        try {
            Long parsedEventId = Long.parseLong(parts[0]);
            Integer userId = Integer.parseInt(parts[1]);

            if (!parsedEventId.equals(eventId)) {
                log.warn("Queue entry event ID mismatch. Expected: {}, Got: {}", eventId, parsedEventId);
                return;
            }

            // Generate booking token
            String token = queueService.generateBookingToken(eventId, userId, userType, 5);

            // Notify user via SSE that their booking window is open
            sseNotificationService.sendBookingWindow(userId, token, 5);

            log.info("Admitted user {} ({}) from queue for event {} with token", 
                    userId, userType, eventId);
        } catch (NumberFormatException ex) {
            log.error("Failed to parse queue entry: {}", queueEntry, ex);
        }
    }

    private String getQueueKey(Long eventId, UserType userType) {
        return String.format(
            userType == UserType.VIP ? VIP_QUEUE_KEY_TEMPLATE : NORMAL_QUEUE_KEY_TEMPLATE,
            eventId
        );
    }
}
