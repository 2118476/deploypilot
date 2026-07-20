package com.deploypilot.dto;

/**
 * Request for the evidence-driven Copilot troubleshooter. All fields are optional:
 * the service selects the latest run and the failed step when they are omitted.
 * {@code event} carries a user-reported safe troubleshooting event (for example
 * MANUAL_DEPLOY_SUCCEEDED) — never any secret value.
 */
public class TroubleshootRequest {
    private Long runId;
    private String stepId;
    private String question;
    private String event;

    public Long getRunId() { return runId; } public void setRunId(Long runId) { this.runId = runId; }
    public String getStepId() { return stepId; } public void setStepId(String stepId) { this.stepId = stepId; }
    public String getQuestion() { return question; } public void setQuestion(String question) { this.question = question; }
    public String getEvent() { return event; } public void setEvent(String event) { this.event = event; }
}
