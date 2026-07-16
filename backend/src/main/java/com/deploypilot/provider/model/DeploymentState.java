package com.deploypilot.provider.model;

/** Normalised deployment state across providers. */
public enum DeploymentState {
    QUEUED,
    BUILDING,
    LIVE,
    FAILED,
    CANCELED,
    UNKNOWN;

    public boolean isTerminal() {
        return this == LIVE || this == FAILED || this == CANCELED;
    }
}
