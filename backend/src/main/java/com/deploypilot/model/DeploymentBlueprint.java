package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "deployment_blueprints", indexes = {
    @Index(name = "idx_blueprints_project", columnList = "project_id")
})
public class DeploymentBlueprint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "analysis_id", nullable = false) private Long analysisId;
    @Column(name = "rules_version", nullable = false, length = 20) private String rulesVersion;
    @Column(name = "blueprint_json", nullable = false, columnDefinition = "TEXT") private String blueprintJson;
    @Column(name = "overrides_json", columnDefinition = "TEXT") private String overridesJson;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public DeploymentBlueprint() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getAnalysisId() { return analysisId; } public void setAnalysisId(Long a) { this.analysisId = a; }
    public String getRulesVersion() { return rulesVersion; } public void setRulesVersion(String r) { this.rulesVersion = r; }
    public String getBlueprintJson() { return blueprintJson; } public void setBlueprintJson(String b) { this.blueprintJson = b; }
    public String getOverridesJson() { return overridesJson; } public void setOverridesJson(String o) { this.overridesJson = o; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
