package com.example.demo.audit;

import com.example.demo.auth.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AuthService authService;
    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuthService authService, AuditLogService auditLogService) {
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            @RequestParam(value = "actorMemberId", required = false) Long actorMemberId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit
    ) {
        authService.requireAdmin(authorization);
        LocalDateTime fromTime = parseDateTime(from);
        LocalDateTime toTime = parseDateTime(to);
        return auditLogService.search(action, entityType, entityId, actorMemberId, fromTime, toTime, limit)
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    @PostMapping("/cleanup")
    public CleanupResponse cleanup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "days", required = false) Integer days
    ) {
        authService.requireAdmin(authorization);
        AuditLogService.PurgeResult result = days == null
                ? auditLogService.purgeByRetentionDays()
                : auditLogService.purgeOlderThanDays(days);
        return new CleanupResponse(
                auditLogService.getRetentionDays(),
                days == null ? null : Math.max(days, 1),
                result.cutoff(),
                result.candidateCount(),
                result.deletedCount()
        );
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            @RequestParam(value = "actorMemberId", required = false) Long actorMemberId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false, defaultValue = "500") int limit
    ) {
        authService.requireAdmin(authorization);
        LocalDateTime fromTime = parseDateTime(from);
        LocalDateTime toTime = parseDateTime(to);
        List<AuditLog> logs = auditLogService.search(action, entityType, entityId, actorMemberId, fromTime, toTime, limit);
        String filename = "audit-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(toCsv(logs));
    }

    private String toCsv(List<AuditLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,actorMemberId,actorEmployeeId,actorName,actorRole,action,entityType,entityId,beforeJson,afterJson,metadataJson\n");
        for (AuditLog log : logs) {
            sb.append(csv(log.getId()))
                    .append(',').append(csv(log.getCreatedAt()))
                    .append(',').append(csv(log.getActorMemberId()))
                    .append(',').append(csv(log.getActorEmployeeId()))
                    .append(',').append(csv(log.getActorName()))
                    .append(',').append(csv(log.getActorRole()))
                    .append(',').append(csv(log.getAction()))
                    .append(',').append(csv(log.getEntityType()))
                    .append(',').append(csv(log.getEntityId()))
                    .append(',').append(csv(log.getBeforeJson()))
                    .append(',').append(csv(log.getAfterJson()))
                    .append(',').append(csv(log.getMetadataJson()))
                    .append('\n');
        }
        return sb.toString();
    }

    private String csv(Object value) {
        if (value == null) return "\"\"";
        String raw = String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private LocalDateTime parseDateTime(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            return OffsetDateTime.parse(input.trim()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(input.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid datetime format. Use ISO-8601, e.g. 2026-02-13T05:00:00Z");
        }
    }

    public record AuditLogResponse(
            Long id,
            Long actorMemberId,
            String actorEmployeeId,
            String actorName,
            String actorRole,
            String action,
            String entityType,
            Long entityId,
            String beforeJson,
            String afterJson,
            String metadataJson,
            LocalDateTime createdAt
    ) {
        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getActorMemberId(),
                    log.getActorEmployeeId(),
                    log.getActorName(),
                    log.getActorRole(),
                    log.getAction(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getBeforeJson(),
                    log.getAfterJson(),
                    log.getMetadataJson(),
                    log.getCreatedAt()
            );
        }
    }

    public record CleanupResponse(
            int configuredRetentionDays,
            Integer requestedDays,
            LocalDateTime cutoff,
            long candidateCount,
            long deletedCount
    ) {}
}
