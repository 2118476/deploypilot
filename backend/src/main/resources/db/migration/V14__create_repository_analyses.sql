CREATE TABLE IF NOT EXISTS repository_analyses (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    repository_full_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json TEXT,
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_repo_analyses_project ON repository_analyses(project_id);
