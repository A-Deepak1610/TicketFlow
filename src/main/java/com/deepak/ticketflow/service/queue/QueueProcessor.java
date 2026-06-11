package com.deepak.ticketflow.service.queue;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.deepak.ticketflow.config.QueueConfiguration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueProcessor {
    private final QueueNotificationService queueNotificationService;
    private final StringRedisTemplate redis;
    private final VirtualQueueService queueService;
    private final QueueConfiguration queueConfig;

    private static final String VIP_QUEUE = "queue:vip";
    private static final String NORMAL_QUEUE = "queue:normal";

    private final AtomicInteger currentVipRate = new AtomicInteger(10);
    private final AtomicInteger currentNormalRate = new AtomicInteger(2);

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        // DEPRECATED: Scheduling disabled in favor of event-driven queue admission
        // When a booking slot becomes available, BookingSlotFreedEvent is published
        // and QueueAdmissionListener immediately admits the next waiting user
        if (!queueConfig.isEnabled()) {
            return;
        }
        log.debug("QueueProcessor.processQueue() is disabled. Event-driven admission is now active.");
    }
}