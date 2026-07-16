package com.deploypilot.model;

import com.deploypilot.model.enums.AutomationMode;
import com.deploypilot.model.enums.AutomationRunStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * An executable automation run. Holds the confirmed action plan, per-step
 * progress (with sanitised provider responses) and captured outputs such as the
 * deployed URLs and provider resource ids. No secret values are stored here.
 */
@Entity
@Table(name = "automation_runs", indexes = {
    @Index(name = "idx_automation_runs_project", columnList = "project_id")
})
public class AutomationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "blueprint_id") private Long blueprintId;
    @Enumerated(EnumType.STRING) @Column(name = "mode", nullable = false, length = 20) private AutomationMode mode;
    @Column(name = "plan_hash", length = 64) private String planHash;
    @Column(name = "repository_full_name", length = 200) private String repositoryFullName;
    @Column(name = "commit_sha", length = 80) private String commitSha;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private AutomationRunStatus status = AutomationRunStatus.PENDING;
    @Column(name = "current_step_index", nullable = false) private int currentStepIndex = 0;
    @Column(name = "plan_json", columnDefinition = "TEXT") private String planJson;
    @Column(name = "plan_inputs_json", columnDefinition = "TEXT") private String planInputsJson;
    @Column(name = "steps_json", columnDefinition = "TEXT") private String stepsJson;
    @Column(name = "outputs_json", columnDefinition = "TEXT") private String outputsJson;
    @Column(name = "verification_run_id") private Long verificationRunId;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;

    public AutomationRun() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getBlueprintId() { return blueprintId; } public void setBlueprintId(Long b) { this.blueprintId = b; }
    public AutomationMode getMode() { return mode; } public void setMode(AutomationMode m) { this.mode = m; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String p) { this.planHash = p; }
    public String getRepositoryFullName() { return repositoryFullName; } public void setRepositoryFullName(String r) { this.repositoryFullName = r; }
    public String getCommitSha() { return commitSha; } public void setCommitSha(String c) { this.commitSha = c; }
    public AutomationRunStatus getStatus() { return status; } public void setStatus(AutomationRunStatus s) { this.status = s; }
    public int getCurrentStepIndex() { return currentStepIndex; } public void setCurrentStepIndex(int c) { this.currentStepIndex = c; }
    public String getPlanJson() { return planJson; } public void setPlanJson(String p) { this.planJson = p; }
    public String getPlanInputsJson() { return planInputsJson; } public void setPlanInputsJson(String p) { this.planInputsJson = p; }
    public String getStepsJson() { return stepsJson; } public void setStepsJson(String s) { this.stepsJson = s; }
    public String getOutputsJson() { return outputsJson; } public void setOutputsJson(String o) { this.outputsJson = o; }
    public Long getVerificationRunId() { return verificationRunId; } public void setVerificationRunId(Long v) { this.verificationRunId = v; }
    public String getFailureReason() { return failureReason; } public void setFailureReason(String f) { this.failureReason = f; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant c) { this.completedAt = c; }
}
