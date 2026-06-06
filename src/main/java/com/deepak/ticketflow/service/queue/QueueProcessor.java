package com.deepak.ticketflow.service.queue;

import com.deepak.ticketflow.config.QueueConfiguration;
import com.deepak.ticketflow.Enum.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

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
        if (!queueConfig.isEnabled()) {
            return;
        }

        // Add jitter to prevent thundering herd
        // Because to avoid  Thundering Herd Problem.
        int jitter = ThreadLocalRandom.current().nextInt(0, 100);
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Process VIPs first
        int vipProcessed = 0;
        while (vipProcessed < currentVipRate.get()) {
            String queueEntry = redis.opsForList().leftPop(VIP_QUEUE);
            if (queueEntry == null) break;

            processUser(queueEntry, UserType.VIP);
            vipProcessed++;
        }

        // Process normal users
        int normalProcessed = 0;
        while (normalProcessed < currentNormalRate.get()) {
            String queueEntry = redis.opsForList().leftPop(NORMAL_QUEUE);
            if (queueEntry == null) break;

            processUser(queueEntry, UserType.NORMAL);
            normalProcessed++;
        }

        if (vipProcessed > 0 || normalProcessed > 0) {
            log.debug("Processed {} VIP and {} normal users", vipProcessed, normalProcessed);
        }

        // Adapt rates based on system health
        adaptProcessingRates();
    }

    private void processUser(String queueEntry, UserType userType) {
        String[] parts = queueEntry.split(":", 2);
        Long eventId = Long.parseLong(parts[0]);
        Integer userId = Integer.parseInt(parts[1]);

        int expiryMinutes = userType == UserType.VIP ? 5 : 5;

        // Generate booking token
        String token = queueService.generateBookingToken(eventId, userId, userType, expiryMinutes);

        log.info("User {} ({}) is ready to book with token {}", userId, userType, token);
        queueNotificationService.sendBookingWindow(userId,token,expiryMinutes);
        // notifyUser(userId, token, expiryMinutes);
    }
    private void adaptProcessingRates() {
        // Monitor success rate - simplified version
        // In production, track actual booking success rate

        int newVipRate = queueConfig.getRates().getVip();
        int newNormalRate = queueConfig.getRates().getNormal();

        // Check queue sizes
        Long vipQueueSize = redis.opsForList().size(VIP_QUEUE);
        Long normalQueueSize = redis.opsForList().size(NORMAL_QUEUE);

        // Increase rates if queues are large
        if (vipQueueSize != null && vipQueueSize > 1000) {
            newVipRate = Math.min(queueConfig.getRates().getMaxVip(), newVipRate + 5);
        }

        if (normalQueueSize != null && normalQueueSize > 5000) {
            newNormalRate = Math.min(queueConfig.getRates().getMaxNormal(), newNormalRate + 2);
        }

        currentVipRate.set(newVipRate);
        currentNormalRate.set(newNormalRate);
    }
}