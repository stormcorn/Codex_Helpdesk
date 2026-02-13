package com.example.demo.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_jobs")
public class EmailNotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private EmailEventType eventType;

    @Column
    private Long recipientMemberId;

    @Column(nullable = false, length = 320)
    private String recipientEmail;

    @Column(nullable = false, length = 120)
    private String templateKey;

    @Column(nullable = false, length = 20)
    private String locale = "zh-TW";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(length = 200)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailJobStatus status = EmailJobStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private int maxAttempts = 5;

    @Column
    private LocalDateTime nextRetryAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(length = 200)
    private String providerMessageId;

    @Column(length = 80)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime sentAt;

    protected EmailNotificationJob() {
    }

    public EmailNotificationJob(
            EmailEventType eventType,
            Long recipientMemberId,
            String recipientEmail,
            String templateKey,
            String locale,
            String payloadJson,
            String dedupeKey,
            String traceId
    ) {
        this.eventType = eventType;
        this.recipientMemberId = recipientMemberId;
        this.recipientEmail = recipientEmail;
        this.templateKey = templateKey;
        this.locale = locale;
        this.payloadJson = payloadJson;
        this.dedupeKey = dedupeKey;
        this.traceId = traceId;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (nextRetryAt == null) nextRetryAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public EmailEventType getEventType() {
        return eventType;
    }

    public Long getRecipientMemberId() {
        return recipientMemberId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public String getLocale() {
        return locale;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public EmailJobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getTraceId() {
        return traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void markProcessing() {
        this.status = EmailJobStatus.PROCESSING;
        this.lastError = null;
    }

    public void markSent(String providerMessageId) {
        this.status = EmailJobStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markFailedAttempt(String error, LocalDateTime nextRetryAt) {
        this.attempts += 1;
        this.lastError = error;
        if (attempts >= maxAttempts) {
            this.status = EmailJobStatus.FAILED;
            this.nextRetryAt = null;
            return;
        }
        this.status = EmailJobStatus.RETRYING;
        this.nextRetryAt = nextRetryAt;
    }
}
