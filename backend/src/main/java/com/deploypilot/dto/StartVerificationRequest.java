package com.deploypilot.dto;

import jakarta.validation.constraints.Size;

public class StartVerificationRequest {
    @Size(max = 500) private String frontendUrl;
    @Size(max = 500) private String backendUrl;
    @Size(max = 200) private String healthPath;
    @Size(max = 80) private String expectedCommit;
    /** Explicit local-development mode: permits http and loopback addresses. */
    private boolean allowInsecureLocal;

    public String getFrontendUrl() { return frontendUrl; } public void setFrontendUrl(String f) { this.frontendUrl = f; }
    public String getBackendUrl() { return backendUrl; } public void setBackendUrl(String b) { this.backendUrl = b; }
    public String getHealthPath() { return healthPath; } public void setHealthPath(String h) { this.healthPath = h; }
    public String getExpectedCommit() { return expectedCommit; } public void setExpectedCommit(String e) { this.expectedCommit = e; }
    public boolean isAllowInsecureLocal() { return allowInsecureLocal; }
    public void setAllowInsecureLocal(boolean a) { this.allowInsecureLocal = a; }
}
