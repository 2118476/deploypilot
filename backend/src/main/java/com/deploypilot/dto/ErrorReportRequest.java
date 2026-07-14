package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ErrorReportRequest {
    @Size(max = 50, message = "Error type must be at most 50 characters")
    private String errorType;

    @NotBlank(message = "Error content is required")
    @Size(max = 20000, message = "Error content must be at most 20000 characters")
    private String content;

    private Long projectId;

    public String getErrorType() { return errorType; } public void setErrorType(String e) { this.errorType = e; }
    public String getContent() { return content; } public void setContent(String c) { this.content = c; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
}
