package com.example.demo.audit;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AuditSchemaPatch {

    @Bean
    CommandLineRunner patchAuditLogsTable(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS audit_logs (
                        id BIGSERIAL PRIMARY KEY,
                        actor_member_id BIGINT,
                        actor_employee_id VARCHAR(100) NOT NULL,
                        actor_name VARCHAR(255) NOT NULL,
                        actor_role VARCHAR(32) NOT NULL,
                        action VARCHAR(100) NOT NULL,
                        entity_type VARCHAR(100) NOT NULL,
                        entity_id BIGINT,
                        before_json TEXT,
                        after_json TEXT,
                        metadata_json TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
                    ON audit_logs (created_at)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
                    ON audit_logs (entity_type, entity_id)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_audit_logs_actor
                    ON audit_logs (actor_member_id, created_at)
                    """);
        };
    }
}
