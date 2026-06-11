package com.deepak.ticketflow.service.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HyperLogLogOperations;

import com.deepak.ticketflow.Enum.UserType;
import com.deepak.ticketflow.config.QueueConfiguration;
import com.deepak.ticketflow.dto.queue.QueueDecision;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class QueueDecisionServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HyperLogLogOperations<String, String> hllOperations;

    private MeterRegistry meterRegistry;
    private QueueConfiguration queueConfig;
    private QueueDecisionService decisionService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        meterRegistry = new SimpleMeterRegistry();
        
        queueConfig = new QueueConfiguration();
        queueConfig.setEnabled(true);
        queueConfig.setMode(QueueConfiguration.Mode.AUTO);
        
        QueueConfiguration.Thresholds thresholds = new QueueConfiguration.Thresholds();
        thresholds.setSoftQueue(0.6);
        thresholds.setHardQueue(0.9);
        queueConfig.setThresholds(thresholds);
        
        QueueConfiguration.Weights weights = new QueueConfiguration.Weights();
        weights.setSession(0.5);
        weights.setRps(0.3);
        weights.setDb(0.2);
        queueConfig.setWeights(weights);

        QueueConfiguration.Load load = new QueueConfiguration.Load();
        load.setMaxConcurrentUsers(1000);
        load.setMaxRps(50);
        load.setRpsWindowSeconds(10);
        load.setRpsShards(10);
        queueConfig.setLoad(load);

        when(redis.opsForList()).thenReturn(listOperations);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForHyperLogLog()).thenReturn(hllOperations);

        decisionService = new QueueDecisionService(redis, queueConfig, meterRegistry);
        decisionService.init();
    }

    @Test
    public void testDecide_WhenQueueIsEmptyAndLoadIsLow_ReturnsNoQueue() {
        // Mock that both VIP and NORMAL queues are empty (size = 0)
        when(listOperations.size("queue:1:vip")).thenReturn(0L);
        when(listOperations.size("queue:1:normal")).thenReturn(0L);

        // Load factor is 0.0 (default uncalculated/mocked is low)
        QueueDecision decision = decisionService.decide(1L, 100, UserType.NORMAL);
        assertEquals(QueueDecision.NO_QUEUE, decision);
    }

    @Test
    public void testDecide_WhenQueueIsNotEmptyAndLoadIsLow_ReturnsSoftQueue() {
        // Mock that normal queue has 1 person
        when(listOperations.size("queue:1:vip")).thenReturn(0L);
        when(listOperations.size("queue:1:normal")).thenReturn(1L);

        QueueDecision decision = decisionService.decide(1L, 100, UserType.NORMAL);
        // Should bypass low load factor check and return SOFT_QUEUE
        assertEquals(QueueDecision.SOFT_QUEUE, decision);
    }

    @Test
    public void testDecide_WhenQueueIsNotEmptyButUserIsVip_ReturnsNoQueue() {
        // Mock that normal queue has 1 person
        when(listOperations.size("queue:1:vip")).thenReturn(0L);
        when(listOperations.size("queue:1:normal")).thenReturn(1L);

        QueueDecision decision = decisionService.decide(1L, 100, UserType.VIP);
        // VIPs always bypass the queue
        assertEquals(QueueDecision.NO_QUEUE, decision);
    }
}
