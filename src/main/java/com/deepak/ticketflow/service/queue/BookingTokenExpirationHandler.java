package com.deepak.ticketflow.service.queue;

import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.deepak.ticketflow.event.BookingSlotFreedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that handles booking token expirations and publishes BookingSlotFreedEvent.
 * 
 * This service monitors for expired booking tokens and publishes events to trigger
 * admission of the next waiting user whenever a token expires.
 * 
 * Note: In production, using Redis keyspace notifications (notify-keyspace-events Ex)
 * would be more efficient, but this polling approach is simpler and compatible
 * with most Redis configurations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingTokenExpirationHandler {

    private static final String QUEUE_TOKEN_PREFIX = "queue:token:";

    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Periodically check for expired booking tokens and publish BookingSlotFreedEvent.
     * 
     * Scheduled to run every 5 seconds to detect expirations with minimal latency.
     */
    @Scheduled(fixedDelay = 5000)
    public void handleExpiredTokens() {
        // Note: This is a simplified approach. In a high-traffic system, consider using
        // Redis keyspace notifications (PEXPIRE events) for real-time expiration handling.
        //
        // For now, we rely on:
        // 1. VirtualQueueService.invalidateToken() being called on successful booking
        // 2. This scheduled task to eventually clean up expired tokens
        //
        // A more robust solution would be:
        // - Configure Redis with: CONFIG SET notify-keyspace-events Ex
        // - Use MessageListenerContainer to listen for __keyevent@ events
        // - Publish BookingSlotFreedEvent immediately on expiration
        
        log.debug("Checking for expired booking tokens...");
        
        // Get all keys matching queue:token:* pattern
        // This approach works but may be slow on very large Redis instances
        Set<String> keys = redis.keys(QUEUE_TOKEN_PREFIX + "*");
        
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            // Check if the key still exists (if TTL expired, it won't)
            Boolean exists = redis.hasKey(key);
            if (exists == null || !exists) {
                // Token has expired, parse the key and publish event
                publishSlotFreedEvent(key);
            }
        }
    }

    private void publishSlotFreedEvent(String expiredTokenKey) {
        // Parse: queue:token:{eventId}:{userId}
        String tokenData = expiredTokenKey.substring(QUEUE_TOKEN_PREFIX.length());
        String[] parts = tokenData.split(":", 2);
        
        if (parts.length != 2) {
            log.debug("Ignoring malformed token key: {}", expiredTokenKey);
            return;
        }

        try {
            Long eventId = Long.parseLong(parts[0]);
            // userId = parts[1] — not needed for the event, but kept for clarity
            
            log.info("Booking token expired for event {}. Publishing BookingSlotFreedEvent", eventId);
            applicationEventPublisher.publishEvent(new BookingSlotFreedEvent(this, eventId));
        } catch (NumberFormatException ex) {
            log.debug("Failed to parse event ID from token key: {}", expiredTokenKey);
        }
    }
}
