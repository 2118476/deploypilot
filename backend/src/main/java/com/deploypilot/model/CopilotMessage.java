package com.deploypilot.model;

import com.deploypilot.model.enums.CopilotRole;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A single Copilot message. Content is sanitised before persistence; a proposed
 * deployment action (if any) is stored as JSON but never contains secret values
 * or an execution nonce.
 */
@Entity
@Table(name = "copilot_messages", indexes = {
    @Index(name = "idx_copilot_messages_conversation", columnList = "conversation_id"),
    @Index(name = "idx_copilot_messages_project", columnList = "project_id")
})
public class CopilotMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "conversation_id", nullable = false) private Long conversationId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false, length = 20) private CopilotRole role;
    @Column(name = "content", nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "proposed_action_json", columnDefinition = "TEXT") private String proposedActionJson;
    @Column(name = "ai_available", nullable = false) private boolean aiAvailable;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public CopilotMessage() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; } public void setConversationId(Long c) { this.conversationId = c; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public CopilotRole getRole() { return role; } public void setRole(CopilotRole r) { this.role = r; }
    public String getContent() { return content; } public void setContent(String c) { this.content = c; }
    public String getProposedActionJson() { return proposedActionJson; } public void setProposedActionJson(String p) { this.proposedActionJson = p; }
    public boolean isAiAvailable() { return aiAvailable; } public void setAiAvailable(boolean a) { this.aiAvailable = a; }
    public Instant getCreatedAt() { return createdAt; }
}
