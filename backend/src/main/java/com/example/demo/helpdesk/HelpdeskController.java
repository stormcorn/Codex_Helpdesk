package com.example.demo.helpdesk;

import com.example.demo.auth.AuthService;
import com.example.demo.auth.Member;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/helpdesk/tickets")
public class HelpdeskController {

    private final HelpdeskTicketService service;
    private final AuthService authService;

    public HelpdeskController(HelpdeskTicketService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String subject,
            @RequestParam String description,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        Member member = authService.requireMember(authorization);
        if (isBlank(name) || isBlank(email) || isBlank(subject) || isBlank(description)) {
            throw new IllegalArgumentException("All fields are required");
        }
        HelpdeskTicketPriority ticketPriority = parsePriority(priority);

        HelpdeskTicket saved = service.createTicket(
                member,
                name,
                email,
                subject,
                description,
                groupId,
                categoryId,
                ticketPriority,
                files == null ? List.of() : files
        );

        return TicketResponse.from(saved, service);
    }

    @GetMapping
    public List<TicketResponse> listRecent(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireMember(authorization);
        return service.listRecentTickets().stream()
                .map(ticket -> TicketResponse.from(ticket, service))
                .toList();
    }

    @PostMapping(value = "/{ticketId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TicketResponse reply(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId,
            @RequestBody ReplyRequest request
    ) {
        Member member = authService.requireMember(authorization);
        HelpdeskTicket updated = service.addReply(ticketId, member, request.content());
        return TicketResponse.from(updated, service);
    }

    @PostMapping(value = "/{ticketId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TicketResponse replyWithAttachments(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId,
            @RequestParam String content,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        Member member = authService.requireMember(authorization);
        HelpdeskTicket updated = service.addReply(ticketId, member, content, files == null ? List.of() : files);
        return TicketResponse.from(updated, service);
    }

    @PatchMapping("/{ticketId}/status")
    public TicketResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId,
            @RequestBody StatusUpdateRequest request
    ) {
        Member member = authService.requireItOrAdmin(authorization);
        HelpdeskTicketStatus status;
        try {
            status = HelpdeskTicketStatus.valueOf(request.status().trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Status must be OPEN, PROCEEDING, PENDING, CLOSED, or DELETED");
        }

        HelpdeskTicket updated = service.changeStatus(ticketId, member, status);
        return TicketResponse.from(updated, service);
    }

    @PatchMapping("/{ticketId}/delete")
    public TicketResponse softDelete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId
    ) {
        Member member = authService.requireMember(authorization);
        HelpdeskTicket updated = service.softDelete(ticketId, member);
        return TicketResponse.from(updated, service);
    }

    @PatchMapping("/{ticketId}/supervisor-approve")
    public TicketResponse supervisorApprove(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId
    ) {
        Member member = authService.requireMember(authorization);
        HelpdeskTicket updated = service.approveUrgentTicket(ticketId, member);
        return TicketResponse.from(updated, service);
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId
    ) {
        authService.requireMember(authorization);
        HelpdeskTicketService.AttachmentDownload file = service.getAttachment(ticketId, attachmentId);
        Resource resource = new FileSystemResource(file.path());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.originalFilename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(resource);
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> view(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId
    ) {
        authService.requireMember(authorization);
        HelpdeskTicketService.AttachmentDownload file = service.getAttachment(ticketId, attachmentId);
        Resource resource = new FileSystemResource(file.path());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(resource);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private HelpdeskTicketPriority parsePriority(String rawPriority) {
        if (rawPriority == null || rawPriority.isBlank()) {
            return HelpdeskTicketPriority.GENERAL;
        }
        try {
            return HelpdeskTicketPriority.valueOf(rawPriority.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Priority must be GENERAL or URGENT");
        }
    }

    public record TicketResponse(Long id, String name, String email, String subject, String description, String status,
                                 String priority, boolean supervisorApproved, Long supervisorApprovedByMemberId,
                                 LocalDateTime supervisorApprovedAt, Long groupId, String groupName,
                                 Long categoryId, String categoryName,
                                 Long createdByMemberId, String createdByEmployeeId, boolean deleted,
                                 LocalDateTime deletedAt, LocalDateTime createdAt,
                                 List<AttachmentResponse> attachments,
                                 List<MessageResponse> messages,
                                 List<StatusHistoryResponse> statusHistories) {
        static TicketResponse from(HelpdeskTicket ticket, HelpdeskTicketService service) {
            return new TicketResponse(
                    ticket.getId(),
                    ticket.getName(),
                    ticket.getEmail(),
                    ticket.getSubject(),
                    ticket.getDescription(),
                    ticket.getStatus().name(),
                    ticket.getPriority().name(),
                    ticket.isSupervisorApproved(),
                    ticket.getSupervisorApprovedByMemberId(),
                    ticket.getSupervisorApprovedAt(),
                    ticket.getGroup() == null ? null : ticket.getGroup().getId(),
                    ticket.getGroup() == null ? null : ticket.getGroup().getName(),
                    ticket.getCategory() == null ? null : ticket.getCategory().getId(),
                    ticket.getCategory() == null ? null : ticket.getCategory().getName(),
                    ticket.getCreatedByMemberId(),
                    service.getCreatorEmployeeId(ticket),
                    ticket.isDeleted(),
                    ticket.getDeletedAt(),
                    ticket.getCreatedAt(),
                    deduplicateAttachments(ticket.getAttachments()),
                    deduplicateMessages(service.sortMessagesByCreatedAt(ticket.getMessages())),
                    deduplicateStatusHistories(service.sortStatusHistoriesByCreatedAt(ticket.getStatusHistories()))
            );
        }

        private static List<AttachmentResponse> deduplicateAttachments(List<HelpdeskAttachment> attachments) {
            Map<Long, AttachmentResponse> unique = new LinkedHashMap<>();
            for (HelpdeskAttachment attachment : attachments) {
                unique.putIfAbsent(attachment.getId(), AttachmentResponse.from(attachment));
            }
            return List.copyOf(unique.values());
        }

        private static List<MessageResponse> deduplicateMessages(List<HelpdeskTicketMessage> messages) {
            Map<Long, MessageResponse> unique = new LinkedHashMap<>();
            for (HelpdeskTicketMessage message : messages) {
                unique.putIfAbsent(message.getId(), MessageResponse.from(message));
            }
            return List.copyOf(unique.values());
        }

        private static List<StatusHistoryResponse> deduplicateStatusHistories(List<HelpdeskTicketStatusHistory> histories) {
            Map<Long, StatusHistoryResponse> unique = new LinkedHashMap<>();
            for (HelpdeskTicketStatusHistory history : histories) {
                unique.putIfAbsent(history.getId(), StatusHistoryResponse.from(history));
            }
            return List.copyOf(unique.values());
        }
    }

    public record AttachmentResponse(Long id, String originalFilename, String contentType, long sizeBytes) {
        static AttachmentResponse from(HelpdeskAttachment attachment) {
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getOriginalFilename(),
                    attachment.getContentType(),
                    attachment.getSizeBytes()
            );
        }
    }

    public record MessageResponse(Long id, String content, String authorEmployeeId, String authorName, String authorRole,
                                  LocalDateTime createdAt) {
        static MessageResponse from(HelpdeskTicketMessage message) {
            return new MessageResponse(
                    message.getId(),
                    message.getContent(),
                    message.getAuthorEmployeeId(),
                    message.getAuthorName(),
                    message.getAuthorRole().name(),
                    message.getCreatedAt()
            );
        }
    }

    public record StatusHistoryResponse(Long id, String fromStatus, String toStatus, Long changedByMemberId,
                                        String changedByEmployeeId, String changedByName, String changedByRole,
                                        LocalDateTime createdAt) {
        static StatusHistoryResponse from(HelpdeskTicketStatusHistory history) {
            return new StatusHistoryResponse(
                    history.getId(),
                    history.getFromStatus() == null ? null : history.getFromStatus().name(),
                    history.getToStatus().name(),
                    history.getChangedByMemberId(),
                    history.getChangedByEmployeeId(),
                    history.getChangedByName(),
                    history.getChangedByRole(),
                    history.getCreatedAt()
            );
        }
    }

    public record ReplyRequest(String content) {
    }

    public record StatusUpdateRequest(String status) {
    }
}
