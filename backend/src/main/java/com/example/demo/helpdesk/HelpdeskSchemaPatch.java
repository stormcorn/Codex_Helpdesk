package com.example.demo.helpdesk;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class HelpdeskSchemaPatch {

    @Bean
    CommandLineRunner patchHelpdeskTicketDeleteColumns(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("UPDATE helpdesk_tickets SET deleted = FALSE WHERE deleted IS NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ALTER COLUMN deleted SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
            jdbcTemplate.execute("UPDATE helpdesk_tickets SET status = 'DELETED' WHERE deleted = TRUE AND status <> 'DELETED'");
        };
    }
}
