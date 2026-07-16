package com.deploypilot.service;

import com.deploypilot.model.ProjectActivityEvent;
import com.deploypilot.model.enums.ActivityEventType;
import com.deploypilot.repository.ProjectActivityEventRepository;
import com.deploypilot.util.SecretRedactionUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Records ownership-protected project activity from real lifecycle changes only.
 * Summaries are redacted and length-capped; no secret values or raw provider
 * responses are ever stored. Used for the dashboard timeline and Copilot context.
 */
@Service
public class ProjectActivityService {

    static final int MAX_SUMMARY = 500;

    private final ProjectActivityEventRepository repository;

    public ProjectActivityService(ProjectActivityEventRepository repository) {
        this.repository = repository;
    }

    public void record(Long userId, Long projectId, Long automationRunId, ActivityEventType type,
                       String provider, String actionId, String summary, String status) {
        try {
            ProjectActivityEvent e = new ProjectActivityEvent();
            e.setUserId(userId);
            e.setProjectId(projectId);
            e.setAutomationRunId(automationRunId);
            e.setEventType(type);
            e.setProvider(provider);
            e.setActionId(actionId);
            e.setSummary(cap(SecretRedactionUtil.redact(summary == null ? type.name() : summary)));
            e.setStatus(status);
            repository.save(e);
        } catch (Exception ignored) {
            // Activity recording must never break the operation it is describing.
        }
    }

    public void record(Long userId, Long projectId, ActivityEventType type, String summary) {
        record(userId, projectId, null, type, null, null, summary, null);
    }

    public List<ProjectActivityEvent> recent(Long projectId, int limit) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, Math.max(1, Math.min(limit, 50))));
    }

    private static String cap(String s) {
        if (s == null) return "";
        return s.length() > MAX_SUMMARY ? s.substring(0, MAX_SUMMARY) : s;
    }
}
