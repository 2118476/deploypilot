package com.deploypilot.model.enums;

/**
 * Deterministic project lifecycle status shown on the intelligent dashboard.
 * Computed from stored records only — never from AI. Distinct from the legacy
 * {@link ProjectStatus} on the project entity (PLANNING/…).
 */
public enum ProjectDashboardStatus {
    NOT_ANALYSED,
    ANALYSING,
    BLUEPRINT_READY,
    SETUP_REQUIRED,
    WAITING_FOR_CONNECTION,
    WAITING_FOR_SECRET,
    WAITING_FOR_CONFIRMATION,
    DEPLOYING,
    PAUSED,
    VERIFYING,
    HEALTHY,
    DEGRADED,
    FAILED,
    UNKNOWN
}
