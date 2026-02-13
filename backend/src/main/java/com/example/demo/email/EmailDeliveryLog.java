package com.example.demo.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_delivery_logs")
public class EmailDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 320)
    private String recipientEmail;

    @Column(nullable = false, length = 120)
    private String templateKey;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(length = 200)
    private String providerMessageId;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 80)
    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 80)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected EmailDeliveryLog() {
    }

    public EmailDeliveryLog(
            Long jobId,
            String eventType,
            String recipientEmail,
            String templateKey,
            String provider,
            String providerMessageId,
            boolean success,
            String errorCode,
            String errorMessage,
            String traceId
    ) {
        this.jobId = jobId;
        this.eventType = eventType;
        this.recipientEmail = recipientEmail;
        this.templateKey = templateKey;
        this.provider = provider;
        this.providerMessageId = providerMessageId;
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.traceId = traceId;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
