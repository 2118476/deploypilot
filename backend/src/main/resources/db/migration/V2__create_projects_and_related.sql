CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    github_url VARCHAR(255),
    local_folder_path VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_user ON projects(user_id);

CREATE TABLE IF NOT EXISTS project_technologies (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    technology VARCHAR(50) NOT NULL,
    version VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proj_tech_project ON project_technologies(project_id);

CREATE TABLE IF NOT EXISTS project_services (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    service_name VARCHAR(50) NOT NULL,
    configured BOOLEAN NOT NULL DEFAULT FALSE,
    config_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proj_svc_project ON project_services(project_id);
