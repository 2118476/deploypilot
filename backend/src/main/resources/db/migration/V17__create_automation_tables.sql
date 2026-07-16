-- Stage 4: Controlled Deployment Automation
-- Per-user provider connections, short-lived confirmations, automation runs and
-- encrypted user-supplied deployment secrets. Provider credentials are stored
-- encrypted (AES-GCM); this schema never stores plaintext tokens.

CREATE TABLE IF NOT EXISTS provider_connections (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    connection_type VARCHAR(30) NOT NULL,
    account_label VARCHAR(200),
    external_account_id VARCHAR(200),
    scopes VARCHAR(500),
    encrypted_credential TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONNECTED',
    last_error VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP,
    CONSTRAINT uq_connection_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_connections_user ON provider_connections(user_id);

-- User-supplied deployment secret values (e.g. database password, provider-issued
-- keys) held only while an automation needs them, encrypted at rest. Values are
-- never returned to the client after saving.
CREATE TABLE IF NOT EXISTS automation_secrets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    var_name VARCHAR(200) NOT NULL,
    encrypted_value TEXT NOT NULL,
    destination VARCHAR(60),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_secret_project_var UNIQUE (project_id, var_name)
);

CREATE INDEX idx_secrets_project ON automation_secrets(project_id);

-- Executable automation run: holds the confirmed plan, per-step progress with
-- sanitised provider responses, and captured outputs (URLs, resource ids). No
-- secret values are ever persisted here.
CREATE TABLE IF NOT EXISTS automation_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    blueprint_id BIGINT,
    mode VARCHAR(20) NOT NULL,
    plan_hash VARCHAR(64),
    repository_full_name VARCHAR(200),
    commit_sha VARCHAR(80),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_step_index INT NOT NULL DEFAULT 0,
    plan_json TEXT,
    plan_inputs_json TEXT,
    steps_json TEXT,
    outputs_json TEXT,
    verification_run_id BIGINT,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_automation_runs_project ON automation_runs(project_id);

-- Short-lived, single-use confirmation bound to the exact plan, repository/commit
-- and provider accounts. A changed plan invalidates it; an expired or already
-- consumed confirmation cannot be replayed.
CREATE TABLE IF NOT EXISTS deployment_confirmations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    automation_run_id BIGINT,
    nonce VARCHAR(80) NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    repository_full_name VARCHAR(200),
    commit_sha VARCHAR(80),
    account_binding VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    CONSTRAINT uq_confirmation_nonce UNIQUE (nonce)
);

CREATE INDEX idx_confirmations_project ON deployment_confirmations(project_id);
