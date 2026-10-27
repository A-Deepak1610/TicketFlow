package com.deepak.ticketflow.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVerifyResponse {
    private boolean verified;
    private String captchaToken;
    private int expiresInSeconds;
    private String message;
    private int computedRiskScore;
    private String riskCategory; // "HUMAN_VERIFIED", "FLAGGED", "SUSPICIOUS"
}
