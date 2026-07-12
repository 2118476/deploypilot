package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "error_reports")
public class ErrorReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id") private Long userId;
    @Column(name = "project_id") private Long projectId;
    @Column(name = "error_type", length = 50) private String errorType;
    @Column(name = "original_content", columnDefinition = "TEXT") private String originalContent;
    @Column(name = "redacted_content", columnDefinition = "TEXT") private String redactedContent;
    @Column(name = "ai_response", columnDefinition = "TEXT") private String aiResponse;
    @Column(nullable = false) private boolean resolved = false;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public ErrorReport() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getErrorType() { return errorType; } public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getOriginalContent() { return originalContent; } public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }
    public String getRedactedContent() { return redactedContent; } public void setRedactedContent(String redactedContent) { this.redactedContent = redactedContent; }
    public String getAiResponse() { return aiResponse; } public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }
    public boolean isResolved() { return resolved; } public void setResolved(boolean resolved) { this.resolved = resolved; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
