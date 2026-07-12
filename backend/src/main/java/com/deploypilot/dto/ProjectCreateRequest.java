package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectCreateRequest {
    @NotBlank @Size(max = 100) private String name;
    @Size(max = 500) private String description;
    @Size(max = 255) private String githubUrl;
    @Size(max = 255) private String localFolderPath;
    private String status = "PLANNING";
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getGithubUrl() { return githubUrl; } public void setGithubUrl(String g) { this.githubUrl = g; }
    public String getLocalFolderPath() { return localFolderPath; } public void setLocalFolderPath(String l) { this.localFolderPath = l; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
}
