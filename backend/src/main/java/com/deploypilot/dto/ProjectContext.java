package com.deploypilot.dto;

import java.util.List;
import java.util.Map;

/**
 * Bounded, sanitized snapshot of a project used as the Copilot's evidence base.
 * {@code promptText} is the redacted, length-capped text safe to send to the AI.
 * The structured facts let the Copilot answer deterministically and verify that
 * any AI claim is backed by real records. Contains no secret values.
 */
public class ProjectContext {

    private String promptText;
    private String deterministicSummary;
    private boolean hasAnalysis;
    private boolean hasBlueprint;
    private boolean databaseRequired;
    private String latestRunStatus;
    private String currentStepId;
    private String currentStepTitle;
    private String backendUrl;
    private String frontendUrl;
    private String pullRequestUrl;
    private String verificationStatus;
    private String supabaseProjectRef;
    private List<String> missingRequiredSecrets = List.of();
    private Map<String, Boolean> connectionsConnected = Map.of();

    public String getPromptText() { return promptText; } public void setPromptText(String p) { this.promptText = p; }
    public String getDeterministicSummary() { return deterministicSummary; } public void setDeterministicSummary(String d) { this.deterministicSummary = d; }
    public boolean isHasAnalysis() { return hasAnalysis; } public void setHasAnalysis(boolean h) { this.hasAnalysis = h; }
    public boolean isHasBlueprint() { return hasBlueprint; } public void setHasBlueprint(boolean h) { this.hasBlueprint = h; }
    public boolean isDatabaseRequired() { return databaseRequired; } public void setDatabaseRequired(boolean d) { this.databaseRequired = d; }
    public String getLatestRunStatus() { return latestRunStatus; } public void setLatestRunStatus(String s) { this.latestRunStatus = s; }
    public String getCurrentStepId() { return currentStepId; } public void setCurrentStepId(String s) { this.currentStepId = s; }
    public String getCurrentStepTitle() { return currentStepTitle; } public void setCurrentStepTitle(String s) { this.currentStepTitle = s; }
    public String getBackendUrl() { return backendUrl; } public void setBackendUrl(String b) { this.backendUrl = b; }
    public String getFrontendUrl() { return frontendUrl; } public void setFrontendUrl(String f) { this.frontendUrl = f; }
    public String getPullRequestUrl() { return pullRequestUrl; } public void setPullRequestUrl(String p) { this.pullRequestUrl = p; }
    public String getVerificationStatus() { return verificationStatus; } public void setVerificationStatus(String v) { this.verificationStatus = v; }
    public String getSupabaseProjectRef() { return supabaseProjectRef; } public void setSupabaseProjectRef(String s) { this.supabaseProjectRef = s; }
    public List<String> getMissingRequiredSecrets() { return missingRequiredSecrets; } public void setMissingRequiredSecrets(List<String> m) { this.missingRequiredSecrets = m; }
    public Map<String, Boolean> getConnectionsConnected() { return connectionsConnected; } public void setConnectionsConnected(Map<String, Boolean> c) { this.connectionsConnected = c; }

    public boolean isConnected(String provider) { return Boolean.TRUE.equals(connectionsConnected.get(provider)); }
    public boolean deploymentSucceeded() { return "SUCCEEDED".equals(latestRunStatus); }
    public boolean deploymentFailed() { return "FAILED".equals(latestRunStatus); }
    public boolean deploymentRunning() { return "RUNNING".equals(latestRunStatus) || "PENDING".equals(latestRunStatus); }
    public boolean deploymentPaused() { return "PAUSED".equals(latestRunStatus); }
}
