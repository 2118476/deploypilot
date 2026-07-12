package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "security_checks")
public class SecurityCheck {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id") private Long projectId;
    @Column(name = "user_id") private Long userId;
    @Column(name = "check_name", nullable = false, length = 100) private String checkName;
    @Column(name = "check_category", nullable = false, length = 50) private String checkCategory;
    @Column(nullable = false) private boolean passed = false;
    @Column(columnDefinition = "TEXT") private String details;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public SecurityCheck() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getCheckName() { return checkName; } public void setCheckName(String checkName) { this.checkName = checkName; }
    public String getCheckCategory() { return checkCategory; } public void setCheckCategory(String checkCategory) { this.checkCategory = checkCategory; }
    public boolean isPassed() { return passed; } public void setPassed(boolean passed) { this.passed = passed; }
    public String getDetails() { return details; } public void setDetails(String details) { this.details = details; }
    public Instant getCreatedAt() { return createdAt; }
}
