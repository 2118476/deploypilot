package com.deploypilot.dto;

import com.deploypilot.model.DeploymentTarget;

import java.time.Instant;

public class DeploymentTargetResponse {
    private Long id;
    private Long projectId;
    private String componentId;
    private String targetType;
    private String platform;
    private String url;
    private String healthPath;
    private String expectedCommit;
    private Instant createdAt;
    private Instant lastVerifiedAt;
    private String lastResult;

    public static DeploymentTargetResponse from(DeploymentTarget t) {
        DeploymentTargetResponse r = new DeploymentTargetResponse();
        r.id = t.getId(); r.projectId = t.getProjectId(); r.componentId = t.getComponentId();
        r.targetType = t.getTargetType().name(); r.platform = t.getPlatform(); r.url = t.getUrl();
        r.healthPath = t.getHealthPath(); r.expectedCommit = t.getExpectedCommit();
        r.createdAt = t.getCreatedAt(); r.lastVerifiedAt = t.getLastVerifiedAt(); r.lastResult = t.getLastResult();
        return r;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getComponentId() { return componentId; }
    public String getTargetType() { return targetType; }
    public String getPlatform() { return platform; }
    public String getUrl() { return url; }
    public String getHealthPath() { return healthPath; }
    public String getExpectedCommit() { return expectedCommit; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
    public String getLastResult() { return lastResult; }
}
