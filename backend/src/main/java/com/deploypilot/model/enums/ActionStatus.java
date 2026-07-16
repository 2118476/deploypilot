package com.deploypilot.model.enums;

/** Per-step execution status within an automation run. */
public enum ActionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
