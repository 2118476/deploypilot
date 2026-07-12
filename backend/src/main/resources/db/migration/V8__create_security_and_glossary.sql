CREATE TABLE IF NOT EXISTS security_checks (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT,
    user_id BIGINT,
    check_name VARCHAR(100) NOT NULL,
    check_category VARCHAR(50) NOT NULL,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sec_checks_project ON security_checks(project_id);
CREATE INDEX idx_sec_checks_user ON security_checks(user_id);

CREATE TABLE IF NOT EXISTS glossary_terms (
    id BIGSERIAL PRIMARY KEY,
    term VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE,
    definition TEXT NOT NULL,
    example TEXT,
    category VARCHAR(50),
    related_terms VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_glossary_slug ON glossary_terms(slug);
