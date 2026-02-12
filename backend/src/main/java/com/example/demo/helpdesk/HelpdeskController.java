package com.example.demo.helpdesk;

import com.example.demo.auth.AuthService;
import com.example.demo.auth.Member;
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
import java.util.List;

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
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        Member member = authService.requireMember(authorization);
        if (isBlank(name) || isBlank(email) || isBlank(subject) || isBlank(description)) {
            throw new IllegalArgumentException("All fields are required");
        }

        HelpdeskTicket saved = service.createTicket(
                member,
                name,
                email,
                subject,
                description,
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

    @PostMapping("/{ticketId}/messages")
    public TicketResponse reply(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long ticketId,
            @RequestBody ReplyRequest request
    ) {
        Member member = authService.requireItOrAdmin(authorization);
        HelpdeskTicket updated = service.addReply(ticketId, member, request.content());
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

    @GetMapping("/{ticketId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId
    ) {
        authService.requireMember(resolveAuthorization(authorization, token));
        HelpdeskTicketService.AttachmentDownload file = service.getAttachment(ticketId, attachmentId);
        Resource resource = new FileSystemResource(file.path());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("Content-Disposition", "attachment; filename=\"" + file.originalFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> view(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId
    ) {
        authService.requireMember(resolveAuthorization(authorization, token));
        HelpdeskTicketService.AttachmentDownload file = service.getAttachment(ticketId, attachmentId);
        Resource resource = new FileSystemResource(file.path());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(resource);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveAuthorization(String authorization, String token) {
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }
        if (token != null && !token.isBlank()) {
            return "Bearer " + token;
        }
        return null;
    }

    public record TicketResponse(Long id, String name, String email, String subject, String description, String status,
                                 Long createdByMemberId, boolean deleted, LocalDateTime deletedAt, LocalDateTime createdAt,
                                 List<AttachmentResponse> attachments,
                                 List<MessageResponse> messages) {
        static TicketResponse from(HelpdeskTicket ticket, HelpdeskTicketService service) {
            return new TicketResponse(
                    ticket.getId(),
                    ticket.getName(),
                    ticket.getEmail(),
                    ticket.getSubject(),
                    ticket.getDescription(),
                    ticket.getStatus().name(),
                    ticket.getCreatedByMemberId(),
                    ticket.isDeleted(),
                    ticket.getDeletedAt(),
                    ticket.getCreatedAt(),
                    ticket.getAttachments().stream().map(AttachmentResponse::from).toList(),
                    service.sortMessagesByCreatedAt(ticket.getMessages()).stream().map(MessageResponse::from).toList()
            );
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

    public record ReplyRequest(String content) {
    }

    public record StatusUpdateRequest(String status) {
    }
}
