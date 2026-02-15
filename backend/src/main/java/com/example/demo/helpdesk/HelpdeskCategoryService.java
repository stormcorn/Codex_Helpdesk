package com.example.demo.helpdesk;

import com.example.demo.audit.AuditLogService;
import com.example.demo.auth.Member;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class HelpdeskCategoryService {

    private final HelpdeskCategoryRepository categoryRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public HelpdeskCategoryService(
            HelpdeskCategoryRepository categoryRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<HelpdeskCategory> listAll() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public HelpdeskCategory requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Category not found"));
    }

    @Transactional
    public HelpdeskCategory createCategory(Member actor, String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Category name is required");
        }
        if (categoryRepository.existsByNameIgnoreCase(normalized)) {
            throw new ResponseStatusException(CONFLICT, "Category name already exists");
        }
        HelpdeskCategory created = categoryRepository.save(new HelpdeskCategory(normalized));
        auditLogService.record(
                actor,
                "HELPDESK_CATEGORY_CREATE",
                "HELPDESK_CATEGORY",
                created.getId(),
                null,
                toJson(categorySnapshot(created)),
                null
        );
        return created;
    }

    private Map<String, Object> categorySnapshot(HelpdeskCategory category) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", category.getId());
        out.put("name", category.getName());
        out.put("createdAt", category.getCreatedAt());
        return out;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }
}
