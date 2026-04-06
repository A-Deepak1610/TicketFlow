package com.deepak.ticketflow.service.queue;

import com.deepak.ticketflow.dto.queue.QueueJoinResponse;
import com.deepak.ticketflow.dto.queue.QueuePositionResponse;
import com.deepak.ticketflow.model.queue.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualQueueService {

    private final StringRedisTemplate redis;

    private static final String VIP_QUEUE = "queue:vip";
    private static final String NORMAL_QUEUE = "queue:normal";
    private static final String TOKEN_PREFIX = "queue:token:";

    /**
     * Join queue based on user type
     */
    public QueueJoinResponse joinQueue(Long eventId, Integer userId, UserType userType) {
        String queueKey = userType == UserType.VIP ? VIP_QUEUE : NORMAL_QUEUE;
        String queueValue = eventId + ":" + userId;

        Long position = redis.opsForList().rightPush(queueKey, queueValue);
        log.info("User {} ({}) joined queue at position {}", userId, userType, position);

        // Calculate estimated wait time
        int estimatedWaitSeconds = calculateWaitTime(userType, position);

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
        String queueKey = userType == UserType.VIP ? VIP_QUEUE : NORMAL_QUEUE;
        String queueValue = eventId + ":" + userId;

        // Check if already has active token
        String existingToken = redis.opsForValue().get(TOKEN_PREFIX + userId);
        if (existingToken != null) {
            return QueuePositionResponse.builder()
                    .status("ready_to_book")
                    .token(existingToken)
                    .tokenExpirySeconds((int) redis.getExpire(TOKEN_PREFIX + userId, TimeUnit.SECONDS))
                    .build();
        }

        Long positionInOwnQueue = redis.opsForList().indexOf(queueKey, queueValue);

        if (positionInOwnQueue == null || positionInOwnQueue == -1) {
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
            Long vipAheadCount = redis.opsForList().size(VIP_QUEUE);
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
    public String generateBookingToken(Integer userId, UserType userType, int expiryMinutes) {
        String token = UUID.randomUUID().toString();
        String tokenKey = TOKEN_PREFIX + userId;
        redis.opsForValue().set(tokenKey, token, Duration.ofMinutes(expiryMinutes));
        redis.opsForValue().set("token:" + token, String.valueOf(userId), Duration.ofMinutes(expiryMinutes));

        log.info("Generated booking token for user {} ({}), expires in {} minutes",
                userId, userType, expiryMinutes);
        return token;
    }

    /**
     * Validate booking token
     */
    public boolean validateToken(String token, Integer userId) {
        String storedUserId = redis.opsForValue().get("token:" + token);
        return storedUserId != null && storedUserId.equals(String.valueOf(userId));
    }

    /**
     * Invalidate token after booking
     */
    public void invalidateToken(String token, Integer userId) {
        redis.delete("token:" + token);
        redis.delete(TOKEN_PREFIX + userId);
    }

    /**
     * Remove user from queue (when they get token)
     */
    public void removeFromQueue(Long eventId, Integer userId, UserType userType) {
        String queueKey = userType == UserType.VIP ? VIP_QUEUE : NORMAL_QUEUE;
        String queueValue = eventId + ":" + userId;
        redis.opsForList().remove(queueKey, 1, queueValue);
    }

    private int calculateWaitTime(UserType userType, Long position) {
        // Assume VIPs processed at 10/sec, normal at 2/sec
        if (userType == UserType.VIP) {
            return (int) (position / 10);
        } else {
            Long vipAhead = redis.opsForList().size(VIP_QUEUE);
            long effectivePosition = (vipAhead != null ? vipAhead : 0) + position;
            return (int) (effectivePosition / 2);
        }
    }
}