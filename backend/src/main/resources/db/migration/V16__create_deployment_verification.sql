CREATE TABLE IF NOT EXISTS deployment_targets (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    component_id VARCHAR(120),
    target_type VARCHAR(20) NOT NULL,
    platform VARCHAR(60),
    url VARCHAR(500) NOT NULL,
    health_path VARCHAR(200),
    expected_commit VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_verified_at TIMESTAMP,
    last_result VARCHAR(20)
);

CREATE INDEX idx_targets_project ON deployment_targets(project_id);

CREATE TABLE IF NOT EXISTS verification_runs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    blueprint_id BIGINT,
    blueprint_commit VARCHAR(80),
    frontend_url VARCHAR(500),
    backend_url VARCHAR(500),
    overall_status VARCHAR(20) NOT NULL,
    result_json TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_verifications_project ON verification_runs(project_id);
