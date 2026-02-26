package com.example.demo.helpdesk;

import com.example.demo.audit.AuditLogService;
import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRepository;
import com.example.demo.auth.MemberRole;
import com.example.demo.email.EmailNotificationService;
import com.example.demo.group.DepartmentGroup;
import com.example.demo.group.DepartmentGroupService;
import com.example.demo.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class HelpdeskTicketService {

    private final HelpdeskTicketRepository repository;
    private final HelpdeskTicketMessageRepository messageRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;
    private final DepartmentGroupService groupService;
    private final HelpdeskCategoryService categoryService;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    private final HelpdeskAttachmentService attachmentService;
    private final HelpdeskTicketHistoryService historyService;
    private final HelpdeskTicketSnapshotService snapshotService;
    private final HelpdeskRealtimePublisher realtimePublisher;

    public HelpdeskTicketService(
            HelpdeskTicketRepository repository,
            HelpdeskTicketMessageRepository messageRepository,
            MemberRepository memberRepository,
            AuditLogService auditLogService,
            DepartmentGroupService groupService,
            HelpdeskCategoryService categoryService,
            NotificationService notificationService,
            EmailNotificationService emailNotificationService,
            HelpdeskAttachmentService attachmentService,
            HelpdeskTicketHistoryService historyService,
            HelpdeskTicketSnapshotService snapshotService,
            HelpdeskRealtimePublisher realtimePublisher
    ) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
        this.groupService = groupService;
        this.categoryService = categoryService;
        this.notificationService = notificationService;
        this.emailNotificationService = emailNotificationService;
        this.attachmentService = attachmentService;
        this.historyService = historyService;
        this.snapshotService = snapshotService;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public HelpdeskTicket createTicket(
            Member creator,
            String name,
            String email,
            String subject,
            String description,
            Long groupId,
            Long categoryId,
            HelpdeskTicketPriority priority,
            List<MultipartFile> files
    ) {
        if (groupId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Group is required");
        }
        DepartmentGroup group = groupService.requireGroup(groupId);
        if (categoryId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Category is required");
        }
        HelpdeskCategory category = categoryService.requireCategory(categoryId);
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
                category,
                priority
        );
        HelpdeskTicket savedTicket = repository.save(ticket);
        historyService.appendStatusHistory(savedTicket, null, savedTicket.getStatus(), creator);
        attachmentService.saveAttachments(savedTicket, files);

        HelpdeskTicket finalTicket = repository.save(savedTicket);
        auditLogService.record(
                creator,
                "TICKET_CREATE",
                "TICKET",
                finalTicket.getId(),
                null,
                snapshotService.toJson(snapshotService.ticketSnapshot(finalTicket)),
                snapshotService.toJson(Map.of("attachmentsCount", finalTicket.getAttachments().size()))
        );
        notificationService.notifyTicketCreated(finalTicket, creator);
        emailNotificationService.enqueueTicketCreated(finalTicket, creator);
        realtimePublisher.publishTicketCreated(finalTicket.getId(), creator.getId());
        return finalTicket;
    }

    public List<HelpdeskTicket> listRecentTickets() {
        return repository.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional
    public HelpdeskTicket addReply(Long ticketId, Member author, String content, List<MultipartFile> files) {
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
        attachmentService.saveAttachments(ticket, files == null ? List.of() : files);
        repository.save(ticket);
        HelpdeskTicket updated = repository.findWithDetailsById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        notificationService.notifyTicketReplied(updated, author);
        emailNotificationService.enqueueTicketReplied(updated, author);
        realtimePublisher.publishTicketReplied(updated.getId(), author.getId());
        return updated;
    }

    @Transactional
    public HelpdeskTicket addReply(Long ticketId, Member author, String content) {
        return addReply(ticketId, author, content, List.of());
    }

    @Transactional
    public HelpdeskTicket changeStatus(Long ticketId, HelpdeskTicketStatus status) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = snapshotService.ticketSnapshot(ticket);
        HelpdeskTicketStatus fromStatus = ticket.getStatus();
        ticket.setStatus(status);
        if (fromStatus != status) {
            historyService.appendStatusHistory(ticket, fromStatus, status, null);
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
                    snapshotService.toJson(before),
                    snapshotService.toJson(snapshotService.ticketSnapshot(updated)),
                    snapshotService.toJson(Map.of("fromStatus", fromStatus.name(), "toStatus", status.name()))
            );
        }
        return updated;
    }

    @Transactional
    public HelpdeskTicket changeStatus(Long ticketId, Member actor, HelpdeskTicketStatus status) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = snapshotService.ticketSnapshot(ticket);
        HelpdeskTicketStatus fromStatus = ticket.getStatus();
        ticket.setStatus(status);
        if (fromStatus != status) {
            historyService.appendStatusHistory(ticket, fromStatus, status, actor);
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
                    snapshotService.toJson(before),
                    snapshotService.toJson(snapshotService.ticketSnapshot(updated)),
                    snapshotService.toJson(Map.of("fromStatus", fromStatus.name(), "toStatus", status.name()))
            );
        }
        notificationService.notifyTicketStatusChanged(updated, actor, status);
        if (status == HelpdeskTicketStatus.CLOSED) {
            emailNotificationService.enqueueTicketClosed(updated, actor);
        }
        realtimePublisher.publishTicketStatusChanged(updated.getId(), actor.getId());
        return updated;
    }

    @Transactional
    public HelpdeskTicket softDelete(Long ticketId, Member actor) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = snapshotService.ticketSnapshot(ticket);
        Long ownerId = ticket.getCreatedByMemberId();
        boolean isOwner = ownerId != null && ownerId.equals(actor.getId());
        boolean isPrivileged = actor.getRole() == MemberRole.IT || actor.getRole() == MemberRole.ADMIN;
        if (!isOwner && !isPrivileged) {
            throw new ResponseStatusException(FORBIDDEN, "No permission to delete this ticket");
        }
        HelpdeskTicketStatus fromStatus = ticket.getStatus();
        ticket.softDelete();
        if (fromStatus != HelpdeskTicketStatus.DELETED) {
            historyService.appendStatusHistory(ticket, fromStatus, HelpdeskTicketStatus.DELETED, actor);
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
                    snapshotService.toJson(before),
                    snapshotService.toJson(snapshotService.ticketSnapshot(updated)),
                    snapshotService.toJson(Map.of("fromStatus", fromStatus.name(), "toStatus", HelpdeskTicketStatus.DELETED.name()))
            );
        }
        realtimePublisher.publishTicketDeleted(updated.getId(), actor.getId());
        return updated;
    }

    @Transactional
    public HelpdeskTicket approveUrgentTicket(Long ticketId, Member actor) {
        HelpdeskTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        Map<String, Object> before = snapshotService.ticketSnapshot(ticket);
        if (ticket.getPriority() != HelpdeskTicketPriority.URGENT) {
            throw new ResponseStatusException(FORBIDDEN, "Only urgent tickets require supervisor approval");
        }
        if (ticket.getGroup() == null || !groupService.isSupervisor(ticket.getGroup().getId(), actor.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "Group supervisor only");
        }
        if (!ticket.isSupervisorApproved()) {
            ticket.markSupervisorApproved(actor.getId());
            historyService.appendStatusHistory(ticket, ticket.getStatus(), ticket.getStatus(), actor);
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
                    snapshotService.toJson(before),
                    snapshotService.toJson(snapshotService.ticketSnapshot(updated)),
                    snapshotService.toJson(Map.of("groupId", updated.getGroup() == null ? null : updated.getGroup().getId()))
            );
        }
        realtimePublisher.publishTicketSupervisorApproved(updated.getId(), actor.getId());
        return updated;
    }

    public AttachmentDownload getAttachment(Long ticketId, Long attachmentId) {
        return attachmentService.getAttachment(ticketId, attachmentId);
    }

    public List<HelpdeskTicketMessage> sortMessagesByCreatedAt(Collection<HelpdeskTicketMessage> messages) {
        return historyService.sortMessagesByCreatedAt(messages);
    }

    public List<HelpdeskTicketStatusHistory> sortStatusHistoriesByCreatedAt(Collection<HelpdeskTicketStatusHistory> histories) {
        return historyService.sortStatusHistoriesByCreatedAt(histories);
    }

    @Transactional(readOnly = true)
    public String getCreatorEmployeeId(HelpdeskTicket ticket) {
        Long memberId = ticket.getCreatedByMemberId();
        if (memberId == null) return null;
        return memberRepository.findById(memberId).map(Member::getEmployeeId).orElse(null);
    }

    public record AttachmentDownload(java.nio.file.Path path, String originalFilename, String contentType) {
    }
}
