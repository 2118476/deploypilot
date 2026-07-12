CREATE TABLE IF NOT EXISTS guide_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    slug VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    icon VARCHAR(50),
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS guides (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    content TEXT,
    difficulty VARCHAR(20),
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guides_category ON guides(category_id);
CREATE INDEX idx_guides_slug ON guides(slug);

CREATE TABLE IF NOT EXISTS guide_sections (
    id BIGSERIAL PRIMARY KEY,
    guide_id BIGINT NOT NULL,
    title VARCHAR(200),
    content TEXT,
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guide_sections_guide ON guide_sections(guide_id);
