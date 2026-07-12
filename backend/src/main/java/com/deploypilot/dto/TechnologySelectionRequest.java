package com.deploypilot.dto;

import java.util.List;

public class TechnologySelectionRequest {
    private String projectType;
    private String frontendTech;
    private String backendTech;
    private String database;
    private String frontendHost;
    private String backendHost;
    private List<String> additionalServices;

    public String getProjectType() { return projectType; } public void setProjectType(String p) { this.projectType = p; }
    public String getFrontendTech() { return frontendTech; } public void setFrontendTech(String f) { this.frontendTech = f; }
    public String getBackendTech() { return backendTech; } public void setBackendTech(String b) { this.backendTech = b; }
    public String getDatabase() { return database; } public void setDatabase(String d) { this.database = d; }
    public String getFrontendHost() { return frontendHost; } public void setFrontendHost(String f) { this.frontendHost = f; }
    public String getBackendHost() { return backendHost; } public void setBackendHost(String b) { this.backendHost = b; }
    public List<String> getAdditionalServices() { return additionalServices; } public void setAdditionalServices(List<String> s) { this.additionalServices = s; }
}
