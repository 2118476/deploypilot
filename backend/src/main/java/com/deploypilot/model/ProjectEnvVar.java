package com.deploypilot.model;

import com.deploypilot.model.enums.EnvVarClassification;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "project_env_vars")
public class ProjectEnvVar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "definition_id") private Long definitionId;
    @Column(name = "variable_name", nullable = false, length = 100) private String variableName;
    @Column(length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(length = 20) private EnvVarClassification classification;
    @Column(name = "local_location", length = 100) private String localLocation;
    @Column(name = "production_location", length = 100) private String productionLocation;
    @Column(nullable = false) private boolean required = false;
    @Column(nullable = false) private boolean configured = false;
    @Column(name = "last_verified_at") private Instant lastVerifiedAt;
    @Column(columnDefinition = "TEXT") private String notes;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public ProjectEnvVar() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getDefinitionId() { return definitionId; } public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public String getVariableName() { return variableName; } public void setVariableName(String variableName) { this.variableName = variableName; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public EnvVarClassification getClassification() { return classification; } public void setClassification(EnvVarClassification classification) { this.classification = classification; }
    public String getLocalLocation() { return localLocation; } public void setLocalLocation(String localLocation) { this.localLocation = localLocation; }
    public String getProductionLocation() { return productionLocation; } public void setProductionLocation(String productionLocation) { this.productionLocation = productionLocation; }
    public boolean isRequired() { return required; } public void setRequired(boolean required) { this.required = required; }
    public boolean isConfigured() { return configured; } public void setConfigured(boolean configured) { this.configured = configured; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; } public void setLastVerifiedAt(Instant lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
