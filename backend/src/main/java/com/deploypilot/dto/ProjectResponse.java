package com.deploypilot.dto;

import com.deploypilot.model.enums.ProjectStatus;
import java.time.Instant;
import java.util.List;

public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private String githubUrl;
    private String localFolderPath;
    private ProjectStatus status;
    private Long userId;
    private List<TechnologyDto> technologies;
    private List<String> services;
    private Instant createdAt;
    private Instant updatedAt;

    public ProjectResponse() {}
    public ProjectResponse(Long id, String name, String description, String githubUrl,
                           String localFolderPath, ProjectStatus status, Long userId, Instant createdAt, Instant updatedAt) {
        this.id = id; this.name = name; this.description = description; this.githubUrl = githubUrl;
        this.localFolderPath = localFolderPath; this.status = status; this.userId = userId;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getGithubUrl() { return githubUrl; } public void setGithubUrl(String g) { this.githubUrl = g; }
    public String getLocalFolderPath() { return localFolderPath; } public void setLocalFolderPath(String l) { this.localFolderPath = l; }
    public ProjectStatus getStatus() { return status; } public void setStatus(ProjectStatus s) { this.status = s; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public List<TechnologyDto> getTechnologies() { return technologies; } public void setTechnologies(List<TechnologyDto> t) { this.technologies = t; }
    public List<String> getServices() { return services; } public void setServices(List<String> s) { this.services = s; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
