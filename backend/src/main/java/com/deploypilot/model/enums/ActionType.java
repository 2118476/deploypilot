package com.deploypilot.model.enums;

/**
 * Classification of every planned action, ordered from least to most impactful.
 * DESTRUCTIVE actions are never generated in this stage.
 */
public enum ActionType {
    READ_ONLY,
    CREATE,
    UPDATE,
    DEPLOY,
    RESTART,
    DESTRUCTIVE
}
