package com.deploypilot.dto;

import java.time.Instant;
import java.util.Map;

public class BlueprintResponse {
    private Long id;
    private Long projectId;
    private Long analysisId;
    private String rulesVersion;
    private boolean stale;
    private Map<String, String> overrides;
    private BlueprintResult result;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getAnalysisId() { return analysisId; } public void setAnalysisId(Long a) { this.analysisId = a; }
    public String getRulesVersion() { return rulesVersion; } public void setRulesVersion(String r) { this.rulesVersion = r; }
    public boolean isStale() { return stale; } public void setStale(boolean s) { this.stale = s; }
    public Map<String, String> getOverrides() { return overrides; } public void setOverrides(Map<String, String> o) { this.overrides = o; }
    public BlueprintResult getResult() { return result; } public void setResult(BlueprintResult r) { this.result = r; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant u) { this.updatedAt = u; }
}
