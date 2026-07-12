CREATE TABLE IF NOT EXISTS deployment_plans (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE,
    generated_at TIMESTAMP,
    plan_json TEXT NOT NULL,
    current_step_index INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deploy_plan_project ON deployment_plans(project_id);

CREATE TABLE IF NOT EXISTS step_progress (
    id BIGSERIAL PRIMARY KEY,
    deployment_plan_id BIGINT NOT NULL,
    step_index INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    note TEXT,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(deployment_plan_id, step_index)
);

CREATE INDEX idx_step_progress_plan ON step_progress(deployment_plan_id);
