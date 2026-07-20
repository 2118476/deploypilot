package com.deploypilot.troubleshoot;

import com.deploypilot.dto.ExecutionStep;
import com.deploypilot.dto.ProjectContext;
import com.deploypilot.dto.VerificationResult;
import com.deploypilot.model.AutomationRun;
import com.deploypilot.model.Project;
import com.deploypilot.model.ProjectActivityEvent;
import com.deploypilot.model.VerificationRun;
import com.deploypilot.model.enums.ActivityEventType;
import com.deploypilot.repository.AutomationRunRepository;
import com.deploypilot.repository.ProjectActivityEventRepository;
import com.deploypilot.repository.VerificationRunRepository;
import com.deploypilot.service.ProjectContextService;
import com.deploypilot.util.SecretRedactionUtil;
import com.deploypilot.verify.LogSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Assembles a bounded, sanitised {@link TroubleshootingContext} focused on a
 * single failed step. Reuses {@link ProjectContextService} (which already applies
 * redaction and computes connections, missing secrets and URLs) and adds the
 * failed-step log (capped in the 4,000–6,000 range) plus the safe troubleshooting
 * events used to prevent repetitive loops. Never emits secrets.
 */
@Service
public class TroubleshootingContextService {

    private static final Logger log = LoggerFactory.getLogger(TroubleshootingContextService.class);
    private static final int MAX_EVENTS = 30;

    private final ProjectContextService projectContextService;
    private final AutomationRunRepository automationRunRepository;
    private final VerificationRunRepository verificationRepository;
    private final ProjectActivityEventRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final LogSanitizer logSanitizer;

    public TroubleshootingContextService(ProjectContextService projectContextService,
                                         AutomationRunRepository automationRunRepository,
                                         VerificationRunRepository verificationRepository,
                                         ProjectActivityEventRepository activityRepository,
                                         ObjectMapper objectMapper,
                                         LogSanitizer logSanitizer) {
        this.projectContextService = projectContextService;
        this.automationRunRepository = automationRunRepository;
        this.verificationRepository = verificationRepository;
        this.activityRepository = activityRepository;
        this.objectMapper = objectMapper;
        this.logSanitizer = logSanitizer;
    }

    /**
     * @param runId  the run to inspect, or null for the latest run
     * @param stepId the failed step to focus on, or null to auto-select the failed/running step
     */
    public TroubleshootingContext build(Project project, Long userId, Long runId, String stepId) {
        TroubleshootingContext ctx = new TroubleshootingContext();
        ctx.setProjectId(project.getId());
        ctx.setRepositoryFullName(safe(project.getGithubUrl()));

        // Reuse the already-sanitised project context for connections, URLs, missing
        // secrets and verification status (no duplicated repository logic).
        ProjectContext base = projectContextService.build(project, userId);
        ctx.setConnections(base.getConnectionsConnected());
        ctx.setMissingRequiredSecrets(base.getMissingRequiredSecrets());
        ctx.setFrontendUrl(base.getFrontendUrl());
        ctx.setBackendUrl(base.getBackendUrl());
        ctx.setPullRequestUrl(base.getPullRequestUrl());
        ctx.setVerificationStatus(base.getVerificationStatus());

        AutomationRun run = selectRun(project.getId(), runId);
        if (run != null) {
            ctx.setRunId(run.getId());
            ctx.setRunStatus(run.getStatus().name());
            ctx.setFailureReason(safe(run.getFailureReason()));
            fillSteps(ctx, run, stepId);
        }
        fillVerification(ctx, project.getId());
        fillEvents(ctx, project.getId(), run != null ? run.getId() : null);
        return ctx;
    }

    private AutomationRun selectRun(Long projectId, Long runId) {
        if (runId != null) {
            AutomationRun run = automationRunRepository.findById(runId).orElse(null);
            if (run != null && run.getProjectId().equals(projectId)) return run;
        }
        return automationRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, 1))
            .stream().findFirst().orElse(null);
    }

    private void fillSteps(TroubleshootingContext ctx, AutomationRun run, String stepId) {
        List<ExecutionStep> steps = readSteps(run);
        ExecutionStep target = pickFailedStep(steps, stepId);
        List<String> completed = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (ExecutionStep s : steps) {
            if ("SUCCEEDED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus())) {
                completed.add(s.getTitle());
            } else if ("PENDING".equals(s.getStatus())) {
                pending.add(s.getTitle());
            }
        }
        ctx.setCompletedSteps(completed);
        ctx.setPendingSteps(pending);

        if (target != null) {
            ctx.setFailedStepId(target.getId());
            ctx.setFailedStepTitle(target.getTitle());
            ctx.setFailedStepProvider(target.getProvider());
            ctx.setFailedAt(target.getFinishedAt() != null ? target.getFinishedAt() : target.getStartedAt());
            ctx.setFailedStepLog(buildFailedLog(target));
        }
    }

    /** Prefer the explicitly requested step; otherwise the FAILED step; otherwise the RUNNING one. */
    static ExecutionStep pickFailedStep(List<ExecutionStep> steps, String stepId) {
        if (stepId != null && !stepId.isBlank()) {
            ExecutionStep byId = steps.stream().filter(s -> stepId.equals(s.getId())).findFirst().orElse(null);
            if (byId != null) return byId;
        }
        ExecutionStep failed = steps.stream().filter(s -> "FAILED".equals(s.getStatus())).findFirst().orElse(null);
        if (failed != null) return failed;
        return steps.stream().filter(s -> "RUNNING".equals(s.getStatus())).findFirst().orElse(null);
    }

    private String buildFailedLog(ExecutionStep step) {
        StringBuilder raw = new StringBuilder();
        if (step.getDetail() != null) raw.append(step.getDetail()).append('\n');
        if (step.getSanitizedLog() != null) raw.append(step.getSanitizedLog());
        String sanitized = sanitize(raw.toString());
        if (sanitized.length() > TroubleshootingContext.MAX_LOG_CHARS) {
            sanitized = sanitized.substring(0, TroubleshootingContext.MAX_LOG_CHARS) + "\n[log truncated]";
        }
        return sanitized;
    }

    private void fillVerification(TroubleshootingContext ctx, Long projectId) {
        List<VerificationRun> runs = verificationRepository.findByProjectIdOrderByStartedAtDesc(projectId, PageRequest.of(0, 1));
        if (runs.isEmpty()) return;
        VerificationRun run = runs.get(0);
        ctx.setVerificationStatus(run.getOverallStatus().name());
        VerificationResult result = parse(run.getResultJson(), VerificationResult.class);
        if (result == null) return;
        for (VerificationResult.CheckResult c : result.getChecks()) {
            String status = c.getStatus() == null ? "" : c.getStatus();
            if ("FAIL".equals(status)) {
                ctx.getFailedChecks().add(safe(c.getTitle() != null ? c.getTitle() : c.getId()));
            } else if ("WARN".equals(status) || "WARNING".equals(status)) {
                ctx.getWarningChecks().add(safe(c.getTitle() != null ? c.getTitle() : c.getId()));
            }
        }
    }

    /** Reads the safe troubleshooting events for loop prevention and host-key branching. */
    private void fillEvents(TroubleshootingContext ctx, Long projectId, Long runId) {
        List<ProjectActivityEvent> events = activityRepository.findByProjectIdOrderByCreatedAtDesc(
            projectId, PageRequest.of(0, MAX_EVENTS));
        // Events are newest-first; the latest manual-deploy result wins.
        String manual = "UNKNOWN";
        boolean relinked = false;
        for (ProjectActivityEvent e : events) {
            if (runId != null && e.getAutomationRunId() != null && !runId.equals(e.getAutomationRunId())) continue;
            ActivityEventType type = e.getEventType();
            switch (type) {
                case USER_REPORTED_RELINK_COMPLETED -> { relinked = true; ctx.getAttemptedRemedies().add(safe(e.getSummary())); }
                case RETRY_ATTEMPTED -> ctx.getAttemptedRemedies().add(safe(e.getSummary()));
                case RELINK_REPOSITORY_RECOMMENDED, RETRY_RECOMMENDED ->
                    ctx.getPreviousRecommendations().add(e.getEventType() + ": " + safe(e.getSummary()));
                case MANUAL_DEPLOY_SUCCEEDED -> { if ("UNKNOWN".equals(manual)) manual = "SUCCEEDED"; }
                case MANUAL_DEPLOY_FAILED -> { if ("UNKNOWN".equals(manual)) manual = "FAILED"; }
                case MANUAL_DEPLOY_RESULT_UNKNOWN -> { /* keep UNKNOWN */ }
                case SAME_FAILURE_REPEATED -> ctx.setSameFailureRepeated(true);
                default -> { }
            }
        }
        ctx.setRelinkReportedByUser(relinked);
        ctx.setManualDeployResult(manual);
    }

    /** The error code the previous troubleshoot for this run settled on (encoded in actionId), or null. */
    public String lastTroubleshootErrorCode(Long projectId, Long runId) {
        List<ProjectActivityEvent> events = activityRepository.findByProjectIdOrderByCreatedAtDesc(
            projectId, PageRequest.of(0, MAX_EVENTS));
        for (ProjectActivityEvent e : events) {
            if (e.getEventType() == ActivityEventType.COPILOT_TROUBLESHOOT_RUN
                && (runId == null || runId.equals(e.getAutomationRunId()))) {
                return e.getActionId();
            }
        }
        return null;
    }

    // ---------- helpers ----------

    private List<ExecutionStep> readSteps(AutomationRun run) {
        if (run.getStepsJson() == null) return List.of();
        try {
            return objectMapper.readValue(run.getStepsJson(), new TypeReference<List<ExecutionStep>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private <T> T parse(String json, Class<T> type) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Unreadable {} JSON while building troubleshooting context", type.getSimpleName());
            return null;
        }
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "";
        try {
            return logSanitizer.sanitize(s).content();
        } catch (IllegalArgumentException e) {
            return SecretRedactionUtil.redact(s);
        }
    }

    private static String safe(String s) { return s == null ? null : SecretRedactionUtil.redact(s); }

    static String normaliseProvider(String p) {
        return p == null ? "" : p.toUpperCase(Locale.ROOT);
    }
}
