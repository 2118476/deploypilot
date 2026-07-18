package com.deploypilot.service;

import com.deploypilot.dto.BlueprintResult;
import com.deploypilot.dto.BlueprintResult.Component;
import com.deploypilot.dto.BlueprintResult.EnvVarMapping;
import com.deploypilot.dto.BlueprintResult.Finding;
import com.deploypilot.dto.BlueprintResult.Step;
import com.deploypilot.dto.StackDetectionResult;
import com.deploypilot.dto.StackDetectionResult.Detection;
import com.deploypilot.dto.StackDetectionResult.EnvVarFinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintGenerationServiceTest {

    private final BlueprintGenerationService service = new BlueprintGenerationService();

    /** Synthetic analysis mirroring a React/Vite + Spring Boot + PostgreSQL monorepo. */
    private StackDetectionResult monorepoAnalysis() {
        StackDetectionResult a = new StackDetectionResult();
        a.setRepository("demo/app");
        a.setStructure("MONOREPO");
        a.getDetections().add(det("FRONTEND_FRAMEWORK", "React", "frontend", "HIGH"));
        a.getDetections().add(det("BUILD_TOOL", "Vite", "frontend", "HIGH"));
        a.getDetections().add(det("LANGUAGE", "TypeScript", "frontend", "HIGH"));
        a.getDetections().add(det("BACKEND_FRAMEWORK", "Spring Boot", "backend", "HIGH"));
        a.getDetections().add(det("LANGUAGE", "Java 17", "backend", "HIGH"));
        a.getDetections().add(det("BUILD_TOOL", "Maven", "backend", "HIGH"));
        a.getDetections().add(det("DATABASE", "PostgreSQL", "backend", "HIGH"));
        a.getDetections().add(det("HOSTING", "Netlify", "", "HIGH"));
        a.getDetections().add(det("CONTAINER", "Docker", "backend", "HIGH"));
        a.getDetections().add(det("EXTERNAL_SERVICE", "Flyway migrations", "backend", "HIGH"));
        a.getBuildCommands().add("cd frontend && npm run build");
        a.getBuildCommands().add("cd backend && mvn clean package");
        a.getStartCommands().add("cd backend && java -jar target/*.jar");
        a.getEnvironmentVariables().add(env("DATABASE_URL", "SECRET_OR_SENSITIVE", ".env.example"));
        a.getEnvironmentVariables().add(env("DATABASE_PASSWORD", "SECRET_OR_SENSITIVE", ".env.example"));
        a.getEnvironmentVariables().add(env("JWT_SECRET", "SECRET_OR_SENSITIVE", ".env.example"));
        a.getEnvironmentVariables().add(env("GEMINI_API_KEY", "SECRET_OR_SENSITIVE", ".env.example"));
        a.getEnvironmentVariables().add(env("FRONTEND_URL", "CONFIGURATION", ".env.example"));
        a.getEnvironmentVariables().add(env("VITE_API_BASE_URL", "PUBLIC_CONFIGURATION", ".env.example"));
        a.getAnalyzedFiles().add("frontend/package.json");
        a.getAnalyzedFiles().add("backend/pom.xml");
        a.getAnalyzedFiles().add("netlify.toml");
        a.getAnalyzedFiles().add(".env.example");
        return a;
    }

    private Detection det(String category, String name, String path, String confidence) {
        return new Detection(category, name, path, confidence,
            new java.util.ArrayList<>(List.of(name + " evidence")));
    }

    private EnvVarFinding env(String name, String classification, String source) {
        return new EnvVarFinding(name, classification, source);
    }

    private BlueprintResult generate() {
        return service.generate(monorepoAnalysis(), Map.of(), Map.of());
    }

    private Component component(BlueprintResult bp, String id) {
        return bp.getComponents().stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    private EnvVarMapping envVar(BlueprintResult bp, String name) {
        return bp.getEnvironmentVariables().stream().filter(v -> v.getName().equals(name)).findFirst().orElse(null);
    }

    // ---- recommendations ----

    @Test
    void recommendsNetlifyForReactVite() {
        Component f = component(generate(), "frontend@frontend");
        assertNotNull(f);
        assertEquals("Netlify", f.getRecommendedPlatform().getPlatform());
        assertEquals("Netlify", f.getSelectedPlatform());
        assertTrue(f.getAlternatives().stream().anyMatch(p -> p.getPlatform().equals("Vercel")));
        assertEquals("dist", f.getPublishDirectory());
        assertEquals("frontend", f.getRootDirectory());
        assertEquals("npm run build", f.getBuildCommand());
        assertFalse(f.getRecommendedPlatform().getEvidence().isEmpty());
    }

    @Test
    void recommendsRenderForSpringBootWithColdStartWarning() {
        Component b = component(generate(), "backend@backend");
        assertNotNull(b);
        assertEquals("Render", b.getRecommendedPlatform().getPlatform());
        assertTrue(b.getAlternatives().stream().anyMatch(p -> p.getPlatform().equals("Railway")));
        assertNotNull(b.getRecommendedPlatform().getColdStartNote());
        assertNotNull(b.getRecommendedPlatform().getFreeTierNote());
        assertEquals("Java 17", b.getRuntime());
        assertEquals("mvn clean package", b.getBuildCommand());
    }

    @Test
    void recommendsSupabaseForPostgres() {
        Component db = component(generate(), "database@postgresql");
        assertNotNull(db);
        assertEquals("Supabase PostgreSQL", db.getRecommendedPlatform().getPlatform());
        assertTrue(db.getAlternatives().stream().anyMatch(p -> p.getPlatform().equals("Render PostgreSQL")));
    }

    @Test
    void recommendsVercelForNextJs() {
        StackDetectionResult a = new StackDetectionResult();
        a.setRepository("demo/next");
        a.setStructure("SINGLE_APPLICATION");
        a.getDetections().add(det("FRONTEND_FRAMEWORK", "Next.js", "", "HIGH"));
        a.getBuildCommands().add("npm run build");
        BlueprintResult bp = service.generate(a, Map.of(), Map.of());
        assertEquals("Vercel", bp.getComponents().get(0).getRecommendedPlatform().getPlatform());
    }

    @Test
    void noDatabaseMeansNoDatabaseRecommendation() {
        StackDetectionResult a = new StackDetectionResult();
        a.setRepository("demo/static");
        a.setStructure("SINGLE_APPLICATION");
        a.getDetections().add(det("FRONTEND_FRAMEWORK", "React", "", "HIGH"));
        a.getBuildCommands().add("npm run build");
        BlueprintResult bp = service.generate(a, Map.of(), Map.of());
        assertTrue(bp.getComponents().stream().noneMatch(c -> c.getType().equals("DATABASE")));
    }

    // ---- environment variable mapping ----

    @Test
    void mapsEnvVarsToComponentsAndPlatforms() {
        BlueprintResult bp = generate();
        EnvVarMapping api = envVar(bp, "VITE_API_BASE_URL");
        assertEquals("frontend@frontend", api.getComponentId());
        assertEquals("Netlify", api.getTargetPlatform());
        assertEquals("BACKEND_PUBLIC_URL", api.getDependsOnOutput());

        EnvVarMapping db = envVar(bp, "DATABASE_URL");
        assertEquals("backend@backend", db.getComponentId());
        assertEquals("Render", db.getTargetPlatform());
        assertEquals("SECRET_OR_SENSITIVE", db.getClassification());
        assertEquals(Boolean.TRUE, db.getRequired());
        assertEquals("DATABASE_CONNECTION_INFO", db.getDependsOnOutput());
        assertTrue(db.getExpectedFormat().contains("jdbc:postgresql"));

        assertTrue(envVar(bp, "JWT_SECRET").isGeneratable());
        assertFalse(envVar(bp, "GEMINI_API_KEY").isGeneratable());
        assertEquals("FRONTEND_PUBLIC_URL", envVar(bp, "FRONTEND_URL").getDependsOnOutput());
    }

    @Test
    void dependentValuesUsePlaceholdersNotInventedUrls() {
        BlueprintResult bp = generate();
        assertTrue(envVar(bp, "VITE_API_BASE_URL").getExpectedFormat().contains("${BACKEND_PUBLIC_URL}"));
        assertTrue(envVar(bp, "FRONTEND_URL").getExpectedFormat().contains("${FRONTEND_PUBLIC_URL}"));
        for (EnvVarMapping v : bp.getEnvironmentVariables()) {
            if (v.getExpectedFormat() != null) {
                assertFalse(v.getExpectedFormat().contains("netlify.app"), v.getName());
                assertFalse(v.getExpectedFormat().contains("onrender.com"), v.getName());
            }
        }
    }

    // ---- deployment ordering ----

    @Test
    void ordersStepsByDependency() {
        BlueprintResult bp = generate();
        List<Step> steps = bp.getSteps();
        int db = indexOf(steps, "Prepare the production database");
        int beEnv = indexOf(steps, "Configure backend environment variables");
        int beDeploy = indexOf(steps, "Deploy the backend");
        int feEnv = indexOf(steps, "Configure frontend environment variables");
        int feDeploy = indexOf(steps, "Deploy the frontend");
        int cors = indexOf(steps, "Allow the frontend origin on the backend");
        int verify = indexOf(steps, "Verify the deployment end to end");
        assertTrue(db < beEnv && beEnv < beDeploy && beDeploy < feEnv
            && feEnv < feDeploy && feDeploy < cors && cors < verify);

        Step backendEnv = steps.get(beEnv);
        assertTrue(backendEnv.getBlockedBy().contains(steps.get(db).getIndex()));
        Step frontendEnv = steps.get(feEnv);
        assertTrue(frontendEnv.getBlockedBy().contains(steps.get(beDeploy).getIndex()));
        Step corsStep = steps.get(cors);
        assertTrue(corsStep.getBlockedBy().contains(steps.get(feDeploy).getIndex()));

        assertEquals("BACKEND_PUBLIC_URL", steps.get(beDeploy).getProduces());
        assertTrue(steps.get(beDeploy).getUnlocksVariables().contains("VITE_API_BASE_URL"));
        assertEquals("FRONTEND_PUBLIC_URL", steps.get(feDeploy).getProduces());
        assertTrue(steps.get(feDeploy).getUnlocksVariables().contains("FRONTEND_URL"));
    }

    private int indexOf(List<Step> steps, String title) {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).getTitle().equals(title)) return i;
        fail("Missing step: " + title);
        return -1;
    }

    // ---- overrides ----

    @Test
    void overrideRecalculatesTargetsAndPreservesRecommendation() {
        BlueprintResult bp = service.generate(monorepoAnalysis(),
            Map.of("frontend@frontend", "Vercel"), Map.of());
        Component f = component(bp, "frontend@frontend");
        assertEquals("Vercel", f.getSelectedPlatform());
        assertEquals("Netlify", f.getRecommendedPlatform().getPlatform()); // original preserved
        assertEquals("Vercel", envVar(bp, "VITE_API_BASE_URL").getTargetPlatform());
        // steps follow the selected platform
        assertTrue(bp.getSteps().stream().anyMatch(s -> "Vercel".equals(s.getWhere())));
    }

    @Test
    void allowedPlatformsListsRecommendationPlusAlternatives() {
        BlueprintResult bp = generate();
        List<String> allowed = service.allowedPlatforms(component(bp, "frontend@frontend"));
        assertEquals(List.of("Netlify", "Vercel"), allowed);
    }

    // ---- readiness findings ----

    @Test
    void flagsSecretBehindPublicPrefixAsBlocker() {
        StackDetectionResult a = monorepoAnalysis();
        a.getEnvironmentVariables().add(env("VITE_SECRET_KEY", "SECRET_OR_SENSITIVE", ".env.example"));
        BlueprintResult bp = service.generate(a, Map.of(), Map.of());
        Finding blocker = bp.getFindings().stream()
            .filter(f -> f.getSeverity().equals("BLOCKER") && f.getDetail().contains("VITE_SECRET_KEY"))
            .findFirst().orElse(null);
        assertNotNull(blocker, "public-prefixed secret must be a BLOCKER");
        assertTrue(blocker.isRequiresConfirmation());
        assertNotNull(blocker.getProposedFix());
    }

    @Test
    void flagsMissingBuildCommandAsBlocker() {
        StackDetectionResult a = monorepoAnalysis();
        a.getBuildCommands().clear();
        BlueprintResult bp = service.generate(a, Map.of(), Map.of());
        assertTrue(bp.getFindings().stream().anyMatch(f ->
            f.getSeverity().equals("BLOCKER") && f.getTitle().contains("build command")));
    }

    @Test
    void springWithoutDockerOnRenderGetsWarningAndDockerfilePreview() {
        StackDetectionResult a = monorepoAnalysis();
        a.setDetections(new java.util.ArrayList<>(a.getDetections().stream()
            .filter(d -> !d.getCategory().equals("CONTAINER")).toList()));
        BlueprintResult bp = service.generate(a, Map.of(), Map.of());
        assertTrue(bp.getFindings().stream().anyMatch(f ->
            f.getSeverity().equals("WARNING") && f.getTitle().contains("Dockerfile")));
        assertTrue(bp.getFilePreviews().stream().anyMatch(p -> p.getPath().endsWith("Dockerfile")));
    }

    // ---- file previews ----

    @Test
    void netlifyPreviewUsesDetectedPathsAndSpaRedirect() {
        BlueprintResult bp = generate();
        BlueprintResult.FilePreview netlify = bp.getFilePreviews().stream()
            .filter(p -> p.getPath().equals("netlify.toml")).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, netlify.getExists());
        assertTrue(netlify.getSuggestedContent().contains("base = \"frontend\""));
        assertTrue(netlify.getSuggestedContent().contains("publish = \"dist\""));
        assertTrue(netlify.getSuggestedContent().contains("/index.html"));
    }

    @Test
    void envExamplePreviewContainsNamesButNoValues() {
        BlueprintResult bp = generate();
        BlueprintResult.FilePreview envFile = bp.getFilePreviews().stream()
            .filter(p -> p.getPath().equals(".env.example")).findFirst().orElseThrow();
        assertTrue(envFile.getSuggestedContent().contains("DATABASE_URL="));
        assertTrue(envFile.getSuggestedContent().contains("JWT_SECRET="));
        assertFalse(envFile.getSuggestedContent().contains("change-me"));
    }

    @Test
    void diffShowsChangedLinesOnly() {
        String diff = service.simpleDiff("a\nb\nc\n", "a\nX\nc\n");
        assertTrue(diff.contains("- b"));
        assertTrue(diff.contains("+ X"));
        assertEquals("(no changes needed)", service.simpleDiff("same\n", "same\n"));
    }

    // ==================== Supabase accuracy regressions ====================

    /** React/Vite frontend + Express backend, both using Supabase. */
    private StackDetectionResult supabaseAnalysis() {
        StackDetectionResult a = new StackDetectionResult();
        a.setRepository("acme/jobpilot");
        a.setStructure("MONOREPO");
        a.getDetections().add(det("FRONTEND_FRAMEWORK", "React", "frontend", "HIGH"));
        a.getDetections().add(det("BUILD_TOOL", "Vite", "frontend", "HIGH"));
        a.getDetections().add(det("BACKEND_FRAMEWORK", "Express (Node.js)", "backend", "HIGH"));
        a.getDetections().add(det("LANGUAGE", "TypeScript", "backend", "HIGH"));
        a.getDetections().add(det("EXTERNAL_SERVICE", "Supabase", "frontend", "HIGH"));
        a.getDetections().add(det("HEALTH_ENDPOINT", "/api/health", "backend", "HIGH"));
        a.getBuildCommands().add("cd frontend && npm run build");
        a.getStartCommands().add("cd backend && npm run start");
        // frontend (public) + backend (secret) Supabase vars
        a.getEnvironmentVariables().add(env("VITE_SUPABASE_URL", "PUBLIC_CONFIGURATION", "frontend/.env.example"));
        a.getEnvironmentVariables().add(env("VITE_SUPABASE_ANON_KEY", "PUBLIC_PUBLISHABLE_CREDENTIAL", "frontend/.env.example"));
        a.getEnvironmentVariables().add(env("SUPABASE_URL", "CONFIGURATION", "backend/.env.example"));
        a.getEnvironmentVariables().add(env("SUPABASE_SERVICE_ROLE_KEY", "SECRET_OR_SENSITIVE", "backend/.env.example"));
        return a;
    }

    private Finding finding(BlueprintResult bp, String titleFragment) {
        return bp.getFindings().stream()
            .filter(f -> f.getTitle().toLowerCase().contains(titleFragment.toLowerCase()))
            .findFirst().orElse(null);
    }

    // ---- Fix 1: anon key is not a blocker; RLS informational finding present ----

    @Test
    void supabaseAnonKeyIsNotFlaggedAsExposedSecret() {
        BlueprintResult bp = service.generate(supabaseAnalysis(), Map.of(), Map.of());
        // no BLOCKER about the anon key being an exposed secret
        assertTrue(bp.getFindings().stream().noneMatch(f ->
            f.getSeverity().equals("BLOCKER") && f.getDetail().contains("VITE_SUPABASE_ANON_KEY")),
            "publishable anon key must not be a blocker");
        assertEquals("PUBLIC_PUBLISHABLE_CREDENTIAL", envVar(bp, "VITE_SUPABASE_ANON_KEY").getClassification());
    }

    @Test
    void supabaseAnonKeyAddsRlsInformationalWarning() {
        BlueprintResult bp = service.generate(supabaseAnalysis(), Map.of(), Map.of());
        Finding rls = finding(bp, "Row Level Security");
        assertNotNull(rls, "an RLS informational warning is expected");
        assertEquals("INFORMATIONAL", rls.getSeverity());
        assertTrue(rls.getDetail().contains("VITE_SUPABASE_ANON_KEY"));
    }

    @Test
    void publicPrefixedServiceRoleKeyStaysABlocker() {
        StackDetectionResult a = supabaseAnalysis();
        // a service role key wrongly exposed with a public prefix must still block
        a.getEnvironmentVariables().add(env("VITE_SUPABASE_SERVICE_ROLE_KEY", "SECRET_OR_SENSITIVE", "frontend/.env.example"));
        BlueprintResult bp = service.generate(a, Map.of(), Map.of());
        assertTrue(bp.getFindings().stream().anyMatch(f ->
            f.getSeverity().equals("BLOCKER") && f.getDetail().contains("VITE_SUPABASE_SERVICE_ROLE_KEY")),
            "an exposed service-role key must remain a blocker");
    }

    // ---- Fix 2: detected health path drives the Render recommendation ----

    @Test
    void detectedHealthPathBecomesRenderHealthCheckPath() {
        BlueprintResult bp = service.generate(supabaseAnalysis(), Map.of(), Map.of());
        Component backend = component(bp, "backend@backend");
        assertEquals("/api/health", backend.getHealthCheckPath());
        // render.yaml preview should use the exact detected path
        BlueprintResult.FilePreview render = bp.getFilePreviews().stream()
            .filter(p -> p.getPath().equals("render.yaml")).findFirst().orElse(null);
        assertNotNull(render);
        assertTrue(render.getSuggestedContent().contains("healthCheckPath: /api/health"));
        assertTrue(bp.getFindings().stream().anyMatch(f -> f.getTitle().contains("Health-check path detected")));
    }

    // ---- Fix 3: Supabase vars map to the correct component ----

    @Test
    void supabaseVarsMapToCorrectComponents() {
        BlueprintResult bp = service.generate(supabaseAnalysis(), Map.of(), Map.of());
        assertEquals("frontend@frontend", envVar(bp, "VITE_SUPABASE_URL").getComponentId());
        assertEquals("frontend@frontend", envVar(bp, "VITE_SUPABASE_ANON_KEY").getComponentId());
        assertEquals("backend@backend", envVar(bp, "SUPABASE_URL").getComponentId());
        assertEquals("backend@backend", envVar(bp, "SUPABASE_SERVICE_ROLE_KEY").getComponentId());
    }

    @Test
    void backendSupabaseRelationshipNeverUsesVitePublicVar() {
        BlueprintResult bp = service.generate(supabaseAnalysis(), Map.of(), Map.of());
        BlueprintResult.Relationship backendToSupabase = bp.getRelationships().stream()
            .filter(r -> r.getFromComponent().equals("backend@backend") && r.getToComponent().contains("supabase"))
            .findFirst().orElse(null);
        assertNotNull(backendToSupabase, "backend should connect to Supabase");
        assertNotNull(backendToSupabase.getViaVariable());
        assertFalse(backendToSupabase.getViaVariable().startsWith("VITE_"),
            "backend must never use a VITE_ public variable");
        // the frontend also connects to Supabase via a public var
        BlueprintResult.Relationship frontendToSupabase = bp.getRelationships().stream()
            .filter(r -> r.getFromComponent().equals("frontend@frontend") && r.getToComponent().contains("supabase"))
            .findFirst().orElse(null);
        assertNotNull(frontendToSupabase, "frontend should connect to Supabase");
        assertTrue(frontendToSupabase.getViaVariable().startsWith("VITE_"));
    }

    @Test
    void serviceRoleKeyValueSourceIsBackendOnly() {
        BlueprintResult bp = service.generate(supabaseAnalysis(), Map.of(), Map.of());
        assertTrue(envVar(bp, "SUPABASE_SERVICE_ROLE_KEY").getValueSource().toLowerCase().contains("never expose"));
    }
}
