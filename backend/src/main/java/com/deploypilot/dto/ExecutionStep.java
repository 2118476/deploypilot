package com.deploypilot.dto;

import java.time.Instant;

/**
 * One executed (or pending) step of an automation run. Detail and logs are
 * sanitised; no secret values ever appear here.
 */
public class ExecutionStep {
    private String id;
    private int order;
    private String type;
    private String provider;
    private String title;
    private String status;          // ActionStatus name
    private String detail;
    private String sanitizedLog;
    private Instant startedAt;
    private Instant finishedAt;

    public ExecutionStep() {}

    public String getId() { return id; } public void setId(String i) { this.id = i; }
    public int getOrder() { return order; } public void setOrder(int o) { this.order = o; }
    public String getType() { return type; } public void setType(String t) { this.type = t; }
    public String getProvider() { return provider; } public void setProvider(String p) { this.provider = p; }
    public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getDetail() { return detail; } public void setDetail(String d) { this.detail = d; }
    public String getSanitizedLog() { return sanitizedLog; } public void setSanitizedLog(String l) { this.sanitizedLog = l; }
    public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant s) { this.startedAt = s; }
    public Instant getFinishedAt() { return finishedAt; } public void setFinishedAt(Instant f) { this.finishedAt = f; }
}
