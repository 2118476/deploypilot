package com.deploypilot.service;

import com.deploypilot.dto.BlueprintResponse;
import com.deploypilot.dto.StartVerificationRequest;
import com.deploypilot.dto.VerificationResult;
import com.deploypilot.dto.VerificationRunResponse;
import com.deploypilot.exception.ConflictException;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.DeploymentTarget;
import com.deploypilot.model.Project;
import com.deploypilot.model.VerificationRun;
import com.deploypilot.model.enums.DeploymentTargetType;
import com.deploypilot.model.enums.VerificationStatus;
import com.deploypilot.repository.DeploymentTargetRepository;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.repository.VerificationRunRepository;
import com.deploypilot.util.CurrentUserUtil;
import com.deploypilot.verify.SafeUrlValidator;
import com.deploypilot.verify.VerificationEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates verification runs: ownership, duplicate prevention, simple
 * rate limiting, asynchronous execution, bounded history and target upserts.
 * All network access happens inside VerificationEngine via SafeHttpClient.
 */
@Service
public class DeploymentVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentVerificationService.class);

    static final int MAX_RUNS_PER_PROJECT = 20;
    static final long MIN_SECONDS_BETWEEN_RUNS = 10;

    private final VerificationEngine engine;
    private final SafeUrlValidator urlValidator;
    private final VerificationRunRepository runRepository;
    private final DeploymentTargetRepository targetRepository;
    private final ProjectRepository projectRepository;
    private final DeploymentBlueprintService blueprintService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<Long, Instant> lastStartByProject = new ConcurrentHashMap<>();

    public DeploymentVerificationService(VerificationEngine engine,
                                         SafeUrlValidator urlValidator,
                                         VerificationRunRepository runRepository,
                                         DeploymentTargetRepository targetRepository,
                                         ProjectRepository projectRepository,
                                         DeploymentBlueprintService blueprintService,
                                         ObjectMapper objectMapper) {
        this.engine = engine;
        this.urlValidator = urlValidator;
        this.runRepository = runRepository;
        this.targetRepository = targetRepository;
        this.projectRepository = projectRepository;
        this.blueprintService = blueprintService;
        this.objectMapper = objectMapper;
    }

    public VerificationRunResponse start(Long projectId, StartVerificationRequest request) {
        return start(projectId, request, true);
    }

    /**
     * Automation-initiated verification (run automatically after a deployment).
     * Skips the interactive rate limit and duplicate guard, which exist to stop
     * users spamming the form, not the server's own post-deploy check.
     */
    public VerificationRunResponse startForAutomation(Long projectId, StartVerificationRequest request) {
        return start(projectId, request, false);
    }

    private VerificationRunResponse start(Long projectId, StartVerificationRequest request, boolean interactive) {
        Long userId = requireOwnedProject(projectId).getUserId();

        boolean hasFrontend = notBlank(request.getFrontendUrl());
        boolean hasBackend = notBlank(request.getBackendUrl());
        if (!hasFrontend && !hasBackend) {
            throw new IllegalArgumentException("Provide at least one URL to verify (frontend or backend)");
        }
        // validate up-front so obviously unsafe URLs fail fast with a clear 400
        if (hasFrontend) urlValidator.validate(request.getFrontendUrl().trim(), request.isAllowInsecureLocal());
        if (hasBackend) urlValidator.validate(request.getBackendUrl().trim(), request.isAllowInsecureLocal());

        if (interactive) {
            if (runRepository.existsByProjectIdAndOverallStatus(projectId, VerificationStatus.RUNNING)) {
                throw new ConflictException("A verification is already running for this project. Wait for it to finish.");
            }
            Instant last = lastStartByProject.get(projectId);
            if (last != null && Instant.now().isBefore(last.plusSeconds(MIN_SECONDS_BETWEEN_RUNS))) {
                throw new ConflictException("Please wait a few seconds between verification runs.");
            }
            lastStartByProject.put(projectId, Instant.now());
        }

        // blueprint context (optional — verification works without a blueprint)
        Long blueprintId = null;
        boolean spaExpected = true;
        String platformHint = null;
        try {
            BlueprintResponse bp = blueprintService.getLatest(projectId);
            blueprintId = bp.getId();
            if (bp.getResult() != null) {
                var frontendComp = bp.getResult().getComponents().stream()
                    .filter(c -> "FRONTEND".equals(c.getType())).findFirst().orElse(null);
                if (frontendComp != null) spaExpected = !"Next.js".equals(frontendComp.getName());
                platformHint = bp.getResult().getComponents().stream()
                    .filter(c -> "BACKEND".equals(c.getType()))
                    .map(com.deploypilot.dto.BlueprintResult.Component::getSelectedPlatform)
                    .findFirst().orElse(null);
            }
        } catch (ResourceNotFoundException ignored) {
            // no blueprint yet — proceed with defaults
        }

        VerificationRun run = new VerificationRun();
        run.setProjectId(projectId);
        run.setUserId(userId);
        run.setBlueprintId(blueprintId);
        run.setBlueprintCommit(trimOrNull(request.getExpectedCommit()));
        run.setFrontendUrl(trimOrNull(request.getFrontendUrl()));
        run.setBackendUrl(trimOrNull(request.getBackendUrl()));
        run.setOverallStatus(VerificationStatus.RUNNING);
        run = runRepository.save(run);

        upsertTargets(projectId, userId, request);

        VerificationEngine.Context ctx = new VerificationEngine.Context(
            trimOrNull(request.getFrontendUrl()),
            trimOrNull(request.getBackendUrl()),
            trimOrNull(request.getHealthPath()),
            trimOrNull(request.getExpectedCommit()),
            request.isAllowInsecureLocal(),
            spaExpected,
            platformHint);

        long runId = run.getId();
        executor.execute(() -> execute(runId, projectId, ctx));
        return toResponse(run);
    }

    /** Runs the engine and persists the outcome; never throws. */
    void execute(long runId, long projectId, VerificationEngine.Context ctx) {
        VerificationRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;
        try {
            VerificationEngine.Outcome outcome = engine.verify(ctx);
            run.setResultJson(objectMapper.writeValueAsString(outcome.result()));
            run.setOverallStatus(outcome.overallStatus());
        } catch (Exception e) {
            log.error("Verification run {} failed", runId, e);
            run.setOverallStatus(VerificationStatus.FAILED);
            try {
                VerificationResult r = new VerificationResult();
                r.setSummary("Verification failed unexpectedly. Please re-run it.");
                run.setResultJson(objectMapper.writeValueAsString(r));
            } catch (Exception ignored) { }
        }
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
        updateTargetResults(projectId, run);
        enforceHistoryLimit(projectId);
    }

    public VerificationRunResponse getRun(Long projectId, Long runId) {
        requireOwnedProject(projectId);
        VerificationRun run = runRepository.findById(runId)
            .filter(x -> x.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("Verification run not found"));
        return toResponse(run);
    }

    public List<VerificationRunResponse> list(Long projectId, int limit) {
        requireOwnedProject(projectId);
        int capped = Math.max(1, Math.min(limit, 10));
        return runRepository.findByProjectIdOrderByStartedAtDesc(projectId, PageRequest.of(0, capped))
            .stream().map(this::toResponse).toList();
    }

    // ---------- internals ----------

    private void upsertTargets(Long projectId, Long userId, StartVerificationRequest request) {
        List<DeploymentTarget> existing = targetRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        if (notBlank(request.getFrontendUrl())) {
            upsert(existing, projectId, userId, DeploymentTargetType.FRONTEND,
                request.getFrontendUrl().trim(), null, null);
        }
        if (notBlank(request.getBackendUrl())) {
            upsert(existing, projectId, userId, DeploymentTargetType.BACKEND,
                request.getBackendUrl().trim(), trimOrNull(request.getHealthPath()),
                trimOrNull(request.getExpectedCommit()));
        }
    }

    private void upsert(List<DeploymentTarget> existing, Long projectId, Long userId,
                        DeploymentTargetType type, String url, String healthPath, String expectedCommit) {
        DeploymentTarget target = existing.stream()
            .filter(t -> t.getTargetType() == type).findFirst().orElseGet(DeploymentTarget::new);
        target.setProjectId(projectId);
        target.setUserId(userId);
        target.setTargetType(type);
        target.setUrl(url);
        if (healthPath != null) target.setHealthPath(healthPath);
        if (expectedCommit != null) target.setExpectedCommit(expectedCommit);
        targetRepository.save(target);
    }

    private void updateTargetResults(Long projectId, VerificationRun run) {
        for (DeploymentTarget t : targetRepository.findByProjectIdOrderByCreatedAtAsc(projectId)) {
            t.setLastVerifiedAt(run.getCompletedAt());
            t.setLastResult(run.getOverallStatus().name());
            targetRepository.save(t);
        }
    }

    private void enforceHistoryLimit(Long projectId) {
        long count = runRepository.countByProjectId(projectId);
        if (count <= MAX_RUNS_PER_PROJECT) return;
        List<VerificationRun> oldestFirst = runRepository.findByProjectIdOrderByStartedAtAsc(projectId);
        int toDelete = (int) (count - MAX_RUNS_PER_PROJECT);
        for (int i = 0; i < toDelete && i < oldestFirst.size(); i++) {
            runRepository.delete(oldestFirst.get(i));
        }
    }

    private VerificationRunResponse toResponse(VerificationRun run) {
        VerificationRunResponse r = new VerificationRunResponse();
        r.setId(run.getId());
        r.setProjectId(run.getProjectId());
        r.setBlueprintId(run.getBlueprintId());
        r.setFrontendUrl(run.getFrontendUrl());
        r.setBackendUrl(run.getBackendUrl());
        r.setOverallStatus(run.getOverallStatus());
        r.setStartedAt(run.getStartedAt());
        r.setCompletedAt(run.getCompletedAt());
        if (run.getResultJson() != null) {
            try {
                r.setResult(objectMapper.readValue(run.getResultJson(), VerificationResult.class));
            } catch (Exception e) {
                log.warn("Verification run {} has unreadable result JSON", run.getId());
            }
        }
        return r;
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

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private String trimOrNull(String s) { return notBlank(s) ? s.trim() : null; }
}
