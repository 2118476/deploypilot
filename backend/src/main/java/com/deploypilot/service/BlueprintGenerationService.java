package com.deploypilot.service;

import com.deploypilot.dto.BlueprintResult;
import com.deploypilot.dto.BlueprintResult.Component;
import com.deploypilot.dto.BlueprintResult.EnvVarMapping;
import com.deploypilot.dto.BlueprintResult.FilePreview;
import com.deploypilot.dto.BlueprintResult.Finding;
import com.deploypilot.dto.BlueprintResult.PlatformOption;
import com.deploypilot.dto.BlueprintResult.Relationship;
import com.deploypilot.dto.BlueprintResult.Step;
import com.deploypilot.dto.StackDetectionResult;
import com.deploypilot.dto.StackDetectionResult.Detection;
import com.deploypilot.dto.StackDetectionResult.EnvVarFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic blueprint rules. Consumes a stored StackDetectionResult and
 * produces platform recommendations, an environment-variable map, findings,
 * dependency-aware deployment steps and configuration-file previews.
 *
 * No AI is involved and no network access happens here; the optional
 * currentFileContents map (used for diffs) is supplied by the orchestrator.
 */
@Service
public class BlueprintGenerationService {

    public static final String RULES_VERSION = "1.0";
    private static final String PRICING_REMINDER = "Provider pricing and limits can change; check the provider's current terms.";

    private static final Pattern PUBLIC_PREFIX = Pattern.compile("^(VITE_|NEXT_PUBLIC_|REACT_APP_|PUBLIC_|EXPO_PUBLIC_)");
    private static final Pattern API_URL_NAME = Pattern.compile("(?i)(api|backend).*(url|base)|_(api)$");
    private static final Pattern CORS_NAME = Pattern.compile("(?i)^(frontend_url|cors_origin|allowed_origins?|client_url)$");
    private static final Pattern DB_VAR_NAME = Pattern.compile("(?i)^(database_|db_|postgres_|mongo(db)?_)");
    private static final Pattern GENERATABLE_SECRET = Pattern.compile("(?i)^(jwt_secret|session_secret|secret_key|app_secret|auth_secret)$");
    // Intentionally public/publishable credentials (Supabase anon key, publishable keys) — never secrets.
    private static final Pattern PUBLISHABLE_CREDENTIAL = Pattern.compile(
        "(?i)(anon_key|anon_public_key|publishable_key|publishable_default_key)$");

    public BlueprintResult generate(StackDetectionResult analysis,
                                    Map<String, String> overrides,
                                    Map<String, String> currentFileContents) {
        Map<String, String> ov = overrides == null ? Map.of() : overrides;
        Map<String, String> files = currentFileContents == null ? Map.of() : currentFileContents;

        BlueprintResult bp = new BlueprintResult();
        bp.setRepository(analysis.getRepository());
        bp.setStructure(analysis.getStructure());
        bp.setRulesVersion(RULES_VERSION);

        boolean hasNetlifyConfig = hasDetection(analysis, "HOSTING", "Netlify");
        boolean hasVercelConfig = hasDetection(analysis, "HOSTING", "Vercel");
        boolean hasRenderConfig = hasDetection(analysis, "HOSTING", "Render");
        boolean hasDocker = !detections(analysis, "CONTAINER").isEmpty();
        boolean hasFlyway = hasDetection(analysis, "EXTERNAL_SERVICE", "Flyway migrations");

        // ---------- components ----------
        List<Component> frontends = buildFrontends(analysis, hasNetlifyConfig, hasVercelConfig);
        List<Component> backends = buildBackends(analysis, hasDocker, hasRenderConfig);
        List<Component> databases = buildDatabases(analysis);
        bp.getComponents().addAll(frontends);
        bp.getComponents().addAll(backends);
        bp.getComponents().addAll(databases);

        // apply user overrides (validated by the orchestrator)
        for (Component c : bp.getComponents()) {
            String chosen = ov.get(c.getId());
            c.setSelectedPlatform(chosen != null ? chosen : c.getRecommendedPlatform().getPlatform());
        }

        Component frontend = frontends.isEmpty() ? null : frontends.get(0);
        Component backend = backends.isEmpty() ? null : backends.get(0);
        Component database = databases.isEmpty() ? null : databases.get(0);

        // ---------- environment variables ----------
        mapEnvironmentVariables(analysis, bp, frontend, backend, database);

        EnvVarMapping apiUrlVar = bp.getEnvironmentVariables().stream()
            .filter(v -> "BACKEND_PUBLIC_URL".equals(v.getDependsOnOutput())).findFirst().orElse(null);
        EnvVarMapping corsVar = bp.getEnvironmentVariables().stream()
            .filter(v -> "FRONTEND_PUBLIC_URL".equals(v.getDependsOnOutput())).findFirst().orElse(null);

        // ---------- relationships ----------
        buildRelationships(bp, frontend, backend, database, apiUrlVar, corsVar, analysis);

        // ---------- findings ----------
        buildFindings(analysis, bp, frontends, backends, database, apiUrlVar,
            hasNetlifyConfig, hasVercelConfig, hasDocker, hasFlyway);

        // ---------- steps ----------
        buildSteps(bp, frontend, backend, database, apiUrlVar, corsVar);

        // ---------- file previews ----------
        buildFilePreviews(analysis, bp, frontend, backend, hasDocker, files);

        return bp;
    }

    /** Platforms a user may select for a component: the recommendation plus its alternatives. */
    public List<String> allowedPlatforms(Component component) {
        List<String> allowed = new ArrayList<>();
        allowed.add(component.getRecommendedPlatform().getPlatform());
        component.getAlternatives().forEach(a -> allowed.add(a.getPlatform()));
        return allowed;
    }

    // ==================== components ====================

    private List<Component> buildFrontends(StackDetectionResult analysis, boolean netlifyCfg, boolean vercelCfg) {
        List<Component> result = new ArrayList<>();
        for (Detection d : detections(analysis, "FRONTEND_FRAMEWORK")) {
            Component c = new Component();
            c.setId("frontend@" + (d.getPath().isEmpty() ? "root" : d.getPath()));
            c.setType("FRONTEND");
            c.setName(d.getName());
            c.setPath(d.getPath());
            c.setRootDirectory(d.getPath());
            c.setBuildTool(detectionNameAt(analysis, "BUILD_TOOL", d.getPath()));
            c.setRuntime(detectionNameAt(analysis, "LANGUAGE", d.getPath()));
            c.setBuildCommand(commandFor(analysis.getBuildCommands(), d.getPath()));
            c.setStartCommand(commandFor(analysis.getStartCommands(), d.getPath()));
            c.setPublishDirectory(publishDirFor(d.getName(), c.getBuildTool(), c));

            boolean isNext = "Next.js".equals(d.getName());
            PlatformOption netlify = platform("Netlify",
                isNext ? "Supports Next.js through its runtime adapter" : "Simple static hosting for built single-page apps",
                d.getConfidence(), d.getEvidence(),
                "Free tier commonly available for personal projects",
                null,
                "Larger teams or heavy usage may eventually need a paid plan. " + PRICING_REMINDER);
            PlatformOption vercel = platform("Vercel",
                isNext ? "Native Next.js support including server-side rendering" : "Static and SPA hosting with preview deploys",
                d.getConfidence(), d.getEvidence(),
                "Hobby tier commonly available for personal projects",
                null,
                "Commercial use requires a paid plan. " + PRICING_REMINDER);

            PlatformOption primary;
            PlatformOption alt;
            if (isNext) {
                primary = vercel; alt = netlify;
                alt.getEvidence().add("Verify SSR/ISR feature support before choosing Netlify for Next.js");
            } else if (vercelCfg && !netlifyCfg) {
                primary = vercel; alt = netlify;
                primary.getEvidence().add("vercel.json already present in the repository");
            } else {
                primary = netlify; alt = vercel;
                if (netlifyCfg) primary.getEvidence().add("netlify.toml already present in the repository");
            }
            primary.setRequiresConfirmation(!"HIGH".equals(d.getConfidence()));
            c.setRecommendedPlatform(primary);
            c.getAlternatives().add(alt);
            result.add(c);
        }
        return result;
    }

    private List<Component> buildBackends(StackDetectionResult analysis, boolean hasDocker, boolean renderCfg) {
        List<Component> result = new ArrayList<>();
        for (Detection d : detections(analysis, "BACKEND_FRAMEWORK")) {
            Component c = new Component();
            c.setId("backend@" + (d.getPath().isEmpty() ? "root" : d.getPath()));
            c.setType("BACKEND");
            c.setName(d.getName());
            c.setPath(d.getPath());
            c.setRootDirectory(d.getPath());
            c.setBuildTool(detectionNameAt(analysis, "BUILD_TOOL", d.getPath()));
            c.setRuntime(detectionNameAt(analysis, "LANGUAGE", d.getPath()));
            c.setBuildCommand(commandFor(analysis.getBuildCommands(), d.getPath()));
            c.setStartCommand(commandFor(analysis.getStartCommands(), d.getPath()));
            String detectedHealthPath = detections(analysis, "HEALTH_ENDPOINT").stream()
                .map(Detection::getName).findFirst().orElse(null);
            c.setHealthCheckPath(detectedHealthPath);
            if (detectedHealthPath != null) {
                c.getNotes().add("Detected health route " + detectedHealthPath
                    + " — set this as the hosting platform's health-check path.");
            } else {
                c.getNotes().add("Configure a health-check path on the hosting platform once you know your API's health endpoint.");
            }
            if (hasDocker) {
                c.getNotes().add("A Dockerfile was detected; deploying as a Docker service is recommended.");
            }

            PlatformOption render = platform("Render",
                hasDocker ? "Runs the detected Dockerfile directly as a web service"
                          : "Straightforward web-service hosting for " + d.getName(),
                d.getConfidence(), d.getEvidence(),
                "Free web-service tier commonly available",
                "Free instances sleep after inactivity; the first request afterwards can take up to a minute",
                "Production workloads may eventually need a paid instance. " + PRICING_REMINDER);
            if (renderCfg) render.getEvidence().add("render.yaml already present in the repository");
            PlatformOption railway = platform("Railway",
                "Simple deploys from GitHub with usage-based pricing",
                d.getConfidence(), d.getEvidence(),
                "Trial credit commonly available; ongoing usage is metered",
                "Instances may sleep on low-cost plans",
                "Usage beyond the trial requires a paid plan. " + PRICING_REMINDER);

            render.setRequiresConfirmation(!"HIGH".equals(d.getConfidence()));
            c.setRecommendedPlatform(render);
            c.getAlternatives().add(railway);
            result.add(c);
        }
        return result;
    }

    private List<Component> buildDatabases(StackDetectionResult analysis) {
        List<Component> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Detection d : detections(analysis, "DATABASE")) {
            if (!seen.add(d.getName())) continue;
            Component c = new Component();
            c.setId("database@" + d.getName().toLowerCase().replace(' ', '-'));
            c.setType("DATABASE");
            c.setName(d.getName());
            c.setPath(null);

            PlatformOption primary;
            List<PlatformOption> alts = new ArrayList<>();
            switch (d.getName()) {
                case "PostgreSQL" -> {
                    primary = platform("Supabase PostgreSQL",
                        "Managed PostgreSQL with a usable free tier",
                        d.getConfidence(), d.getEvidence(),
                        "Free tier commonly available",
                        "Free-tier projects may pause after prolonged inactivity",
                        "Growing data or traffic may need a paid plan. " + PRICING_REMINDER);
                    primary.getEvidence().add("When the backend host is IPv4-only (e.g. Render), use Supabase's session pooler connection");
                    alts.add(platform("Render PostgreSQL", "Same platform as the backend keeps things in one dashboard",
                        d.getConfidence(), d.getEvidence(),
                        "Free instance commonly available with a limited lifetime",
                        null, "Long-term use requires a paid instance. " + PRICING_REMINDER));
                    alts.add(platform("Existing PostgreSQL provider", "Reuse a database you already operate",
                        d.getConfidence(), d.getEvidence(), null, null, PRICING_REMINDER));
                }
                case "MongoDB" -> {
                    primary = platform("MongoDB Atlas", "Managed MongoDB with a free shared cluster",
                        d.getConfidence(), d.getEvidence(),
                        "Free shared cluster commonly available", null,
                        "Dedicated clusters are paid. " + PRICING_REMINDER);
                    alts.add(platform("Existing MongoDB provider", "Reuse a database you already operate",
                        d.getConfidence(), d.getEvidence(), null, null, PRICING_REMINDER));
                }
                case "Firestore" -> primary = platform("Firebase", "Firestore is managed by Firebase",
                    d.getConfidence(), d.getEvidence(), "Spark plan commonly available", null, PRICING_REMINDER);
                default -> {
                    primary = platform("Existing " + d.getName() + " provider",
                        "Reuse a database you already operate",
                        d.getConfidence(), d.getEvidence(), null, null, PRICING_REMINDER);
                }
            }
            primary.setRequiresConfirmation(!"HIGH".equals(d.getConfidence()));
            c.setRecommendedPlatform(primary);
            c.setAlternatives(alts);
            result.add(c);
        }
        return result;
    }

    // ==================== environment variables ====================

    /** Publishable/anon credentials are always public, regardless of a stale stored classification. */
    private String effectiveClassification(String name, String stored) {
        if (name != null && PUBLISHABLE_CREDENTIAL.matcher(name).find()) {
            return "PUBLIC_PUBLISHABLE_CREDENTIAL";
        }
        return stored;
    }

    private void mapEnvironmentVariables(StackDetectionResult analysis, BlueprintResult bp,
                                         Component frontend, Component backend, Component database) {
        Map<String, EnvVarFinding> unique = new LinkedHashMap<>();
        for (EnvVarFinding v : analysis.getEnvironmentVariables()) {
            unique.putIfAbsent(v.getName(), v);
        }
        for (EnvVarFinding v : unique.values()) {
            EnvVarMapping m = new EnvVarMapping();
            m.setName(v.getName());
            // Re-derive publishable/anon credentials by name so a blueprint generated
            // from an older analysis (which may have marked the anon key as a secret)
            // is self-correcting: the anon key is public, never a secret blocker.
            m.setClassification(effectiveClassification(v.getName(), v.getClassification()));
            m.setSourceEvidence(v.getSource());

            Component owner = ownerOf(v, frontend, backend);
            m.setComponentId(owner != null ? owner.getId() : null);
            m.setTargetPlatform(owner != null ? owner.getSelectedPlatform() : null);

            applyValueRules(m, v.getName(), backend, database, frontend);
            bp.getEnvironmentVariables().add(m);
        }
    }

    private Component ownerOf(EnvVarFinding v, Component frontend, Component backend) {
        String source = v.getSource() == null ? "" : v.getSource();
        if (frontend != null && !frontend.getPath().isEmpty() && source.startsWith(frontend.getPath() + "/")) return frontend;
        if (backend != null && !backend.getPath().isEmpty() && source.startsWith(backend.getPath() + "/")) return backend;
        // Non-public Supabase vars (SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY) are
        // backend-only; the VITE_/NEXT_PUBLIC_ variants belong to the frontend.
        boolean isSupabase = v.getName().toUpperCase().contains("SUPABASE");
        boolean isPublic = PUBLIC_PREFIX.matcher(v.getName()).find();
        if (isSupabase && !isPublic && backend != null) return backend;
        if (isPublic && frontend != null) return frontend;
        if (source.contains("application.yml") || source.contains("application.properties")) return backend;
        return backend != null ? backend : frontend;
    }

    private void applyValueRules(EnvVarMapping m, String name, Component backend, Component database, Component frontend) {
        String dbPlatform = database != null ? database.getSelectedPlatform() : "your database provider";
        boolean springBackend = backend != null && backend.getName().contains("Spring");

        if (name.matches("(?i)^database_url$")) {
            m.setValueSource(dbPlatform + " connection details");
            m.setRequired(database != null ? Boolean.TRUE : null);
            m.setExpectedFormat(springBackend
                ? "jdbc:postgresql://<host>:5432/<database>"
                : "Connection URL from " + dbPlatform);
            m.setDependsOnOutput("DATABASE_CONNECTION_INFO");
        } else if (DB_VAR_NAME.matcher(name).find()) {
            m.setValueSource(dbPlatform + " connection details");
            m.setRequired(database != null ? Boolean.TRUE : null);
            m.setExpectedFormat("Value from " + dbPlatform);
            m.setDependsOnOutput("DATABASE_CONNECTION_INFO");
        } else if (GENERATABLE_SECRET.matcher(name).find()) {
            m.setValueSource("Securely generated — DeployPilot can generate one in your browser when you ask");
            m.setExpectedFormat("Random string, at least 32 characters");
            m.setGeneratable(true);
            m.setRequired(Boolean.TRUE);
        } else if (name.matches("(?i)^gemini_api_key$")) {
            m.setValueSource("Google AI Studio (aistudio.google.com) — issued by Google, cannot be generated by DeployPilot");
            m.setExpectedFormat("API key issued by Google");
        } else if (CORS_NAME.matcher(name).find()) {
            m.setValueSource("Frontend production URL — available after the frontend deploy");
            m.setExpectedFormat("${FRONTEND_PUBLIC_URL}");
            m.setDependsOnOutput("FRONTEND_PUBLIC_URL");
        } else if (PUBLIC_PREFIX.matcher(name).find() && API_URL_NAME.matcher(name).find()) {
            m.setValueSource("Backend public URL — available after the backend deploy");
            m.setExpectedFormat("${BACKEND_PUBLIC_URL}<api base path, e.g. /api>");
            m.setDependsOnOutput("BACKEND_PUBLIC_URL");
        } else if (name.matches("(?i)^port$")) {
            m.setValueSource("Set automatically by the hosting platform — do not configure manually");
            m.setRequired(Boolean.FALSE);
        } else if (name.matches("(?i)^spring_profiles_active$")) {
            m.setValueSource("Static value you choose, e.g. prod");
            m.setExpectedFormat("prod");
            m.setRequired(springBackend ? Boolean.TRUE : null);
        } else if (name.toUpperCase().contains("SUPABASE")) {
            // Frontend uses VITE_SUPABASE_URL + VITE_SUPABASE_ANON_KEY (public);
            // backend uses SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY (secret).
            if ("PUBLIC_PUBLISHABLE_CREDENTIAL".equals(m.getClassification())) {
                m.setValueSource("Supabase publishable anon key (public, safe in the browser when Row Level Security "
                    + "is enabled) — Supabase dashboard -> Project Settings -> API");
                m.setExpectedFormat("Publishable anon key from Supabase");
            } else if (name.matches("(?i).*service_role.*")) {
                m.setValueSource("Supabase service role key (secret, backend-only — never expose to the browser) — "
                    + "Supabase dashboard -> Project Settings -> API");
                m.setExpectedFormat("Service role key from Supabase");
                m.setRequired(Boolean.TRUE);
            } else if (name.matches("(?i).*supabase_url$")) {
                boolean isPublic = PUBLIC_PREFIX.matcher(name).find();
                m.setValueSource((isPublic ? "Supabase project URL (public)" : "Supabase project URL")
                    + " — Supabase dashboard -> Project Settings -> API");
                m.setExpectedFormat("https://<project-ref>.supabase.co");
            } else {
                m.setValueSource("Supabase project settings");
            }
        } else if (name.matches("(?i)^firebase_")) {
            m.setValueSource("Firebase console");
        } else if (name.matches("(?i)(api_key|token|secret|password)")) {
            m.setValueSource("Issued by the external provider — set manually, never committed");
        } else if (name.matches("(?i).*app_name$")) {
            m.setValueSource("Your choice (display name)");
        } else {
            m.setValueSource("Set manually");
        }
    }

    // ==================== relationships ====================

    private void buildRelationships(BlueprintResult bp, Component frontend, Component backend,
                                    Component database, EnvVarMapping apiUrlVar, EnvVarMapping corsVar,
                                    StackDetectionResult analysis) {
        if (frontend != null && backend != null) {
            bp.getRelationships().add(new Relationship(frontend.getId(), backend.getId(),
                apiUrlVar != null
                    ? "Calls the backend API using " + apiUrlVar.getName()
                    : "Calls the backend API (no API-URL variable was detected)",
                apiUrlVar != null ? apiUrlVar.getName() : null));
        }
        if (backend != null && database != null) {
            String dbVar = bp.getEnvironmentVariables().stream()
                .filter(v -> "DATABASE_CONNECTION_INFO".equals(v.getDependsOnOutput()))
                .map(EnvVarMapping::getName).findFirst().orElse(null);
            bp.getRelationships().add(new Relationship(backend.getId(), database.getId(),
                "Connects to " + database.getName() + (dbVar != null ? " using " + dbVar : "")
                    + ". The database must exist before the backend can start successfully.",
                dbVar));
        }
        if (backend != null && corsVar != null && frontend != null) {
            bp.getRelationships().add(new Relationship(backend.getId(), frontend.getId(),
                "Allows the frontend origin (CORS) through " + corsVar.getName(), corsVar.getName()));
        }
        for (Detection svc : detections(analysis, "EXTERNAL_SERVICE")) {
            if ("Flyway migrations".equals(svc.getName())) continue;
            String key = svc.getName().toUpperCase().split(" ")[0];
            String serviceId = "service@" + svc.getName().toLowerCase().replace(' ', '-');
            // Backend uses only non-public vars for this service (e.g. SUPABASE_URL,
            // SUPABASE_SERVICE_ROLE_KEY) — never a public VITE_/NEXT_PUBLIC_ variable.
            if (backend != null) {
                String backendVar = bp.getEnvironmentVariables().stream()
                    .map(EnvVarMapping::getName)
                    .filter(n -> n.toUpperCase().contains(key))
                    .filter(n -> !PUBLIC_PREFIX.matcher(n).find())
                    .findFirst().orElse(null);
                bp.getRelationships().add(new Relationship(backend.getId(), serviceId,
                    "Uses " + svc.getName() + (backendVar != null ? " via " + backendVar : ""), backendVar));
            }
            // Supabase also has a browser client, wired with the public frontend vars.
            if (frontend != null && "Supabase".equals(svc.getName())) {
                String frontendVar = bp.getEnvironmentVariables().stream()
                    .map(EnvVarMapping::getName)
                    .filter(n -> n.toUpperCase().contains("SUPABASE"))
                    .filter(n -> PUBLIC_PREFIX.matcher(n).find())
                    .findFirst().orElse(null);
                if (frontendVar != null) {
                    bp.getRelationships().add(new Relationship(frontend.getId(), serviceId,
                        "Frontend uses the Supabase browser client via public "
                            + "VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY", frontendVar));
                }
            }
        }
    }

    // ==================== findings ====================

    private void buildFindings(StackDetectionResult analysis, BlueprintResult bp,
                               List<Component> frontends, List<Component> backends, Component database,
                               EnvVarMapping apiUrlVar, boolean netlifyCfg, boolean vercelCfg,
                               boolean hasDocker, boolean hasFlyway) {
        // secrets behind public build prefixes are embedded in shipped JS
        for (EnvVarMapping v : bp.getEnvironmentVariables()) {
            if (PUBLIC_PREFIX.matcher(v.getName()).find()
                && "SECRET_OR_SENSITIVE".equals(v.getClassification())) {
                bp.getFindings().add(finding("BLOCKER",
                    "Secret exposed through a public build variable",
                    v.getName() + " uses a public prefix, so its value is embedded in the JavaScript shipped to every visitor.",
                    "Found in " + v.getSourceEvidence(), v.getSourceEvidence(),
                    "Move this value to a backend-only environment variable and access it through your backend API.",
                    true));
            }
        }
        for (Component f : frontends) {
            if (f.getBuildCommand() == null) {
                bp.getFindings().add(finding("BLOCKER",
                    "No build command detected for the frontend",
                    "No build script was found for " + label(f) + ", so the hosting platform cannot build it.",
                    "No build script in " + (f.getPath().isEmpty() ? "package.json" : f.getPath() + "/package.json"),
                    f.getPath().isEmpty() ? "package.json" : f.getPath() + "/package.json",
                    "Add a \"build\" script to package.json (for Vite: \"vite build\").",
                    true));
            }
        }
        if (database != null) {
            boolean hasDbVar = bp.getEnvironmentVariables().stream()
                .anyMatch(v -> "DATABASE_CONNECTION_INFO".equals(v.getDependsOnOutput()));
            if (!hasDbVar) {
                bp.getFindings().add(finding("WARNING",
                    "Database detected but no database environment variable found",
                    database.getName() + " is used, but no connection variable (e.g. DATABASE_URL) appears in the analysed configuration.",
                    "Database evidence: " + String.join("; ", database.getRecommendedPlatform().getEvidence()),
                    null,
                    "Configure the database connection through environment variables rather than hardcoded values.",
                    true));
            }
        }
        if (!frontends.isEmpty() && !backends.isEmpty() && apiUrlVar == null) {
            bp.getFindings().add(finding("WARNING",
                "No frontend API-URL variable detected",
                "The frontend and backend are deployed separately, but no variable like VITE_API_BASE_URL was found. The frontend may hardcode the backend address (often localhost), which breaks in production.",
                "No public API-URL variable among detected environment variables",
                null,
                "Read the backend base URL from an environment variable in the frontend build.",
                true));
        }
        for (Component b : backends) {
            if (b.getName().contains("Spring") && !hasDocker && "Render".equals(b.getSelectedPlatform())) {
                bp.getFindings().add(finding("WARNING",
                    "Spring Boot on Render usually needs a Dockerfile",
                    "No Dockerfile was detected. Render runs Java applications most reliably as Docker services.",
                    "No CONTAINER evidence in the repository analysis",
                    (b.getPath().isEmpty() ? "" : b.getPath() + "/") + "Dockerfile",
                    "Add the suggested Dockerfile preview to " + (b.getPath().isEmpty() ? "the repository root" : b.getPath()) + ".",
                    true));
            }
        }
        if (frontends.size() > 1) {
            bp.getFindings().add(finding("WARNING", "Multiple frontends detected",
                "More than one frontend component was found; confirm which ones should be deployed.",
                frontends.stream().map(this::label).reduce((a, c) -> a + ", " + c).orElse(""),
                null, "Review the component list and remove or confirm extras.", true));
        }
        if (backends.size() > 1) {
            bp.getFindings().add(finding("WARNING", "Multiple backends detected",
                "More than one backend component was found; confirm which ones should be deployed.",
                backends.stream().map(this::label).reduce((a, c) -> a + ", " + c).orElse(""),
                null, "Review the component list and remove or confirm extras.", true));
        }
        if (netlifyCfg && vercelCfg) {
            bp.getFindings().add(finding("WARNING", "Conflicting hosting configuration files",
                "Both netlify.toml and vercel.json exist; deployments may behave differently per platform.",
                "netlify.toml and vercel.json both present", null,
                "Keep only the configuration for the platform you choose.", true));
        }
        for (Component f : frontends) {
            boolean spa = !"Next.js".equals(f.getName());
            if (spa && "Netlify".equals(f.getSelectedPlatform())) {
                if (!netlifyCfg) {
                    bp.getFindings().add(finding("INFORMATIONAL", "Add an SPA redirect configuration",
                        "Single-page apps on Netlify need a catch-all redirect to index.html so client-side routes work on refresh.",
                        "No netlify.toml detected", "netlify.toml",
                        "Use the suggested netlify.toml preview below.", false));
                } else {
                    bp.getFindings().add(finding("INFORMATIONAL", "Existing netlify.toml found",
                        "Compare the current file with the suggested preview to confirm the base directory, publish directory and SPA redirect.",
                        "netlify.toml present in repository", "netlify.toml", null, false));
                    if ("MONOREPO".equals(analysis.getStructure()) && !f.getPath().isEmpty()) {
                        bp.getFindings().add(finding("INFORMATIONAL", "Verify the monorepo base directory",
                            "The frontend lives in '" + f.getPath() + "'; netlify.toml must set base = \"" + f.getPath() + "\" and a publish path relative to it.",
                            "Monorepo structure with frontend at " + f.getPath(), "netlify.toml", null, false));
                    }
                }
            }
        }
        if (hasFlyway) {
            bp.getFindings().add(finding("INFORMATIONAL", "Database migrations run automatically",
                "Flyway was detected; schema migrations apply automatically when the backend starts against the production database.",
                "flyway-core dependency detected", null, null, false));
        }
        // A Supabase publishable/anon key in the browser is fine ONLY with Row Level Security on.
        EnvVarMapping anonKey = bp.getEnvironmentVariables().stream()
            .filter(v -> "PUBLIC_PUBLISHABLE_CREDENTIAL".equals(v.getClassification()))
            .filter(v -> v.getName().toUpperCase().contains("SUPABASE") || v.getName().toUpperCase().contains("ANON"))
            .findFirst().orElse(null);
        if (anonKey != null) {
            bp.getFindings().add(finding("INFORMATIONAL", "Supabase anon key is public — enable Row Level Security",
                anonKey.getName() + " is a publishable key that Supabase browser clients require. It is safe to ship "
                    + "in the frontend ONLY if Row Level Security (RLS) is enabled on every table it can reach; "
                    + "without RLS, anyone with this key can read or write your data.",
                "Found publishable credential " + anonKey.getName() + " in " + anonKey.getSourceEvidence(), null,
                "In the Supabase dashboard, enable RLS on all exposed tables and add policies before going live. "
                    + "Keep SUPABASE_SERVICE_ROLE_KEY backend-only — never expose it to the browser.",
                false));
        }
        for (Component b : backends) {
            if (b.getHealthCheckPath() != null) {
                bp.getFindings().add(finding("INFORMATIONAL", "Health-check path detected",
                    "A health route (" + b.getHealthCheckPath() + ") was detected for " + label(b)
                        + ". Hosting platforms use it to know the service is up.",
                    "Express health route in the analysed source", null,
                    "Set " + b.getSelectedPlatform() + "'s health-check path to " + b.getHealthCheckPath() + ".", false));
            } else {
                bp.getFindings().add(finding("INFORMATIONAL", "Configure a health-check path",
                    "A health-check path could not be detected for " + label(b) + ". Hosting platforms use it to know the service is up.",
                    "No health endpoint evidence available from analysis", null,
                    "Set the platform's health-check path to your API's health endpoint once deployed.", false));
            }
        }
    }

    // ==================== steps ====================

    private void buildSteps(BlueprintResult bp, Component frontend, Component backend,
                            Component database, EnvVarMapping apiUrlVar, EnvVarMapping corsVar) {
        List<Step> steps = new ArrayList<>();
        Integer dbStep = null, backendEnvStep = null, backendDeployStep = null,
            frontendEnvStep = null, frontendDeployStep = null, corsStep = null;

        List<String> dbVarNames = bp.getEnvironmentVariables().stream()
            .filter(v -> "DATABASE_CONNECTION_INFO".equals(v.getDependsOnOutput()))
            .map(EnvVarMapping::getName).toList();
        List<String> backendVarNames = backend == null ? List.of() : bp.getEnvironmentVariables().stream()
            .filter(v -> backend.getId().equals(v.getComponentId()))
            .map(EnvVarMapping::getName).toList();

        if (database != null) {
            Step s = step(steps.size() + 1, "Prepare the production database",
                "Create a " + database.getName() + " database on " + database.getSelectedPlatform()
                    + " and copy its connection details.",
                database.getSelectedPlatform(),
                List.of(),
                "DATABASE_CONNECTION_INFO",
                dbVarNames,
                "Database exists and connection details are available.",
                List.of());
            steps.add(s); dbStep = s.getIndex();
        }
        if (backend != null) {
            Step env = step(steps.size() + 1, "Configure backend environment variables",
                "Set the backend variables listed in the environment-variable table"
                    + (database != null ? ", using the database connection details from the previous step" : "") + ".",
                backend.getSelectedPlatform() + " dashboard",
                database != null ? List.of("DATABASE_CONNECTION_INFO") : List.of(),
                null,
                List.of(),
                "All backend variables are set (except those that depend on the frontend URL).",
                dbStep != null ? List.of(dbStep) : List.of());
            steps.add(env); backendEnvStep = env.getIndex();

            Step deploy = step(steps.size() + 1, "Deploy the backend",
                "Deploy " + label(backend) + " and wait for a successful start.",
                backend.getSelectedPlatform(),
                List.of(),
                "BACKEND_PUBLIC_URL",
                apiUrlVar != null ? List.of(apiUrlVar.getName()) : List.of(),
                "Backend is live and its public URL is known.",
                List.of(backendEnvStep));
            steps.add(deploy); backendDeployStep = deploy.getIndex();
        }
        if (frontend != null) {
            boolean needsBackendUrl = apiUrlVar != null && backendDeployStep != null;
            Step env = step(steps.size() + 1, "Configure frontend environment variables",
                needsBackendUrl
                    ? "Set " + apiUrlVar.getName() + " to the backend public URL (" + apiUrlVar.getExpectedFormat() + ") and any other frontend variables."
                    : "Set the frontend variables listed in the environment-variable table.",
                frontend.getSelectedPlatform() + " dashboard",
                needsBackendUrl ? List.of("BACKEND_PUBLIC_URL") : List.of(),
                null, List.of(),
                "Frontend build variables are configured.",
                needsBackendUrl ? List.of(backendDeployStep) : List.of());
            steps.add(env); frontendEnvStep = env.getIndex();

            Step deploy = step(steps.size() + 1, "Deploy the frontend",
                "Deploy " + label(frontend) + " and wait for the build to publish.",
                frontend.getSelectedPlatform(),
                List.of(),
                "FRONTEND_PUBLIC_URL",
                corsVar != null ? List.of(corsVar.getName()) : List.of(),
                "Frontend is live and its public URL is known.",
                List.of(frontendEnvStep));
            steps.add(deploy); frontendDeployStep = deploy.getIndex();
        }
        if (backend != null && corsVar != null && frontendDeployStep != null) {
            Step s = step(steps.size() + 1, "Allow the frontend origin on the backend",
                "Set " + corsVar.getName() + " to the frontend public URL and redeploy or restart the backend.",
                backend.getSelectedPlatform() + " dashboard",
                List.of("FRONTEND_PUBLIC_URL"),
                null, List.of(),
                "Backend accepts browser requests from the production frontend.",
                List.of(frontendDeployStep, backendDeployStep));
            steps.add(s); corsStep = s.getIndex();
        }
        List<Integer> verifyBlockers = new ArrayList<>();
        if (corsStep != null) verifyBlockers.add(corsStep);
        else if (frontendDeployStep != null) verifyBlockers.add(frontendDeployStep);
        else if (backendDeployStep != null) verifyBlockers.add(backendDeployStep);
        steps.add(step(steps.size() + 1, "Verify the deployment end to end",
            frontend != null && backend != null
                ? "Open the frontend, exercise a flow that calls the backend, and confirm data loads without console errors."
                : "Open the deployed application and confirm it responds correctly.",
            "Browser",
            List.of(), null, List.of(),
            "The application works in production.",
            verifyBlockers));

        bp.setSteps(steps);
    }

    // ==================== file previews ====================

    private void buildFilePreviews(StackDetectionResult analysis, BlueprintResult bp,
                                   Component frontend, Component backend, boolean hasDocker,
                                   Map<String, String> currentFiles) {
        Set<String> analyzed = new LinkedHashSet<>(analysis.getAnalyzedFiles());

        if (frontend != null && "Netlify".equals(frontend.getSelectedPlatform())) {
            String base = frontend.getPath();
            String suggested = "[build]\n"
                + (base.isEmpty() ? "" : "  base = \"" + base + "\"\n")
                + "  publish = \"" + orPlaceholder(frontend.getPublishDirectory(), "<build output directory>") + "\"\n"
                + "  command = \"" + orPlaceholder(frontend.getBuildCommand() != null ? "npm ci && npm run build" : null, "<your build command>") + "\"\n"
                + "\n[[redirects]]\n  from = \"/*\"\n  to = \"/index.html\"\n  status = 200\n";
            addPreview(bp, "netlify.toml", "Netlify build settings and SPA redirect",
                analyzed.contains("netlify.toml"), currentFiles.get("netlify.toml"), suggested,
                "Tells Netlify where the frontend lives, how to build it, and routes all paths to index.html so client-side routing works.");
        }

        if (backend != null && "Render".equals(backend.getSelectedPlatform())) {
            String dockerPath = (backend.getPath().isEmpty() ? "" : "./" + backend.getPath() + "/") + "Dockerfile";
            StringBuilder render = new StringBuilder();
            render.append("services:\n  - type: web\n    name: ").append(slug(analysis.getRepository()))
                .append("-backend\n");
            if (hasDocker) {
                render.append("    env: docker\n    dockerfilePath: ").append(dockerPath).append("\n");
            } else {
                render.append("    env: docker  # add the suggested Dockerfile first\n    dockerfilePath: ")
                    .append(dockerPath).append("\n");
            }
            render.append("    healthCheckPath: ")
                .append(backend.getHealthCheckPath() != null
                    ? backend.getHealthCheckPath()
                    : "<your health endpoint, e.g. /api/health>")
                .append("\n    envVars:\n");
            bp.getEnvironmentVariables().stream()
                .filter(v -> backend.getId().equals(v.getComponentId()))
                .filter(v -> !"Set automatically by the hosting platform — do not configure manually".equals(v.getValueSource()))
                .forEach(v -> render.append("      - key: ").append(v.getName()).append("\n        sync: false\n"));
            addPreview(bp, "render.yaml", "Render infrastructure-as-code definition",
                analyzed.contains("render.yaml"), currentFiles.get("render.yaml"), render.toString(),
                "Declares the backend web service so Render provisions it consistently. Values marked sync: false are set in the dashboard, never committed.");
        }

        if (backend != null && backend.getName().contains("Spring") && !hasDocker) {
            String p = backend.getPath().isEmpty() ? "" : backend.getPath() + "/";
            String suggested = "FROM maven:3.9-eclipse-temurin-17 AS build\n"
                + "WORKDIR /app\n"
                + "COPY " + p + "pom.xml .\n"
                + "RUN mvn dependency:go-offline -B\n"
                + "COPY " + p + "src ./src\n"
                + "RUN mvn clean package -DskipTests -B\n\n"
                + "FROM eclipse-temurin:17-jre-alpine\n"
                + "WORKDIR /app\n"
                + "COPY --from=build /app/target/*.jar app.jar\n"
                + "EXPOSE 8080\n"
                + "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n";
            addPreview(bp, p + "Dockerfile", "Container build for the Spring Boot backend",
                false, null, suggested,
                "Render runs Java applications most reliably as Docker services; this multi-stage build produces a small runtime image.");
        }

        StringBuilder env = new StringBuilder("# Environment template — placeholders only, never commit real values\n");
        for (EnvVarMapping v : bp.getEnvironmentVariables()) {
            env.append(v.getName()).append("=<").append(
                v.getExpectedFormat() != null ? v.getExpectedFormat().replace('<', '(').replace('>', ')') : "set-me")
                .append(">\n");
        }
        addPreview(bp, ".env.example", "Documented environment template",
            analyzed.contains(".env.example"), null, env.toString(),
            "Documents required variable names for other developers without exposing values. Real .env files must stay gitignored.");

        String gitignore = "# Dependencies and build output\nnode_modules/\ndist/\nbuild/\ntarget/\n\n# Environment files — never commit real values\n.env\n.env.local\n.env.*.local\n!.env.example\n";
        addPreview(bp, ".gitignore", "Keep secrets and build output out of Git",
            null, null, gitignore,
            "Ensures real environment files and build artifacts are never committed.");
    }

    // ==================== helpers ====================

    private List<Detection> detections(StackDetectionResult analysis, String category) {
        return analysis.getDetections().stream()
            .filter(d -> category.equals(d.getCategory())).toList();
    }

    private boolean hasDetection(StackDetectionResult analysis, String category, String name) {
        return analysis.getDetections().stream()
            .anyMatch(d -> category.equals(d.getCategory()) && name.equals(d.getName()));
    }

    private String detectionNameAt(StackDetectionResult analysis, String category, String path) {
        return detections(analysis, category).stream()
            .filter(d -> d.getPath().equals(path))
            .map(Detection::getName).findFirst().orElse(null);
    }

    private String commandFor(List<String> commands, String path) {
        if (commands == null) return null;
        for (String c : commands) {
            if (path.isEmpty()) {
                if (!c.startsWith("cd ")) return c;
            } else if (c.startsWith("cd " + path + " && ")) {
                return c.substring(("cd " + path + " && ").length());
            }
        }
        return null;
    }

    private String publishDirFor(String framework, String buildTool, Component c) {
        if ("Next.js".equals(framework)) {
            c.getNotes().add("Vercel manages the Next.js build output automatically.");
            return ".next";
        }
        if ("Vite".equals(buildTool)) return "dist";
        if ("Angular".equals(framework)) {
            c.getNotes().add("Angular outputs to dist/<project-name>; confirm the exact folder from angular.json.");
            return "dist/<project-name>";
        }
        c.getNotes().add("Publish directory assumed to be 'dist'; verify your build output folder.");
        return "dist";
    }

    private PlatformOption platform(String name, String reason, String confidence, List<String> evidence,
                                    String freeTier, String coldStart, String pricing) {
        PlatformOption p = new PlatformOption();
        p.setPlatform(name);
        p.setReason(reason);
        p.setConfidence(confidence);
        p.setEvidence(new ArrayList<>(evidence));
        p.setFreeTierNote(freeTier);
        p.setColdStartNote(coldStart);
        p.setPricingNote(pricing);
        return p;
    }

    private Finding finding(String severity, String title, String detail, String evidence,
                            String file, String fix, boolean confirm) {
        Finding f = new Finding();
        f.setSeverity(severity); f.setTitle(title); f.setDetail(detail);
        f.setEvidence(evidence); f.setAffectedFile(file); f.setProposedFix(fix);
        f.setRequiresConfirmation(confirm);
        return f;
    }

    private Step step(int index, String title, String what, String where, List<String> inputs,
                      String produces, List<String> unlocks, String expected, List<Integer> blockedBy) {
        Step s = new Step();
        s.setIndex(index); s.setTitle(title); s.setWhat(what); s.setWhere(where);
        s.setInputs(new ArrayList<>(inputs)); s.setProduces(produces);
        s.setUnlocksVariables(new ArrayList<>(unlocks)); s.setExpectedResult(expected);
        s.setBlockedBy(new ArrayList<>(blockedBy));
        return s;
    }

    private void addPreview(BlueprintResult bp, String path, String purpose, Boolean exists,
                            String current, String suggested, String reason) {
        FilePreview p = new FilePreview();
        p.setPath(path); p.setPurpose(purpose); p.setExists(exists);
        p.setCurrentContent(current); p.setSuggestedContent(suggested); p.setReason(reason);
        if (current != null && suggested != null) p.setDiff(simpleDiff(current, suggested));
        bp.getFilePreviews().add(p);
    }

    /** Minimal line diff: trims the common prefix/suffix and marks the middle. */
    String simpleDiff(String current, String suggested) {
        String[] a = current.split("\n", -1);
        String[] b = suggested.split("\n", -1);
        int start = 0;
        while (start < a.length && start < b.length && a[start].equals(b[start])) start++;
        int endA = a.length, endB = b.length;
        while (endA > start && endB > start && a[endA - 1].equals(b[endB - 1])) { endA--; endB--; }
        if (start == endA && start == endB) return "(no changes needed)";
        StringBuilder d = new StringBuilder();
        for (int i = Math.max(0, start - 2); i < start; i++) d.append("  ").append(a[i]).append('\n');
        for (int i = start; i < endA; i++) d.append("- ").append(a[i]).append('\n');
        for (int i = start; i < endB; i++) d.append("+ ").append(b[i]).append('\n');
        for (int i = endA; i < Math.min(a.length, endA + 2); i++) d.append("  ").append(a[i]).append('\n');
        return d.toString();
    }

    private String label(Component c) {
        return c.getName() + (c.getPath() == null || c.getPath().isEmpty() ? "" : " (" + c.getPath() + ")");
    }

    private String orPlaceholder(String value, String placeholder) {
        return value != null ? value : placeholder;
    }

    private String slug(String repository) {
        String name = repository.contains("/") ? repository.substring(repository.indexOf('/') + 1) : repository;
        return name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }
}
