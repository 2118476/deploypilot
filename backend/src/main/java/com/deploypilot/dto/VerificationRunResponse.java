package com.deploypilot.dto;

import com.deploypilot.model.enums.VerificationStatus;

import java.time.Instant;

public class VerificationRunResponse {
    private Long id;
    private Long projectId;
    private Long blueprintId;
    private String frontendUrl;
    private String backendUrl;
    private VerificationStatus overallStatus;
    private VerificationResult result;
    private Instant startedAt;
    private Instant completedAt;

    public Long getId() { return id; } public void setId(Long i) { this.id = i; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getBlueprintId() { return blueprintId; } public void setBlueprintId(Long b) { this.blueprintId = b; }
    public String getFrontendUrl() { return frontendUrl; } public void setFrontendUrl(String f) { this.frontendUrl = f; }
    public String getBackendUrl() { return backendUrl; } public void setBackendUrl(String b) { this.backendUrl = b; }
    public VerificationStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(VerificationStatus s) { this.overallStatus = s; }
    public VerificationResult getResult() { return result; } public void setResult(VerificationResult r) { this.result = r; }
    public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant s) { this.startedAt = s; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant c) { this.completedAt = c; }
}
