CREATE TABLE IF NOT EXISTS command_snippets (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    command TEXT NOT NULL,
    description TEXT,
    explanation TEXT,
    warning TEXT,
    is_destructive BOOLEAN NOT NULL DEFAULT FALSE,
    beginner_mode BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commands_category ON command_snippets(category);

CREATE TABLE IF NOT EXISTS env_var_definitions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    category VARCHAR(30),
    platform VARCHAR(50),
    local_file_location VARCHAR(100),
    production_location VARCHAR(100),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    example_value VARCHAR(200),
    documentation_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS project_env_vars (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    definition_id BIGINT,
    variable_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    classification VARCHAR(20),
    local_location VARCHAR(100),
    production_location VARCHAR(100),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    configured BOOLEAN NOT NULL DEFAULT FALSE,
    last_verified_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proj_env_project ON project_env_vars(project_id);
