package com.deploypilot.dto;

import com.deploypilot.model.enums.EnvVarClassification;

public class EnvVarCreateRequest {
    private String variableName;
    private String description;
    private EnvVarClassification classification;
    private String localLocation;
    private String productionLocation;
    private boolean required;
    private String notes;

    public String getVariableName() { return variableName; } public void setVariableName(String v) { this.variableName = v; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public EnvVarClassification getClassification() { return classification; } public void setClassification(EnvVarClassification c) { this.classification = c; }
    public String getLocalLocation() { return localLocation; } public void setLocalLocation(String l) { this.localLocation = l; }
    public String getProductionLocation() { return productionLocation; } public void setProductionLocation(String p) { this.productionLocation = p; }
    public boolean isRequired() { return required; } public void setRequired(boolean r) { this.required = r; }
    public String getNotes() { return notes; } public void setNotes(String n) { this.notes = n; }
}
