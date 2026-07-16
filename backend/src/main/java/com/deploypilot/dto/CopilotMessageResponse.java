package com.deploypilot.dto;

import java.time.Instant;

/** A single Copilot message in a conversation. */
public class CopilotMessageResponse {
    private Long id;
    private String role;            // USER | ASSISTANT
    private String content;
    private boolean aiAvailable;
    private ProposedAction proposedAction;
    private Instant createdAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getRole() { return role; } public void setRole(String r) { this.role = r; }
    public String getContent() { return content; } public void setContent(String c) { this.content = c; }
    public boolean isAiAvailable() { return aiAvailable; } public void setAiAvailable(boolean a) { this.aiAvailable = a; }
    public ProposedAction getProposedAction() { return proposedAction; } public void setProposedAction(ProposedAction p) { this.proposedAction = p; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
}
