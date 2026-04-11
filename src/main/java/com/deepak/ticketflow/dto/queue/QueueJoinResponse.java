package com.deepak.ticketflow.dto.queue;

import com.deepak.ticketflow.Enum.UserType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueueJoinResponse {
    private String mode;  // DIRECT, SOFT_QUEUE, HARD_QUEUE
    private Long position;
    private Integer estimatedWaitSeconds;
    private UserType userType;
    private String token;  // For direct booking mode
    private Integer expiresIn;  // Token expiry in seconds
    private Long vipAheadCount;  // For normal users
    private Long normalAheadCount;  // For normal users
}