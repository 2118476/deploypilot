CREATE TABLE IF NOT EXISTS deployment_blueprints (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    analysis_id BIGINT NOT NULL,
    rules_version VARCHAR(20) NOT NULL,
    blueprint_json TEXT NOT NULL,
    overrides_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blueprints_project ON deployment_blueprints(project_id);
