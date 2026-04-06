package com.deepak.ticketflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "queue")
@RefreshScope
@Data
public class QueueConfiguration {
    private boolean enabled = true;
    private Mode mode = Mode.AUTO;
    private Thresholds thresholds = new Thresholds();
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
    public static class Rates {
        private int vip = 10;
        private int normal = 2;
        private int maxVip = 50;
        private int maxNormal = 10;
    }
}