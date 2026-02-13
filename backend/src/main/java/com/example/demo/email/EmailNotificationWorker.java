package com.example.demo.email;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationWorker {

    private final EmailNotificationService emailNotificationService;

    public EmailNotificationWorker(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @Scheduled(fixedDelayString = "${app.email.worker-fixed-delay-ms:15000}")
    public void dispatch() {
        emailNotificationService.dispatchPendingJobs();
    }
}
