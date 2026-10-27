package com.deepak.ticketflow.repository;

import com.deepak.ticketflow.model.ScalperAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScalperAuditLogRepository extends JpaRepository<ScalperAuditLog, Long> {
    List<ScalperAuditLog> findTop50ByOrderByTimestampDesc();
    long countByActionTaken(String actionTaken);
    List<ScalperAuditLog> findByClientIp(String clientIp);
}
