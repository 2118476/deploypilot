package com.deploypilot.service;

import com.deploypilot.dto.BlueprintResponse;
import com.deploypilot.dto.BlueprintResult;
import com.deploypilot.dto.DeploymentActionPlan;
import com.deploypilot.dto.DeploymentActionPlan.EnvVarPlanItem;
import com.deploypilot.dto.DeploymentActionPlan.PlannedAction;
import com.deploypilot.dto.PlanRequest;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.ProviderConnection;
import com.deploypilot.model.enums.ActionType;
import com.deploypilot.model.enums.AutomationMode;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.model.Project;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderRegistry;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.AutomationSecretRepository;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.util.CurrentUserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Deterministically turns the latest blueprint plus the user's selections into a
 * classified, dependency-ordered action plan. The plan is reviewable and carries
 * no secret values; a stable {@code planHash} lets a confirmation bind to exactly
 * these actions. This service performs no external writes.
 */
@Service
public class ActionPlanService {

    private static final Logger log = LoggerFactory.getLogger(ActionPlanService.class);

    private final ProjectRepository projectRepository;
    private final DeploymentBlueprintService blueprintService;
    private final ConnectionService connectionService;
    private final AutomationSecretRepository secretRepository;
    private final ProviderRegistry providers;

    public ActionPlanService(ProjectRepository projectRepository,
                             DeploymentBlueprintService blueprintService,
                             ConnectionService connectionService,
                             AutomationSecretRepository secretRepository,
                             ProviderRegistry providers) {
        this.projectRepository = projectRepository;
        this.blueprintService = blueprintService;
        this.connectionService = connectionService;
        this.secretRepository = secretRepository;
        this.providers = providers;
    }

    public DeploymentActionPlan build(Long projectId, PlanRequest request) {
        Project project = requireOwnedProject(projectId);
        Long userId = project.getUserId();
        AutomationMode mode = parseMode(request.getMode());

        BlueprintResponse blueprint = blueprintService.getLatest(projectId);
        BlueprintResult bp = blueprint.getResult();
        if (bp == null) {
            throw new IllegalArgumentException("The latest blueprint is unreadable; regenerate it before automating");
        }

        DeploymentActionPlan plan = new DeploymentActionPlan();
        plan.setRepository(bp.getRepository());
        plan.setMode(mode.name());

        Set<String> suppliedSecrets = new HashSet<>();
        secretRepository.findByProjectIdOrderByVarNameAsc(projectId)
            .forEach(s -> suppliedSecrets.add(s.getVarName()));

        // Resolve connections + branch/commit (best-effort).
        Map<ProviderType, ProviderConnection> connections = new EnumMap<>(ProviderType.class);
        for (ProviderType p : ProviderType.values()) {
            connectionService.findConnection(userId, p).ifPresent(c -> connections.put(p, c));
        }
        String branch = notBlank(request.getBranch()) ? request.getBranch().trim() : null;
        resolveRepoState(bp, connections.get(ProviderType.GITHUB), userId, branch, plan);

        // Components by type.
        BlueprintResult.Component backend = componentOfType(bp, "BACKEND");
        BlueprintResult.Component frontend = componentOfType(bp, "FRONTEND");
        BlueprintResult.Component database = componentOfType(bp, "DATABASE");

        List<PlannedAction> actions = new ArrayList<>();
        int[] order = {0};

        // 1. Database handoff (import-only in this stage).
        if (database != null) {
            plan.setDatabase(buildDatabaseHandoff(bp, database, suppliedSecrets));
            actions.add(action(order, "database.confirm", ActionType.READ_ONLY, "NONE", null,
                database.getName(), "Confirm database connection",
                "Verify the imported database connection is reachable. DeployPilot never creates, resets or deletes a database in this stage.",
                null, false, false, true, false, null, List.of(), List.of()));
            if (plan.getDatabase().isRequired() && !plan.getDatabase().isConnectionSupplied()) {
                plan.getBlockers().add("A database is required but no connection has been supplied. "
                    + "Add the database connection fields, then regenerate the plan.");
            }
        }

        // 2. Repository configuration PR (only if the blueprint proposes file changes).
        List<String> proposedFiles = proposedConfigFiles(bp);
        boolean gitHubConnected = connections.containsKey(ProviderType.GITHUB);
        if (!proposedFiles.isEmpty()) {
            String ghAccount = gitHubConnected ? connections.get(ProviderType.GITHUB).getAccountLabel() : null;
            PlannedAction pr = action(order, "repo.pr", ActionType.CREATE, "GITHUB", ghAccount,
                "Repository", "Open a pull request with generated configuration",
                "Create a dedicated branch, commit " + String.join(", ", proposedFiles)
                    + " and open a pull request for your review. DeployPilot never commits to the default branch and never force-pushes.",
                null, true, false, true, true,
                "No cost.", List.of(), List.of());
            actions.add(pr);
            if (!gitHubConnected) {
                plan.getWarnings().add("Connect GitHub to open the configuration pull request automatically.");
            }
        }

        // Build env-var routing (safety: secrets never routed to the frontend host).
        EnvRouting routing = routeEnvVars(bp, backend, frontend, suppliedSecrets, plan);

        // 3. Backend service (Render).
        PlannedAction backendEnsure = null, backendDeploy = null;
        ProviderType backendProvider = backend != null ? providerForPlatform(backend.getSelectedPlatform()) : null;
        if (backend != null && backendProvider != null) {
            requireConnection(connections, backendProvider, backend.getName(), plan);
            String account = label(connections, backendProvider);
            String existing = request.getExistingSites().get(backend.getId());
            boolean reuse = notBlank(existing);
            backendEnsure = action(order, "backend.ensure", reuse ? ActionType.UPDATE : ActionType.CREATE,
                backendProvider.name(), account, backend.getName(),
                reuse ? "Reuse existing backend service" : "Create backend web service",
                reuse ? "Reuse the selected existing service and update its build/branch settings."
                      : "Create a new Web Service linked to " + bp.getRepository() + " on the free plan.",
                reuse ? existing : null, !reuse, reuse, reuse, false,
                "Free plan (no cost). DeployPilot never selects a paid plan.", List.of(), List.of());
            actions.add(backendEnsure);

            PlannedAction backendEnv = action(order, "backend.env", ActionType.UPDATE, backendProvider.name(), account,
                backend.getName(), "Set backend environment variables",
                "Apply backend configuration and secrets (values masked). Secrets are sent only to the backend host, never to the frontend.",
                null, false, true, true, false, null, routing.backendEnvNames, List.of(backendEnsure.getId()));
            actions.add(backendEnv);

            backendDeploy = action(order, "backend.deploy", ActionType.DEPLOY, backendProvider.name(), account,
                backend.getName(), "Deploy backend", "Trigger a backend deployment and wait for it to go live, then capture its URL.",
                null, false, false, true, false, null, List.of(), List.of(backendEnv.getId()));
            actions.add(backendDeploy);
        } else if (backend != null) {
            plan.getWarnings().add(backend.getSelectedPlatform() + " automation for the backend is not supported yet — "
                + "use Guide Me for this component.");
        }

        // 4. Frontend site (Netlify).
        PlannedAction frontendDeploy = null;
        ProviderType frontendProvider = frontend != null ? providerForPlatform(frontend.getSelectedPlatform()) : null;
        if (frontend != null && frontendProvider != null) {
            requireConnection(connections, frontendProvider, frontend.getName(), plan);
            String account = label(connections, frontendProvider);
            String existing = request.getExistingSites().get(frontend.getId());
            boolean reuse = notBlank(existing);
            PlannedAction frontendEnsure = action(order, "frontend.ensure", reuse ? ActionType.UPDATE : ActionType.CREATE,
                frontendProvider.name(), account, frontend.getName(),
                reuse ? "Reuse existing frontend site" : "Create frontend site",
                reuse ? "Reuse the selected existing site and update its build/publish settings."
                      : "Create a new site linked to " + bp.getRepository() + " on the free plan.",
                reuse ? existing : null, !reuse, reuse, reuse, false,
                "Free plan (no cost). DeployPilot never selects a paid plan.", List.of(), List.of());
            actions.add(frontendEnsure);

            List<String> deps = new ArrayList<>(List.of(frontendEnsure.getId()));
            if (backendDeploy != null) deps.add(backendDeploy.getId()); // API URL comes from the backend
            PlannedAction frontendEnv = action(order, "frontend.env", ActionType.UPDATE, frontendProvider.name(), account,
                frontend.getName(), "Set frontend environment variables",
                "Apply frontend build variables, including the backend API URL captured from the backend deploy. Only frontend-safe values are sent.",
                null, false, true, true, false, null, routing.frontendEnvNames, deps);
            actions.add(frontendEnv);

            frontendDeploy = action(order, "frontend.deploy", ActionType.DEPLOY, frontendProvider.name(), account,
                frontend.getName(), "Deploy frontend", "Trigger a frontend deployment, wait for it to go live and capture its URL.",
                null, false, false, true, false, null, List.of(), List.of(frontendEnv.getId()));
            actions.add(frontendDeploy);
        } else if (frontend != null) {
            plan.getWarnings().add(frontend.getSelectedPlatform() + " automation for the frontend is not supported yet — "
                + "use Guide Me for this component.");
        }

        // 5. Cross-wire backend CORS from the frontend URL, then restart the backend.
        if (backendEnsure != null && frontendDeploy != null && routing.backendCorsVar != null) {
            String account = label(connections, backendProvider);
            PlannedAction cors = action(order, "backend.cors", ActionType.UPDATE, backendProvider.name(), account,
                backend.getName(), "Set backend allowed frontend origin",
                "Set the backend's CORS/frontend-origin variable (" + routing.backendCorsVar
                    + ") to the deployed frontend URL.",
                null, false, true, true, false, null, List.of(routing.backendCorsVar),
                List.of(backendEnsure.getId(), frontendDeploy.getId()));
            actions.add(cors);
            PlannedAction restart = action(order, "backend.restart", ActionType.RESTART, backendProvider.name(), account,
                backend.getName(), "Restart backend", "Restart/redeploy the backend so the new allowed origin takes effect.",
                null, false, true, true, false, null, List.of(), List.of(cors.getId()));
            actions.add(restart);
        }

        // 6. Automatic Stage 3 verification.
        if (backendDeploy != null || frontendDeploy != null) {
            List<String> deps = new ArrayList<>();
            if (frontendDeploy != null) deps.add(frontendDeploy.getId());
            if (backendDeploy != null) deps.add(backendDeploy.getId());
            actions.add(action(order, "verify", ActionType.READ_ONLY, "NONE", null, null,
                "Run deployment verification",
                "Run read-only Stage 3 verification against the captured URLs and report HEALTHY / DEGRADED / UNHEALTHY / INCONCLUSIVE.",
                null, false, false, true, false, null, List.of(), deps));
        }

        plan.setActions(actions);
        plan.setEnvironmentVariables(routing.items);

        // Executable only in Deploy-for-me and only when nothing blocks it.
        boolean noBlockers = plan.getBlockers().isEmpty();
        plan.setExecutable(mode == AutomationMode.DEPLOY_FOR_ME && noBlockers && hasProviderAction(actions));
        plan.setPlanHash(hash(plan, connections));
        return plan;
    }

    // ---------- env var routing ----------

    private static class EnvRouting {
        final List<EnvVarPlanItem> items = new ArrayList<>();
        final List<String> backendEnvNames = new ArrayList<>();
        final List<String> frontendEnvNames = new ArrayList<>();
        String backendCorsVar;
    }

    private EnvRouting routeEnvVars(BlueprintResult bp, BlueprintResult.Component backend,
                                    BlueprintResult.Component frontend, Set<String> suppliedSecrets,
                                    DeploymentActionPlan plan) {
        EnvRouting r = new EnvRouting();
        String backendId = backend != null ? backend.getId() : null;
        String frontendId = frontend != null ? frontend.getId() : null;

        for (BlueprintResult.EnvVarMapping m : bp.getEnvironmentVariables()) {
            boolean secret = "SECRET_OR_SENSITIVE".equals(m.getClassification());
            boolean toFrontend = frontendId != null && frontendId.equals(m.getComponentId());
            boolean toBackend = backendId != null && backendId.equals(m.getComponentId());

            EnvVarPlanItem item = new EnvVarPlanItem();
            item.setName(m.getName());
            item.setRequired(Boolean.TRUE.equals(m.getRequired()));
            item.setSecret(secret);
            item.setGeneratable(m.isGeneratable());
            item.setSource(m.getValueSource());

            String destination;
            if (toFrontend && secret) {
                // Safety: a secret must never be pushed to the frontend host.
                destination = "Backend service (kept off the frontend)";
                plan.getWarnings().add("Variable " + m.getName()
                    + " is sensitive and will not be sent to the frontend host.");
                if (backendId != null) r.backendEnvNames.add(m.getName());
            } else if (toFrontend) {
                destination = "Frontend site";
                r.frontendEnvNames.add(m.getName());
            } else if (toBackend) {
                destination = "Backend service";
                r.backendEnvNames.add(m.getName());
            } else {
                destination = "Repository (.env.example)";
            }
            item.setDestination(destination);
            item.setValueStatus(valueStatus(m, secret, suppliedSecrets));
            r.items.add(item);

            if (isCorsVar(m) && toBackend) r.backendCorsVar = m.getName();

            if (item.isRequired() && "NEEDS_INPUT".equals(item.getValueStatus())) {
                plan.getBlockers().add("Provide a value for required variable " + m.getName()
                    + " (" + destination + ").");
            }
        }
        return r;
    }

    private String valueStatus(BlueprintResult.EnvVarMapping m, boolean secret, Set<String> suppliedSecrets) {
        if (m.isGeneratable()) return "WILL_BE_GENERATED";
        if (notBlank(m.getDependsOnOutput())) return "FROM_PREVIOUS_STEP";
        if (suppliedSecrets.contains(m.getName())) return "READY";
        if (!secret && notBlank(m.getExpectedFormat())) return "READY";
        return "NEEDS_INPUT";
    }

    private boolean isCorsVar(BlueprintResult.EnvVarMapping m) {
        String n = m.getName() == null ? "" : m.getName().toUpperCase(Locale.ROOT);
        return n.contains("FRONTEND_URL") || n.contains("CORS") || n.contains("ALLOWED_ORIGIN")
            || "FRONTEND_PUBLIC_URL".equals(m.getDependsOnOutput());
    }

    // ---------- database ----------

    private DeploymentActionPlan.DatabaseHandoff buildDatabaseHandoff(BlueprintResult bp,
            BlueprintResult.Component database, Set<String> suppliedSecrets) {
        DeploymentActionPlan.DatabaseHandoff h = new DeploymentActionPlan.DatabaseHandoff();
        h.setRequired(true);
        String platform = notBlank(database.getSelectedPlatform()) ? database.getSelectedPlatform() : database.getName();
        h.setDetectedProvider(platform);
        List<String> fields = new ArrayList<>();
        for (BlueprintResult.EnvVarMapping m : bp.getEnvironmentVariables()) {
            String n = m.getName() == null ? "" : m.getName().toUpperCase(Locale.ROOT);
            if (n.contains("DATABASE_URL") || n.contains("DB_URL") || n.contains("DATABASE_USERNAME")
                || n.contains("DATABASE_PASSWORD") || n.contains("POSTGRES")) {
                fields.add(m.getName());
            }
        }
        if (fields.isEmpty()) fields = List.of("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD");
        h.setRequiredFields(fields);
        h.setConnectionSupplied(fields.stream().anyMatch(suppliedSecrets::contains));
        h.setInstructions("Create or open your " + platform + " database, copy the connection details and add them as "
            + "deployment secrets. DeployPilot tests connectivity only — it never creates, resets or deletes a database "
            + "in this stage.");
        return h;
    }

    // ---------- helpers ----------

    private void resolveRepoState(BlueprintResult bp, ProviderConnection gitHub, Long userId,
                                  String requestedBranch, DeploymentActionPlan plan) {
        String branch = requestedBranch;
        String commit = null;
        if (gitHub != null && notBlank(bp.getRepository())) {
            try {
                ProviderCredential cred = connectionService.requireCredential(userId, ProviderType.GITHUB);
                RepositoryRef ref = RepositoryRef.parse(bp.getRepository());
                var repo = providers.git().getRepository(cred, ref);
                if (branch == null) branch = repo.defaultBranch();
                commit = providers.git().getLatestCommit(cred, ref, branch).sha();
            } catch (Exception e) {
                log.debug("Could not resolve repo state for plan: {}", e.getMessage());
                plan.getWarnings().add("Could not read the latest commit from GitHub; the plan will use the branch tip at execution time.");
            }
        }
        plan.setBranch(branch);
        plan.setCommitSha(commit);
    }

    private List<String> proposedConfigFiles(BlueprintResult bp) {
        List<String> files = new ArrayList<>();
        for (BlueprintResult.FilePreview fp : bp.getFilePreviews()) {
            // Only files that are new or differ from what's in the repo need a PR.
            if (fp.getSuggestedContent() != null && !Boolean.TRUE.equals(fp.getExists())) {
                files.add(fp.getPath());
            } else if (fp.getDiff() != null && !fp.getDiff().isBlank()) {
                files.add(fp.getPath());
            }
        }
        return files;
    }

    private ProviderType providerForPlatform(String platform) {
        if (platform == null) return null;
        return switch (platform.trim().toLowerCase(Locale.ROOT)) {
            case "netlify" -> ProviderType.NETLIFY;
            case "render" -> ProviderType.RENDER;
            default -> null;
        };
    }

    private void requireConnection(Map<ProviderType, ProviderConnection> connections, ProviderType provider,
                                   String component, DeploymentActionPlan plan) {
        if (!connections.containsKey(provider)) {
            plan.getBlockers().add("Connect your " + provider + " account to deploy " + component + ".");
        }
    }

    private String label(Map<ProviderType, ProviderConnection> connections, ProviderType provider) {
        ProviderConnection c = connections.get(provider);
        return c != null ? c.getAccountLabel() : null;
    }

    private boolean hasProviderAction(List<PlannedAction> actions) {
        return actions.stream().anyMatch(a -> !"NONE".equals(a.getProvider()));
    }

    private PlannedAction action(int[] order, String id, ActionType type, String provider, String account,
                                 String component, String title, String description, String targetResource,
                                 boolean createsNew, boolean changesExisting, boolean reversible,
                                 boolean requiresRepoChange, String costNote, List<String> envNames, List<String> dependsOn) {
        PlannedAction a = new PlannedAction();
        a.setId(id);
        a.setOrder(++order[0]);
        a.setType(type.name());
        a.setProvider(provider);
        a.setAccount(account);
        a.setComponent(component);
        a.setTitle(title);
        a.setDescription(description);
        a.setTargetResource(targetResource);
        a.setCreatesNewResource(createsNew);
        a.setChangesExisting(changesExisting);
        a.setReversible(reversible);
        a.setRequiresRepositoryChange(requiresRepoChange);
        a.setCostNote(costNote);
        a.setEnvironmentVariableNames(new ArrayList<>(envNames));
        a.setDependsOn(new ArrayList<>(dependsOn));
        return a;
    }

    private BlueprintResult.Component componentOfType(BlueprintResult bp, String type) {
        return bp.getComponents().stream().filter(c -> type.equals(c.getType())).findFirst().orElse(null);
    }

    private AutomationMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) return AutomationMode.GUIDE_ME;
        try {
            return AutomationMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown automation mode: " + mode);
        }
    }

    /**
     * Canonical fingerprint over the actions, repository/commit, provider accounts
     * and env-var destinations. Any change to what would be executed changes the
     * hash, so a stale confirmation cannot authorise a different plan.
     */
    String hash(DeploymentActionPlan plan, Map<ProviderType, ProviderConnection> connections) {
        StringBuilder sb = new StringBuilder();
        sb.append("mode=").append(plan.getMode()).append('\n');
        sb.append("repo=").append(nullSafe(plan.getRepository())).append('\n');
        sb.append("branch=").append(nullSafe(plan.getBranch())).append('\n');
        sb.append("commit=").append(nullSafe(plan.getCommitSha())).append('\n');
        for (PlannedAction a : plan.getActions()) {
            sb.append("action=").append(a.getId()).append('|').append(a.getType()).append('|')
              .append(a.getProvider()).append('|').append(a.isCreatesNewResource()).append('|')
              .append(nullSafe(a.getTargetResource())).append('|')
              .append(String.join(",", a.getEnvironmentVariableNames())).append('\n');
        }
        // Bind to the exact provider accounts.
        for (ProviderType p : ProviderType.values()) {
            ProviderConnection c = connections.get(p);
            sb.append("account=").append(p).append(':')
              .append(c != null ? nullSafe(c.getExternalAccountId()) + "/" + nullSafe(c.getAccountLabel()) : "none")
              .append('\n');
        }
        for (EnvVarPlanItem e : plan.getEnvironmentVariables()) {
            sb.append("env=").append(e.getName()).append('|').append(e.getDestination()).append('\n');
        }
        return sha256Hex(sb.toString());
    }

    /** Provider account binding string stored on the confirmation. */
    String accountBinding(Map<ProviderType, ProviderConnection> connections) {
        StringBuilder sb = new StringBuilder();
        for (ProviderType p : ProviderType.values()) {
            ProviderConnection c = connections.get(p);
            if (c != null) {
                if (sb.length() > 0) sb.append(';');
                sb.append(p).append('=').append(nullSafe(c.getExternalAccountId()));
            }
        }
        return sb.toString();
    }

    Map<ProviderType, ProviderConnection> connectionsFor(Long userId) {
        Map<ProviderType, ProviderConnection> connections = new EnumMap<>(ProviderType.class);
        for (ProviderType p : ProviderType.values()) {
            connectionService.findConnection(userId, p).ifPresent(c -> connections.put(p, c));
        }
        return connections;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private Project requireOwnedProject(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not your project");
        }
        return project;
    }
}
