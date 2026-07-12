package com.deploypilot.dto;

import com.deploypilot.model.enums.EnvVarCategory;

public class EnvVarDefinitionResponse {
    private Long id;
    private String name;
    private String description;
    private EnvVarCategory category;
    private String platform;
    private String localFileLocation;
    private String productionLocation;
    private boolean required;
    private String exampleValue;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String n) { this.name = n; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public EnvVarCategory getCategory() { return category; } public void setCategory(EnvVarCategory c) { this.category = c; }
    public String getPlatform() { return platform; } public void setPlatform(String p) { this.platform = p; }
    public String getLocalFileLocation() { return localFileLocation; } public void setLocalFileLocation(String l) { this.localFileLocation = l; }
    public String getProductionLocation() { return productionLocation; } public void setProductionLocation(String p) { this.productionLocation = p; }
    public boolean isRequired() { return required; } public void setRequired(boolean r) { this.required = r; }
    public String getExampleValue() { return exampleValue; } public void setExampleValue(String e) { this.exampleValue = e; }
}
