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
import com.deploypilot.model.enums.DatabaseChoice;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.model.Project;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderRegistry;
import com.deploypilot.provider.model.MigrationInfo;
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
import java.util.stream.Collectors;

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
    private final MigrationDiscoveryService migrationDiscoveryService;
    private final com.deploypilot.repoaccess.RepositoryFileReaderFactory readerFactory;

    // Backend-only variables filled from the prepared Supabase project. These are
    // set on the backend host (Render) and never on the frontend host (Netlify).
    static final Set<String> DB_BACKEND_VARS = Set.of(
        "DATABASE_URL", "JDBC_DATABASE_URL", "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY",
        "DATABASE_PASSWORD", "DATABASE_USERNAME");

    // Non-secret configuration DeployPilot can supply itself. DeployPilot's only
    // supported AI provider is Gemini, so AI_PROVIDER is set to "gemini"; the
    // model (GEMINI_MODEL) is left to the repository/app default unless overridden.
    public static final Map<String, String> KNOWN_CONFIG_DEFAULTS = Map.of("AI_PROVIDER", "gemini");

    public ActionPlanService(ProjectRepository projectRepository,
                             DeploymentBlueprintService blueprintService,
                             ConnectionService connectionService,
                             AutomationSecretRepository secretRepository,
                             ProviderRegistry providers,
                             MigrationDiscoveryService migrationDiscoveryService,
                             com.deploypilot.repoaccess.RepositoryFileReaderFactory readerFactory) {
        this.projectRepository = projectRepository;
        this.blueprintService = blueprintService;
        this.connectionService = connectionService;
        this.secretRepository = secretRepository;
        this.providers = providers;
        this.migrationDiscoveryService = migrationDiscoveryService;
        this.readerFactory = readerFactory;
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

        // A Supabase-only app (e.g. @supabase/supabase-js with no separate Postgres
        // client) has Supabase as an external service, not a detected DATABASE
        // component. When the user explicitly chose a Supabase database, run the
        // controlled Supabase path anyway using a synthesized database component.
        if (database == null && DatabaseChoice.parse(request.getDatabaseChoice()) != DatabaseChoice.MANUAL) {
            database = synthesizeSupabaseDatabase();
        }

        List<PlannedAction> actions = new ArrayList<>();
        int[] order = {0};

        // 1. Database — controlled Supabase automation, or manual import (default).
        DbPlan dbPlan = new DbPlan(false, null);
        if (database != null) {
            dbPlan = planDatabase(bp, database, request, connections, userId, projectId, plan, order, actions, suppliedSecrets);
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
        EnvRouting routing = routeEnvVars(bp, backend, frontend, suppliedSecrets, plan, dbPlan.supabaseActive());

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

            List<String> backendDeployDeps = new ArrayList<>(List.of(backendEnv.getId()));
            // Backend database variables come from the prepared Supabase project (backend-only secrets).
            if (dbPlan.supabaseActive() && !routing.dbBackendEnvNames.isEmpty()) {
                List<String> dbDeps = new ArrayList<>(List.of(backendEnsure.getId()));
                if (dbPlan.lastDbActionId() != null) dbDeps.add(dbPlan.lastDbActionId());
                PlannedAction dbEnv = action(order, "backend.database-env", ActionType.UPDATE, backendProvider.name(), account,
                    backend.getName(), "Set backend database variables",
                    "Set the backend's database connection variables from the prepared Supabase project. These are "
                        + "backend-only secrets and are never sent to the frontend.",
                    null, false, true, true, false, null, routing.dbBackendEnvNames, dbDeps);
                actions.add(dbEnv);
                backendDeployDeps.add(dbEnv.getId());
            }

            backendDeploy = action(order, "backend.deploy", ActionType.DEPLOY, backendProvider.name(), account,
                backend.getName(), "Deploy backend", "Trigger a backend deployment and wait for it to go live, then capture its URL.",
                null, false, false, true, false, null, List.of(), List.copyOf(backendDeployDeps));
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

        // 6. Verify the database connection (Supabase automation only).
        if (dbPlan.supabaseActive() && dbPlan.lastDbActionId() != null) {
            List<String> deps = new ArrayList<>(List.of(dbPlan.lastDbActionId()));
            if (backendDeploy != null) deps.add(backendDeploy.getId());
            actions.add(action(order, "verify.database", ActionType.READ_ONLY, "NONE", null,
                database != null ? database.getName() : "Database", "Verify database connection",
                "Confirm the Supabase project is active and healthy and that the backend has its database configuration.",
                null, false, false, true, false, null, List.of(), deps));
        }

        // 7. Automatic Stage 3 verification.
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
        final List<String> dbBackendEnvNames = new ArrayList<>();
        String backendCorsVar;
    }

    private EnvRouting routeEnvVars(BlueprintResult bp, BlueprintResult.Component backend,
                                    BlueprintResult.Component frontend, Set<String> suppliedSecrets,
                                    DeploymentActionPlan plan, boolean supabaseActive) {
        EnvRouting r = new EnvRouting();
        String backendId = backend != null ? backend.getId() : null;
        String frontendId = frontend != null ? frontend.getId() : null;

        for (BlueprintResult.EnvVarMapping m : bp.getEnvironmentVariables()) {
            String name = m.getName();
            boolean secret = "SECRET_OR_SENSITIVE".equals(m.getClassification());
            boolean toFrontend = frontendId != null && frontendId.equals(m.getComponentId());
            boolean toBackend = backendId != null && backendId.equals(m.getComponentId());

            EnvVarPlanItem item = new EnvVarPlanItem();
            item.setName(name);
            item.setRequired(Boolean.TRUE.equals(m.getRequired()));
            item.setSecret(secret);
            item.setGeneratable(m.isGeneratable());
            item.setSource(m.getValueSource());

            String status = valueStatus(m, secret, suppliedSecrets);
            // DeployPilot can supply a few well-known non-secret config values itself.
            if ("NEEDS_INPUT".equals(status) && KNOWN_CONFIG_DEFAULTS.containsKey(name.toUpperCase(Locale.ROOT))) {
                status = "READY";
            }
            String destination;
            boolean pushBackend = false, pushFrontend = false, pushDbBackend = false;

            if (isPortVar(name)) {
                // PORT is assigned by the hosting platform and must never be configured.
                destination = "Assigned automatically by the hosting platform (never configured by DeployPilot)";
                status = "MANAGED_BY_PLATFORM";
            } else if (isSupabasePublicFrontendVar(name)) {
                // VITE_/NEXT_PUBLIC_ Supabase URL + anon/publishable key are public → frontend host.
                destination = supabaseActive ? "Frontend site (public Supabase value)" : "Frontend site";
                pushFrontend = frontendId != null;
                if (supabaseActive) status = "FROM_PREVIOUS_STEP";
            } else if (isSupabaseManagedBackendVar(name)) {
                // SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and the DB connection vars are backend-only.
                if (supabaseActive) {
                    destination = "Backend service (from Supabase)";
                    pushDbBackend = backendId != null;
                    status = "FROM_PREVIOUS_STEP";
                } else {
                    destination = "Backend service";
                    pushBackend = backendId != null;
                }
            } else if (toFrontend && secret) {
                // Safety: a secret must never be pushed to the frontend host.
                destination = "Backend service (kept off the frontend)";
                plan.getWarnings().add("Variable " + name + " is sensitive and will not be sent to the frontend host.");
                pushBackend = backendId != null;
            } else if (toFrontend) {
                destination = "Frontend site";
                pushFrontend = true;
            } else if (toBackend) {
                destination = "Backend service";
                pushBackend = true;
            } else {
                destination = "Repository (.env.example)";
            }

            // Env-var hygiene: never include an optional variable that has no value in a
            // provider update. Required-but-missing values still block below.
            boolean hasValue = "READY".equals(status) || "WILL_BE_GENERATED".equals(status)
                || "FROM_PREVIOUS_STEP".equals(status);
            boolean willSet = item.isRequired() || hasValue;
            if (!willSet && (pushBackend || pushFrontend || pushDbBackend)) {
                destination = destination + " — skipped (optional, no value provided)";
                pushBackend = pushFrontend = pushDbBackend = false;
            }
            if (pushDbBackend) r.dbBackendEnvNames.add(name);
            if (pushBackend) r.backendEnvNames.add(name);
            if (pushFrontend) r.frontendEnvNames.add(name);

            item.setDestination(destination);
            item.setValueStatus(status);
            r.items.add(item);

            if (isCorsVar(m) && toBackend) r.backendCorsVar = name;

            if (item.isRequired() && "NEEDS_INPUT".equals(status)) {
                plan.getBlockers().add("Provide a value for required variable " + name + " (" + destination + ").");
            }
        }
        return r;
    }

    private boolean isPortVar(String name) {
        return name != null && name.equalsIgnoreCase("PORT");
    }

    /** Public Supabase browser-client variables: VITE_/NEXT_PUBLIC_ URL + anon key (never service-role). */
    private boolean isSupabasePublicFrontendVar(String name) {
        if (name == null) return false;
        String n = name.toUpperCase(Locale.ROOT);
        boolean publicPrefix = n.startsWith("VITE_SUPABASE_") || n.startsWith("NEXT_PUBLIC_SUPABASE_");
        return publicPrefix && !n.contains("SERVICE_ROLE");
    }

    /** Backend-only variables filled from the managed Supabase project (never public/VITE_). */
    private boolean isSupabaseManagedBackendVar(String name) {
        if (name == null) return false;
        String n = name.toUpperCase(Locale.ROOT);
        if (n.startsWith("VITE_") || n.startsWith("NEXT_PUBLIC_")) return false;
        return DB_BACKEND_VARS.contains(n);
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

    /** Result of planning the database part: whether Supabase automation is active and its final action id. */
    private record DbPlan(boolean supabaseActive, String lastDbActionId) {}

    private DbPlan planDatabase(BlueprintResult bp, BlueprintResult.Component database, PlanRequest request,
                                Map<ProviderType, ProviderConnection> connections, Long userId, Long projectId,
                                DeploymentActionPlan plan, int[] order, List<PlannedAction> actions, Set<String> suppliedSecrets) {
        DatabaseChoice choice = DatabaseChoice.parse(request.getDatabaseChoice());
        boolean supabaseConnected = connections.containsKey(ProviderType.SUPABASE);
        String platform = notBlank(database.getSelectedPlatform()) ? database.getSelectedPlatform() : database.getName();

        DeploymentActionPlan.DatabaseHandoff h = new DeploymentActionPlan.DatabaseHandoff();
        h.setRequired(true);
        h.setDetectedProvider(platform);
        h.setChoice(choice.name());
        h.setSupabaseConnected(supabaseConnected);
        h.setSupabaseOrgId(request.getSupabaseOrgId());
        h.setSupabaseProjectRef(request.getSupabaseProjectRef());
        h.setSupabaseProjectName(request.getSupabaseProjectName());
        h.setSupabaseRegion(request.getSupabaseRegion());
        h.setApplyMigrations(request.isApplyMigrations());
        List<String> fields = dbFields(bp);
        h.setRequiredFields(fields);
        h.setConnectionSupplied(fields.stream().anyMatch(suppliedSecrets::contains));

        // Supabase-managed actions and routing appear whenever the user chose a
        // Supabase project (create/existing) and connected their account — the plan
        // must show the creation actions and route the managed variables regardless
        // of mode. Whether the plan can actually execute is gated separately by mode.
        boolean supabaseAutomation = choice != DatabaseChoice.MANUAL && supabaseConnected;

        if (!supabaseAutomation) {
            if (choice != DatabaseChoice.MANUAL && !supabaseConnected) {
                plan.getBlockers().add("Connect your Supabase account to let DeployPilot prepare the database, or choose manual import.");
            }
            h.setInstructions("Create or open your " + platform + " database, copy the connection details and add them as "
                + "deployment secrets. In manual mode DeployPilot tests connectivity only — it never creates, resets or deletes a database.");
            plan.setDatabase(h);
            actions.add(action(order, "database.confirm", ActionType.READ_ONLY, "NONE", null, database.getName(),
                "Confirm database connection",
                "Verify the imported database connection is reachable. In manual mode DeployPilot never creates, resets or deletes a database.",
                null, false, false, true, false, null, List.of(), List.of()));
            if (h.isRequired() && !h.isConnectionSupplied()) {
                plan.getBlockers().add("A database is required but no connection has been supplied. "
                    + "Add the connection fields (or choose Supabase automation), then regenerate the plan.");
            }
            return new DbPlan(false, null);
        }

        // ----- controlled Supabase automation -----
        String account = label(connections, ProviderType.SUPABASE);
        String lastDbActionId;
        String refForMigrations = null;

        if (choice == DatabaseChoice.CREATE_SUPABASE_PROJECT) {
            if (!notBlank(request.getSupabaseOrgId())) plan.getBlockers().add("Select a Supabase organization to create the project in.");
            if (!notBlank(request.getSupabaseProjectName())) plan.getBlockers().add("Choose a name for the new Supabase project.");
            h.setInstructions("DeployPilot will create a new free Supabase project in the selected organization and region, "
                + "wait until it is ACTIVE_HEALTHY, then retrieve its URL, anon/publishable key and service-role key through the "
                + "Supabase Management API and store them encrypted. It never selects a paid plan and never asks you for the "
                + "service-role key. Repository schema is applied only after your explicit confirmation.");
            PlannedAction create = action(order, "database.create", ActionType.CREATE, "SUPABASE", account, database.getName(),
                "Create free Supabase project",
                "Create a new free Supabase project '" + safeName(request.getSupabaseProjectName()) + "'"
                    + (notBlank(request.getSupabaseRegion()) ? " in region " + safeName(request.getSupabaseRegion()) : "")
                    + " in the selected organization.",
                null, true, false, false, false, "Free plan (no cost). DeployPilot never selects a paid Supabase plan.", List.of(), List.of());
            actions.add(create);
            PlannedAction wait = action(order, "database.wait", ActionType.READ_ONLY, "SUPABASE", account, database.getName(),
                "Wait until the project is ACTIVE_HEALTHY",
                "Wait until the new Supabase project reaches ACTIVE_HEALTHY before using it.",
                null, false, false, true, false, null, List.of(), List.of(create.getId()));
            actions.add(wait);
            lastDbActionId = wait.getId();
        } else {
            if (!notBlank(request.getSupabaseProjectRef())) plan.getBlockers().add("Select an existing Supabase project to use.");
            refForMigrations = request.getSupabaseProjectRef();
            h.setInstructions("DeployPilot will use your existing Supabase project, read its credentials through the Management "
                + "API and store them encrypted, then apply approved, safe repository schema only after your confirmation.");
            PlannedAction inspect = action(order, "database.inspect", ActionType.READ_ONLY, "SUPABASE", account, database.getName(),
                "Inspect Supabase project", "Read the selected Supabase project's status and details (read-only).",
                request.getSupabaseProjectRef(), false, false, true, false, null, List.of(), List.of());
            actions.add(inspect);
            lastDbActionId = inspect.getId();
        }

        // Credentials are retrieved and stored encrypted BEFORE any schema is applied.
        PlannedAction creds = action(order, "database.credentials", ActionType.READ_ONLY, "SUPABASE", account, database.getName(),
            "Retrieve and store database credentials",
            "Retrieve the project URL, anon/publishable key and service-role key through the Supabase Management API and store "
                + "them encrypted. The service-role key and database password stay backend-only and are never exposed to the browser.",
            null, false, false, true, false, null, List.of(), List.of(lastDbActionId));
        actions.add(creds);
        lastDbActionId = creds.getId();

        // Repository-owned schema/migrations are always detected and shown (checksum +
        // SQL-safety). Applying requires explicit confirmation; destructive SQL is blocked.
        List<MigrationInfo> migrations = discoverMigrations(bp, plan.getBranch(), projectId, refForMigrations, userId);
        migrations.forEach(mi -> h.getMigrations().add(new DeploymentActionPlan.DatabaseHandoff.MigrationView(
            mi.name(), mi.checksum(), mi.order(), mi.previouslyApplied(), mi.destructive(), mi.safetyClassification(), mi.reason())));
        boolean anyDestructive = migrations.stream().anyMatch(MigrationInfo::destructive);
        boolean anyPending = migrations.stream().anyMatch(mi -> !mi.previouslyApplied() && !mi.destructive());
        if (!migrations.isEmpty()) {
            PlannedAction inspectMig = action(order, "database.migrations.inspect", ActionType.READ_ONLY, "NONE", null, database.getName(),
                "Review repository database schema",
                "Review " + migrations.size() + " repository-owned schema/migration file(s), their order, checksums and SQL-safety result.",
                null, false, false, true, false, null, List.of(), List.of(lastDbActionId));
            actions.add(inspectMig);
            lastDbActionId = inspectMig.getId();
        }
        if (anyDestructive) {
            String names = migrations.stream().filter(MigrationInfo::destructive).map(MigrationInfo::name)
                .collect(Collectors.joining(", "));
            plan.getBlockers().add("Potentially destructive SQL detected (" + names + "). DeployPilot will not apply it "
                + "automatically — review it with a database expert outside DeployPilot.");
        } else if (anyPending && request.isApplyMigrations()) {
            PlannedAction applyMig = action(order, "database.migrations.apply", ActionType.UPDATE, "SUPABASE", account, database.getName(),
                "Apply repository schema after confirmation",
                "Apply the safe, not-yet-applied repository schema/migrations in order after your confirmation. Checksums prevent "
                    + "re-applying completed ones.",
                null, false, true, false, false, null, List.of(), List.of(lastDbActionId));
            actions.add(applyMig);
            lastDbActionId = applyMig.getId();
        } else if (anyPending) {
            plan.getWarnings().add("Repository database schema detected but not selected for automatic apply. Enable "
                + "\"apply schema/migrations\" and confirm the plan to apply it — DeployPilot never changes your database schema "
                + "without explicit confirmation.");
        }

        plan.setDatabase(h);
        return new DbPlan(true, lastDbActionId);
    }

    private List<String> dbFields(BlueprintResult bp) {
        List<String> fields = new ArrayList<>();
        for (BlueprintResult.EnvVarMapping m : bp.getEnvironmentVariables()) {
            String n = m.getName() == null ? "" : m.getName().toUpperCase(Locale.ROOT);
            if (n.contains("DATABASE_URL") || n.contains("DB_URL") || n.contains("DATABASE_USERNAME")
                || n.contains("DATABASE_PASSWORD") || n.contains("POSTGRES")) {
                fields.add(m.getName());
            }
        }
        return fields.isEmpty() ? List.of("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD") : fields;
    }

    private List<MigrationInfo> discoverMigrations(BlueprintResult bp, String branch, Long projectId,
                                                   String supabaseRef, Long userId) {
        try {
            RepositoryRef ref = RepositoryRef.parse(bp.getRepository());
            String b = notBlank(branch) ? branch : "main";
            // Read the repository with the connected user's GitHub credential, not
            // only the server-level token, so private repositories are reachable.
            ProviderCredential gitHubCredential = connectionService.findConnection(userId, ProviderType.GITHUB)
                .map(c -> safeGitHubCredential(userId)).orElse(null);
            var reader = readerFactory.forCredentialOrDefault(gitHubCredential);
            return migrationDiscoveryService.discover(ref, b, projectId, supabaseRef, reader);
        } catch (Exception e) {
            log.debug("Migration discovery skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private ProviderCredential safeGitHubCredential(Long userId) {
        try {
            return connectionService.requireCredential(userId, ProviderType.GITHUB);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeName(String name) {
        return name == null ? "" : name.replaceAll("[^A-Za-z0-9 _.-]", "");
    }

    private String shortReason(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) return e.getClass().getSimpleName();
        return m.length() > 100 ? m.substring(0, 100) : m;
    }

    /** A stand-in DATABASE component for a Supabase-only app that has no detected Postgres component. */
    private BlueprintResult.Component synthesizeSupabaseDatabase() {
        BlueprintResult.Component c = new BlueprintResult.Component();
        c.setId("database@supabase");
        c.setType("DATABASE");
        c.setName("Supabase");
        c.setSelectedPlatform("Supabase");
        return c;
    }

    // ---------- helpers ----------

    private void resolveRepoState(BlueprintResult bp, ProviderConnection gitHub, Long userId,
                                  String requestedBranch, DeploymentActionPlan plan) {
        String branch = requestedBranch;
        String commit = null;
        if (gitHub != null && notBlank(bp.getRepository())) {
            ProviderCredential cred = safeGitHubCredential(userId);
            RepositoryRef ref = null;
            try {
                ref = RepositoryRef.parse(bp.getRepository());
            } catch (Exception e) {
                log.warn("Unparseable repository '{}' for plan: {}", bp.getRepository(), e.getMessage());
            }
            if (cred != null && ref != null) {
                // Resolve the default branch separately: reading repository metadata
                // can fail (or need extra scopes) without blocking commit resolution.
                if (branch == null) {
                    try {
                        branch = providers.git().getRepository(cred, ref).defaultBranch();
                    } catch (Exception e) {
                        log.warn("Could not read repository metadata for {}: {} — defaulting branch to main",
                            ref.fullName(), e.toString());
                        branch = "main";
                    }
                }
                try {
                    commit = providers.git().getLatestCommit(cred, ref, branch).sha();
                } catch (Exception e) {
                    log.warn("Could not resolve latest commit for {} on branch {}: {}",
                        ref.fullName(), branch, e.toString());
                    plan.getWarnings().add("Could not read the latest commit from GitHub (" + shortReason(e)
                        + "); the plan will use the branch tip at execution time.");
                }
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
        // Bind the database choices and migration identities into the hash so any
        // change (choice, org/project, region, migrations) requires re-confirmation.
        DeploymentActionPlan.DatabaseHandoff db = plan.getDatabase();
        if (db != null) {
            sb.append("db=").append(nullSafe(db.getChoice())).append('|').append(nullSafe(db.getSupabaseOrgId()))
              .append('|').append(nullSafe(db.getSupabaseProjectRef())).append('|').append(nullSafe(db.getSupabaseProjectName()))
              .append('|').append(nullSafe(db.getSupabaseRegion())).append('|').append(db.isApplyMigrations()).append('\n');
            for (DeploymentActionPlan.DatabaseHandoff.MigrationView mv : db.getMigrations()) {
                sb.append("mig=").append(mv.name()).append('|').append(mv.checksum()).append('|').append(mv.destructive()).append('\n');
            }
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
