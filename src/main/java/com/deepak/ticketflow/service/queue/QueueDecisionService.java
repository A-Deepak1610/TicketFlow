package com.deepak.ticketflow.service.queue;

import com.deepak.ticketflow.config.QueueConfiguration;
import com.deepak.ticketflow.dto.queue.QueueDecision;
import com.deepak.ticketflow.model.queue.UserType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueDecisionService {

    private final StringRedisTemplate redis;
    private final QueueConfiguration queueConfig;

    private volatile double cachedLoadFactor = 0.0;
    private final AtomicLong cachedActiveUsers = new AtomicLong(0);

    // Cache for sticky decisions
    private final Cache<String, QueueDecision> decisionCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofMinutes(5))
                    .maximumSize(10000)
                    .build();

    @PostConstruct
    public void init() {
        // Initialize cache
        refreshLoadFactor();
    }

    @Scheduled(fixedRate = 500) // Calculate every 500ms
    public void refreshLoadFactor() {
        try {
            this.cachedLoadFactor = calculateLoadFactor();
            log.debug("Refreshed load factor: {}", cachedLoadFactor);
        } catch (Exception e) {
            log.error("Failed to refresh load factor", e);
            this.cachedLoadFactor = 0.8; // Conservative fallback
        }
    }

    @CircuitBreaker(name = "queue-decision", fallbackMethod = "getFallbackDecision")
    public QueueDecision decide(Long eventId, Integer userId, UserType userType) {
        // VIP users bypass queue entirely
        if (userType == UserType.VIP) {
            return QueueDecision.NO_QUEUE;
        }

        // Check sticky decision
        String cacheKey = eventId + ":" + userId;
        QueueDecision cached = decisionCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Check feature flags
        if (!queueConfig.isEnabled()) {
            return QueueDecision.NO_QUEUE;
        }

        if (queueConfig.getMode() == QueueConfiguration.Mode.ALWAYS_QUEUE) {
            return QueueDecision.HARD_QUEUE;
        }

        if (queueConfig.getMode() == QueueConfiguration.Mode.NEVER_QUEUE) {
            return QueueDecision.NO_QUEUE;
        }

        // Auto mode - use load factor
        QueueDecision decision;
        if (cachedLoadFactor < queueConfig.getThresholds().getSoftQueue()) {
            decision = QueueDecision.NO_QUEUE;
        } else if (cachedLoadFactor < queueConfig.getThresholds().getHardQueue()) {
            decision = QueueDecision.SOFT_QUEUE;
        } else {
            decision = QueueDecision.HARD_QUEUE;
        }

        // Cache decision for this user
        decisionCache.put(cacheKey, decision);

        return decision;
    }

    private QueueDecision getFallbackDecision(Long eventId, Integer userId, UserType userType, Exception e) {
        log.warn("Circuit breaker open, using fallback decision for user {}", userId);
        // Conservative: put in queue if VIP? No, still let VIP through
        return userType == UserType.VIP ? QueueDecision.NO_QUEUE : QueueDecision.HARD_QUEUE;
    }

    private double calculateLoadFactor() {
        // Factor 1: Active sessions (40% weight)
        double sessionFactor = getSessionLoadFactor();

        // Factor 2: Request rate (30% weight)
        double rateFactor = getRequestRateFactor();

        // Factor 3: Database pressure (20% weight)
        double dbFactor = getDatabasePressureFactor();

        // Panic mode: DB at 95%+
        if (dbFactor > queueConfig.getThresholds().getPanicMode()) {
            return 1.0;
        }

        // Factor 4: Urgency (only matters when system is busy)
        double urgencyFactor = (sessionFactor > 0.5) ? getUrgencyFactor() : 0.0;

        return (sessionFactor * 0.4) + (rateFactor * 0.3) + (dbFactor * 0.2) + (urgencyFactor * 0.1);
    }

    private double getSessionLoadFactor() {
        // Track active users in Redis with HyperLogLog or simple counter
        String activeKey = "active:users";
        Long activeUsers = redis.opsForValue().increment(activeKey);
        redis.expire(activeKey, Duration.ofSeconds(10));

        if (activeUsers != null) {
            cachedActiveUsers.set(activeUsers);
        }

        long maxConcurrent = 10000; // Configurable
        return Math.min(1.0, (double) cachedActiveUsers.get() / maxConcurrent);
    }

    private double getRequestRateFactor() {
        String key = "rps:" + (System.currentTimeMillis() / 1000);
        Long requestsThisSecond = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofSeconds(2));

        long maxRPS = 500; // Configurable
        return Math.min(1.0, (double) (requestsThisSecond != null ? requestsThisSecond : 0) / maxRPS);
    }

    private double getDatabasePressureFactor() {
        // Simplified - in production, use HikariCP metrics
        // For now, return moderate value
        return 0.5;
    }

    private double getUrgencyFactor() {
        // Simplified - check remaining tickets
        // For now, return low urgency
        return 0.3;
    }

    public void clearDecisionForUser(Long eventId, Integer userId) {
        decisionCache.invalidate(eventId + ":" + userId);
    }
}