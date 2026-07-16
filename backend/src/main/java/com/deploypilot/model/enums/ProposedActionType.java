package com.deploypilot.model.enums;

/**
 * A typed action the Copilot may propose. The Copilot never executes these — it
 * only prepares a deterministic plan and sends the user to the existing review
 * and confirmation flow. The set is server-owned; AI free-form JSON is never
 * trusted to define an action.
 */
public enum ProposedActionType {
    NONE,
    DEPLOY,
    RETRY_FAILED_STEP
}
