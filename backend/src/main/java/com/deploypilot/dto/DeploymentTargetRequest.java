package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DeploymentTargetRequest {
    @NotNull(message = "Target type is required")
    private String targetType; // FRONTEND | BACKEND | HEALTH | VERSION

    @NotBlank(message = "URL is required")
    @Size(max = 500)
    private String url;

    @Size(max = 120) private String componentId;
    @Size(max = 60) private String platform;
    @Size(max = 200) private String healthPath;
    @Size(max = 80) private String expectedCommit;

    public String getTargetType() { return targetType; } public void setTargetType(String t) { this.targetType = t; }
    public String getUrl() { return url; } public void setUrl(String u) { this.url = u; }
    public String getComponentId() { return componentId; } public void setComponentId(String c) { this.componentId = c; }
    public String getPlatform() { return platform; } public void setPlatform(String p) { this.platform = p; }
    public String getHealthPath() { return healthPath; } public void setHealthPath(String h) { this.healthPath = h; }
    public String getExpectedCommit() { return expectedCommit; } public void setExpectedCommit(String e) { this.expectedCommit = e; }
}
