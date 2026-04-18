package com.deepak.ticketflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "queue")
@RefreshScope
@Data
public class QueueConfiguration {
    private boolean enabled = true;
    private Mode mode = Mode.AUTO;
    private Thresholds thresholds = new Thresholds();
    private Weights weights = new Weights();
    private DecisionTtl decisionTtl = new DecisionTtl();
    private Load load = new Load();
    private Rates rates = new Rates();

    public enum Mode {
        AUTO, ALWAYS_QUEUE, NEVER_QUEUE
    }

    @Data
    public static class Thresholds {
        private double softQueue = 0.6;
        private double hardQueue = 0.9;
        private double panicMode = 0.95;
    }

    @Data
    public static class Weights {
        private double session = 0.5;
        private double rps = 0.3;
        private double db = 0.2;
    }

    @Data
    public static class DecisionTtl {
        private int hardQueueSeconds = 10;
        private int softQueueSeconds = 30;
        private int noQueueSeconds = 60;
    }

    @Data
    public static class Load {
        private long maxConcurrentUsers = 1000;
        private long maxRps = 50;
        private int activeUsersTtlSeconds = 10;
        private int rpsCounterTtlSeconds = 2;
        private int rpsShards = 10;
        private int rpsWindowSeconds = 10;
    }

    @Data
    public static class Rates {
        private int vip = 10;
        private int normal = 2;
        private int maxVip = 50;
        private int maxNormal = 10;
    }
}