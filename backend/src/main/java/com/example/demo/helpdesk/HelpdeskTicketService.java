package com.example.demo.helpdesk;

import com.example.demo.audit.AuditLogService;
import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.group.DepartmentGroup;
import com.example.demo.group.DepartmentGroupService;
import com.example.demo.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class HelpdeskTicketService {

    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;

    private final HelpdeskTicketRepository repository;
    private final HelpdeskAttachmentRepository attachmentRepository;
    private final HelpdeskTicketMessageRepository messageRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final DepartmentGroupService groupService;
    private final NotificationService notificationService;
    private final Path uploadDir;

    public HelpdeskTicketService(
            HelpdeskTicketRepository repository,
            HelpdeskAttachmentRepository attachmentRepository,
            HelpdeskTicketMessageRepository messageRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            DepartmentGroupService groupService,
            NotificationService notificationService,
            @Value("${helpdesk.upload-dir:/tmp/helpdesk-uploads}") String uploadDir
    ) {
        this.repository = repository;
        this.attachmentRepository = attachmentRepository;
        this.messageRepository = messageRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.groupService = groupService;
        this.notificationService = notificationService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional
    public HelpdeskTicket createTicket(
            Member creator,
            String name,
            String email,
            String subject,
            String description,
            Long groupId,
            HelpdeskTicketPriority priority,
            List<MultipartFile> files
    ) {
        if (groupId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Group is required");
        }
        DepartmentGroup group = groupService.requireGroup(groupId);
        if (!groupService.isMemberInGroup(groupId, creator.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You are not a member of this group");
        }
        if (priority == HelpdeskTicketPriority.URGENT && !groupService.hasSupervisor(groupId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Urgent tickets require a supervisor in the group");
        }

        HelpdeskTicket ticket = new HelpdeskTicket(
                name.trim(),
                email.trim(),
                subject.trim(),
                description.trim(),
                creator.getId(),
                group,
                priority
        );
        HelpdeskTicket savedTicket = repository.save(ticket);
        appendStatusHistory(savedTicket, null, savedTicket.getStatus(), creator);

        try {
            Files.createDirectories(uploadDir);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }

                validateFileSize(file);
                String originalFilename = sanitizeFilename(file.getOriginalFilename());
                String storedFilename = UUID.randomUUID() + "-" + originalFilename;
                Path target = uploadDir.resolve(storedFilename);

                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }

                HelpdeskAttachment attachment = new HelpdeskAttachment(
                        savedTicket,
                        originalFilename,
                        storedFilename,
                        defaultContentType(file.getContentType()),
                        file.getSize()
                );
                savedTicket.addAttachment(attachment);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("File upload failed");
        }

        HelpdeskTicket finalTicket = repository.save(savedTicket);
        auditLogService.record(
                creator,
                "TICKET_CREATE",
                "TICKET",
                finalTicket.getId(),
                null,
                toJson(ticketSnapshot(finalTicket)),
                toJson(Map.of("attachmentsCount", finalTicket.getAttachments().size()))
        );
        notificationService.notifyTicketCreated(finalTicket, creator);
        return finalTicket;
    }

    public List<HelpdeskTicket> listRecentTickets() {
        return repository.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional
    public HelpdeskTicket addReply(Long ticketId, Member author, String content) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Reply content is required");
        }

        HelpdeskTicketMessage message = new HelpdeskTicketMessage(
                ticket,
                content.trim(),
                author.getEmployeeId(),
                author.getName(),
                author.getRole()
        );
        messageRepository.save(message);
        ticket.addMessage(message);
        repository.save(ticket);
        HelpdeskTicket updated = repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        notificationService.notifyTicketReplied(updated, author);
        return updated;
    }

    @Transactional
    public HelpdeskTicket changeStatus(Long ticketId, HelpdeskTicketStatus status) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = ticketSnapshot(ticket);
        HelpdeskTicketStatus fromStatus = ticket.getStatus();
        ticket.setStatus(status);
        if (fromStatus != status) {
            appendStatusHistory(ticket, fromStatus, status, null);
        }
        repository.save(ticket);
        HelpdeskTicket updated = repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        if (fromStatus != status) {
            auditLogService.record(
                    null,
                    "TICKET_STATUS_CHANGE",
                    "TICKET",
                    updated.getId(),
                    toJson(before),
                    toJson(ticketSnapshot(updated)),
                    toJson(Map.of("fromStatus", fromStatus.name(), "toStatus", status.name()))
            );
        }
        return updated;
    }

    @Transactional
    public HelpdeskTicket changeStatus(Long ticketId, Member actor, HelpdeskTicketStatus status) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = ticketSnapshot(ticket);
        HelpdeskTicketStatus fromStatus = ticket.getStatus();
        ticket.setStatus(status);
        if (fromStatus != status) {
            appendStatusHistory(ticket, fromStatus, status, actor);
        }
        repository.save(ticket);
        HelpdeskTicket updated = repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        if (fromStatus != status) {
            auditLogService.record(
                    actor,
                    "TICKET_STATUS_CHANGE",
                    "TICKET",
                    updated.getId(),
                    toJson(before),
                    toJson(ticketSnapshot(updated)),
                    toJson(Map.of("fromStatus", fromStatus.name(), "toStatus", status.name()))
            );
        }
        notificationService.notifyTicketStatusChanged(updated, actor, status);
        return updated;
    }

    @Transactional
    public HelpdeskTicket softDelete(Long ticketId, Member actor) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = ticketSnapshot(ticket);
        Long ownerId = ticket.getCreatedByMemberId();
        boolean isOwner = ownerId != null && ownerId.equals(actor.getId());
        boolean isPrivileged = actor.getRole() == MemberRole.IT || actor.getRole() == MemberRole.ADMIN;
        if (!isOwner && !isPrivileged) {
            throw new ResponseStatusException(FORBIDDEN, "No permission to delete this ticket");
        }
        HelpdeskTicketStatus fromStatus = ticket.getStatus();
        ticket.softDelete();
        if (fromStatus != HelpdeskTicketStatus.DELETED) {
            appendStatusHistory(ticket, fromStatus, HelpdeskTicketStatus.DELETED, actor);
        }
        repository.save(ticket);
        HelpdeskTicket updated = repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        if (fromStatus != HelpdeskTicketStatus.DELETED) {
            auditLogService.record(
                    actor,
                    "TICKET_SOFT_DELETE",
                    "TICKET",
                    updated.getId(),
                    toJson(before),
                    toJson(ticketSnapshot(updated)),
                    toJson(Map.of("fromStatus", fromStatus.name(), "toStatus", HelpdeskTicketStatus.DELETED.name()))
            );
        }
        return updated;
    }

    @Transactional
    public HelpdeskTicket approveUrgentTicket(Long ticketId, Member actor) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = ticketSnapshot(ticket);
        if (ticket.getPriority() != HelpdeskTicketPriority.URGENT) {
            throw new ResponseStatusException(FORBIDDEN, "Only urgent tickets require supervisor approval");
        }
        if (ticket.getGroup() == null || !groupService.isSupervisor(ticket.getGroup().getId(), actor.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "Group supervisor only");
        }
        if (!ticket.isSupervisorApproved()) {
            ticket.markSupervisorApproved(actor.getId());
            repository.save(ticket);
        }
        HelpdeskTicket updated = repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        if (!Boolean.TRUE.equals(before.get("supervisorApproved"))) {
            auditLogService.record(
                    actor,
                    "TICKET_SUPERVISOR_APPROVE",
                    "TICKET",
                    updated.getId(),
                    toJson(before),
                    toJson(ticketSnapshot(updated)),
                    toJson(Map.of("groupId", updated.getGroup() == null ? null : updated.getGroup().getId()))
            );
        }
        return updated;
    }

    public AttachmentDownload getAttachment(Long ticketId, Long attachmentId) {
        HelpdeskAttachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Attachment not found"));
        Path path = uploadDir.resolve(attachment.getStoredFilename());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file missing");
        }
        return new AttachmentDownload(path, attachment.getOriginalFilename(), attachment.getContentType());
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Each file must be smaller than 5MB");
        }
    }

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "file" : filename);
        return cleaned.replaceAll("[\\\\/]+", "_");
    }

    private String defaultContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }

    public List<HelpdeskTicketMessage> sortMessagesByCreatedAt(Collection<HelpdeskTicketMessage> messages) {
        return messages.stream()
                .sorted(Comparator.comparing(HelpdeskTicketMessage::getCreatedAt))
                .toList();
    }

    public List<HelpdeskTicketStatusHistory> sortStatusHistoriesByCreatedAt(Collection<HelpdeskTicketStatusHistory> histories) {
        return histories.stream()
                .sorted(Comparator.comparing(HelpdeskTicketStatusHistory::getCreatedAt))
                .toList();
    }

    private void appendStatusHistory(
            HelpdeskTicket ticket,
            HelpdeskTicketStatus fromStatus,
            HelpdeskTicketStatus toStatus,
            Member actor
    ) {
        String employeeId = actor != null ? actor.getEmployeeId() : "SYSTEM";
        String name = actor != null ? actor.getName() : "System";
        String role = actor != null ? actor.getRole().name() : "SYSTEM";
        Long memberId = actor != null ? actor.getId() : null;

        ticket.addStatusHistory(new HelpdeskTicketStatusHistory(
                ticket,
                fromStatus,
                toStatus,
                memberId,
                employeeId,
                name,
                role
        ));
    }

    private Map<String, Object> ticketSnapshot(HelpdeskTicket ticket) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", ticket.getId());
        out.put("status", ticket.getStatus().name());
        out.put("priority", ticket.getPriority().name());
        out.put("deleted", ticket.isDeleted());
        out.put("supervisorApproved", ticket.isSupervisorApproved());
        out.put("groupId", ticket.getGroup() == null ? null : ticket.getGroup().getId());
        out.put("groupName", ticket.getGroup() == null ? null : ticket.getGroup().getName());
        return out;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }

    public record AttachmentDownload(Path path, String originalFilename, String contentType) {
    }
}
