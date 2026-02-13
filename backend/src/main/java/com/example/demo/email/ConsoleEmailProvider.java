package com.example.demo.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailProvider.class);

    @Override
    public String providerName() {
        return "CONSOLE";
    }

    @Override
    public String send(EmailMessage message) {
        String providerMessageId = "console-" + UUID.randomUUID();
        log.info(
                "email sent provider={} messageId={} to={} subject={} text={}",
                providerName(),
                providerMessageId,
                message.to(),
                message.subject(),
                message.textBody()
        );
        return providerMessageId;
    }
}
