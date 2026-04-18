package com.deepak.ticketflow.service.queue;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.deepak.ticketflow.Enum.UserType;
import com.deepak.ticketflow.config.QueueConfiguration;
import com.deepak.ticketflow.dto.queue.QueueDecision;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueDecisionService {

    private static final String ACTIVE_USERS_HLL_PREFIX = "active:users";
    private static final int ACTIVE_USERS_WINDOW_SECONDS = 10;
    private static final String RPS_KEY_PREFIX = "rps:";
    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) " +
                    "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                    "return current",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final QueueConfiguration queueConfig;
    private final MeterRegistry meterRegistry;

    private volatile double cachedLoadFactor = 0.0;
    private final AtomicLong cachedActiveUsers = new AtomicLong(0);
    private final AtomicLong cachedLoadFactorBasisPoints = new AtomicLong(0);

    private Counter queueDecisionCounter;

    // Cache for sticky decisions
    private final Cache<String, QueueDecision> decisionCache =
            Caffeine.newBuilder()
                    .expireAfter(new Expiry<String, QueueDecision>() {
                        @Override
                        public long expireAfterCreate(String key, QueueDecision value, long currentTime) {
                            return ttlForDecision(value).toNanos();
                        }

                        @Override
                        public long expireAfterUpdate(String key, QueueDecision value, long currentTime, long currentDuration) {
                            return ttlForDecision(value).toNanos();
                        }

                        @Override
                        public long expireAfterRead(String key, QueueDecision value, long currentTime, long currentDuration) {
                            return currentDuration;
                        }
                    })
                    .maximumSize(10000)
                    .build();

    @PostConstruct
    public void init() {
        // Initialize cache
        this.queueDecisionCounter = Counter.builder("queue.decision.total")
                .description("Total queue decisions by outcome")
                .register(meterRegistry);
        meterRegistry.gauge("queue.load.factor", cachedLoadFactorBasisPoints, value -> value.get() / 10_000.0d);
        refreshLoadFactor();
    }

    @Scheduled(fixedRate = 500) // Calculate every 500ms
    public void refreshLoadFactor() {
        log.debug("Refreshed load factor calculation cycle");
        try {
            this.cachedLoadFactor = calculateLoadFactor();
            this.cachedLoadFactorBasisPoints.set((long) (this.cachedLoadFactor * 10_000));
            log.debug("Refreshed load factor: {}", cachedLoadFactor);
        } catch (Exception e) {
            log.error("Failed to refresh load factor", e);
            this.cachedLoadFactor = 0.8; // Conservative fallback
            this.cachedLoadFactorBasisPoints.set((long) (this.cachedLoadFactor * 10_000));
        }
    }

    @CircuitBreaker(name = "queue-decision", fallbackMethod = "getFallbackDecision")
    public QueueDecision decide(Long eventId, Integer userId, UserType userType) {
        recordUserSignal(userId);

        // VIP users bypass queue entirely
        if (userType == UserType.VIP) {
            return QueueDecision.NO_QUEUE;
        }

        // Check sticky decision
        String cacheKey = eventId + ":" + userId;
        QueueDecision cached = decisionCache.getIfPresent(cacheKey); //we use Caffeine for "If we already decided for this user, don’t recompute — just reuse it"
        if (cached != null) {
            recordDecision(cached);
            return cached;
        }

        // Check feature flags
        if (!queueConfig.isEnabled()) {
            recordDecision(QueueDecision.NO_QUEUE);
            return QueueDecision.NO_QUEUE;
        }

        if (queueConfig.getMode() == QueueConfiguration.Mode.ALWAYS_QUEUE) {
            recordDecision(QueueDecision.HARD_QUEUE);
            return QueueDecision.HARD_QUEUE;
        }

        if (queueConfig.getMode() == QueueConfiguration.Mode.NEVER_QUEUE) {
            recordDecision(QueueDecision.NO_QUEUE);
            return QueueDecision.NO_QUEUE;
        }

        // Backpressure: force queue when request-rate pressure exceeds capacity.
        if (getRequestRateFactor() >= 1.0) {
            QueueDecision hardQueue = QueueDecision.HARD_QUEUE;
            decisionCache.put(cacheKey, hardQueue);
            recordDecision(hardQueue);
            return hardQueue;
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
        recordDecision(decision);

        return decision;
    }

    private QueueDecision getFallbackDecision(Long eventId, Integer userId, UserType userType, Exception e) {
        log.warn("Circuit breaker open, using fallback decision for user {}", userId);
        return userType == UserType.VIP ? QueueDecision.NO_QUEUE : QueueDecision.SOFT_QUEUE;
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

        QueueConfiguration.Weights weights = queueConfig.getWeights();
        double weightedLoad =
                (sessionFactor * weights.getSession())
                        + (rateFactor * weights.getRps())
                        + (dbFactor * weights.getDb());

        return Math.min(1.0, weightedLoad);
    }

    private double getSessionLoadFactor() {
        // Get active users from current fixed time window
        long currentSecond = System.currentTimeMillis() / 1000;
        long window = currentSecond / ACTIVE_USERS_WINDOW_SECONDS;
        String windowKey = ACTIVE_USERS_HLL_PREFIX + ":" + window;
        
        Long activeUsers = redis.opsForHyperLogLog().size(windowKey);

        if (activeUsers != null) {
            cachedActiveUsers.set(activeUsers);
        }

        long maxConcurrent = Math.max(1, queueConfig.getLoad().getMaxConcurrentUsers());
        return Math.min(1.0, (double) cachedActiveUsers.get() / maxConcurrent);
    }

    private double getRequestRateFactor() {
        int windowSeconds = Math.max(1, queueConfig.getLoad().getRpsWindowSeconds());
        int shards = Math.max(1, queueConfig.getLoad().getRpsShards());
        //this will seconds from 1st jan 1970 till now
        long currentSecond = System.currentTimeMillis() / 1000;

        long totalRequests = 0;
        for (int secondOffset = 0; secondOffset < windowSeconds; secondOffset++) {
            long second = currentSecond - secondOffset;
            for (int shard = 0; shard < shards; shard++) {
                String shardKey = RPS_KEY_PREFIX + second + ":" + shard;//Each second is split into 10 shards to avoid hot key problem in redis
                String count = redis.opsForValue().get(shardKey);
                totalRequests += parseLong(count);
            }
        }
        double averageRps = (double) totalRequests / windowSeconds;
        long maxRps = Math.max(1, queueConfig.getLoad().getMaxRps());
        return Math.min(1.0, averageRps / maxRps);
    }

    private double getDatabasePressureFactor() {
        try {
            io.micrometer.core.instrument.Gauge activeGauge = meterRegistry
                    .find("hikaricp.connections.active")
                    .gauge();
            io.micrometer.core.instrument.Gauge maxGauge = meterRegistry
                    .find("hikaricp.connections.max")
                    .gauge();
            io.micrometer.core.instrument.Gauge pendingGauge = meterRegistry
                    .find("hikaricp.connections.pending")
                    .gauge();

            if (activeGauge == null || maxGauge == null) {
                return 0.5; // fallback
            }

            Double active = activeGauge.value();
            Double max = maxGauge.value();
            Double pending = pendingGauge != null ? pendingGauge.value() : null;

            if (active == null || max == null || max == 0) {
                return 0.5; // fallback
            }
            // Base pressure: how full the pool is
            double usageFactor = active / max;
            // Extra pressure: waiting threads
            double pendingFactor = (pending != null ? pending : 0) / max;
            // Combine (weighted)
            double pressure = (usageFactor * 0.7) + (pendingFactor * 0.3);
            return Math.min(1.0, pressure);
        } catch (Exception e) {
            log.warn("Failed to read DB metrics, using fallback");
            return 0.5;
        }
    }

    private void recordUserSignal(Integer userId) {
        if (userId == null) {
            return;
        }

        // Use fixed time window instead of continuously extending TTL
        long currentSecond = System.currentTimeMillis() / 1000;
        long window = currentSecond / ACTIVE_USERS_WINDOW_SECONDS;
        String windowKey = ACTIVE_USERS_HLL_PREFIX + ":" + window;
        
        redis.opsForHyperLogLog().add(windowKey, userId.toString());
        // Set expiration to 2x window size to keep previous window data available
        redis.expire(windowKey, Duration.ofSeconds(ACTIVE_USERS_WINDOW_SECONDS * 2));
        
        // Record RPS with sharding to prevent hot keys
        long second = currentSecond;
        int shard = getShardForUser(userId);
        String shardKey = RPS_KEY_PREFIX + second + ":" + shard;

        redis.execute(
                INCR_WITH_EXPIRE_SCRIPT,
                Collections.singletonList(shardKey),
                String.valueOf(Math.max(1, queueConfig.getLoad().getRpsCounterTtlSeconds()))
        );
    }

    private int getShardForUser(Integer userId) {
        int shards = Math.max(1, queueConfig.getLoad().getRpsShards());
        return Math.floorMod(Objects.requireNonNullElse(userId, 0), shards);
    }

    private long parseLong(String value) {
        if (value == null) {
            return 0L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Duration ttlForDecision(QueueDecision decision) {
        QueueConfiguration.DecisionTtl ttl = queueConfig.getDecisionTtl();

        return switch (decision) {
            case HARD_QUEUE -> Duration.ofSeconds(Math.max(1, ttl.getHardQueueSeconds()));
            case SOFT_QUEUE -> Duration.ofSeconds(Math.max(1, ttl.getSoftQueueSeconds()));
            case NO_QUEUE -> Duration.ofSeconds(Math.max(1, ttl.getNoQueueSeconds()));
        };
    }

    private void recordDecision(QueueDecision decision) {
        queueDecisionCounter.increment();
        meterRegistry.counter("queue.decision.by_type", Tags.of("decision", decision.name())).increment();
        log.info("LoadFactor={}, Decision={}", cachedLoadFactor, decision);
    }

    public void clearDecisionForUser(Long eventId, Integer userId) {

        decisionCache.invalidate(eventId + ":" + userId);
    }
}