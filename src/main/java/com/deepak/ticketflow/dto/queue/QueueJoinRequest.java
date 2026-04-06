package com.deepak.ticketflow.dto.queue;

import lombok.Data;

@Data
public class QueueJoinRequest {
    private Long eventId;
    private Integer userId;
    private UserType userType;
}