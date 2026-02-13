package com.example.demo.email;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {
}
