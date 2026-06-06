package com.deepak.ticketflow.service.queue;

import com.deepak.ticketflow.dto.queue.QueueJoinResponse;
import com.deepak.ticketflow.dto.queue.QueuePositionResponse;
import com.deepak.ticketflow.Enum.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualQueueService {

    private final StringRedisTemplate redis;

    private static final String TOKEN_PREFIX = "queue:token:";

    private String getQueueKey(Long eventId, UserType userType) {
        return "queue:" + eventId + ":" + userType.name().toLowerCase();
    }

    private String buildTokenKey(Long eventId, Integer userId) {
        return TOKEN_PREFIX + eventId + ":" + userId;
    }

    /**
     * Join queue based on user type
     */
    public QueueJoinResponse joinQueue(Long eventId, Integer userId, UserType userType) {
        String queueKey = getQueueKey(eventId, userType);
        String queueValue = eventId + ":" + userId;

        // Check for duplicate entry
        List<String> list = redis.opsForList().range(queueKey, 0, -1);
        Long positionInList = null;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).equals(queueValue)) {
                    positionInList = (long) i;
                    break;
                }
            }
        }

        if (positionInList != null) {
            // User already in queue
            log.info("User {} ({}) already in queue at position {}", userId, userType, positionInList + 1);
            int estimatedWaitSeconds = calculateWaitTime(userType, positionInList);
            return QueueJoinResponse.builder()
                    .mode("QUEUE")
                    .position(positionInList + 1)
                    .estimatedWaitSeconds(estimatedWaitSeconds)
                    .userType(userType)
                    .build();
        }

        Long position = redis.opsForList().rightPush(queueKey, queueValue);
        log.info("User {} ({}) joined queue at position {}", userId, userType, position);

        // Calculate estimated wait time
        int estimatedWaitSeconds = calculateWaitTime(userType, position - 1);

        return QueueJoinResponse.builder()
                .mode("QUEUE")
                .position(position)
                .estimatedWaitSeconds(estimatedWaitSeconds)
                .userType(userType)
                .build();
    }

    /**
     * Get current queue position
     */
    public QueuePositionResponse getPosition(Long eventId, Integer userId, UserType userType) {
        String queueKey = getQueueKey(eventId, userType);
        String queueValue = eventId + ":" + userId;

        // Check if already has active token
        String existingToken = redis.opsForValue().get(buildTokenKey(eventId, userId));
        if (existingToken != null) {
            Long expirySeconds = redis.getExpire(buildTokenKey(eventId, userId), TimeUnit.SECONDS);
            Integer tokenExpirySeconds = expirySeconds != null ? expirySeconds.intValue() : null;
            return QueuePositionResponse.builder()
                    .status("ready_to_book")
                    .token(existingToken)
                    .tokenExpirySeconds(tokenExpirySeconds)
                    .build();
        }

        // Find position using range query since indexOf doesn't exist
        List<String> list = redis.opsForList().range(queueKey, 0, -1);
        Long positionInOwnQueue = null;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).equals(queueValue)) {
                    positionInOwnQueue = (long) i;
                    break;
                }
            }
        }

        if (positionInOwnQueue == null) {
            return QueuePositionResponse.builder()
                    .status("not_in_queue")
                    .build();
        }

        if (userType == UserType.VIP) {
            return QueuePositionResponse.builder()
                    .status("in_queue")
                    .position(positionInOwnQueue + 1)
                    .aheadCount(positionInOwnQueue)
                    .userType(UserType.VIP)
                    .build();
        } else {
            Long vipAheadCount = redis.opsForList().size(getQueueKey(eventId, UserType.VIP));
            long totalAhead = (vipAheadCount != null ? vipAheadCount : 0) + positionInOwnQueue;

            return QueuePositionResponse.builder()
                    .status("in_queue")
                    .position(totalAhead + 1)
                    .aheadCount(totalAhead)
                    .vipAheadCount(vipAheadCount != null ? vipAheadCount : 0)
                    .normalAheadCount(positionInOwnQueue)
                    .userType(UserType.NORMAL)
                    .build();
        }
    }

    /**
     * Generate booking token when user reaches front of queue
     */
    public String generateBookingToken(Long eventId, Integer userId, UserType userType, int expiryMinutes) {
        String token = UUID.randomUUID().toString();
        String tokenKey = buildTokenKey(eventId, userId);
        Duration expiry = Duration.ofMinutes(expiryMinutes);

        // Use setIfAbsent to prevent token overwrite (atomic operation)
        Boolean success = redis.opsForValue().setIfAbsent(tokenKey, token, expiry);
        if (Boolean.FALSE.equals(success)) {
            // Token already exists, return existing token
            String existingToken = redis.opsForValue().get(tokenKey);
            log.info("Token already exists for user {} ({}), returning existing token", userId, userType);
            return existingToken;
        }

        redis.opsForValue().set("token:" + token, eventId + ":" + userId, expiry);

        log.info("Generated booking token for user {} ({}), expires in {} minutes",
                userId, userType, expiryMinutes);
        return token;
    }

    /**
     * Validate booking token
     */
    public boolean validateToken(String token, Long eventId, Integer userId) {
        String storedUserId = redis.opsForValue().get("token:" + token);
        return storedUserId != null && storedUserId.equals(eventId + ":" + userId);
    }

    /**
     * Invalidate token after booking
     */
    public void invalidateToken(String token, Long eventId, Integer userId) {
        redis.delete("token:" + token);
        redis.delete(buildTokenKey(eventId, userId));
    }

    /**
     * Remove user from queue (when they get token)
     */
    public void removeFromQueue(Long eventId, Integer userId, UserType userType) {
        String queueKey = getQueueKey(eventId, userType);
        String queueValue = eventId + ":" + userId;
        redis.opsForList().remove(queueKey, 1, queueValue);
    }

    private int calculateWaitTime(UserType userType, Long position) {
        // Improved: Use userType-based processing rates
        int processingRate = userType == UserType.VIP ? 10 : 5;
        return (int) (position / processingRate);
    }
}