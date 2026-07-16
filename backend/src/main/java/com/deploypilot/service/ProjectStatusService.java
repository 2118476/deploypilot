package com.deploypilot.service;

import com.deploypilot.dto.BlueprintResult;
import com.deploypilot.dto.ProjectStatusResponse;
import com.deploypilot.dto.ProjectStatusResponse.Milestone;
import com.deploypilot.dto.ProjectStatusResponse.RecommendedAction;
import com.deploypilot.dto.ProjectStatusResponse.RequiredAction;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.*;
import com.deploypilot.model.enums.AnalysisStatus;
import com.deploypilot.model.enums.ProjectDashboardStatus;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.repository.*;
import com.deploypilot.util.CurrentUserUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Computes a deterministic, beginner-friendly project status purely from stored
 * records. Never calls AI. The dashboard works fully when Gemini is unavailable;
 * AI may only add an optional simpler explanation on top.
 */
@Service
public class ProjectStatusService {

    private final ProjectRepository projectRepository;
    private final RepositoryAnalysisRepository analysisRepository;
    private final DeploymentBlueprintRepository blueprintRepository;
    private final AutomationRunRepository automationRunRepository;
    private final VerificationRunRepository verificationRepository;
    private final ProviderConnectionRepository connectionRepository;
    private final AutomationSecretRepository secretRepository;
    private final ProjectActivityService activityService;
    private final ObjectMapper objectMapper;

    public ProjectStatusService(ProjectRepository projectRepository,
                                RepositoryAnalysisRepository analysisRepository,
                                DeploymentBlueprintRepository blueprintRepository,
                                AutomationRunRepository automationRunRepository,
                                VerificationRunRepository verificationRepository,
                                ProviderConnectionRepository connectionRepository,
                                AutomationSecretRepository secretRepository,
                                ProjectActivityService activityService,
                                ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.analysisRepository = analysisRepository;
        this.blueprintRepository = blueprintRepository;
        this.automationRunRepository = automationRunRepository;
        this.verificationRepository = verificationRepository;
        this.connectionRepository = connectionRepository;
        this.secretRepository = secretRepository;
        this.activityService = activityService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProjectStatusResponse getStatus(Long projectId) {
        Project project = requireOwnedProject(projectId);
        return compute(project);
    }

    /** Ownership-checked recent activity for the project timeline. */
    @Transactional(readOnly = true)
    public List<com.deploypilot.dto.ActivityEventResponse> getActivity(Long projectId, int limit) {
        requireOwnedProject(projectId);
        return activityService.recent(projectId, limit).stream()
            .map(com.deploypilot.dto.ActivityEventResponse::from).toList();
    }

    ProjectStatusResponse compute(Project project) {
        Long projectId = project.getId();
        Long userId = project.getUserId();

        RepositoryAnalysis latestAnalysis = analysisRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId).orElse(null);
        boolean analysed = analysisRepository.findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, AnalysisStatus.COMPLETED).isPresent();
        DeploymentBlueprint blueprint = blueprintRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId).orElse(null);
        BlueprintResult bp = blueprint != null ? parse(blueprint.getBlueprintJson(), BlueprintResult.class) : null;
        AutomationRun run = automationRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, 1))
            .stream().findFirst().orElse(null);
        VerificationRun verification = verificationRepository.findByProjectIdOrderByStartedAtDesc(projectId, PageRequest.of(0, 1))
            .stream().findFirst().orElse(null);
        Map<String, String> outputs = run != null ? readOutputs(run) : Map.of();

        ProjectStatusResponse r = new ProjectStatusResponse();
        r.setProjectId(projectId);
        r.setProjectName(project.getName());
        r.setFrontendUrl(outputs.get(DeploymentExecutor.OUT_FRONTEND_URL));
        r.setBackendUrl(outputs.get(DeploymentExecutor.OUT_BACKEND_URL));
        r.setPullRequestUrl(outputs.get(DeploymentExecutor.OUT_PR_URL));
        r.setSupabaseProjectUrl(outputs.get("supabaseProjectUrl"));
        if (run != null) {
            r.setLatestRunId(run.getId());
            r.setLatestRunStatus(run.getStatus().name());
            r.setMode(run.getMode() != null ? run.getMode().name() : null);
        }
        if (verification != null) r.setVerificationStatus(verification.getOverallStatus().name());
        r.setLastUpdated(lastUpdated(project, blueprint, run));

        Map<ProviderType, Boolean> connected = connections(userId);
        Set<ProviderType> requiredProviders = requiredProviders(bp);
        List<ProviderType> missingConnections = requiredProviders.stream().filter(p -> !connected.getOrDefault(p, false)).toList();
        List<String> missingSecrets = bp != null ? missingRequiredSecrets(projectId, bp) : List.of();

        ProjectDashboardStatus status = classify(latestAnalysis, analysed, bp, run, verification,
            missingConnections, missingSecrets);
        r.setStatus(status.name());
        r.setSummary(summarize(status, r, missingConnections, missingSecrets));
        r.setCurrentAction(currentAction(status, run, outputs));
        r.setMilestones(milestones(project, analysed, bp, requiredProviders, connected, outputs, verification));
        r.setRequiredActions(requiredActions(status, missingConnections, missingSecrets, r));
        r.setRecommendedNextStep(recommended(status, r));
        return r;
    }

    // ---------- classification ----------

    private ProjectDashboardStatus classify(RepositoryAnalysis latestAnalysis, boolean analysed, BlueprintResult bp,
                                            AutomationRun run, VerificationRun verification,
                                            List<ProviderType> missingConnections, List<String> missingSecrets) {
        if (!analysed) {
            if (latestAnalysis != null && latestAnalysis.getStatus() == AnalysisStatus.RUNNING) return ProjectDashboardStatus.ANALYSING;
            return ProjectDashboardStatus.NOT_ANALYSED;
        }
        if (bp == null) return ProjectDashboardStatus.SETUP_REQUIRED;

        if (run != null) {
            switch (run.getStatus()) {
                case PENDING -> { return ProjectDashboardStatus.WAITING_FOR_CONFIRMATION; }
                case RUNNING -> {
                    String step = currentStepId(run);
                    return step != null && step.startsWith("verify") ? ProjectDashboardStatus.VERIFYING : ProjectDashboardStatus.DEPLOYING;
                }
                case PAUSED -> { return ProjectDashboardStatus.PAUSED; }
                case FAILED -> { return ProjectDashboardStatus.FAILED; }
                case SUCCEEDED -> {
                    String v = verification != null ? verification.getOverallStatus().name() : null;
                    if ("HEALTHY".equals(v)) return ProjectDashboardStatus.HEALTHY;
                    return ProjectDashboardStatus.DEGRADED; // deployed but not confirmed fully healthy
                }
                case CANCELLED -> { /* fall through to readiness */ }
            }
        }
        if (!missingConnections.isEmpty()) return ProjectDashboardStatus.WAITING_FOR_CONNECTION;
        if (!missingSecrets.isEmpty()) return ProjectDashboardStatus.WAITING_FOR_SECRET;
        return ProjectDashboardStatus.BLUEPRINT_READY;
    }

    private String summarize(ProjectDashboardStatus status, ProjectStatusResponse r,
                             List<ProviderType> missingConnections, List<String> missingSecrets) {
        return switch (status) {
            case NOT_ANALYSED -> "This project hasn't been analysed yet. Point DeployPilot at your GitHub repository to begin.";
            case ANALYSING -> "DeployPilot is reading your repository to detect the technology stack.";
            case SETUP_REQUIRED -> "Your repository is analysed. Generate a deployment blueprint to see the recommended plan.";
            case BLUEPRINT_READY -> "Your blueprint is ready and everything needed is connected. Review the deployment plan and confirm to deploy.";
            case WAITING_FOR_CONNECTION -> "Connect " + missingConnections.stream().map(Enum::name).map(this::title)
                .reduce((a, b) -> a + " and " + b).orElse("the required provider") + " so DeployPilot can deploy for you.";
            case WAITING_FOR_SECRET -> "DeployPilot needs a few values before deploying: " + String.join(", ", missingSecrets) + ".";
            case WAITING_FOR_CONFIRMATION -> "A deployment plan is confirmed and ready. Start it when you're ready.";
            case DEPLOYING -> "DeployPilot is deploying your project. You can watch each step below.";
            case VERIFYING -> "The deployment finished; DeployPilot is now verifying that everything works.";
            case PAUSED -> "The deployment is paused, waiting for something from you (for example, a database connection).";
            case HEALTHY -> "Your deployment is live and verified healthy" + urlSuffix(r) + ".";
            case DEGRADED -> "Your deployment is live but verification found an issue to look at" + urlSuffix(r) + ".";
            case FAILED -> "The last deployment step failed. Review the details and retry from the failed step.";
            case UNKNOWN -> "DeployPilot could not determine the current status from the available records.";
        };
    }

    private String currentAction(ProjectDashboardStatus status, AutomationRun run, Map<String, String> outputs) {
        if (run == null) return null;
        if (status == ProjectDashboardStatus.DEPLOYING || status == ProjectDashboardStatus.VERIFYING || status == ProjectDashboardStatus.PAUSED) {
            List<com.deploypilot.dto.ExecutionStep> steps = readSteps(run);
            return steps.stream().filter(s -> "RUNNING".equals(s.getStatus())).map(com.deploypilot.dto.ExecutionStep::getTitle)
                .findFirst().orElseGet(() -> run.getFailureReason());
        }
        return null;
    }

    // ---------- milestones ----------

    private List<Milestone> milestones(Project project, boolean analysed, BlueprintResult bp,
                                       Set<ProviderType> requiredProviders, Map<ProviderType, Boolean> connected,
                                       Map<String, String> outputs, VerificationRun verification) {
        boolean providersConnected = !requiredProviders.isEmpty() && requiredProviders.stream().allMatch(p -> connected.getOrDefault(p, false));
        List<Milestone> m = new ArrayList<>();
        m.add(new Milestone("repository_imported", "Repository imported", project.getGithubUrl() != null));
        m.add(new Milestone("analysis_completed", "Analysis completed", analysed));
        m.add(new Milestone("blueprint_generated", "Blueprint generated", bp != null));
        m.add(new Milestone("providers_connected", "Providers connected", providersConnected));
        m.add(new Milestone("supabase_prepared", "Supabase prepared", outputs.containsKey("supabaseProjectRef")));
        m.add(new Milestone("config_pr_created", "Configuration PR created", outputs.containsKey(DeploymentExecutor.OUT_PR_URL)));
        m.add(new Milestone("backend_deployed", "Backend deployed", outputs.containsKey(DeploymentExecutor.OUT_BACKEND_URL)));
        m.add(new Milestone("frontend_deployed", "Frontend deployed", outputs.containsKey(DeploymentExecutor.OUT_FRONTEND_URL)));
        m.add(new Milestone("verification_completed", "Verification completed", verification != null && verification.getCompletedAt() != null));
        return m;
    }

    // ---------- required + recommended actions ----------

    private List<RequiredAction> requiredActions(ProjectDashboardStatus status, List<ProviderType> missingConnections,
                                                 List<String> missingSecrets, ProjectStatusResponse r) {
        List<RequiredAction> actions = new ArrayList<>();
        switch (status) {
            case NOT_ANALYSED -> actions.add(new RequiredAction("RUN_ANALYSIS", "Analyse your repository", "Import a GitHub repository and run analysis."));
            case SETUP_REQUIRED -> actions.add(new RequiredAction("GENERATE_BLUEPRINT", "Generate the blueprint", "Create the deployment plan from your analysis."));
            case WAITING_FOR_CONNECTION -> missingConnections.forEach(p ->
                actions.add(new RequiredAction("CONNECT_PROVIDER", "Connect " + title(p.name()), "Connect your " + title(p.name()) + " account.")));
            case WAITING_FOR_SECRET -> missingSecrets.forEach(s ->
                actions.add(new RequiredAction("ADD_SECRET", "Add " + s, "Provide a value for " + s + ".")));
            case WAITING_FOR_CONFIRMATION -> actions.add(new RequiredAction("CONFIRM_DEPLOYMENT", "Start the confirmed deployment", "Execute the plan you confirmed."));
            case BLUEPRINT_READY -> actions.add(new RequiredAction("REVIEW_PLAN", "Review the deployment plan", "Open the plan and confirm the exact actions."));
            case FAILED -> actions.add(new RequiredAction("RETRY_FAILED_STEP", "Retry the failed step", "Fix the issue then retry from where it stopped."));
            default -> { }
        }
        if (r.getPullRequestUrl() != null && (status == ProjectDashboardStatus.HEALTHY || status == ProjectDashboardStatus.DEGRADED)) {
            actions.add(new RequiredAction("MERGE_PR", "Merge the configuration pull request", "Merge the generated config so it stays in version control."));
        }
        return actions;
    }

    private RecommendedAction recommended(ProjectDashboardStatus status, ProjectStatusResponse r) {
        return switch (status) {
            case NOT_ANALYSED -> new RecommendedAction("RUN_ANALYSIS", "Analyse repository");
            case SETUP_REQUIRED -> new RecommendedAction("GENERATE_BLUEPRINT", "Generate blueprint");
            case WAITING_FOR_CONNECTION -> new RecommendedAction("CONNECT_PROVIDER", "Connect providers");
            case WAITING_FOR_SECRET -> new RecommendedAction("ADD_SECRET", "Add missing values");
            case BLUEPRINT_READY -> new RecommendedAction("REVIEW_PLAN", "Review & confirm plan");
            case WAITING_FOR_CONFIRMATION -> new RecommendedAction("CONFIRM_DEPLOYMENT", "Start deployment");
            case FAILED -> new RecommendedAction("RETRY_FAILED_STEP", "Retry failed step");
            case DEGRADED -> new RecommendedAction("VERIFY", "Review verification");
            case HEALTHY -> r.getPullRequestUrl() != null ? new RecommendedAction("MERGE_PR", "Merge config PR") : null;
            default -> null;
        };
    }

    // ---------- helpers ----------

    private Set<ProviderType> requiredProviders(BlueprintResult bp) {
        Set<ProviderType> req = new LinkedHashSet<>();
        if (bp == null) return req;
        req.add(ProviderType.GITHUB);
        for (BlueprintResult.Component c : bp.getComponents()) {
            if ("Netlify".equalsIgnoreCase(c.getSelectedPlatform())) req.add(ProviderType.NETLIFY);
            if ("Render".equalsIgnoreCase(c.getSelectedPlatform())) req.add(ProviderType.RENDER);
        }
        return req;
    }

    private Map<ProviderType, Boolean> connections(Long userId) {
        Map<ProviderType, Boolean> map = new EnumMap<>(ProviderType.class);
        for (ProviderType p : ProviderType.values()) {
            map.put(p, connectionRepository.findByUserIdAndProvider(userId, p)
                .map(c -> c.getStatus() == com.deploypilot.model.enums.ConnectionStatus.CONNECTED).orElse(false));
        }
        return map;
    }

    private List<String> missingRequiredSecrets(Long projectId, BlueprintResult bp) {
        Set<String> stored = new HashSet<>();
        secretRepository.findByProjectIdOrderByVarNameAsc(projectId).forEach(s -> stored.add(s.getVarName()));
        List<String> missing = new ArrayList<>();
        for (BlueprintResult.EnvVarMapping m : bp.getEnvironmentVariables()) {
            boolean required = Boolean.TRUE.equals(m.getRequired());
            boolean secret = "SECRET_OR_SENSITIVE".equals(m.getClassification());
            boolean derivable = m.isGeneratable() || (m.getDependsOnOutput() != null && !m.getDependsOnOutput().isBlank());
            if (required && secret && !derivable && !stored.contains(m.getName())) missing.add(m.getName());
        }
        return missing;
    }

    private String currentStepId(AutomationRun run) {
        return readSteps(run).stream().filter(s -> "RUNNING".equals(s.getStatus()))
            .map(com.deploypilot.dto.ExecutionStep::getId).findFirst().orElse(null);
    }

    private List<com.deploypilot.dto.ExecutionStep> readSteps(AutomationRun run) {
        if (run.getStepsJson() == null) return List.of();
        try { return objectMapper.readValue(run.getStepsJson(), new TypeReference<List<com.deploypilot.dto.ExecutionStep>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private Map<String, String> readOutputs(AutomationRun run) {
        if (run.getOutputsJson() == null) return Map.of();
        try { return objectMapper.readValue(run.getOutputsJson(), new TypeReference<LinkedHashMap<String, String>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private <T> T parse(String json, Class<T> type) {
        if (json == null) return null;
        try { return objectMapper.readValue(json, type); } catch (Exception e) { return null; }
    }

    private Instant lastUpdated(Project project, DeploymentBlueprint blueprint, AutomationRun run) {
        if (run != null && run.getUpdatedAt() != null) return run.getUpdatedAt();
        if (blueprint != null && blueprint.getUpdatedAt() != null) return blueprint.getUpdatedAt();
        return project.getUpdatedAt();
    }

    private String urlSuffix(ProjectStatusResponse r) {
        return r.getFrontendUrl() != null ? " at " + r.getFrontendUrl() : "";
    }

    private String title(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }

    private Project requireOwnedProject(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) throw new UnauthorizedAccessException("Not your project");
        return project;
    }
}
