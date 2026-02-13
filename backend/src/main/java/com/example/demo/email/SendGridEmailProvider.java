package com.example.demo.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "sendgrid")
public class SendGridEmailProvider implements EmailProvider {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final String endpoint;

    public SendGridEmailProvider(
            ObjectMapper objectMapper,
            @Value("${app.email.sendgrid.api-key:}") String apiKey,
            @Value("${app.email.from-email:}") String fromEmail,
            @Value("${app.email.from-name:Helpdesk}") String fromName,
            @Value("${app.email.sendgrid.endpoint:https://api.sendgrid.com/v3/mail/send}") String endpoint
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.endpoint = endpoint;
    }

    @Override
    public String providerName() {
        return "SENDGRID";
    }

    @Override
    public String send(EmailMessage message) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SendGrid api key is missing");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Email from-address is missing");
        }

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", message.to())))),
                "from", Map.of("email", fromEmail, "name", fromName),
                "subject", message.subject(),
                "content", List.of(
                        Map.of("type", "text/plain", "value", message.textBody()),
                        Map.of("type", "text/html", "value", message.htmlBody())
                )
        );

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SendGrid payload", e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("SendGrid send failed: " + response.getStatusCode().value() + " " + response.getBody());
        }

        String providerMessageId = response.getHeaders().getFirst("X-Message-Id");
        return (providerMessageId == null || providerMessageId.isBlank())
                ? "sendgrid-" + UUID.randomUUID()
                : providerMessageId;
    }
}
