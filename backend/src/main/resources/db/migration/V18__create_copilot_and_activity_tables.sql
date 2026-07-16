-- Stage 5: Project Copilot, Intelligent Dashboard and Controlled Supabase Automation
-- Persistent project-aware Copilot conversations, an ownership-protected activity
-- audit trail, and a record of applied database migrations so retries never
-- reapply them. No secret values are ever stored in these tables.

CREATE TABLE IF NOT EXISTS copilot_conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_copilot_conversation_project UNIQUE (user_id, project_id)
);

CREATE INDEX idx_copilot_conversations_project ON copilot_conversations(project_id);

CREATE TABLE IF NOT EXISTS copilot_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    proposed_action_json TEXT,
    ai_available BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_copilot_messages_conversation ON copilot_messages(conversation_id);
CREATE INDEX idx_copilot_messages_project ON copilot_messages(project_id);

-- Ownership-protected activity audit. Generated only from real lifecycle changes,
-- never from AI statements. Carries safe structured metadata, no secrets and no
-- raw provider responses.
CREATE TABLE IF NOT EXISTS project_activity_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    automation_run_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    provider VARCHAR(20),
    action_id VARCHAR(60),
    summary VARCHAR(500) NOT NULL,
    status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_events_project ON project_activity_events(project_id);

-- Records which repository-owned migrations have been applied to which Supabase
-- project, with the file checksum, so retries and re-runs never reapply them.
CREATE TABLE IF NOT EXISTS applied_database_migrations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    supabase_project_ref VARCHAR(120) NOT NULL,
    migration_name VARCHAR(300) NOT NULL,
    checksum VARCHAR(80) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_applied_migration UNIQUE (project_id, supabase_project_ref, migration_name)
);

CREATE INDEX idx_applied_migrations_project ON applied_database_migrations(project_id);
