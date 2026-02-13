package com.example.demo.audit;

import com.example.demo.auth.Member;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final int retentionDays;

    public AuditLogService(
            AuditLogRepository repository,
            ObjectMapper objectMapper,
            @Value("${app.audit.retention-days:180}") int retentionDays
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public void record(
            Member actor,
            String action,
            String entityType,
            Long entityId,
            String beforeJson,
            String afterJson,
            String metadataJson
    ) {
        repository.save(new AuditLog(
                actor == null ? null : actor.getId(),
                actor == null ? "SYSTEM" : actor.getEmployeeId(),
                actor == null ? "System" : actor.getName(),
                actor == null ? "SYSTEM" : actor.getRole().name(),
                action,
                entityType,
                entityId,
                beforeJson,
                afterJson,
                enrichMetadata(metadataJson)
        ));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> search(
            String action,
            String entityType,
            Long entityId,
            Long actorMemberId,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        String normalizedAction = normalize(action);
        String normalizedEntityType = normalize(entityType);

        return repository.findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).stream()
                .filter(log -> normalizedAction == null || normalizedAction.equals(log.getAction()))
                .filter(log -> normalizedEntityType == null || normalizedEntityType.equals(log.getEntityType()))
                .filter(log -> entityId == null || entityId.equals(log.getEntityId()))
                .filter(log -> actorMemberId == null || actorMemberId.equals(log.getActorMemberId()))
                .filter(log -> fromTime == null || !log.getCreatedAt().isBefore(fromTime))
                .filter(log -> toTime == null || !log.getCreatedAt().isAfter(toTime))
                .limit(safeLimit)
                .toList();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private String enrichMetadata(String metadataJson) {
        String traceId = normalizeText(MDC.get(TRACE_ID_MDC_KEY));
        String rawMetadata = normalizeText(metadataJson);

        if (traceId == null && rawMetadata == null) {
            return null;
        }

        ObjectNode root = objectMapper.createObjectNode();
        if (rawMetadata != null) {
            try {
                JsonNode parsed = objectMapper.readTree(rawMetadata);
                if (parsed != null && parsed.isObject()) {
                    root.setAll((ObjectNode) parsed);
                } else {
                    root.put("rawMetadata", rawMetadata);
                }
            } catch (Exception ignored) {
                root.put("rawMetadata", rawMetadata);
            }
        }
        if (traceId != null) {
            root.put("traceId", traceId);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return rawMetadata;
        }
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public PurgeResult purgeByRetentionDays() {
        return purgeOlderThanDays(retentionDays);
    }

    @Transactional
    public PurgeResult purgeOlderThanDays(int days) {
        int safeDays = Math.max(days, 1);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDays);
        long candidates = repository.countByCreatedAtBefore(cutoff);
        long deleted = repository.deleteByCreatedAtBefore(cutoff);
        return new PurgeResult(cutoff, candidates, deleted);
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public record PurgeResult(
            LocalDateTime cutoff,
            long candidateCount,
            long deletedCount
    ) {}
}
