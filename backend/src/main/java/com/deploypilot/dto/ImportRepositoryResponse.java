package com.deploypilot.dto;

public class ImportRepositoryResponse {
    private Long projectId;
    private String projectName;
    private RepositoryAnalysisResponse analysis;
    private BlueprintResponse blueprint;

    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public String getProjectName() { return projectName; } public void setProjectName(String n) { this.projectName = n; }
    public RepositoryAnalysisResponse getAnalysis() { return analysis; } public void setAnalysis(RepositoryAnalysisResponse a) { this.analysis = a; }
    public BlueprintResponse getBlueprint() { return blueprint; } public void setBlueprint(BlueprintResponse b) { this.blueprint = b; }
}
