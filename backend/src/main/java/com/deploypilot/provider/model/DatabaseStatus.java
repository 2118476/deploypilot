package com.deploypilot.provider.model;

/** Normalised database-project status across the values a provider reports. */
public enum DatabaseStatus {
    UNKNOWN,
    COMING_UP,
    ACTIVE_HEALTHY,
    INACTIVE,
    PAUSED,
    FAILED,
    REMOVED;

    public boolean isReady() { return this == ACTIVE_HEALTHY; }
    public boolean isTerminalFailure() { return this == FAILED || this == REMOVED; }
}
