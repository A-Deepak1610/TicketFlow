package com.deepak.ticketflow.dto.queue;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueuePositionResponse {
    private String status;  // "in_queue", "not_in_queue", "ready_to_book"
    private Long position;
    private Long aheadCount;
    private Long vipAheadCount;
    private Long normalAheadCount;
    private UserType userType;
    private String token;  // When ready to book
    private Integer tokenExpirySeconds;
}