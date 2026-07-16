package com.deploypilot.dto;

import java.util.List;

/** A Copilot conversation with its message history (bounded). */
public class ConversationResponse {
    private Long conversationId;
    private Long projectId;
    private boolean aiAvailable;
    private List<CopilotMessageResponse> messages;

    public Long getConversationId() { return conversationId; } public void setConversationId(Long c) { this.conversationId = c; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public boolean isAiAvailable() { return aiAvailable; } public void setAiAvailable(boolean a) { this.aiAvailable = a; }
    public List<CopilotMessageResponse> getMessages() { return messages; } public void setMessages(List<CopilotMessageResponse> m) { this.messages = m; }
}
