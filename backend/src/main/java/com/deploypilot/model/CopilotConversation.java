package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** One project-aware Copilot conversation, owned by a user and bound to a project. */
@Entity
@Table(name = "copilot_conversations", uniqueConstraints = {
    @UniqueConstraint(name = "uq_copilot_conversation_project", columnNames = {"user_id", "project_id"})
}, indexes = {
    @Index(name = "idx_copilot_conversations_project", columnList = "project_id")
})
public class CopilotConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public CopilotConversation() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
