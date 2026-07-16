package com.deploypilot.dto;

import java.time.Instant;

/**
 * A short-lived confirmation the client must present to execute the plan. The
 * {@code nonce} is single-use and expires; presenting it authorises exactly the
 * plan identified by {@code planHash}.
 */
public class ConfirmationResponse {
    private Long runId;
    private String nonce;
    private String planHash;
    private String mode;
    private Instant expiresAt;
    private final String consentNotice = DeploymentActionPlan.CONSENT_NOTICE;
    private DeploymentActionPlan plan;

    public Long getRunId() { return runId; } public void setRunId(Long r) { this.runId = r; }
    public String getNonce() { return nonce; } public void setNonce(String n) { this.nonce = n; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String p) { this.planHash = p; }
    public String getMode() { return mode; } public void setMode(String m) { this.mode = m; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant e) { this.expiresAt = e; }
    public String getConsentNotice() { return consentNotice; }
    public DeploymentActionPlan getPlan() { return plan; } public void setPlan(DeploymentActionPlan p) { this.plan = p; }
}
