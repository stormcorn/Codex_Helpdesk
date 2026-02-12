package com.example.demo.helpdesk;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(nullable = false)
    private boolean deleted;

    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HelpdeskAttachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<HelpdeskTicketMessage> messages = new LinkedHashSet<>();

    protected HelpdeskTicket() {
    }

    public HelpdeskTicket(String name, String email, String subject, String description, Long createdByMemberId) {
        this.name = name;
        this.email = email;
        this.subject = subject;
        this.description = description;
        this.createdByMemberId = createdByMemberId;
        this.status = HelpdeskTicketStatus.OPEN;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = HelpdeskTicketStatus.OPEN;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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

    public void setStatus(HelpdeskTicketStatus status) {
        this.status = status;
    }

    public void softDelete() {
        if (!deleted) {
            deletedAt = LocalDateTime.now();
        }
        deleted = true;
        status = HelpdeskTicketStatus.DELETED;
    }
}
