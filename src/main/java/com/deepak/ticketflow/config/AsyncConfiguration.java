package com.deepak.ticketflow.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous event handling.
 * 
 * Enables @Async annotation processing for Spring event listeners.
 * This allows BookingSlotFreedEvent to be processed asynchronously,
 * preventing queue admission from blocking the booking flow.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    /**
     * Create a thread pool for async event listeners.
     * 
     * @return the configured executor
     */
    @Bean(name = "eventListenerExecutor")
    public Executor eventListenerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);          // Minimum threads
        executor.setMaxPoolSize(16);          // Maximum threads
        executor.setQueueCapacity(100);       // Maximum pending tasks
        executor.setThreadNamePrefix("event-listener-");
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
