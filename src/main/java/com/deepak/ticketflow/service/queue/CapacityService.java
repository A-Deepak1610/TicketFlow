package com.deepak.ticketflow.service.queue;

import java.time.Duration;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.deepak.ticketflow.config.QueueConfiguration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityService {

    private static final String RPS_KEY_PREFIX = "rps:";
    private static final String ACTIVE_BOOKING_EVENTS_KEY = "active:booking:events";

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final QueueConfiguration queueConfig;

    public int calculateMaxActiveBookings() {
        double dbPressure = getDatabasePressureFactor();
        double requestRateFactor = getRequestRateFactor();
        double activeUserLoad = getActiveUserLoadFactor();
        double cpuUtilization = getCpuUtilizationFactor();
        double paymentLatency = getPaymentLatencyFactor();

        double loadFactor = (dbPressure * 0.35)
                + (requestRateFactor * 0.20)
                + (activeUserLoad * 0.25)
                + (cpuUtilization * 0.15)
                + (paymentLatency * 0.05);

        int capacity;
        if (loadFactor > 0.9d) {
            capacity = 300;
        } else if (loadFactor > 0.7d) {
            capacity = 700;
        } else {
            capacity = 1500;
        }

        log.debug("Calculated active booking capacity={}, loadFactor={}, dbPressure={}, requestRateFactor={}, activeUserLoad={}, cpuUtilization={}, paymentLatency={}",
                capacity, loadFactor, dbPressure, requestRateFactor, activeUserLoad, cpuUtilization, paymentLatency);
        return capacity;
    }

    private double getDatabasePressureFactor() {
        try {
            Gauge activeGauge = meterRegistry.find("hikaricp.connections.active").gauge();
            Gauge maxGauge = meterRegistry.find("hikaricp.connections.max").gauge();
            Gauge pendingGauge = meterRegistry.find("hikaricp.connections.pending").gauge();

            if (activeGauge == null || maxGauge == null) {
                return 0.5d;
            }

            Double active = activeGauge.value();
            Double max = maxGauge.value();
            Double pending = pendingGauge != null ? pendingGauge.value() : 0.0d;

            if (active == null || max == null || max == 0.0d) {
                return 0.5d;
            }

            double usageFactor = active / max;
            double pendingFactor = pending / Math.max(1.0d, max);
            return Math.min(1.0d, (usageFactor * 0.7d) + (pendingFactor * 0.3d));
        } catch (Exception ex) {
            log.debug("Falling back to neutral database pressure factor", ex);
            return 0.5d;
        }
    }

    private double getRequestRateFactor() {
        int windowSeconds = Math.max(1, queueConfig.getLoad().getRpsWindowSeconds());
        int shards = Math.max(1, queueConfig.getLoad().getRpsShards());
        long currentSecond = System.currentTimeMillis() / 1000;

        long totalRequests = 0L;
        for (int secondOffset = 0; secondOffset < windowSeconds; secondOffset++) {
            long second = currentSecond - secondOffset;
            for (int shard = 0; shard < shards; shard++) {
                String shardKey = RPS_KEY_PREFIX + second + ":" + shard;
                String count = redis.opsForValue().get(shardKey);
                if (count != null) {
                    try {
                        totalRequests += Long.parseLong(count);
                    } catch (NumberFormatException ex) {
                        log.debug("Ignoring malformed RPS counter value {} for key {}", count, shardKey);
                    }
                }
            }
        }

        double averageRps = (double) totalRequests / windowSeconds;
        long maxRps = Math.max(1L, queueConfig.getLoad().getMaxRps());
        return Math.min(1.0d, averageRps / maxRps);
    }

    private double getActiveUserLoadFactor() {
        Set<String> eventIds = redis.opsForSet().members(ACTIVE_BOOKING_EVENTS_KEY);
        if (eventIds == null || eventIds.isEmpty()) {
            return 0.0d;
        }

        long activeUsers = 0L;
        for (String eventId : eventIds) {
            String activeBookingKey = "active:booking:" + eventId;
            Long setSize = redis.opsForSet().size(activeBookingKey);
            if (setSize != null) {
                activeUsers += setSize;
            }
        }

        long maxConcurrentUsers = Math.max(1L, queueConfig.getLoad().getMaxConcurrentUsers());
        return Math.min(1.0d, (double) activeUsers / maxConcurrentUsers);
    }

    private double getCpuUtilizationFactor() {
        Gauge cpuGauge = meterRegistry.find("system.cpu.usage").gauge();
        if (cpuGauge == null) {
            cpuGauge = meterRegistry.find("process.cpu.usage").gauge();
        }

        if (cpuGauge == null) {
            return 0.4d;
        }

        Double value = cpuGauge.value();
        if (value == null) {
            return 0.4d;
        }

        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double getPaymentLatencyFactor() {
        Gauge paymentLatencyGauge = meterRegistry.find("payment.latency").gauge();
        if (paymentLatencyGauge == null) {
            return 0.0d;
        }

        Double latency = paymentLatencyGauge.value();
        if (latency == null) {
            return 0.0d;
        }

        // Placeholder for future enhancement: normalize against a 1 second target.
        return Math.max(0.0d, Math.min(1.0d, latency / 1000.0d));
    }
}
