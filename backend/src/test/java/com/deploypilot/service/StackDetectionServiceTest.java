package com.deploypilot.service;

import com.deploypilot.dto.StackDetectionResult;
import com.deploypilot.dto.StackDetectionResult.Detection;
import com.deploypilot.dto.StackDetectionResult.EnvVarFinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StackDetectionServiceTest {

    private final StackDetectionService service = new StackDetectionService();

    private static final String FRONTEND_PACKAGE_JSON = """
        {
          "scripts": { "build": "vite build", "dev": "vite" },
          "dependencies": { "react": "^19.0.0", "react-dom": "^19.0.0" },
          "devDependencies": { "vite": "^6.0.0", "typescript": "^5.6.0" }
        }
        """;

    private static final String BACKEND_POM = """
        <project>
          <parent><artifactId>spring-boot-starter-parent</artifactId></parent>
          <properties><java.version>17</java.version></properties>
          <dependencies>
            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>
          </dependencies>
        </project>
        """;

    private static final String ENV_EXAMPLE = """
        DATABASE_URL=jdbc:postgresql://localhost:5432/app
        JWT_SECRET=change-me
        VITE_API_BASE_URL=http://localhost:8080/api
        APP_NAME=Demo
        """;

    private StackDetectionResult detectMonorepo() {
        Map<String, String> files = Map.of(
            "frontend/package.json", FRONTEND_PACKAGE_JSON,
            "backend/pom.xml", BACKEND_POM,
            ".env.example", ENV_EXAMPLE,
            "netlify.toml", "[build]\n  base = \"frontend\"\n",
            "backend/Dockerfile", "FROM eclipse-temurin:17-jre-alpine\n");
        List<String> allPaths = List.of(
            "frontend/package.json", "frontend/package-lock.json", "frontend/tsconfig.json",
            "backend/pom.xml", ".env.example", "netlify.toml", "backend/Dockerfile");
        return service.detect("demo/app", allPaths, files, List.of(), List.of());
    }

    private Detection find(StackDetectionResult result, String category, String name) {
        return result.getDetections().stream()
            .filter(d -> d.getCategory().equals(category) && d.getName().equals(name))
            .findFirst().orElse(null);
    }

    @Test
    void detectsMonorepoStructure() {
        assertEquals("MONOREPO", detectMonorepo().getStructure());
    }

    @Test
    void detectsReactWithViteAndEvidence() {
        StackDetectionResult result = detectMonorepo();
        Detection react = find(result, "FRONTEND_FRAMEWORK", "React");
        assertNotNull(react);
        assertEquals("HIGH", react.getConfidence());
        assertEquals("frontend", react.getPath());
        assertFalse(react.getEvidence().isEmpty());
        assertNotNull(find(result, "BUILD_TOOL", "Vite"));
    }

    @Test
    void detectsSpringBootWithJavaVersion() {
        StackDetectionResult result = detectMonorepo();
        Detection spring = find(result, "BACKEND_FRAMEWORK", "Spring Boot");
        assertNotNull(spring);
        assertEquals("HIGH", spring.getConfidence());
        assertEquals("backend", spring.getPath());
        assertNotNull(find(result, "LANGUAGE", "Java 17"));
    }

    @Test
    void detectsPostgresFromPom() {
        Detection db = find(detectMonorepo(), "DATABASE", "PostgreSQL");
        assertNotNull(db);
        assertEquals("HIGH", db.getConfidence());
        assertTrue(db.getEvidence().get(0).contains("backend/pom.xml"));
    }

    @Test
    void detectsHostingAndContainer() {
        StackDetectionResult result = detectMonorepo();
        assertNotNull(find(result, "HOSTING", "Netlify"));
        assertNotNull(find(result, "CONTAINER", "Docker"));
        assertNotNull(find(result, "PACKAGE_MANAGER", "npm"));
    }

    @Test
    void classifiesEnvVarsWithoutValues() {
        StackDetectionResult result = detectMonorepo();
        Map<String, String> byName = result.getEnvironmentVariables().stream()
            .collect(java.util.stream.Collectors.toMap(EnvVarFinding::getName, EnvVarFinding::getClassification, (a, b) -> a));
        assertEquals("SECRET_OR_SENSITIVE", byName.get("DATABASE_URL"));
        assertEquals("SECRET_OR_SENSITIVE", byName.get("JWT_SECRET"));
        assertEquals("PUBLIC_CONFIGURATION", byName.get("VITE_API_BASE_URL"));
        assertEquals("CONFIGURATION", byName.get("APP_NAME"));
        // No secret values anywhere in the serialized result
        assertFalse(result.getEnvironmentVariables().toString().contains("change-me"));
    }

    @Test
    void collectsBuildCommands() {
        StackDetectionResult result = detectMonorepo();
        assertTrue(result.getBuildCommands().stream().anyMatch(c -> c.contains("npm run build")));
        assertTrue(result.getBuildCommands().stream().anyMatch(c -> c.contains("mvn clean package")));
    }

    @Test
    void singleAppStructureForRootManifest() {
        StackDetectionResult result = service.detect("demo/single",
            List.of("package.json"),
            Map.of("package.json", FRONTEND_PACKAGE_JSON),
            List.of(), List.of());
        assertEquals("SINGLE_APPLICATION", result.getStructure());
    }

    @Test
    void warnsWhenNothingDetected() {
        StackDetectionResult result = service.detect("demo/empty",
            List.of("readme.md"), Map.of(), List.of(), List.of());
        assertEquals("UNKNOWN", result.getStructure());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void badPackageJsonProducesWarningNotFailure() {
        StackDetectionResult result = service.detect("demo/broken",
            List.of("package.json"),
            Map.of("package.json", "{not valid json"),
            List.of(), List.of());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("package.json")));
    }

    // ---- regression: Supabase anon key is publishable, not a secret (Fix 1) ----

    private EnvVarFinding env(StackDetectionResult r, String name) {
        return r.getEnvironmentVariables().stream()
            .filter(v -> v.getName().equals(name)).findFirst().orElse(null);
    }

    @Test
    void classifiesSupabaseAnonKeyAsPublishableNotSecret() {
        String env = String.join("\n",
            "VITE_SUPABASE_URL=https://x.supabase.co",
            "VITE_SUPABASE_ANON_KEY=public-anon-key",
            "SUPABASE_SERVICE_ROLE_KEY=super-secret",
            "DATABASE_PASSWORD=hunter2");
        StackDetectionResult r = service.detect("demo/app",
            List.of(".env.example"), Map.of(".env.example", env), List.of(), List.of());

        assertEquals("PUBLIC_PUBLISHABLE_CREDENTIAL", env(r, "VITE_SUPABASE_ANON_KEY").getClassification());
        // the service role key and DB password stay secrets
        assertEquals("SECRET_OR_SENSITIVE", env(r, "SUPABASE_SERVICE_ROLE_KEY").getClassification());
        assertEquals("SECRET_OR_SENSITIVE", env(r, "DATABASE_PASSWORD").getClassification());
        // the URL is public config, not a credential
        assertEquals("PUBLIC_CONFIGURATION", env(r, "VITE_SUPABASE_URL").getClassification());
    }

    @Test
    void plainSupabaseAnonKeyIsAlsoPublishable() {
        StackDetectionResult r = service.detect("demo/app",
            List.of(".env.example"),
            Map.of(".env.example", "SUPABASE_ANON_KEY=anon\nNEXT_PUBLIC_SUPABASE_ANON_KEY=anon2"),
            List.of(), List.of());
        assertEquals("PUBLIC_PUBLISHABLE_CREDENTIAL", env(r, "SUPABASE_ANON_KEY").getClassification());
        assertEquals("PUBLIC_PUBLISHABLE_CREDENTIAL", env(r, "NEXT_PUBLIC_SUPABASE_ANON_KEY").getClassification());
    }

    // ---- regression: Express health-route detection (Fix 2) ----

    private static final String EXPRESS_PACKAGE_JSON = """
        {
          "scripts": { "start": "node server.js" },
          "dependencies": { "express": "^4.19.0" }
        }
        """;

    @Test
    void detectsDirectExpressHealthRoute() {
        String server = "const app = require('express')();\n"
            + "app.get('/api/health', (req, res) => res.json({status:'ok'}));\n"
            + "app.listen(3000);\n";
        StackDetectionResult r = service.detect("demo/jobpilot",
            List.of("package.json", "server.js"),
            Map.of("package.json", EXPRESS_PACKAGE_JSON, "server.js", server),
            List.of(), List.of());
        Detection health = find(r, "HEALTH_ENDPOINT", "/api/health");
        assertNotNull(health, "should detect /api/health");
    }

    @Test
    void detectsMountedExpressHealthRoute() {
        // router.get("/health") mounted at app.use("/api", router) -> /api/health
        String server = "const router = express.Router();\n"
            + "router.get('/health', (req, res) => res.send('ok'));\n"
            + "app.use('/api', router);\n";
        StackDetectionResult r = service.detect("demo/jobpilot",
            List.of("package.json", "server.js"),
            Map.of("package.json", EXPRESS_PACKAGE_JSON, "server.js", server),
            List.of(), List.of());
        Detection health = find(r, "HEALTH_ENDPOINT", "/api/health");
        assertNotNull(health, "mounted router path should resolve to /api/health");
    }

    @Test
    void noHealthRouteMeansNoHealthDetection() {
        StackDetectionResult r = service.detect("demo/app",
            List.of("package.json", "server.js"),
            Map.of("package.json", EXPRESS_PACKAGE_JSON,
                "server.js", "app.get('/', (req,res)=>res.send('hi'));"),
            List.of(), List.of());
        assertTrue(r.getDetections().stream().noneMatch(d -> d.getCategory().equals("HEALTH_ENDPOINT")));
    }
}
