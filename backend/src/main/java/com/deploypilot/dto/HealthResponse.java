package com.deploypilot.dto;

import java.time.Instant;

public class HealthResponse {
    private String status;
    private String version;
    private Instant timestamp;

    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getVersion() { return version; } public void setVersion(String v) { this.version = v; }
    public Instant getTimestamp() { return timestamp; } public void setTimestamp(Instant t) { this.timestamp = t; }
}
