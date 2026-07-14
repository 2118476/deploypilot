package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AnalyzeRepositoryRequest {
    @NotBlank(message = "Repository is required")
    @Size(max = 200, message = "Repository must be at most 200 characters")
    private String repository;

    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }
}
