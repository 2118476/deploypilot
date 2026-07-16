package com.deploypilot.model.enums;

/**
 * Kinds of project activity recorded in the audit trail. Events are emitted only
 * from real lifecycle changes, never from AI statements.
 */
public enum ActivityEventType {
    REPOSITORY_IMPORTED,
    ANALYSIS_COMPLETED,
    BLUEPRINT_GENERATED,
    PROVIDER_CONNECTED,
    PROVIDER_DISCONNECTED,
    SUPABASE_PREPARED,
    CONFIG_PR_CREATED,
    DATABASE_CREATED,
    MIGRATIONS_APPLIED,
    BACKEND_DEPLOYED,
    FRONTEND_DEPLOYED,
    VERIFICATION_COMPLETED,
    AUTOMATION_STARTED,
    AUTOMATION_STEP_COMPLETED,
    AUTOMATION_STEP_FAILED,
    AUTOMATION_SUCCEEDED,
    AUTOMATION_FAILED,
    AUTOMATION_PAUSED,
    COPILOT_PLAN_PROPOSED
}
