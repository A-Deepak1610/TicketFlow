package com.deepak.ticketflow.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaChallengeResponse {
    private String challengeId;
    private String question;
    private String challengeType; // "MATH_EQUATION", "GRAPHICAL_PATTERN", "REVERSE_STRING"
    private String svgVisual;    // Graphical visual puzzle payload
    private int expiresInSeconds;
    private String riskLevel;     // "LOW", "MODERATE", "ELEVATED"
}
