package com.deploypilot.dto;

public class ErrorReportRequest {
    private String errorType;
    private String content;
    private Long projectId;

    public String getErrorType() { return errorType; } public void setErrorType(String e) { this.errorType = e; }
    public String getContent() { return content; } public void setContent(String c) { this.content = c; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
}
