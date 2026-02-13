package com.example.demo.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    long countByCreatedAtBefore(LocalDateTime cutoff);
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
