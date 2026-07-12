package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "project_services")
public class ProjectService {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "service_name", nullable = false, length = 50) private String serviceName;
    @Column(nullable = false) private boolean configured = false;
    @Column(name = "config_json", columnDefinition = "TEXT") private String configJson;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public ProjectService() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getServiceName() { return serviceName; } public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public boolean isConfigured() { return configured; } public void setConfigured(boolean configured) { this.configured = configured; }
    public String getConfigJson() { return configJson; } public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
