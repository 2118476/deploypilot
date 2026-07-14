package com.deploypilot.dto;

import com.deploypilot.model.enums.AnalysisStatus;

import java.time.Instant;

public class RepositoryAnalysisResponse {
    private Long id;
    private Long projectId;
    private String repository;
    private AnalysisStatus status;
    private String errorMessage;
    private StackDetectionResult result;
    private Instant createdAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public String getRepository() { return repository; } public void setRepository(String r) { this.repository = r; }
    public AnalysisStatus getStatus() { return status; } public void setStatus(AnalysisStatus s) { this.status = s; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String e) { this.errorMessage = e; }
    public StackDetectionResult getResult() { return result; } public void setResult(StackDetectionResult r) { this.result = r; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
}
