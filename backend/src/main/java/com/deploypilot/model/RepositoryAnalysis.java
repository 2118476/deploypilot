package com.deploypilot.model;

import com.deploypilot.model.enums.AnalysisStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "repository_analyses", indexes = {
    @Index(name = "idx_repo_analyses_project", columnList = "project_id")
})
public class RepositoryAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "repository_full_name", nullable = false, length = 200) private String repositoryFullName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AnalysisStatus status = AnalysisStatus.RUNNING;
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public RepositoryAnalysis() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getRepositoryFullName() { return repositoryFullName; } public void setRepositoryFullName(String r) { this.repositoryFullName = r; }
    public AnalysisStatus getStatus() { return status; } public void setStatus(AnalysisStatus status) { this.status = status; }
    public String getResultJson() { return resultJson; } public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
