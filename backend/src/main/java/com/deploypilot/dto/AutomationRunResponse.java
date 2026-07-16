package com.deploypilot.dto;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full view of an automation run: its plan, per-step progress, captured outputs
 * (URLs and resource ids — never secrets) and the automatic verification result.
 */
public class AutomationRunResponse {
    private Long id;
    private Long projectId;
    private String mode;
    private String status;
    private String planHash;
    private String repository;
    private String branch;
    private String commitSha;
    private int currentStepIndex;
    private List<ExecutionStep> steps;
    private Map<String, String> outputs = new LinkedHashMap<>();
    private Long verificationRunId;
    private String verificationStatus;
    private String failureReason;
    private DeploymentActionPlan plan;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public Long getId() { return id; } public void setId(Long i) { this.id = i; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public String getMode() { return mode; } public void setMode(String m) { this.mode = m; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String p) { this.planHash = p; }
    public String getRepository() { return repository; } public void setRepository(String r) { this.repository = r; }
    public String getBranch() { return branch; } public void setBranch(String b) { this.branch = b; }
    public String getCommitSha() { return commitSha; } public void setCommitSha(String c) { this.commitSha = c; }
    public int getCurrentStepIndex() { return currentStepIndex; } public void setCurrentStepIndex(int c) { this.currentStepIndex = c; }
    public List<ExecutionStep> getSteps() { return steps; } public void setSteps(List<ExecutionStep> s) { this.steps = s; }
    public Map<String, String> getOutputs() { return outputs; } public void setOutputs(Map<String, String> o) { this.outputs = o; }
    public Long getVerificationRunId() { return verificationRunId; } public void setVerificationRunId(Long v) { this.verificationRunId = v; }
    public String getVerificationStatus() { return verificationStatus; } public void setVerificationStatus(String v) { this.verificationStatus = v; }
    public String getFailureReason() { return failureReason; } public void setFailureReason(String f) { this.failureReason = f; }
    public DeploymentActionPlan getPlan() { return plan; } public void setPlan(DeploymentActionPlan p) { this.plan = p; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant u) { this.updatedAt = u; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant c) { this.completedAt = c; }
}
