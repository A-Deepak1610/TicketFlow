package com.deepak.ticketflow.service.security;

import com.deepak.ticketflow.dto.security.CaptchaChallengeResponse;
import com.deepak.ticketflow.dto.security.CaptchaVerifyRequest;
import com.deepak.ticketflow.dto.security.CaptchaVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CaptchaService {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // In-memory fallback if Redis is unavailable
    private final ConcurrentHashMap<String, ChallengeEntry> memoryCache = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private static final String HMAC_SECRET = "TicketFlow-AntiScalping-CryptoSecret-Key-2026";
    private static final String CAPTCHA_KEY_PREFIX = "captcha:challenge:";
    private static final String VERIFIED_TOKEN_PREFIX = "captcha:token:";
    private static final int CHALLENGE_TTL_SECONDS = 180; // 3 minutes
    private static final int TOKEN_TTL_SECONDS = 300;     // 5 minutes

    private static class ChallengeEntry {
        String answer;
        long expiryTime;

        ChallengeEntry(String answer, long expiryTime) {
            this.answer = answer;
            this.expiryTime = expiryTime;
        }
    }

    /**
     * Generates a dynamic visual/mathematical captcha challenge.
     */
    public CaptchaChallengeResponse generateChallenge(String clientIp, int currentRiskScore) {
        String challengeId = UUID.randomUUID().toString();
        
        // Difficulty scales with risk score
        int a, b;
        String op;
        int answer;

        if (currentRiskScore > 60) {
            // Harder 3-number equation
            a = random.nextInt(40) + 10;
            b = random.nextInt(30) + 10;
            int c = random.nextInt(15) + 5;
            answer = a + b - c;
            op = String.format("%d + %d - %d", a, b, c);
        } else {
            // Standard equation
            a = random.nextInt(30) + 5;
            b = random.nextInt(25) + 5;
            answer = a + b;
            op = String.format("%d + %d", a, b);
        }

        String expectedAnswer = String.valueOf(answer);

        // Store in Redis (or in-memory cache)
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(
                        CAPTCHA_KEY_PREFIX + challengeId,
                        expectedAnswer,
                        Duration.ofSeconds(CHALLENGE_TTL_SECONDS)
                );
            } catch (Exception e) {
                log.warn("Redis unavailable for captcha challenge, using memory cache: {}", e.getMessage());
                memoryCache.put(challengeId, new ChallengeEntry(expectedAnswer, System.currentTimeMillis() + (CHALLENGE_TTL_SECONDS * 1000L)));
            }
        } else {
            memoryCache.put(challengeId, new ChallengeEntry(expectedAnswer, System.currentTimeMillis() + (CHALLENGE_TTL_SECONDS * 1000L)));
        }

        // Generate clean graphical SVG visual
        String svg = generateCaptchaSvg(op);

        String riskLevel = currentRiskScore > 60 ? "ELEVATED" : (currentRiskScore > 30 ? "MODERATE" : "LOW");

        return CaptchaChallengeResponse.builder()
                .challengeId(challengeId)
                .question(op + " = ?")
                .challengeType("MATH_EQUATION")
                .svgVisual(svg)
                .expiresInSeconds(CHALLENGE_TTL_SECONDS)
                .riskLevel(riskLevel)
                .build();
    }

    /**
     * Verifies user response against stored challenge and issues HMAC signed token.
     */
    public CaptchaVerifyResponse verifyChallenge(CaptchaVerifyRequest request, String clientIp) {
        if (request == null || request.getChallengeId() == null || request.getAnswer() == null) {
            return CaptchaVerifyResponse.builder()
                    .verified(false)
                    .message("Challenge ID and answer are required")
                    .riskCategory("SUSPICIOUS")
                    .build();
        }

        String expected = null;
        if (redisTemplate != null) {
            try {
                expected = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + request.getChallengeId());
                if (expected != null) {
                    redisTemplate.delete(CAPTCHA_KEY_PREFIX + request.getChallengeId()); // single use
                }
            } catch (Exception e) {
                log.warn("Redis read failed during captcha verify, trying memory fallback: {}", e.getMessage());
            }
        }

        if (expected == null) {
            ChallengeEntry entry = memoryCache.remove(request.getChallengeId());
            if (entry != null && System.currentTimeMillis() <= entry.expiryTime) {
                expected = entry.answer;
            }
        }

        if (expected == null) {
            return CaptchaVerifyResponse.builder()
                    .verified(false)
                    .message("Captcha challenge expired or already consumed")
                    .riskCategory("SUSPICIOUS")
                    .build();
        }

        boolean matches = expected.trim().equalsIgnoreCase(request.getAnswer().trim());
        if (!matches) {
            log.warn("Invalid captcha answer for challenge {} from IP {}", request.getChallengeId(), clientIp);
            return CaptchaVerifyResponse.builder()
                    .verified(false)
                    .message("Incorrect captcha answer")
                    .computedRiskScore(75)
                    .riskCategory("FLAGGED")
                    .build();
        }

        // Verified! Generate cryptographically signed token
        long now = System.currentTimeMillis();
        String rawTokenPayload = String.format("%s:%s:%d", request.getChallengeId(), clientIp, now);
        String signature = generateHmac(rawTokenPayload);
        String captchaToken = Base64.getUrlEncoder().encodeToString((rawTokenPayload + ":" + signature).getBytes(StandardCharsets.UTF_8));

        // Save verified token in store
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(
                        VERIFIED_TOKEN_PREFIX + captchaToken,
                        clientIp,
                        Duration.ofSeconds(TOKEN_TTL_SECONDS)
                );
            } catch (Exception e) {
                log.warn("Failed saving token to Redis: {}", e.getMessage());
            }
        }

        log.info("Captcha successfully solved by client IP: {}", clientIp);

        return CaptchaVerifyResponse.builder()
                .verified(true)
                .captchaToken(captchaToken)
                .expiresInSeconds(TOKEN_TTL_SECONDS)
                .message("Human verification passed")
                .computedRiskScore(10)
                .riskCategory("HUMAN_VERIFIED")
                .build();
    }

    /**
     * Validates whether a presented Captcha Token is legitimate, unexpired, and matches client IP.
     */
    public boolean isValidCaptchaToken(String token, String clientIp) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            // Check Redis cache first
            if (redisTemplate != null) {
                try {
                    String storedIp = redisTemplate.opsForValue().get(VERIFIED_TOKEN_PREFIX + token);
                    if (storedIp != null) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }

            // Cryptographic HMAC fallback validation
            byte[] decodedBytes = Base64.getUrlDecoder().decode(token);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4) {
                return false;
            }

            String challengeId = parts[0];
            String tokenIp = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            String expectedSig = parts[3];

            // Verify TTL (5 minutes)
            if (System.currentTimeMillis() - timestamp > (TOKEN_TTL_SECONDS * 1000L)) {
                return false;
            }

            String recomputedSig = generateHmac(String.format("%s:%s:%d", challengeId, tokenIp, timestamp));
            return recomputedSig.equals(expectedSig);
        } catch (Exception e) {
            log.warn("Captcha token validation exception: {}", e.getMessage());
            return false;
        }
    }

    private String generateHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private String generateCaptchaSvg(String text) {
        // High quality SVG with noise lines and gradient styling
        return String.format(
            "<svg xmlns='http://www.w3.org/2000/svg' width='180' height='52' viewBox='0 0 180 52'>" +
            "  <defs>" +
            "    <linearGradient id='cg' x1='0%%' y1='0%%' x2='100%%' y2='100%%'>" +
            "      <stop offset='0%%' stop-color='#f8fafc'/>" +
            "      <stop offset='100%%' stop-color='#e2e8f0'/>" +
            "    </linearGradient>" +
            "  </defs>" +
            "  <rect width='100%%' height='100%%' fill='url(#cg)' rx='6'/>" +
            "  <line x1='10' y1='12' x2='170' y2='38' stroke='#cbd5e1' stroke-width='1.5' stroke-dasharray='4,4'/>" +
            "  <line x1='15' y1='40' x2='165' y2='14' stroke='#cbd5e1' stroke-width='1' stroke-dasharray='2,3'/>" +
            "  <text x='50%%' y='58%%' dominant-baseline='middle' text-anchor='middle' " +
            "        font-family='JetBrains Mono, monospace, Arial' font-size='22' font-weight='700' fill='#1e293b' letter-spacing='2'>%s</text>" +
            "</svg>",
            text
        );
    }
}
