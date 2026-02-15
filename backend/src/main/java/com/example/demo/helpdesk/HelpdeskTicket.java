package com.example.demo.helpdesk;

import com.example.demo.group.DepartmentGroup;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "helpdesk_tickets")
public class HelpdeskTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column
    private Long createdByMemberId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private HelpdeskTicketStatus status;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private HelpdeskTicketPriority priority;

    @Column(nullable = false)
    private boolean deleted;

    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private DepartmentGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private HelpdeskCategory category;

    @Column(nullable = false)
    private boolean supervisorApproved;

    @Column
    private Long supervisorApprovedByMemberId;

    @Column
    private LocalDateTime supervisorApprovedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HelpdeskAttachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<HelpdeskTicketMessage> messages = new LinkedHashSet<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<HelpdeskTicketStatusHistory> statusHistories = new LinkedHashSet<>();

    protected HelpdeskTicket() {
    }

    public HelpdeskTicket(String name, String email, String subject, String description, Long createdByMemberId,
                          DepartmentGroup group,
                          HelpdeskCategory category,
                          HelpdeskTicketPriority priority) {
        this.name = name;
        this.email = email;
        this.subject = subject;
        this.description = description;
        this.createdByMemberId = createdByMemberId;
        this.group = group;
        this.category = category;
        this.status = HelpdeskTicketStatus.OPEN;
        this.priority = priority == null ? HelpdeskTicketPriority.GENERAL : priority;
        this.supervisorApproved = this.priority == HelpdeskTicketPriority.GENERAL;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = HelpdeskTicketStatus.OPEN;
        }
        if (priority == null) {
            priority = HelpdeskTicketPriority.GENERAL;
        }
        if (priority == HelpdeskTicketPriority.GENERAL && !supervisorApproved) {
            supervisorApproved = true;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public HelpdeskTicketStatus getStatus() {
        return status;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public HelpdeskTicketPriority getPriority() {
        return priority;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public DepartmentGroup getGroup() {
        return group;
    }

    public boolean isSupervisorApproved() {
        return supervisorApproved;
    }

    public HelpdeskCategory getCategory() {
        return category;
    }

    public Long getSupervisorApprovedByMemberId() {
        return supervisorApprovedByMemberId;
    }

    public LocalDateTime getSupervisorApprovedAt() {
        return supervisorApprovedAt;
    }

    public List<HelpdeskAttachment> getAttachments() {
        return attachments;
    }

    public void addAttachment(HelpdeskAttachment attachment) {
        attachments.add(attachment);
    }

    public Set<HelpdeskTicketMessage> getMessages() {
        return messages;
    }

    public void addMessage(HelpdeskTicketMessage message) {
        messages.add(message);
    }

    public Set<HelpdeskTicketStatusHistory> getStatusHistories() {
        return statusHistories;
    }

    public void addStatusHistory(HelpdeskTicketStatusHistory history) {
        statusHistories.add(history);
    }

    public void setStatus(HelpdeskTicketStatus status) {
        this.status = status;
        if (status == HelpdeskTicketStatus.DELETED) {
            deleted = true;
            if (deletedAt == null) {
                deletedAt = LocalDateTime.now();
            }
        } else {
            deleted = false;
            deletedAt = null;
        }
    }

    public void softDelete() {
        setStatus(HelpdeskTicketStatus.DELETED);
    }

    public void markSupervisorApproved(Long memberId) {
        supervisorApproved = true;
        supervisorApprovedByMemberId = memberId;
        if (supervisorApprovedAt == null) {
            supervisorApprovedAt = LocalDateTime.now();
        }
    }
}
