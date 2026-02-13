package com.example.demo.helpdesk;

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
@Table(name = "helpdesk_ticket_status_histories")
public class HelpdeskTicketStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private HelpdeskTicket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private HelpdeskTicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private HelpdeskTicketStatus toStatus;

    @Column
    private Long changedByMemberId;

    @Column(nullable = false)
    private String changedByEmployeeId;

    @Column(nullable = false)
    private String changedByName;

    @Column(nullable = false)
    private String changedByRole;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected HelpdeskTicketStatusHistory() {
    }

    public HelpdeskTicketStatusHistory(
            HelpdeskTicket ticket,
            HelpdeskTicketStatus fromStatus,
            HelpdeskTicketStatus toStatus,
            Long changedByMemberId,
            String changedByEmployeeId,
            String changedByName,
            String changedByRole
    ) {
        this.ticket = ticket;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByMemberId = changedByMemberId;
        this.changedByEmployeeId = changedByEmployeeId;
        this.changedByName = changedByName;
        this.changedByRole = changedByRole;
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

    public HelpdeskTicketStatus getFromStatus() {
        return fromStatus;
    }

    public HelpdeskTicketStatus getToStatus() {
        return toStatus;
    }

    public Long getChangedByMemberId() {
        return changedByMemberId;
    }

    public String getChangedByEmployeeId() {
        return changedByEmployeeId;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public String getChangedByRole() {
        return changedByRole;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
