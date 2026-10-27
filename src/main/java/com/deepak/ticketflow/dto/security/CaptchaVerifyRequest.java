package com.deepak.ticketflow.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVerifyRequest {
    private String challengeId;
    private String answer;
    private Long clientTimestamp;
    private Long eventId;
    private Integer mouseEntropyCount;
}
