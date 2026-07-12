package com.deploypilot.model;

import com.deploypilot.model.enums.RecordStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "deployment_records")
public class DeploymentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 50) private String platform;
    @Column(length = 50) private String version;
    @Column(length = 255) private String url;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RecordStatus status = RecordStatus.SUCCESS;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "deployed_at") private Instant deployedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public DeploymentRecord() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getPlatform() { return platform; } public void setPlatform(String platform) { this.platform = platform; }
    public String getVersion() { return version; } public void setVersion(String version) { this.version = version; }
    public String getUrl() { return url; } public void setUrl(String url) { this.url = url; }
    public RecordStatus getStatus() { return status; } public void setStatus(RecordStatus status) { this.status = status; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
    public Instant getDeployedAt() { return deployedAt; } public void setDeployedAt(Instant deployedAt) { this.deployedAt = deployedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
