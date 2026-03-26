package com.deepak.ticketflow.service;

import com.deepak.ticketflow.handlers.SeatLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RedisLockService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "seat:lock:";

    // Lua script — atomically delete only if value matches (prevents releasing another thread's lock)
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    /**
     * Try to acquire locks for multiple seats.
     * Seats are sorted before locking to prevent deadlock across concurrent requests.
     * Returns acquired lock keys, or throws if any lock fails.
     */
    public List<String> acquireSeatsLock(Long eventId, List<String> seatNumbers,
                                         String lockValue, Duration ttl) {
        // Sort to ensure consistent lock ordering — prevents deadlock
        List<String> sorted = seatNumbers.stream().sorted().toList();
        List<String> acquired = new ArrayList<>();
        for (String seat : sorted) {
            String key = buildKey(eventId, seat);
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, lockValue, ttl);
            if (Boolean.TRUE.equals(ok)) {
                acquired.add(key);
            } else {
                // Partial acquisition — release what we got and fail fast
                releaseAll(acquired, lockValue);
                throw  new SeatLockException(
                        "Seat " + seat + " is currently being processed. Please try again.");
            }
        }
        return acquired;
    }
    /**
     * Attempt single seat lock — used during booking confirmation.
     */
    public boolean tryAcquire(String lockKey, String lockValue, Duration ttl) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttl));
    }
    public void releaseAll(List<String> lockKeys, String lockValue) {
        lockKeys.forEach(key -> release(key, lockValue));
    }
    public void release(String lockKey, String lockValue) {
        redisTemplate.execute(
                new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }
    public String buildKey(Long eventId, String seatNumber) {
        return LOCK_PREFIX + eventId + ":" + seatNumber;
    }
}