package com.deepak.ticketflow.dto.queue;

public enum QueueDecision {
    NO_QUEUE,      // Direct booking
    SOFT_QUEUE,    // Queue with faster processing
    HARD_QUEUE     // Standard queue
}