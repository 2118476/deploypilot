package com.deploypilot.dto;

import com.deploypilot.model.ProjectActivityEvent;

import java.time.Instant;

/** Safe public view of a project activity event. */
public class ActivityEventResponse {
    private Long id;
    private Long automationRunId;
    private String eventType;
    private String provider;
    private String actionId;
    private String summary;
    private String status;
    private Instant createdAt;

    public static ActivityEventResponse from(ProjectActivityEvent e) {
        ActivityEventResponse r = new ActivityEventResponse();
        r.id = e.getId();
        r.automationRunId = e.getAutomationRunId();
        r.eventType = e.getEventType().name();
        r.provider = e.getProvider();
        r.actionId = e.getActionId();
        r.summary = e.getSummary();
        r.status = e.getStatus();
        r.createdAt = e.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public Long getAutomationRunId() { return automationRunId; }
    public String getEventType() { return eventType; }
    public String getProvider() { return provider; }
    public String getActionId() { return actionId; }
    public String getSummary() { return summary; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
