package com.deepak.ticketflow.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentResponse {
    private int riskScore; // 0 to 100
    private String riskLevel; // "LOW", "MODERATE", "HIGH"
    private boolean captchaRequired;
    private boolean blocked;
    private List<String> flags;
    private String clientIp;
}
