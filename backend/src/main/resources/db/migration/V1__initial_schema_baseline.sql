CREATE TABLE IF NOT EXISTS members (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE members DROP CONSTRAINT IF EXISTS members_role_check;
ALTER TABLE members ADD CONSTRAINT members_role_check CHECK (role IN ('ADMIN','IT','USER'));

CREATE TABLE IF NOT EXISTS auth_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(120) NOT NULL UNIQUE,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_member_id ON auth_tokens(member_id);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_expires_at ON auth_tokens(expires_at);

CREATE TABLE IF NOT EXISTS department_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS helpdesk_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE helpdesk_categories ALTER COLUMN created_at SET DEFAULT NOW();
UPDATE helpdesk_categories SET created_at = NOW() WHERE created_at IS NULL;
INSERT INTO helpdesk_categories (name, created_at)
SELECT '一般問題', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM helpdesk_categories WHERE LOWER(name) = LOWER('一般問題')
);

CREATE TABLE IF NOT EXISTS department_group_members (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES department_groups(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    supervisor BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_department_group_member UNIQUE (group_id, member_id)
);
CREATE INDEX IF NOT EXISTS idx_department_group_members_member_id
ON department_group_members (member_id);

CREATE TABLE IF NOT EXISTS helpdesk_tickets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    created_by_member_id BIGINT,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    group_id BIGINT,
    category_id BIGINT,
    supervisor_approved BOOLEAN NOT NULL DEFAULT TRUE,
    supervisor_approved_by_member_id BIGINT,
    supervisor_approved_at TIMESTAMP
);

ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
UPDATE helpdesk_tickets SET deleted = FALSE WHERE deleted IS NULL;
ALTER TABLE helpdesk_tickets ALTER COLUMN deleted SET NOT NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
UPDATE helpdesk_tickets SET status = 'DELETED' WHERE deleted = TRUE AND status <> 'DELETED';
ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS group_id BIGINT;
ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS category_id BIGINT;
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
UPDATE helpdesk_tickets
SET category_id = (SELECT id FROM helpdesk_categories ORDER BY id LIMIT 1)
WHERE category_id IS NULL;
ALTER TABLE helpdesk_tickets ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS priority VARCHAR(32) DEFAULT 'GENERAL';
UPDATE helpdesk_tickets SET priority = 'GENERAL' WHERE priority IS NULL;
ALTER TABLE helpdesk_tickets ALTER COLUMN priority SET NOT NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS supervisor_approved BOOLEAN DEFAULT TRUE;
UPDATE helpdesk_tickets SET supervisor_approved = TRUE WHERE supervisor_approved IS NULL;
ALTER TABLE helpdesk_tickets ALTER COLUMN supervisor_approved SET NOT NULL;
ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS supervisor_approved_by_member_id BIGINT;
ALTER TABLE helpdesk_tickets ADD COLUMN IF NOT EXISTS supervisor_approved_at TIMESTAMP;

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
);
CREATE INDEX IF NOT EXISTS idx_helpdesk_ticket_status_histories_ticket_created_at
ON helpdesk_ticket_status_histories (ticket_id, created_at);

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
);

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
);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs (actor_member_id, created_at);

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
);
CREATE INDEX IF NOT EXISTS idx_notification_jobs_status_next_retry
ON notification_jobs (status, next_retry_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_jobs_dedupe_key
ON notification_jobs (dedupe_key)
WHERE dedupe_key IS NOT NULL;

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
);
CREATE INDEX IF NOT EXISTS idx_email_delivery_logs_job_id
ON email_delivery_logs (job_id);
CREATE INDEX IF NOT EXISTS idx_email_delivery_logs_trace_id
ON email_delivery_logs (trace_id);
