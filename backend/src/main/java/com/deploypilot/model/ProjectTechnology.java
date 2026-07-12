package com.deploypilot.model;

import com.deploypilot.model.enums.TechnologyCategory;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "project_technologies")
public class ProjectTechnology {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TechnologyCategory category;
    @Column(nullable = false, length = 50) private String technology;
    @Column(length = 20) private String version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public ProjectTechnology() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public TechnologyCategory getCategory() { return category; } public void setCategory(TechnologyCategory category) { this.category = category; }
    public String getTechnology() { return technology; } public void setTechnology(String technology) { this.technology = technology; }
    public String getVersion() { return version; } public void setVersion(String version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
}
