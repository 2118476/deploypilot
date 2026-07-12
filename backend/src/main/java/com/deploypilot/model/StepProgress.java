package com.deploypilot.model;

import com.deploypilot.model.enums.StepStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "step_progress", indexes = {
    @Index(name = "idx_step_progress_plan", columnList = "deployment_plan_id,step_index", unique = true)
})
public class StepProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "deployment_plan_id", nullable = false) private Long deploymentPlanId;
    @Column(name = "step_index", nullable = false) private int stepIndex;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StepStatus status = StepStatus.NOT_STARTED;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(name = "completed_at") private Instant completedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public StepProgress() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getDeploymentPlanId() { return deploymentPlanId; } public void setDeploymentPlanId(Long deploymentPlanId) { this.deploymentPlanId = deploymentPlanId; }
    public int getStepIndex() { return stepIndex; } public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
    public StepStatus getStatus() { return status; } public void setStatus(StepStatus status) { this.status = status; }
    public String getNote() { return note; } public void setNote(String note) { this.note = note; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
