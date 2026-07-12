package com.deploypilot.dto;

public class EnvVarUpdateRequest {
    private boolean configured;
    private String notes;

    public boolean isConfigured() { return configured; } public void setConfigured(boolean c) { this.configured = c; }
    public String getNotes() { return notes; } public void setNotes(String n) { this.notes = n; }
}
