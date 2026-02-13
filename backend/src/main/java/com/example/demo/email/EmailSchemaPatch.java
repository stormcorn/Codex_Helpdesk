package com.example.demo.email;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class EmailSchemaPatch {

    @Bean
    CommandLineRunner patchEmailTables(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS notification_jobs (
                        id BIGSERIAL PRIMARY KEY,
                        event_type VARCHAR(80) NOT NULL,
                        recipient_member_id BIGINT,
                        recipient_email VARCHAR(320) NOT NULL,
                        template_key VARCHAR(120) NOT NULL,
                        locale VARCHAR(20) NOT NULL DEFAULT 'zh-TW',
                        payload_json TEXT NOT NULL,
                        dedupe_key VARCHAR(200),
                        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        attempts INT NOT NULL DEFAULT 0,
                        max_attempts INT NOT NULL DEFAULT 5,
                        next_retry_at TIMESTAMP,
                        last_error TEXT,
                        provider_message_id VARCHAR(200),
                        trace_id VARCHAR(80),
                        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        sent_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_notification_jobs_status_next_retry
                    ON notification_jobs (status, next_retry_at)
                    """);
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_jobs_dedupe_key
                    ON notification_jobs (dedupe_key)
                    WHERE dedupe_key IS NOT NULL
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS email_delivery_logs (
                        id BIGSERIAL PRIMARY KEY,
                        job_id BIGINT NOT NULL REFERENCES notification_jobs(id),
                        event_type VARCHAR(80) NOT NULL,
                        recipient_email VARCHAR(320) NOT NULL,
                        template_key VARCHAR(120) NOT NULL,
                        provider VARCHAR(40) NOT NULL,
                        provider_message_id VARCHAR(200),
                        success BOOLEAN NOT NULL,
                        error_code VARCHAR(80),
                        error_message TEXT,
                        trace_id VARCHAR(80),
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_email_delivery_logs_job_id
                    ON email_delivery_logs (job_id)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_email_delivery_logs_trace_id
                    ON email_delivery_logs (trace_id)
                    """);
        };
    }
}
