package com.deploypilot.model;

import com.deploypilot.model.enums.VerificationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "verification_runs", indexes = {
    @Index(name = "idx_verifications_project", columnList = "project_id")
})
public class VerificationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "blueprint_id") private Long blueprintId;
    @Column(name = "blueprint_commit", length = 80) private String blueprintCommit;
    @Column(name = "frontend_url", length = 500) private String frontendUrl;
    @Column(name = "backend_url", length = 500) private String backendUrl;
    @Enumerated(EnumType.STRING) @Column(name = "overall_status", nullable = false, length = 20) private VerificationStatus overallStatus = VerificationStatus.RUNNING;
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(name = "started_at", nullable = false) @CreationTimestamp private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;

    public VerificationRun() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getBlueprintId() { return blueprintId; } public void setBlueprintId(Long b) { this.blueprintId = b; }
    public String getBlueprintCommit() { return blueprintCommit; } public void setBlueprintCommit(String c) { this.blueprintCommit = c; }
    public String getFrontendUrl() { return frontendUrl; } public void setFrontendUrl(String f) { this.frontendUrl = f; }
    public String getBackendUrl() { return backendUrl; } public void setBackendUrl(String b) { this.backendUrl = b; }
    public VerificationStatus getOverallStatus() { return overallStatus; } public void setOverallStatus(VerificationStatus s) { this.overallStatus = s; }
    public String getResultJson() { return resultJson; } public void setResultJson(String r) { this.resultJson = r; }
    public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant s) { this.startedAt = s; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant c) { this.completedAt = c; }
}
