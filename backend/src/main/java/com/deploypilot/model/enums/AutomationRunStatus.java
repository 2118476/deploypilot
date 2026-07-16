package com.deploypilot.model.enums;

public enum AutomationRunStatus {
    PENDING,
    RUNNING,
    /** Halted awaiting user input (e.g. a required database connection). */
    PAUSED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
