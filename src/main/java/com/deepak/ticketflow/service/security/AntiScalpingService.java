package com.deepak.ticketflow.service.security;

import com.deepak.ticketflow.dto.security.RiskAssessmentResponse;
import com.deepak.ticketflow.handlers.InvalidReservationException;
import com.deepak.ticketflow.model.ScalperAuditLog;
import com.deepak.ticketflow.repository.ReservationRepository;
import com.deepak.ticketflow.repository.ScalperAuditLogRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AntiScalpingService {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private ScalperAuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private ReservationRepository reservationRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Getter
    @Setter
    private String securityPolicy = "BALANCED"; // "STRICT", "BALANCED", "PERMISSIVE"

    private static final Pattern BOT_USER_AGENT_PATTERN = Pattern.compile(
            "(?i)(headlesschrome|puppeteer|selenium|python-requests|postmanruntime|curl|wget|httpclient|scrapy|playwright)"
    );

    /**
     * Assesses client risk level across multiple behavioral and statistical vectors.
     */
    public RiskAssessmentResponse assessRisk(String clientIp, String userAgent, Integer userId, Long eventId, Integer requestedSeatsCount) {
        int score = 0;
        List<String> flags = new ArrayList<>();

        // 1. User Agent Vector
        if (userAgent == null || userAgent.isBlank()) {
            score += 35;
            flags.add("MISSING_USER_AGENT");
        } else if (BOT_USER_AGENT_PATTERN.matcher(userAgent).find()) {
            score += 55;
            flags.add("AUTOMATED_CLIENT_TOOL_DETECTED");
        }

        // 2. Sliding Window Request Velocity (Redis)
        if (redisTemplate != null) {
            try {
                String rateKey = "scalper:rate:" + clientIp;
                Long count = redisTemplate.opsForValue().increment(rateKey);
                if (count != null && count == 1) {
                    redisTemplate.expire(rateKey, Duration.ofSeconds(10));
                }

                if (count != null && count > 5) {
                    score += 45;
                    flags.add("BURST_RATE_LIMIT_EXCEEDED (" + count + " req/10s)");
                }
            } catch (Exception e) {
                log.debug("Redis rate check bypassed: {}", e.getMessage());
            }
        }

        // 3. Seat Hoarding Vector
        if (requestedSeatsCount != null && requestedSeatsCount > 4) {
            score += 50;
            flags.add("BULK_PURCHASE_VIOLATION");
        }

        // 4. Policy Adjustments
        if ("STRICT".equalsIgnoreCase(securityPolicy)) {
            score += 25; // Strict mode elevates scrutiny
        }

        score = Math.min(score, 100);

        String level = score >= 70 ? "HIGH" : (score >= 30 ? "MODERATE" : "LOW");
        boolean captchaRequired = "STRICT".equalsIgnoreCase(securityPolicy) || score >= 30;
        boolean blocked = score >= 75;

        return RiskAssessmentResponse.builder()
                .riskScore(score)
                .riskLevel(level)
                .captchaRequired(captchaRequired)
                .blocked(blocked)
                .flags(flags)
                .clientIp(clientIp)
                .build();
    }

    /**
     * Validates a seat reservation request against anti-scalping policies.
     * Throws InvalidReservationException if a scalper/bot is detected or captcha is required.
     */
    public void validateReservationRequest(Long eventId, List<String> seatNumbers, Integer userId,
                                           String clientIp, String userAgent, String captchaToken) {
        int seatCount = seatNumbers != null ? seatNumbers.size() : 1;
        RiskAssessmentResponse assessment = assessRisk(clientIp, userAgent, userId, eventId, seatCount);

        // Check if Hard-Blocked
        if (assessment.isBlocked()) {
            String reason = String.join(", ", assessment.getFlags());
            logAndRecordAudit(userId, clientIp, userAgent, eventId, assessment.getRiskScore(), reason, "BLOCKED");
            log.warn("Blocked scalper request from IP: {}, Reason: {}", clientIp, reason);
            throw new InvalidReservationException("Security Shield: Anti-scalping threshold exceeded. Access temporarily restricted.");
        }

        // Check Captcha Requirement
        if (assessment.isCaptchaRequired()) {
            boolean hasValidCaptcha = captchaService.isValidCaptchaToken(captchaToken, clientIp);
            if (!hasValidCaptcha) {
                String reason = "CAPTCHA_CHALLENGE_REQUIRED (Risk: " + assessment.getRiskScore() + ")";
                logAndRecordAudit(userId, clientIp, userAgent, eventId, assessment.getRiskScore(), reason, "CHALLENGED");
                log.info("Captcha challenge required for IP: {}, Risk: {}", clientIp, assessment.getRiskScore());
                throw new InvalidReservationException("Anti-Scalping Protection: Human verification required. Please solve the captcha challenge.");
            }
        }

        // Request passed all security checks
        if (assessment.getRiskScore() > 20) {
            logAndRecordAudit(userId, clientIp, userAgent, eventId, assessment.getRiskScore(), "ELEVATED_PASS", "ALLOWED");
        }
    }

    /**
     * Persists scalper detection entry to the audit log table.
     */
    public void logAndRecordAudit(Integer userId, String clientIp, String userAgent,
                                  Long eventId, int riskScore, String reason, String action) {
        try {
            ScalperAuditLog auditLog = ScalperAuditLog.builder()
                    .userId(userId)
                    .clientIp(clientIp != null ? clientIp : "127.0.0.1")
                    .userAgent(userAgent != null ? (userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent) : "Unknown")
                    .eventId(eventId)
                    .riskScore(riskScore)
                    .detectionReason(reason)
                    .actionTaken(action)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to persist scalper audit log: {}", e.getMessage());
        }
    }

    public List<ScalperAuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }
}
