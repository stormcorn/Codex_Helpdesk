package com.example.demo.helpdesk;

import com.example.demo.auth.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "helpdesk_ticket_messages")
public class HelpdeskTicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private HelpdeskTicket ticket;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private String authorEmployeeId;

    @Column(nullable = false)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole authorRole;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected HelpdeskTicketMessage() {
    }

    public HelpdeskTicketMessage(HelpdeskTicket ticket, String content, String authorEmployeeId, String authorName,
                                 MemberRole authorRole) {
        this.ticket = ticket;
        this.content = content;
        this.authorEmployeeId = authorEmployeeId;
        this.authorName = authorName;
        this.authorRole = authorRole;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public String getAuthorEmployeeId() {
        return authorEmployeeId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public MemberRole getAuthorRole() {
        return authorRole;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
