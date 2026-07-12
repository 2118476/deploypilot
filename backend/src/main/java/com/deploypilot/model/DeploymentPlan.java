package com.deploypilot.model;

import com.deploypilot.model.enums.DeploymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "deployment_plans", indexes = {
    @Index(name = "idx_deploy_plan_project", columnList = "project_id", unique = true)
})
public class DeploymentPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false, unique = true) private Long projectId;
    @Column(name = "generated_at") private Instant generatedAt;
    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT") private String planJson;
    @Column(name = "current_step_index", nullable = false) private int currentStepIndex = 0;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DeploymentStatus status = DeploymentStatus.PENDING;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public DeploymentPlan() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Instant getGeneratedAt() { return generatedAt; } public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public String getPlanJson() { return planJson; } public void setPlanJson(String planJson) { this.planJson = planJson; }
    public int getCurrentStepIndex() { return currentStepIndex; } public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }
    public DeploymentStatus getStatus() { return status; } public void setStatus(DeploymentStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
