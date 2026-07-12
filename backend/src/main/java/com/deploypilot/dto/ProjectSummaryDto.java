package com.deploypilot.dto;

import com.deploypilot.model.enums.ProjectStatus;
import java.time.Instant;

public class ProjectSummaryDto {
    private Long id;
    private String name;
    private String description;
    private ProjectStatus status;
    private String techSummary;
    private int currentStep;
    private int totalSteps;
    private int completedSteps;
    private String nextAction;
    private Instant createdAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String n) { this.name = n; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public ProjectStatus getStatus() { return status; } public void setStatus(ProjectStatus s) { this.status = s; }
    public String getTechSummary() { return techSummary; } public void setTechSummary(String t) { this.techSummary = t; }
    public int getCurrentStep() { return currentStep; } public void setCurrentStep(int c) { this.currentStep = c; }
    public int getTotalSteps() { return totalSteps; } public void setTotalSteps(int t) { this.totalSteps = t; }
    public int getCompletedSteps() { return completedSteps; } public void setCompletedSteps(int c) { this.completedSteps = c; }
    public String getNextAction() { return nextAction; } public void setNextAction(String n) { this.nextAction = n; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
}
