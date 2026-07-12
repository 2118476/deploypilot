package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordUpdateRequest {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min = 6) private String newPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String c) { this.currentPassword = c; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String n) { this.newPassword = n; }
}
