package com.deploypilot.model;

import com.deploypilot.model.enums.DeploymentTargetType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "deployment_targets", indexes = {
    @Index(name = "idx_targets_project", columnList = "project_id")
})
public class DeploymentTarget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "component_id", length = 120) private String componentId;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 20) private DeploymentTargetType targetType;
    @Column(length = 60) private String platform;
    @Column(nullable = false, length = 500) private String url;
    @Column(name = "health_path", length = 200) private String healthPath;
    @Column(name = "expected_commit", length = 80) private String expectedCommit;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "last_verified_at") private Instant lastVerifiedAt;
    @Column(name = "last_result", length = 20) private String lastResult;

    public DeploymentTarget() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public String getComponentId() { return componentId; } public void setComponentId(String c) { this.componentId = c; }
    public DeploymentTargetType getTargetType() { return targetType; } public void setTargetType(DeploymentTargetType t) { this.targetType = t; }
    public String getPlatform() { return platform; } public void setPlatform(String p) { this.platform = p; }
    public String getUrl() { return url; } public void setUrl(String u) { this.url = u; }
    public String getHealthPath() { return healthPath; } public void setHealthPath(String h) { this.healthPath = h; }
    public String getExpectedCommit() { return expectedCommit; } public void setExpectedCommit(String e) { this.expectedCommit = e; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; } public void setLastVerifiedAt(Instant l) { this.lastVerifiedAt = l; }
    public String getLastResult() { return lastResult; } public void setLastResult(String l) { this.lastResult = l; }
}
