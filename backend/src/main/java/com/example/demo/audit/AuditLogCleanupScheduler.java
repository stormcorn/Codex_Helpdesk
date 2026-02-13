package com.example.demo.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditLogCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditLogCleanupScheduler.class);

    private final AuditLogService auditLogService;

    public AuditLogCleanupScheduler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Scheduled(cron = "${app.audit.cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredLogs() {
        AuditLogService.PurgeResult result = auditLogService.purgeByRetentionDays();
        log.info(
                "audit cleanup retentionDays={} cutoff={} candidates={} deleted={}",
                auditLogService.getRetentionDays(),
                result.cutoff(),
                result.candidateCount(),
                result.deletedCount()
        );
    }
}
