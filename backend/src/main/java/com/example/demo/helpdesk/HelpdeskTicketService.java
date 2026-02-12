package com.example.demo.helpdesk;

import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRole;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class HelpdeskTicketService {

    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;

    private final HelpdeskTicketRepository repository;
    private final HelpdeskAttachmentRepository attachmentRepository;
    private final HelpdeskTicketMessageRepository messageRepository;
    private final NotificationService notificationService;
    private final Path uploadDir;

    public HelpdeskTicketService(
            HelpdeskTicketRepository repository,
            HelpdeskAttachmentRepository attachmentRepository,
            HelpdeskTicketMessageRepository messageRepository,
            NotificationService notificationService,
            @Value("${helpdesk.upload-dir:/tmp/helpdesk-uploads}") String uploadDir
    ) {
        this.repository = repository;
        this.attachmentRepository = attachmentRepository;
        this.messageRepository = messageRepository;
        this.notificationService = notificationService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional
    public HelpdeskTicket createTicket(Member creator, String name, String email, String subject, String description, List<MultipartFile> files) {
        HelpdeskTicket ticket = new HelpdeskTicket(name.trim(), email.trim(), subject.trim(), description.trim(), creator.getId());
        HelpdeskTicket savedTicket = repository.save(ticket);

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
        ticket.setStatus(status);
        repository.save(ticket);
        return repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
    }

    @Transactional
    public HelpdeskTicket changeStatus(Long ticketId, Member actor, HelpdeskTicketStatus status) {
        HelpdeskTicket updated = changeStatus(ticketId, status);
        notificationService.notifyTicketStatusChanged(updated, actor, status);
        return updated;
    }

    @Transactional
    public HelpdeskTicket softDelete(Long ticketId, Member actor) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Long ownerId = ticket.getCreatedByMemberId();
        boolean isOwner = ownerId != null && ownerId.equals(actor.getId());
        boolean isPrivileged = actor.getRole() == MemberRole.IT || actor.getRole() == MemberRole.ADMIN;
        if (!isOwner && !isPrivileged) {
            throw new ResponseStatusException(FORBIDDEN, "No permission to delete this ticket");
        }
        ticket.softDelete();
        repository.save(ticket);
        return repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
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

    public record AttachmentDownload(Path path, String originalFilename, String contentType) {
    }
}
