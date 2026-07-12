CREATE TABLE IF NOT EXISTS bookmarks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    item_id BIGINT NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, item_type, item_id)
);

CREATE INDEX idx_bookmarks_user ON bookmarks(user_id);

CREATE TABLE IF NOT EXISTS user_notes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT,
    title VARCHAR(200),
    content TEXT,
    tags VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notes_user ON user_notes(user_id);

CREATE TABLE IF NOT EXISTS deployment_records (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    version VARCHAR(50),
    url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    notes TEXT,
    deployed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deploy_rec_project ON deployment_records(project_id);

CREATE TABLE IF NOT EXISTS error_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    project_id BIGINT,
    error_type VARCHAR(50) NOT NULL,
    original_content TEXT,
    redacted_content TEXT,
    ai_response TEXT,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_errors_user ON error_reports(user_id);
