package com.deploypilot.dto;

import com.deploypilot.model.enums.EnvVarClassification;
import java.time.Instant;

public class EnvVarResponse {
    private Long id;
    private String variableName;
    private String description;
    private EnvVarClassification classification;
    private String localLocation;
    private String productionLocation;
    private boolean required;
    private boolean configured;
    private Instant lastVerifiedAt;
    private String notes;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getVariableName() { return variableName; } public void setVariableName(String v) { this.variableName = v; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public EnvVarClassification getClassification() { return classification; } public void setClassification(EnvVarClassification c) { this.classification = c; }
    public String getLocalLocation() { return localLocation; } public void setLocalLocation(String l) { this.localLocation = l; }
    public String getProductionLocation() { return productionLocation; } public void setProductionLocation(String p) { this.productionLocation = p; }
    public boolean isRequired() { return required; } public void setRequired(boolean r) { this.required = r; }
    public boolean isConfigured() { return configured; } public void setConfigured(boolean c) { this.configured = c; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; } public void setLastVerifiedAt(Instant l) { this.lastVerifiedAt = l; }
    public String getNotes() { return notes; } public void setNotes(String n) { this.notes = n; }
}
