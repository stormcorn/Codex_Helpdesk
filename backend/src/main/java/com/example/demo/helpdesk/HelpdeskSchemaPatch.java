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
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS department_groups (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL UNIQUE,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS helpdesk_categories (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL UNIQUE,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("ALTER TABLE helpdesk_categories ALTER COLUMN created_at SET DEFAULT NOW()");
            jdbcTemplate.execute("UPDATE helpdesk_categories SET created_at = NOW() WHERE created_at IS NULL");
            jdbcTemplate.execute("""
                    INSERT INTO helpdesk_categories (name, created_at)
                    SELECT '一般問題', NOW()
                    WHERE NOT EXISTS (
                        SELECT 1 FROM helpdesk_categories WHERE LOWER(name) = LOWER('一般問題')
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS department_group_members (
                        id BIGSERIAL PRIMARY KEY,
                        group_id BIGINT NOT NULL REFERENCES department_groups(id) ON DELETE CASCADE,
                        member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                        supervisor BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        CONSTRAINT uk_department_group_member UNIQUE (group_id, member_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_department_group_members_member_id
                    ON department_group_members (member_id)
                    """);

            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("UPDATE helpdesk_tickets SET deleted = FALSE WHERE deleted IS NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ALTER COLUMN deleted SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
            jdbcTemplate.execute("UPDATE helpdesk_tickets SET status = 'DELETED' WHERE deleted = TRUE AND status <> 'DELETED'");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS group_id BIGINT");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS category_id BIGINT");
            jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_constraint
                            WHERE conname = 'fk_helpdesk_tickets_group'
                        ) THEN
                            ALTER TABLE helpdesk_tickets
                            ADD CONSTRAINT fk_helpdesk_tickets_group
                            FOREIGN KEY (group_id) REFERENCES department_groups(id);
                        END IF;
                    END $$;
                    """);
            jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_constraint
                            WHERE conname = 'fk_helpdesk_tickets_category'
                        ) THEN
                            ALTER TABLE helpdesk_tickets
                            ADD CONSTRAINT fk_helpdesk_tickets_category
                            FOREIGN KEY (category_id) REFERENCES helpdesk_categories(id);
                        END IF;
                    END $$;
                    """);
            jdbcTemplate.execute("""
                    UPDATE helpdesk_tickets
                    SET category_id = (SELECT id FROM helpdesk_categories ORDER BY id LIMIT 1)
                    WHERE category_id IS NULL
                    """);
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ALTER COLUMN category_id SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS priority VARCHAR(32) DEFAULT 'GENERAL'");
            jdbcTemplate.execute("UPDATE helpdesk_tickets SET priority = 'GENERAL' WHERE priority IS NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ALTER COLUMN priority SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS supervisor_approved BOOLEAN DEFAULT TRUE");
            jdbcTemplate.execute("UPDATE helpdesk_tickets SET supervisor_approved = TRUE WHERE supervisor_approved IS NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ALTER COLUMN supervisor_approved SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS supervisor_approved_by_member_id BIGINT");
            jdbcTemplate.execute("ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS supervisor_approved_at TIMESTAMP");

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS helpdesk_ticket_status_histories (
                        id BIGSERIAL PRIMARY KEY,
                        ticket_id BIGINT NOT NULL REFERENCES helpdesk_tickets(id) ON DELETE CASCADE,
                        from_status VARCHAR(32),
                        to_status VARCHAR(32) NOT NULL,
                        changed_by_member_id BIGINT,
                        changed_by_employee_id VARCHAR(100) NOT NULL,
                        changed_by_name VARCHAR(255) NOT NULL,
                        changed_by_role VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_helpdesk_ticket_status_histories_ticket_created_at
                    ON helpdesk_ticket_status_histories (ticket_id, created_at)
                    """);
            jdbcTemplate.execute("""
                    INSERT INTO helpdesk_ticket_status_histories (
                        ticket_id, from_status, to_status, changed_by_member_id,
                        changed_by_employee_id, changed_by_name, changed_by_role, created_at
                    )
                    SELECT
                        t.id,
                        NULL,
                        t.status,
                        t.created_by_member_id,
                        COALESCE(m.employee_id, 'SYSTEM'),
                        COALESCE(m.name, 'System'),
                        COALESCE(CAST(m.role AS TEXT), 'SYSTEM'),
                        t.created_at
                    FROM helpdesk_tickets t
                    LEFT JOIN members m ON m.id = t.created_by_member_id
                    WHERE NOT EXISTS (
                        SELECT 1 FROM helpdesk_ticket_status_histories h WHERE h.ticket_id = t.id
                    )
                    """);
        };
    }
}
