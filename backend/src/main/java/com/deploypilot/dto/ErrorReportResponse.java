package com.deploypilot.dto;

import java.time.Instant;

public class ErrorReportResponse {
    private Long id;
    private String errorType;
    private String redactedContent;
    private String aiResponse;
    private boolean resolved;
    private Instant createdAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getErrorType() { return errorType; } public void setErrorType(String e) { this.errorType = e; }
    public String getRedactedContent() { return redactedContent; } public void setRedactedContent(String r) { this.redactedContent = r; }
    public String getAiResponse() { return aiResponse; } public void setAiResponse(String a) { this.aiResponse = a; }
    public boolean isResolved() { return resolved; } public void setResolved(boolean r) { this.resolved = r; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
}
