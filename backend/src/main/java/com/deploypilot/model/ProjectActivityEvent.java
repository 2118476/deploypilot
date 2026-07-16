package com.deploypilot.model;

import com.deploypilot.model.enums.ActivityEventType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * An ownership-protected activity record derived from a real lifecycle change.
 * Holds only safe structured metadata — never secret values or raw provider
 * responses.
 */
@Entity
@Table(name = "project_activity_events", indexes = {
    @Index(name = "idx_activity_events_project", columnList = "project_id")
})
public class ProjectActivityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "automation_run_id") private Long automationRunId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 50) private ActivityEventType eventType;
    @Column(name = "provider", length = 20) private String provider;
    @Column(name = "action_id", length = 60) private String actionId;
    @Column(name = "summary", nullable = false, length = 500) private String summary;
    @Column(name = "status", length = 20) private String status;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public ProjectActivityEvent() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getAutomationRunId() { return automationRunId; } public void setAutomationRunId(Long a) { this.automationRunId = a; }
    public ActivityEventType getEventType() { return eventType; } public void setEventType(ActivityEventType e) { this.eventType = e; }
    public String getProvider() { return provider; } public void setProvider(String p) { this.provider = p; }
    public String getActionId() { return actionId; } public void setActionId(String a) { this.actionId = a; }
    public String getSummary() { return summary; } public void setSummary(String s) { this.summary = s; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public Instant getCreatedAt() { return createdAt; }
}
