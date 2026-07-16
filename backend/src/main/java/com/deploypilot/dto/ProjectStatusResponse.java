package com.deploypilot.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, beginner-friendly project status, computed from stored records
 * (never from AI). {@code aiExplanation} is an optional simpler restatement that
 * is absent when AI is unavailable — the rest is always populated.
 */
public class ProjectStatusResponse {

    private Long projectId;
    private String projectName;
    private String status;              // ProjectStatus value
    private String summary;
    private String currentAction;       // "what DeployPilot is doing now", nullable
    private List<Milestone> milestones = new ArrayList<>();
    private List<RequiredAction> requiredActions = new ArrayList<>();
    private RecommendedAction recommendedNextStep;
    private Long latestRunId;
    private String latestRunStatus;
    private String mode;
    private String verificationStatus;
    private String frontendUrl;
    private String backendUrl;
    private String pullRequestUrl;
    private String supabaseProjectUrl;
    private Instant lastUpdated;
    private String aiExplanation;       // optional; null when AI unavailable

    public record Milestone(String key, String label, boolean done) {}
    public record RequiredAction(String type, String label, String detail) {}
    public record RecommendedAction(String type, String label) {}

    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public String getProjectName() { return projectName; } public void setProjectName(String p) { this.projectName = p; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getSummary() { return summary; } public void setSummary(String s) { this.summary = s; }
    public String getCurrentAction() { return currentAction; } public void setCurrentAction(String c) { this.currentAction = c; }
    public List<Milestone> getMilestones() { return milestones; } public void setMilestones(List<Milestone> m) { this.milestones = m; }
    public List<RequiredAction> getRequiredActions() { return requiredActions; } public void setRequiredActions(List<RequiredAction> r) { this.requiredActions = r; }
    public RecommendedAction getRecommendedNextStep() { return recommendedNextStep; } public void setRecommendedNextStep(RecommendedAction r) { this.recommendedNextStep = r; }
    public Long getLatestRunId() { return latestRunId; } public void setLatestRunId(Long l) { this.latestRunId = l; }
    public String getLatestRunStatus() { return latestRunStatus; } public void setLatestRunStatus(String l) { this.latestRunStatus = l; }
    public String getMode() { return mode; } public void setMode(String m) { this.mode = m; }
    public String getVerificationStatus() { return verificationStatus; } public void setVerificationStatus(String v) { this.verificationStatus = v; }
    public String getFrontendUrl() { return frontendUrl; } public void setFrontendUrl(String f) { this.frontendUrl = f; }
    public String getBackendUrl() { return backendUrl; } public void setBackendUrl(String b) { this.backendUrl = b; }
    public String getPullRequestUrl() { return pullRequestUrl; } public void setPullRequestUrl(String p) { this.pullRequestUrl = p; }
    public String getSupabaseProjectUrl() { return supabaseProjectUrl; } public void setSupabaseProjectUrl(String s) { this.supabaseProjectUrl = s; }
    public Instant getLastUpdated() { return lastUpdated; } public void setLastUpdated(Instant l) { this.lastUpdated = l; }
    public String getAiExplanation() { return aiExplanation; } public void setAiExplanation(String a) { this.aiExplanation = a; }
}
