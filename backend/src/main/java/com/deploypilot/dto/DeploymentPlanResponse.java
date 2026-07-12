package com.deploypilot.dto;

import com.deploypilot.model.enums.DeploymentStatus;
import java.time.Instant;
import java.util.List;

public class DeploymentPlanResponse {
    private Long id;
    private Long projectId;
    private List<DeploymentStepDto> steps;
    private int currentStepIndex;
    private DeploymentStatus status;
    private int totalSteps;
    private int completedSteps;
    private Instant generatedAt;
    private Instant createdAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public List<DeploymentStepDto> getSteps() { return steps; } public void setSteps(List<DeploymentStepDto> s) { this.steps = s; }
    public int getCurrentStepIndex() { return currentStepIndex; } public void setCurrentStepIndex(int i) { this.currentStepIndex = i; }
    public DeploymentStatus getStatus() { return status; } public void setStatus(DeploymentStatus s) { this.status = s; }
    public int getTotalSteps() { return totalSteps; } public void setTotalSteps(int t) { this.totalSteps = t; }
    public int getCompletedSteps() { return completedSteps; } public void setCompletedSteps(int c) { this.completedSteps = c; }
    public Instant getGeneratedAt() { return generatedAt; } public void setGeneratedAt(Instant g) { this.generatedAt = g; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
}
