package com.deepak.ticketflow.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scalper_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScalperAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "client_ip", nullable = false, length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "detection_reason", nullable = false, length = 128)
    private String detectionReason;

    @Column(name = "action_taken", nullable = false, length = 64)
    private String actionTaken;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}
