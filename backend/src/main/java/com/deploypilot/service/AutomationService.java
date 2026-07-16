package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.exception.ConflictException;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.AutomationRun;
import com.deploypilot.model.DeploymentConfirmation;
import com.deploypilot.model.Project;
import com.deploypilot.model.ProviderConnection;
import com.deploypilot.model.enums.AutomationMode;
import com.deploypilot.model.enums.AutomationRunStatus;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.repository.AutomationRunRepository;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.util.CurrentUserUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the controlled-automation lifecycle: generate a plan, confirm it
 * (short-lived, single-use), then execute or retry only what was approved. All
 * external changes happen inside {@link DeploymentExecutor}; this service owns
 * ownership checks, drift detection, concurrency guards and mapping to responses.
 */
@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
    static final int MAX_RUNS_PER_PROJECT = 20;

    private final ProjectRepository projectRepository;
    private final ActionPlanService planService;
    private final ConfirmationService confirmationService;
    private final DeploymentExecutor executor;
    private final AutomationRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final boolean executionEnabled;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(2);

    public AutomationService(ProjectRepository projectRepository,
                             ActionPlanService planService,
                             ConfirmationService confirmationService,
                             DeploymentExecutor executor,
                             AutomationRunRepository runRepository,
                             ObjectMapper objectMapper,
                             @Value("${deploypilot.automation.execution-enabled:true}") boolean executionEnabled) {
        this.projectRepository = projectRepository;
        this.planService = planService;
        this.confirmationService = confirmationService;
        this.executor = executor;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        this.executionEnabled = executionEnabled;
    }

    /** Read-only: generate (but never execute) an action plan. */
    public DeploymentActionPlan plan(Long projectId, PlanRequest request) {
        requireOwnedProject(projectId);
        return planService.build(projectId, request);
    }

    /**
     * Issues a confirmation for a specific plan. Recreates the plan from the same
     * inputs, refuses if the hash the client saw no longer matches, and refuses
     * unless the plan is executable (Deploy-for-me and unblocked). Creates a
     * PENDING run — no external change happens yet.
     */
    public ConfirmationResponse confirm(Long projectId, ConfirmRequest request) {
        Project project = requireOwnedProject(projectId);
        Long userId = project.getUserId();
        if (!executionEnabled) {
            throw new UnauthorizedAccessException("Automated execution is disabled on this server.");
        }
        DeploymentActionPlan plan = planService.build(projectId, request);
        AutomationMode mode = AutomationMode.valueOf(plan.getMode());
        if (mode != AutomationMode.DEPLOY_FOR_ME) {
            throw new IllegalArgumentException("Only the Deploy-for-me mode performs external actions.");
        }
        if (!plan.getPlanHash().equals(request.getPlanHash())) {
            throw new ConflictException("The plan changed since you reviewed it. Review the new plan and confirm again.");
        }
        if (!plan.isExecutable()) {
            String reason = plan.getBlockers().isEmpty()
                ? "This plan cannot be executed." : plan.getBlockers().get(0);
            throw new IllegalArgumentException(reason);
        }
        if (runRepository.existsByProjectIdAndStatus(projectId, AutomationRunStatus.RUNNING)) {
            throw new ConflictException("An automation is already running for this project. Wait for it to finish.");
        }

        Map<ProviderType, ProviderConnection> connections = planService.connectionsFor(userId);
        String accountBinding = planService.accountBinding(connections);

        AutomationRun run = new AutomationRun();
        run.setUserId(userId);
        run.setProjectId(projectId);
        run.setMode(mode);
        run.setPlanHash(plan.getPlanHash());
        run.setRepositoryFullName(plan.getRepository());
        run.setCommitSha(plan.getCommitSha());
        run.setStatus(AutomationRunStatus.PENDING);
        run.setPlanJson(write(plan));
        run.setPlanInputsJson(write(new PlanInputs(request)));
        run = runRepository.save(run);
        enforceHistoryLimit(projectId);

        DeploymentConfirmation confirmation = confirmationService.create(userId, projectId, run.getId(),
            mode, plan.getPlanHash(), plan.getRepository(), plan.getCommitSha(), accountBinding);

        ConfirmationResponse response = new ConfirmationResponse();
        response.setRunId(run.getId());
        response.setNonce(confirmation.getNonce());
        response.setPlanHash(plan.getPlanHash());
        response.setMode(mode.name());
        response.setExpiresAt(confirmation.getExpiresAt());
        response.setPlan(plan);
        return response;
    }

    /** Consumes the confirmation and starts the run. External changes begin here. */
    public AutomationRunResponse execute(Long projectId, Long runId, ExecuteRequest request) {
        Project project = requireOwnedProject(projectId);
        Long userId = project.getUserId();
        if (!executionEnabled) {
            throw new UnauthorizedAccessException("Automated execution is disabled on this server.");
        }
        AutomationRun run = requireRun(projectId, runId);
        if (run.getStatus() != AutomationRunStatus.PENDING) {
            throw new ConflictException("This run has already started. Use retry to resume a failed run.");
        }
        if (runRepository.existsByProjectIdAndStatus(projectId, AutomationRunStatus.RUNNING)) {
            throw new ConflictException("An automation is already running for this project.");
        }

        // Rebuild the plan from the stored inputs and refuse if the hash drifted.
        String currentHash = recomputeHash(projectId, run);
        confirmationService.consume(userId, projectId, request.getNonce(), currentHash);

        startAsync(run);
        return toResponse(run);
    }

    /**
     * Retries a failed or paused run from the failed step. Requires a fresh
     * confirmation and refuses if the plan has drifted. Successful steps are
     * never redone (the executor skips them), so resources are not recreated.
     */
    public AutomationRunResponse retry(Long projectId, Long runId, ExecuteRequest request) {
        Project project = requireOwnedProject(projectId);
        Long userId = project.getUserId();
        if (!executionEnabled) {
            throw new UnauthorizedAccessException("Automated execution is disabled on this server.");
        }
        AutomationRun run = requireRun(projectId, runId);
        if (run.getStatus() != AutomationRunStatus.FAILED && run.getStatus() != AutomationRunStatus.PAUSED) {
            throw new ConflictException("Only a failed or paused run can be retried.");
        }
        if (runRepository.existsByProjectIdAndStatus(projectId, AutomationRunStatus.RUNNING)) {
            throw new ConflictException("An automation is already running for this project.");
        }
        String currentHash = recomputeHash(projectId, run);
        if (!currentHash.equals(run.getPlanHash())) {
            throw new ConflictException("The plan changed since this run was created. Start a new automation.");
        }
        confirmationService.consume(userId, projectId, request.getNonce(), currentHash);

        startAsync(run);
        return toResponse(run);
    }

    public AutomationRunResponse getRun(Long projectId, Long runId) {
        requireOwnedProject(projectId);
        return toResponse(requireRun(projectId, runId));
    }

    public List<AutomationRunResponse> list(Long projectId, int limit) {
        requireOwnedProject(projectId);
        int capped = Math.max(1, Math.min(limit, 10));
        return runRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, capped))
            .stream().map(this::toResponse).toList();
    }

    // ---------- internals ----------

    private void startAsync(AutomationRun run) {
        run.setStatus(AutomationRunStatus.RUNNING);
        runRepository.save(run);
        long id = run.getId();
        threadPool.execute(() -> executor.execute(id));
    }

    /** Rebuilds the plan from the stored inputs and returns its hash for drift checks. */
    private String recomputeHash(Long projectId, AutomationRun run) {
        PlanRequest request = readInputs(run);
        DeploymentActionPlan plan = planService.build(projectId, request);
        // Refresh the stored plan so the response reflects any non-hashed display drift.
        run.setPlanJson(write(plan));
        return plan.getPlanHash();
    }

    private void enforceHistoryLimit(Long projectId) {
        long count = runRepository.countByProjectId(projectId);
        if (count <= MAX_RUNS_PER_PROJECT) return;
        List<AutomationRun> oldestFirst = runRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        int toDelete = (int) (count - MAX_RUNS_PER_PROJECT);
        for (int i = 0; i < toDelete && i < oldestFirst.size(); i++) {
            AutomationRun old = oldestFirst.get(i);
            if (old.getStatus() != AutomationRunStatus.RUNNING) runRepository.delete(old);
        }
    }

    AutomationRunResponse toResponse(AutomationRun run) {
        AutomationRunResponse r = new AutomationRunResponse();
        r.setId(run.getId());
        r.setProjectId(run.getProjectId());
        r.setMode(run.getMode() != null ? run.getMode().name() : null);
        r.setStatus(run.getStatus().name());
        r.setPlanHash(run.getPlanHash());
        r.setRepository(run.getRepositoryFullName());
        r.setCommitSha(run.getCommitSha());
        r.setCurrentStepIndex(run.getCurrentStepIndex());
        r.setFailureReason(run.getFailureReason());
        r.setCreatedAt(run.getCreatedAt());
        r.setUpdatedAt(run.getUpdatedAt());
        r.setCompletedAt(run.getCompletedAt());
        if (run.getPlanJson() != null) {
            try {
                DeploymentActionPlan plan = objectMapper.readValue(run.getPlanJson(), DeploymentActionPlan.class);
                r.setPlan(plan);
                r.setBranch(plan.getBranch());
            } catch (Exception e) {
                log.warn("Run {} has unreadable plan JSON", run.getId());
            }
        }
        if (run.getStepsJson() != null) {
            try {
                r.setSteps(objectMapper.readValue(run.getStepsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ExecutionStep>>() {}));
            } catch (Exception e) {
                log.warn("Run {} has unreadable steps JSON", run.getId());
            }
        }
        if (run.getOutputsJson() != null) {
            try {
                Map<String, String> outputs = objectMapper.readValue(run.getOutputsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, String>>() {});
                r.setOutputs(outputs);
                r.setVerificationStatus(outputs.get(DeploymentExecutor.OUT_VERIFICATION_STATUS));
                String vid = outputs.get("verificationRunId");
                if (vid != null) {
                    try { r.setVerificationRunId(Long.parseLong(vid)); } catch (NumberFormatException ignored) { }
                }
            } catch (Exception e) {
                log.warn("Run {} has unreadable outputs JSON", run.getId());
            }
        }
        return r;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise automation state", e);
        }
    }

    private PlanRequest readInputs(AutomationRun run) {
        if (run.getPlanInputsJson() == null) return new PlanRequest();
        try {
            return objectMapper.readValue(run.getPlanInputsJson(), PlanRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored plan inputs are unreadable; start a new automation.");
        }
    }

    private AutomationRun requireRun(Long projectId, Long runId) {
        AutomationRun run = runRepository.findById(runId)
            .filter(x -> x.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("Automation run not found"));
        return run;
    }

    private Project requireOwnedProject(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not your project");
        }
        return project;
    }

    /** Serialisable snapshot of the plan inputs so execute/retry reproduce the plan exactly. */
    static class PlanInputs extends PlanRequest {
        PlanInputs() {}
        PlanInputs(PlanRequest r) {
            setMode(r.getMode());
            setBranch(r.getBranch());
            setExistingSites(r.getExistingSites());
            setNewSiteNames(r.getNewSiteNames());
            // Database (Supabase) choices must be reproduced exactly on execute/retry,
            // otherwise the recomputed plan hash would differ from the confirmed one.
            setDatabaseChoice(r.getDatabaseChoice());
            setSupabaseOrgId(r.getSupabaseOrgId());
            setSupabaseProjectRef(r.getSupabaseProjectRef());
            setSupabaseProjectName(r.getSupabaseProjectName());
            setSupabaseRegion(r.getSupabaseRegion());
            setSupabasePlan(r.getSupabasePlan());
            setApplyMigrations(r.isApplyMigrations());
        }
    }
}
