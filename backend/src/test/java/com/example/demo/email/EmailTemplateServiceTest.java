package com.example.demo.email;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateServiceTest {

    private final EmailTemplateService templateService = new EmailTemplateService("http://localhost:5173");

    @Test
    void renderMessageEscapesHtmlInPayload() {
        EmailMessage message = templateService.renderMessage(
                "ticket_created_v1",
                Map.of(
                        "recipientName", "<Admin>",
                        "ticketId", "100",
                        "subject", "<script>alert(1)</script>",
                        "ticketUrl", "http://localhost:5173/#ticket-100",
                        "actionLabel", "工單已建立"
                ),
                "user@example.com"
        );

        assertThat(message.subject()).contains("#100");
        assertThat(message.htmlBody()).contains("&lt;Admin&gt;");
        assertThat(message.htmlBody()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }
}
