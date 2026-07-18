package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.model.AutomationRun;
import com.deploypilot.model.enums.ActionStatus;
import com.deploypilot.model.enums.ActivityEventType;
import com.deploypilot.model.enums.AutomationRunStatus;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.HostingProvider;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderException;
import com.deploypilot.provider.ProviderRegistry;
import com.deploypilot.provider.model.*;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.AutomationRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Executes a confirmed action plan against the connected providers in dependency
 * order. It is idempotent (reuses resources on retry, never creates duplicates),
 * stops on the first failure, pauses when required input is missing, records
 * sanitised progress and runs Stage 3 verification at the end. No secret values
 * are ever persisted or logged.
 */
@Service
public class DeploymentExecutor {

    private static final Logger log = LoggerFactory.getLogger(DeploymentExecutor.class);

    static final String OUT_BACKEND_SERVICE_ID = "backendServiceId";
    static final String OUT_BACKEND_URL = "backendUrl";
    static final String OUT_BACKEND_DEPLOY_ID = "backendDeployId";
    static final String OUT_FRONTEND_SITE_ID = "frontendSiteId";
    static final String OUT_FRONTEND_URL = "frontendUrl";
    static final String OUT_FRONTEND_DEPLOY_ID = "frontendDeployId";
    static final String OUT_PR_URL = "pullRequestUrl";
    static final String OUT_VERIFICATION_STATUS = "verificationStatus";

    private final AutomationRunRepository runRepository;
    private final DeploymentBlueprintService blueprintService;
    private final ConnectionService connectionService;
    private final SecretService secretService;
    private final ProviderRegistry providers;
    private final DeploymentVerificationService verificationService;
    private final SupabaseDeploymentCollaborator supabase;
    private final ProjectActivityService activityService;
    private final ObjectMapper objectMapper;

    private final long pollIntervalMs;
    private final int pollMaxAttempts;
    private final boolean verifyAllowLocal;

    public DeploymentExecutor(AutomationRunRepository runRepository,
                              DeploymentBlueprintService blueprintService,
                              ConnectionService connectionService,
                              SecretService secretService,
                              ProviderRegistry providers,
                              DeploymentVerificationService verificationService,
                              SupabaseDeploymentCollaborator supabase,
                              ProjectActivityService activityService,
                              ObjectMapper objectMapper,
                              @Value("${deploypilot.automation.poll-interval-ms:2000}") long pollIntervalMs,
                              @Value("${deploypilot.automation.poll-max-attempts:60}") int pollMaxAttempts,
                              @Value("${deploypilot.automation.verify-allow-local:false}") boolean verifyAllowLocal) {
        this.runRepository = runRepository;
        this.blueprintService = blueprintService;
        this.connectionService = connectionService;
        this.secretService = secretService;
        this.providers = providers;
        this.verificationService = verificationService;
        this.supabase = supabase;
        this.activityService = activityService;
        this.objectMapper = objectMapper;
        this.pollIntervalMs = pollIntervalMs;
        this.pollMaxAttempts = pollMaxAttempts;
        this.verifyAllowLocal = verifyAllowLocal;
    }

    /** Entry point; never throws. Establishes the run owner's security context. */
    public void execute(long runId) {
        AutomationRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;
        setSecurityContext(run.getUserId());
        try {
            runPlan(run);
        } catch (Exception fatal) {
            log.error("Automation run {} failed unexpectedly", runId, fatal);
            run.setStatus(AutomationRunStatus.FAILED);
            run.setFailureReason("Automation stopped after an unexpected error.");
            run.setCompletedAt(Instant.now());
            runRepository.save(run);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ---------- orchestration ----------

    private void runPlan(AutomationRun run) throws Exception {
        Long projectId = run.getProjectId();
        Long userId = run.getUserId();
        DeploymentActionPlan plan = objectMapper.readValue(run.getPlanJson(), DeploymentActionPlan.class);
        BlueprintResult bp = blueprintService.getLatest(projectId).getResult();
        Map<String, BlueprintResult.EnvVarMapping> envIndex = indexEnv(bp);

        List<ExecutionStep> steps = loadOrInitSteps(run, plan);
        Map<String, String> outputs = loadOutputs(run);
        Map<ProviderType, ProviderCredential> creds = new EnumMap<>(ProviderType.class);

        run.setStatus(AutomationRunStatus.RUNNING);
        run.setFailureReason(null);
        persist(run, steps, outputs);
        activityService.record(userId, projectId, run.getId(), ActivityEventType.AUTOMATION_STARTED,
            null, null, "Automation started (" + run.getMode() + ")", "RUNNING");

        List<DeploymentActionPlan.PlannedAction> actions = plan.getActions();
        for (int i = 0; i < actions.size(); i++) {
            DeploymentActionPlan.PlannedAction action = actions.get(i);
            ExecutionStep step = steps.get(i);
            if (ActionStatus.SUCCEEDED.name().equals(step.getStatus())
                || ActionStatus.SKIPPED.name().equals(step.getStatus())) {
                continue; // idempotent retry: never redo completed work
            }
            run.setCurrentStepIndex(i);
            step.setStatus(ActionStatus.RUNNING.name());
            step.setStartedAt(Instant.now());
            step.setDetail(null);
            persist(run, steps, outputs);

            StepResult result;
            try {
                result = dispatch(action, plan, bp, envIndex, outputs, creds, projectId, userId);
            } catch (PauseSignal ps) {
                pause(run, steps, outputs, step, ps.getMessage(), userId, projectId);
                return;
            } catch (ProviderException.BillingRequired billing) {
                // Never create a paid resource: pause and explain instead of failing hard.
                pause(run, steps, outputs, step, billing.getMessage(), userId, projectId);
                return;
            } catch (ProviderException pe) {
                fail(run, steps, outputs, step, safe(pe.getMessage()));
                activityService.record(userId, projectId, run.getId(), ActivityEventType.AUTOMATION_FAILED,
                    action.getProvider(), action.getId(), "Failed: " + action.getTitle(), "FAILED");
                return;
            } catch (Exception ex) {
                log.warn("Automation step {} failed", action.getId(), ex);
                fail(run, steps, outputs, step, "The step failed unexpectedly. You can retry from here.");
                activityService.record(userId, projectId, run.getId(), ActivityEventType.AUTOMATION_FAILED,
                    action.getProvider(), action.getId(), "Failed: " + action.getTitle(), "FAILED");
                return;
            }

            outputs.putAll(result.extraOutputs());
            step.setStatus(result.status().name());
            step.setDetail(result.detail());
            step.setSanitizedLog(result.log());
            step.setFinishedAt(Instant.now());
            persist(run, steps, outputs);

            if (result.status() == ActionStatus.FAILED) {
                run.setStatus(AutomationRunStatus.FAILED);
                run.setFailureReason(result.detail());
                run.setCompletedAt(Instant.now());
                persist(run, steps, outputs);
                activityService.record(userId, projectId, run.getId(), ActivityEventType.AUTOMATION_FAILED,
                    action.getProvider(), action.getId(), "Failed: " + action.getTitle(), "FAILED");
                return;
            }
            recordStepActivity(run, action, userId, projectId);
        }

        run.setStatus(AutomationRunStatus.SUCCEEDED);
        run.setCompletedAt(Instant.now());
        persist(run, steps, outputs);
        activityService.record(userId, projectId, run.getId(), ActivityEventType.AUTOMATION_SUCCEEDED,
            null, null, "Deployment automation completed", "SUCCEEDED");
    }

    private void pause(AutomationRun run, List<ExecutionStep> steps, Map<String, String> outputs,
                       ExecutionStep step, String message, Long userId, Long projectId) {
        step.setStatus(ActionStatus.PENDING.name());
        step.setDetail(message);
        run.setStatus(AutomationRunStatus.PAUSED);
        run.setFailureReason(message);
        persist(run, steps, outputs);
        activityService.record(userId, projectId, run.getId(), ActivityEventType.AUTOMATION_PAUSED,
            null, step.getId(), "Paused: " + message, "PAUSED");
    }

    private void recordStepActivity(AutomationRun run, DeploymentActionPlan.PlannedAction action, Long userId, Long projectId) {
        ActivityEventType type = switch (action.getId()) {
            case "repo.pr" -> ActivityEventType.CONFIG_PR_CREATED;
            case "database.create" -> ActivityEventType.DATABASE_CREATED;
            case "database.migrations.apply" -> ActivityEventType.MIGRATIONS_APPLIED;
            case "database.credentials", "database.inspect" -> ActivityEventType.SUPABASE_PREPARED;
            case "backend.deploy" -> ActivityEventType.BACKEND_DEPLOYED;
            case "frontend.deploy" -> ActivityEventType.FRONTEND_DEPLOYED;
            case "verify" -> ActivityEventType.VERIFICATION_COMPLETED;
            default -> null;
        };
        if (type != null) {
            activityService.record(userId, projectId, run.getId(), type,
                "NONE".equals(action.getProvider()) ? null : action.getProvider(), action.getId(), action.getTitle(), "SUCCEEDED");
        }
    }

    private StepResult dispatch(DeploymentActionPlan.PlannedAction action, DeploymentActionPlan plan,
                                BlueprintResult bp, Map<String, BlueprintResult.EnvVarMapping> envIndex,
                                Map<String, String> outputs, Map<ProviderType, ProviderCredential> creds,
                                Long projectId, Long userId) {
        return switch (action.getId()) {
            case "database.confirm" -> handleDatabase(plan, projectId);
            case "database.inspect" -> supabaseStep(() -> supabase.inspect(cred(creds, ProviderType.SUPABASE, userId), plan.getDatabase(), outputs));
            case "database.create" -> supabaseStep(() -> supabase.create(cred(creds, ProviderType.SUPABASE, userId), plan.getDatabase(), projectId, userId, outputs));
            case "database.wait" -> supabaseStep(() -> supabase.waitReady(cred(creds, ProviderType.SUPABASE, userId), outputs));
            case "database.migrations.inspect" -> supabaseStep(() -> supabase.migrationsInspect(repoRef(plan), branchOf(plan), plan.getDatabase(), projectId, outputs));
            case "database.migrations.apply" -> supabaseStep(() -> supabase.migrationsApply(cred(creds, ProviderType.SUPABASE, userId), repoRef(plan), branchOf(plan), projectId, userId, outputs));
            case "database.credentials" -> supabaseStep(() -> supabase.credentials(cred(creds, ProviderType.SUPABASE, userId), projectId, userId, outputs));
            case "verify.database" -> supabaseStep(() -> supabase.verifyDatabase(cred(creds, ProviderType.SUPABASE, userId), outputs));
            case "repo.pr" -> handleRepoPr(plan, bp, creds, userId);
            case "backend.ensure" -> handleEnsure(action, plan, bp, "BACKEND", outputs, creds, userId);
            case "backend.env", "backend.cors", "backend.database-env" -> handleEnv(action, ProviderType.RENDER, OUT_BACKEND_SERVICE_ID,
                envIndex, outputs, creds, projectId, userId);
            case "backend.deploy" -> handleDeploy(plan, ProviderType.RENDER, OUT_BACKEND_SERVICE_ID,
                OUT_BACKEND_DEPLOY_ID, OUT_BACKEND_URL, outputs, creds, userId);
            case "backend.restart" -> handleRestart(ProviderType.RENDER, OUT_BACKEND_SERVICE_ID, outputs, creds, userId);
            case "frontend.ensure" -> handleEnsure(action, plan, bp, "FRONTEND", outputs, creds, userId);
            case "frontend.env" -> handleEnv(action, ProviderType.NETLIFY, OUT_FRONTEND_SITE_ID,
                envIndex, outputs, creds, projectId, userId);
            case "frontend.deploy" -> handleDeploy(plan, ProviderType.NETLIFY, OUT_FRONTEND_SITE_ID,
                OUT_FRONTEND_DEPLOY_ID, OUT_FRONTEND_URL, outputs, creds, userId);
            case "verify" -> handleVerify(plan, outputs, projectId);
            default -> new StepResult(ActionStatus.SKIPPED, "No action for " + action.getId(), null);
        };
    }

    /** Runs a Supabase collaborator operation (which mutates outputs directly) as a step. */
    private StepResult supabaseStep(java.util.function.Supplier<String> op) {
        return new StepResult(ActionStatus.SUCCEEDED, op.get(), null);
    }

    private RepositoryRef repoRef(DeploymentActionPlan plan) {
        return RepositoryRef.parse(plan.getRepository());
    }

    private String branchOf(DeploymentActionPlan plan) {
        return plan.getBranch() != null && !plan.getBranch().isBlank() ? plan.getBranch() : "main";
    }

    // ---------- handlers ----------

    private StepResult handleDatabase(DeploymentActionPlan plan, Long projectId) {
        DeploymentActionPlan.DatabaseHandoff db = plan.getDatabase();
        if (db == null || !db.isRequired()) {
            return new StepResult(ActionStatus.SKIPPED, "No database required.", null);
        }
        Set<String> stored = secretService.storedNames(projectId);
        boolean supplied = db.getRequiredFields().stream().anyMatch(stored::contains);
        if (!supplied) {
            throw new PauseSignal("Waiting for the database connection. Add the required connection fields, "
                + "then retry. DeployPilot never creates or resets a database in this stage.");
        }
        return new StepResult(ActionStatus.SUCCEEDED,
            "Database connection provided (" + db.getDetectedProvider() + "). Connectivity is tested read-only only.", null);
    }

    private StepResult handleRepoPr(DeploymentActionPlan plan, BlueprintResult bp,
                                    Map<ProviderType, ProviderCredential> creds, Long userId) {
        if (bp.getRepository() == null) {
            return new StepResult(ActionStatus.SKIPPED, "No repository linked.", null);
        }
        ProviderCredential cred = cred(creds, ProviderType.GITHUB, userId);
        RepositoryRef ref = RepositoryRef.parse(bp.getRepository());
        String base = plan.getBranch() != null ? plan.getBranch() : "main";
        String branch = "deploypilot/deployment-config";
        List<CommitFile> files = new ArrayList<>();
        for (BlueprintResult.FilePreview fp : bp.getFilePreviews()) {
            if (fp.getSuggestedContent() != null
                && (!Boolean.TRUE.equals(fp.getExists()) || (fp.getDiff() != null && !fp.getDiff().isBlank()))) {
                files.add(new CommitFile(fp.getPath(), fp.getSuggestedContent()));
            }
        }
        if (files.isEmpty()) {
            return new StepResult(ActionStatus.SKIPPED, "No repository changes were required.", null);
        }
        PullRequestResult pr = providers.git().openConfigPullRequest(cred, ref, base, branch,
            "Add DeployPilot deployment configuration",
            "Generated deployment configuration files. Review and merge to keep them in version control. "
                + "No secret values are included.", files);
        if (pr.url() != null) {
            plan.getActions(); // keep reference; store output below via caller map
            return new StepResult(ActionStatus.SUCCEEDED, pr.note() + " " + pr.url(), null, Map.of(OUT_PR_URL, pr.url()));
        }
        return new StepResult(ActionStatus.SUCCEEDED, pr.note(), null);
    }

    private StepResult handleEnsure(DeploymentActionPlan.PlannedAction action, DeploymentActionPlan plan,
                                    BlueprintResult bp, String componentType, Map<String, String> outputs,
                                    Map<ProviderType, ProviderCredential> creds, Long userId) {
        ProviderType provider = ProviderType.valueOf(action.getProvider());
        HostingProvider hosting = providers.hosting(provider);
        ProviderCredential cred = cred(creds, provider, userId);
        BlueprintResult.Component component = component(bp, componentType);
        String idKey = "BACKEND".equals(componentType) ? OUT_BACKEND_SERVICE_ID : OUT_FRONTEND_SITE_ID;

        // Explicit reuse of a user-selected existing resource.
        if (action.getTargetResource() != null) {
            HostingSite site = hosting.getSite(cred, action.getTargetResource());
            captureSite(outputs, idKey, site, componentType);
            return new StepResult(ActionStatus.SUCCEEDED, "Reusing existing " + provider + " resource " + site.name() + ".", null);
        }

        // Idempotent create: reuse a resource already linked to this repository.
        String repo = bp.getRepository();
        for (HostingSite existing : hosting.listSites(cred)) {
            if (existing.linkedRepo() != null && existing.linkedRepo().equalsIgnoreCase(repo)) {
                captureSite(outputs, idKey, existing, componentType);
                return new StepResult(ActionStatus.SUCCEEDED,
                    "Reused existing " + provider + " resource " + existing.name() + " linked to " + repo
                        + " (no duplicate created).", null);
            }
        }

        String name = siteName(repo, "BACKEND".equals(componentType) ? "backend" : "frontend");
        CreateSiteRequest req = new CreateSiteRequest(
            name, repo, plan.getBranch(),
            component != null ? component.getRootDirectory() : null,
            component != null ? component.getBuildCommand() : null,
            component != null ? component.getPublishDirectory() : null,
            component != null ? component.getStartCommand() : null,
            "BACKEND".equals(componentType) ? renderRuntime(component) : null,
            component != null ? component.getHealthCheckPath() : null);
        HostingSite site = hosting.createSite(cred, req);
        captureSite(outputs, idKey, site, componentType);
        return new StepResult(ActionStatus.SUCCEEDED,
            "Created " + provider + " resource " + site.name() + " on the free plan.", null);
    }

    private StepResult handleEnv(DeploymentActionPlan.PlannedAction action, ProviderType provider, String idKey,
                                 Map<String, BlueprintResult.EnvVarMapping> envIndex, Map<String, String> outputs,
                                 Map<ProviderType, ProviderCredential> creds, Long projectId, Long userId) {
        String siteId = outputs.get(idKey);
        if (siteId == null) {
            return new StepResult(ActionStatus.FAILED, "The target resource was not created; cannot set variables.", null);
        }
        HostingProvider hosting = providers.hosting(provider);
        ProviderCredential cred = cred(creds, provider, userId);
        List<EnvVarInput> inputs = new ArrayList<>();
        List<String> set = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String name : action.getEnvironmentVariableNames()) {
            BlueprintResult.EnvVarMapping m = envIndex.get(name);
            // Hard safety net: a sensitive variable must never be sent to the frontend host.
            if (provider == ProviderType.NETLIFY && m != null && "SECRET_OR_SENSITIVE".equals(m.getClassification())) {
                skipped.add(name + " (sensitive; not sent to frontend)");
                continue;
            }
            String value = resolveValue(name, m, outputs, projectId, userId);
            if (value != null) {
                inputs.add(new EnvVarInput(name, value, m != null && "SECRET_OR_SENSITIVE".equals(m.getClassification())));
                set.add(name);
            } else {
                skipped.add(name + " (no value available)");
            }
        }
        if (!inputs.isEmpty()) {
            hosting.setEnvVars(cred, siteId, inputs);
        }
        String detail = set.isEmpty() ? "No variables needed setting." : "Set " + set.size() + " variable(s): " + String.join(", ", set) + ".";
        if (!skipped.isEmpty()) detail += " Skipped: " + String.join(", ", skipped) + ".";
        return new StepResult(ActionStatus.SUCCEEDED, detail, null);
    }

    private StepResult handleDeploy(DeploymentActionPlan plan, ProviderType provider, String idKey,
                                    String deployKey, String urlKey, Map<String, String> outputs,
                                    Map<ProviderType, ProviderCredential> creds, Long userId) {
        String siteId = outputs.get(idKey);
        if (siteId == null) {
            return new StepResult(ActionStatus.FAILED, "The target resource was not created; cannot deploy.", null);
        }
        HostingProvider hosting = providers.hosting(provider);
        ProviderCredential cred = cred(creds, provider, userId);
        DeploymentStatus status = hosting.triggerDeploy(cred, siteId,
            new DeployRequest(plan.getBranch(), plan.getCommitSha(), false));
        String deployId = status.deploymentId();
        if (deployId != null) outputs.put(deployKey, deployId);

        DeploymentState state = status.state();
        for (int attempt = 0; attempt < pollMaxAttempts && !state.isTerminal(); attempt++) {
            sleep(pollIntervalMs);
            if (deployId == null) break;
            status = hosting.getDeploymentStatus(cred, siteId, deployId);
            state = status.state();
        }

        if (state == DeploymentState.LIVE) {
            String url = status.url();
            if (url == null) {
                try { url = hosting.getSite(cred, siteId).url(); } catch (ProviderException ignored) { }
            }
            if (url != null) outputs.put(urlKey, url);
            return new StepResult(ActionStatus.SUCCEEDED,
                "Deployment is live" + (url != null ? " at " + url : "") + ".", null);
        }
        if (state == DeploymentState.FAILED || state == DeploymentState.CANCELED) {
            String logs = deployId != null ? hosting.getSanitizedLogs(cred, siteId, deployId) : null;
            return new StepResult(ActionStatus.FAILED, "Deployment " + state.name().toLowerCase()
                + ". Fix the issue and retry from this step.", logs);
        }
        return new StepResult(ActionStatus.FAILED,
            "Deployment did not reach a live state in time. Check the provider and retry.", null);
    }

    private StepResult handleRestart(ProviderType provider, String idKey, Map<String, String> outputs,
                                     Map<ProviderType, ProviderCredential> creds, Long userId) {
        String siteId = outputs.get(idKey);
        if (siteId == null) {
            return new StepResult(ActionStatus.SKIPPED, "No service to restart.", null);
        }
        HostingProvider hosting = providers.hosting(provider);
        hosting.restart(cred(creds, provider, userId), siteId);
        return new StepResult(ActionStatus.SUCCEEDED, "Restarted the backend to apply the new configuration.", null);
    }

    private StepResult handleVerify(DeploymentActionPlan plan, Map<String, String> outputs, Long projectId) {
        String frontendUrl = outputs.get(OUT_FRONTEND_URL);
        String backendUrl = outputs.get(OUT_BACKEND_URL);
        if (frontendUrl == null && backendUrl == null) {
            return new StepResult(ActionStatus.SKIPPED, "No deployed URLs to verify.", null);
        }
        StartVerificationRequest req = new StartVerificationRequest();
        req.setFrontendUrl(frontendUrl);
        req.setBackendUrl(backendUrl);
        req.setExpectedCommit(plan.getCommitSha());
        req.setAllowInsecureLocal(verifyAllowLocal);
        VerificationRunResponse started = verificationService.startForAutomation(projectId, req);
        outputs.put("verificationRunId", String.valueOf(started.getId()));

        VerificationRunResponse finalRun = started;
        for (int attempt = 0; attempt < 40 && "RUNNING".equals(String.valueOf(finalRun.getOverallStatus())); attempt++) {
            sleep(500);
            finalRun = verificationService.getRun(projectId, started.getId());
        }
        String status = String.valueOf(finalRun.getOverallStatus());
        outputs.put(OUT_VERIFICATION_STATUS, status);
        // Never claim success when verification says the deployment is unhealthy.
        if ("UNHEALTHY".equals(status) || "FAILED".equals(status)) {
            return new StepResult(ActionStatus.FAILED,
                "Verification reported " + status + ". The deployment is not healthy — review and retry.", null);
        }
        return new StepResult(ActionStatus.SUCCEEDED, "Verification reported " + status + ".", null);
    }

    // ---------- value resolution ----------

    /**
     * Resolves a real value for a variable, or null if none is available (in which
     * case it is simply not set). Values are only ever taken from: a generated app
     * secret, a user-supplied secret, or a URL captured from a previous step. A
     * blueprint's human-readable {@code expectedFormat} hint is never used as a
     * value, so descriptive placeholders are never pushed to a provider.
     */
    private String resolveValue(String name, BlueprintResult.EnvVarMapping m, Map<String, String> outputs,
                                Long projectId, Long userId) {
        if (m != null && m.isGeneratable()) {
            return secretService.getOrGenerate(projectId, userId, name);
        }
        Optional<String> supplied = secretService.getValue(projectId, name);
        if (supplied.isPresent()) return supplied.get();
        // The frontend API URL is derived from the captured backend URL.
        if (m != null && "BACKEND_PUBLIC_URL".equals(m.getDependsOnOutput())) {
            String backend = outputs.get(OUT_BACKEND_URL);
            return backend == null ? null : backend + "/api";
        }
        // The backend CORS/frontend-origin is the captured frontend URL.
        if (m != null && ("FRONTEND_PUBLIC_URL".equals(m.getDependsOnOutput()) || isCorsVar(m))) {
            return outputs.get(OUT_FRONTEND_URL);
        }
        // Well-known non-secret config DeployPilot supplies itself (e.g. AI_PROVIDER=gemini).
        String known = ActionPlanService.KNOWN_CONFIG_DEFAULTS.get(name.toUpperCase(Locale.ROOT));
        if (known != null) return known;
        return null;
    }

    private boolean isCorsVar(BlueprintResult.EnvVarMapping m) {
        String n = m.getName() == null ? "" : m.getName().toUpperCase(Locale.ROOT);
        return n.contains("FRONTEND_URL") || n.contains("CORS") || n.contains("ALLOWED_ORIGIN")
            || "FRONTEND_PUBLIC_URL".equals(m.getDependsOnOutput());
    }

    private String renderRuntime(BlueprintResult.Component backend) {
        if (backend == null || backend.getRuntime() == null) return "docker";
        String r = backend.getRuntime().toLowerCase(Locale.ROOT);
        return r.contains("node") ? "node" : "docker";
    }

    // ---------- persistence + helpers ----------

    private void captureSite(Map<String, String> outputs, String idKey, HostingSite site, String componentType) {
        if (site.id() != null) outputs.put(idKey, site.id());
        if (site.url() != null) {
            outputs.put("BACKEND".equals(componentType) ? OUT_BACKEND_URL : OUT_FRONTEND_URL, site.url());
        }
    }

    private ProviderCredential cred(Map<ProviderType, ProviderCredential> cache, ProviderType provider, Long userId) {
        return cache.computeIfAbsent(provider, p -> connectionService.requireCredential(userId, p));
    }

    private void fail(AutomationRun run, List<ExecutionStep> steps, Map<String, String> outputs,
                      ExecutionStep step, String message) {
        step.setStatus(ActionStatus.FAILED.name());
        step.setDetail(message);
        step.setFinishedAt(Instant.now());
        run.setStatus(AutomationRunStatus.FAILED);
        run.setFailureReason(message);
        run.setCompletedAt(Instant.now());
        persist(run, steps, outputs);
    }

    private List<ExecutionStep> loadOrInitSteps(AutomationRun run, DeploymentActionPlan plan) {
        if (run.getStepsJson() != null && !run.getStepsJson().isBlank()) {
            try {
                return objectMapper.readValue(run.getStepsJson(), new TypeReference<List<ExecutionStep>>() {});
            } catch (Exception e) {
                log.warn("Unreadable steps for run {}, reinitialising", run.getId());
            }
        }
        List<ExecutionStep> steps = new ArrayList<>();
        for (DeploymentActionPlan.PlannedAction a : plan.getActions()) {
            ExecutionStep s = new ExecutionStep();
            s.setId(a.getId());
            s.setOrder(a.getOrder());
            s.setType(a.getType());
            s.setProvider(a.getProvider());
            s.setTitle(a.getTitle());
            s.setStatus(ActionStatus.PENDING.name());
            steps.add(s);
        }
        return steps;
    }

    private Map<String, String> loadOutputs(AutomationRun run) {
        if (run.getOutputsJson() != null && !run.getOutputsJson().isBlank()) {
            try {
                return objectMapper.readValue(run.getOutputsJson(), new TypeReference<LinkedHashMap<String, String>>() {});
            } catch (Exception e) {
                log.warn("Unreadable outputs for run {}", run.getId());
            }
        }
        return new LinkedHashMap<>();
    }

    private void persist(AutomationRun run, List<ExecutionStep> steps, Map<String, String> outputs) {
        try {
            run.setStepsJson(objectMapper.writeValueAsString(steps));
            run.setOutputsJson(objectMapper.writeValueAsString(outputs));
        } catch (Exception e) {
            log.error("Could not serialise automation run state", e);
        }
        runRepository.save(run);
    }

    private Map<String, BlueprintResult.EnvVarMapping> indexEnv(BlueprintResult bp) {
        Map<String, BlueprintResult.EnvVarMapping> map = new HashMap<>();
        if (bp != null) {
            for (BlueprintResult.EnvVarMapping m : bp.getEnvironmentVariables()) {
                map.put(m.getName(), m);
            }
        }
        return map;
    }

    private BlueprintResult.Component component(BlueprintResult bp, String type) {
        return bp.getComponents().stream().filter(c -> type.equals(c.getType())).findFirst().orElse(null);
    }

    private String siteName(String repo, String suffix) {
        String base = repo == null ? "app" : (repo.contains("/") ? repo.substring(repo.indexOf('/') + 1) : repo);
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (base.isBlank()) base = "app";
        return base + "-" + suffix;
    }

    private void setSecurityContext(Long userId) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new User("automation-" + userId, "", authorities);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, userId, authorities));
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String safe(String s) { return s == null ? "The provider returned an unexpected result." : s; }

    /** Result of a single step. {@code extraOutputs} are merged into the run outputs. */
    private record StepResult(ActionStatus status, String detail, String log, Map<String, String> extraOutputs) {
        StepResult(ActionStatus status, String detail, String log) { this(status, detail, log, Map.of()); }
    }

    /** Signals that the run must pause for required user input. */
    private static class PauseSignal extends RuntimeException {
        PauseSignal(String message) { super(message); }
    }
}
