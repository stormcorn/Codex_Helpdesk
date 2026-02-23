package com.example.demo.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HelpdeskTicketSnapshotService {

    private final ObjectMapper objectMapper;

    public HelpdeskTicketSnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> ticketSnapshot(HelpdeskTicket ticket) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", ticket.getId());
        out.put("status", ticket.getStatus().name());
        out.put("priority", ticket.getPriority().name());
        out.put("deleted", ticket.isDeleted());
        out.put("supervisorApproved", ticket.isSupervisorApproved());
        out.put("groupId", ticket.getGroup() == null ? null : ticket.getGroup().getId());
        out.put("groupName", ticket.getGroup() == null ? null : ticket.getGroup().getName());
        out.put("categoryId", ticket.getCategory() == null ? null : ticket.getCategory().getId());
        out.put("categoryName", ticket.getCategory() == null ? null : ticket.getCategory().getName());
        return out;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }
}
