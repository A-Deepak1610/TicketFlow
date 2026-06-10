package com.deepak.ticketflow.service.queue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionControllerService {

    private static final String ACTIVE_BOOKING_PREFIX = "active:booking:";
    private static final String ACTIVE_BOOKING_EVENTS_KEY = "active:booking:events";
    private static final String QUEUE_TOKEN_PREFIX = "queue:token:";

    private final StringRedisTemplate redis;
    private final CapacityService capacityService;

    public int getAvailableSlots(Long eventId) {
        if (eventId == null) {
            return 0;
        }

        int maxActiveBookings = Math.max(0, capacityService.calculateMaxActiveBookings());
        Long activeUsers = redis.opsForSet().size(activeBookingKey(eventId));
        long currentActiveUsers = activeUsers != null ? activeUsers : 0L;
        long availableSlots = (long) maxActiveBookings - currentActiveUsers;

        return (int) Math.max(0L, availableSlots);
    }

    public void registerActiveBooking(Long eventId, Integer userId) {
        if (eventId == null || userId == null) {
            return;
        }

        redis.opsForSet().add(activeBookingKey(eventId), userId.toString());
        redis.opsForSet().add(ACTIVE_BOOKING_EVENTS_KEY, eventId.toString());
    }

    public void releaseActiveBooking(Long eventId, Integer userId) {
        if (eventId == null || userId == null) {
            return;
        }

        String key = activeBookingKey(eventId);
        redis.opsForSet().remove(key, userId.toString());

        Long remaining = redis.opsForSet().size(key);
        if (remaining == null || remaining <= 0L) {
            redis.opsForSet().remove(ACTIVE_BOOKING_EVENTS_KEY, eventId.toString());
        }
    }

    public void releaseExpiredBookingToken(String expiredKey) {
        if (expiredKey == null || !expiredKey.startsWith(QUEUE_TOKEN_PREFIX)) {
            return;
        }

        String tokenData = expiredKey.substring(QUEUE_TOKEN_PREFIX.length());
        String[] parts = tokenData.split(":", 2);
        if (parts.length != 2) {
            return;
        }

        try {
            Long eventId = Long.parseLong(parts[0]);
            Integer userId = Integer.parseInt(parts[1]);
            releaseActiveBooking(eventId, userId);
            log.debug("Released expired booking token for event {} and user {}", eventId, userId);
        } catch (NumberFormatException ex) {
            log.debug("Ignoring expired redis key {} because it is not a booking token", expiredKey);
        }
    }

    public Set<Long> getTrackedActiveEventIds() {
        Set<String> members = redis.opsForSet().members(ACTIVE_BOOKING_EVENTS_KEY);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }

        Set<Long> eventIds = new LinkedHashSet<>();
        for (String member : members) {
            try {
                eventIds.add(Long.parseLong(member));
            } catch (NumberFormatException ex) {
                log.debug("Skipping invalid active booking event id {}", member);
            }
        }
        return eventIds;
    }

    private String activeBookingKey(Long eventId) {
        return ACTIVE_BOOKING_PREFIX + eventId;
    }
}
