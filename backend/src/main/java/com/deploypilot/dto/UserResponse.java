package com.deploypilot.dto;

import com.deploypilot.model.enums.UserRole;

import java.time.Instant;

public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private UserRole role;
    private Instant createdAt;

    public UserResponse() {}
    public UserResponse(Long id, String username, String email, UserRole role, Instant createdAt) {
        this.id = id; this.username = username; this.email = email; this.role = role; this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
