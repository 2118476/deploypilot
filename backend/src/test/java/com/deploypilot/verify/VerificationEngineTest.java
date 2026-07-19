package com.deploypilot.verify;

import com.deploypilot.dto.VerificationResult;
import com.deploypilot.dto.VerificationResult.CheckResult;
import com.deploypilot.model.enums.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerificationEngineTest {

    private final VerificationEngine engine = new VerificationEngine(new SafeHttpClient(new SafeUrlValidator()));

    private CheckResult check(VerificationResult r, String id) {
        return r.getChecks().stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    private boolean hasDiagnosis(VerificationResult r, String titleFragment) {
        return r.getDiagnoses().stream().anyMatch(d -> d.getTitle().toLowerCase().contains(titleFragment.toLowerCase()));
    }

    private VerificationEngine.Context ctx(String frontend, String backend, String health, String commit, boolean spa) {
        return new VerificationEngine.Context(frontend, backend, health, commit, true, spa, "Render");
    }

    // ---- frontend availability & provider error page ----

    @Test
    void healthyWhenFrontendServesHtml() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html><body>App</body></html>");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, false));
            assertEquals("PASS", check(out.result(), "frontend.reachable").getStatus());
        }
    }

    @Test
    void detectsNetlifyProviderErrorPage() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", ex -> MockDeploymentServer.respond(ex, 404, "text/html",
                "<html>Page not found</html>", Map.of("x-nf-request-id", "abc123")));
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, false));
            assertEquals("FAIL", check(out.result(), "frontend.reachable").getStatus());
            assertTrue(hasDiagnosis(out.result(), "provider error page"));
            assertEquals(VerificationStatus.UNHEALTHY, out.overallStatus());
        }
    }

    @Test
    void detectsLocalhostInBundle() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html><script src=\"/app.js\"></script></html>");
            fe.route("/app.js", 200, "application/javascript",
                "const api='http://localhost:8080/api';fetch(api)");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, false));
            assertEquals("FAIL", check(out.result(), "frontend.bundle").getStatus());
            assertTrue(hasDiagnosis(out.result(), "localhost"));
        }
    }

    @Test
    void ignoresDormantLibraryLocalhostWhenProductionBackendIsPresent() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer(); MockDeploymentServer be = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html><script src=\"/app.js\"></script></html>");
            fe.route("/app.js", 200, "application/javascript",
                "const production='" + be.baseUrl() + "';const libraryFallback='http://localhost:9999'");
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", 200, "application/json", "{\"ok\":true}");

            VerificationEngine.Outcome out = engine.verify(
                ctx(fe.baseUrl(), be.baseUrl(), "/api/health", null, false));

            assertEquals("PASS", check(out.result(), "frontend.bundle").getStatus());
            assertFalse(hasDiagnosis(out.result(), "localhost"));
        }
    }

    @Test
    void detectsUnreplacedPlaceholderInBundle() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html><script src=\"/app.js\"></script></html>");
            fe.route("/app.js", 200, "application/javascript", "const api='${BACKEND_PUBLIC_URL}/api';");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, false));
            assertTrue(hasDiagnosis(out.result(), "placeholder"));
        }
    }

    // ---- SPA fallback ----

    @Test
    void spaFallbackPassesWhenRoutesReturnHtml() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html>App</html>");
            fe.fallback(200, "text/html", "<html>App</html>"); // any route -> index
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, true));
            assertEquals("PASS", check(out.result(), "frontend.spa").getStatus());
        }
    }

    @Test
    void spaFallbackWarnsOn404() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html>App</html>");
            fe.fallback(404, "text/plain", "not found");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, true));
            assertEquals("WARNING", check(out.result(), "frontend.spa").getStatus());
            assertTrue(hasDiagnosis(out.result(), "deep links"));
        }
    }

    // ---- backend health & api prefix ----

    @Test
    void backendHealthPasses() throws Exception {
        try (MockDeploymentServer be = new MockDeploymentServer()) {
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", 200, "application/json", "{\"status\":\"UP\"}");
            VerificationEngine.Outcome out = engine.verify(ctx(null, be.baseUrl(), "/api/health", null, false));
            assertEquals("PASS", check(out.result(), "backend.health").getStatus());
        }
    }

    @Test
    void backendHealthFailsWhenNoEndpoint() throws Exception {
        try (MockDeploymentServer be = new MockDeploymentServer()) {
            be.route("/", 200, "text/plain", "root");
            be.fallback(404, "text/plain", "nope");
            VerificationEngine.Outcome out = engine.verify(ctx(null, be.baseUrl(), "/api/health", null, false));
            assertEquals("FAIL", check(out.result(), "backend.health").getStatus());
        }
    }

    @Test
    void detectsDuplicatedApiPrefix() throws Exception {
        try (MockDeploymentServer be = new MockDeploymentServer()) {
            be.fallback(200, "application/json", "{}");
            VerificationEngine.Outcome out = engine.verify(
                ctx(null, be.baseUrl() + "/api/api", "/health", null, false));
            assertTrue(hasDiagnosis(out.result(), "duplicated /api"));
        }
    }

    // ---- CORS ----

    @Test
    void corsAcceptedWhenOriginMatches() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer(); MockDeploymentServer be = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html>App</html>");
            String origin = fe.baseUrl();
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", ex -> {
                if (ex.getRequestMethod().equals("OPTIONS")) {
                    MockDeploymentServer.respond(ex, 204, null, "",
                        Map.of("Access-Control-Allow-Origin", origin));
                } else {
                    MockDeploymentServer.respond(ex, 200, "application/json", "{\"status\":\"UP\"}");
                }
            });
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), be.baseUrl(), "/api/health", null, false));
            assertEquals("ACCEPTED", out.result().getCorsResult());
        }
    }

    @Test
    void corsRejectedWhenNoHeader() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer(); MockDeploymentServer be = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html>App</html>");
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", ex -> {
                if (ex.getRequestMethod().equals("OPTIONS")) MockDeploymentServer.respond(ex, 403, null, "");
                else MockDeploymentServer.respond(ex, 200, "application/json", "{}");
            });
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), be.baseUrl(), "/api/health", null, false));
            assertEquals("REJECTED", out.result().getCorsResult());
            assertTrue(hasDiagnosis(out.result(), "cors rejects"));
            assertEquals(VerificationStatus.UNHEALTHY, out.overallStatus());
        }
    }

    @Test
    void corsWrongOriginReported() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer(); MockDeploymentServer be = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html>App</html>");
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", ex -> {
                if (ex.getRequestMethod().equals("OPTIONS")) {
                    MockDeploymentServer.respond(ex, 204, null, "",
                        Map.of("Access-Control-Allow-Origin", "https://some-other-site.example.com"));
                } else MockDeploymentServer.respond(ex, 200, "application/json", "{}");
            });
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), be.baseUrl(), "/api/health", null, false));
            assertEquals("WRONG_ORIGIN", out.result().getCorsResult());
        }
    }

    @Test
    void corsWildcardWithCredentialsConflict() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer(); MockDeploymentServer be = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html>App</html>");
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", ex -> {
                if (ex.getRequestMethod().equals("OPTIONS")) {
                    MockDeploymentServer.respond(ex, 204, null, "",
                        Map.of("Access-Control-Allow-Origin", "*", "Access-Control-Allow-Credentials", "true"));
                } else MockDeploymentServer.respond(ex, 200, "application/json", "{}");
            });
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), be.baseUrl(), "/api/health", null, false));
            assertEquals("WILDCARD_CREDENTIALS_CONFLICT", out.result().getCorsResult());
        }
    }

    // ---- version states ----

    @Test
    void versionCurrentWhenBundleStampMatchesExpected() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html",
                "<html><meta name=\"build-commit\" content=\"abc1234\"><script src=\"/app.js\"></script></html>");
            fe.route("/app.js", 200, "application/javascript", "console.log('build abc1234')");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, "abc1234", false));
            assertEquals("CURRENT", out.result().getVersion().getState());
        }
    }

    @Test
    void versionOutdatedWhenStampDiffersFromExpected() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html",
                "<html><meta name=\"build-commit\" content=\"aaa0000\"></html>");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, "bbb1111", false));
            assertEquals("OUTDATED", out.result().getVersion().getState());
            assertTrue(hasDiagnosis(out.result(), "not the expected version"));
        }
    }

    @Test
    void versionUnknownSuggestsBuildMetadata() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html><body>No stamp here</body></html>");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, "abc1234", false));
            assertEquals("UNKNOWN", out.result().getVersion().getState());
            assertNotNull(out.result().getVersion().getSuggestion());
            assertTrue(out.result().getVersion().getSuggestion().contains("commitSha"));
        }
    }

    // ---- PWA ----

    @Test
    void detectsServiceWorkerRewrittenToHtml() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html",
                "<html><link rel=\"manifest\" href=\"/manifest.webmanifest\"><script>navigator.serviceWorker.register('/sw.js')</script></html>");
            fe.route("/manifest.webmanifest", 200, "application/manifest+json", "{}");
            fe.route("/sw.js", 200, "text/html", "<!doctype html><html>index</html>"); // misconfigured
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, true));
            assertEquals("FAIL", check(out.result(), "pwa.serviceWorker").getStatus());
            assertTrue(hasDiagnosis(out.result(), "sw.js is rewritten"));
        }
    }

    @Test
    void pwaHealthyEmitsUserDeviceCheck() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", ex -> MockDeploymentServer.respond(ex, 200, "text/html",
                "<html><link rel=\"manifest\" href=\"/manifest.webmanifest\"><script src=\"/app.js\"></script><script>navigator.serviceWorker.register('/sw.js')</script></html>",
                Map.of("cache-control", "no-cache")));
            fe.route("/app.js", ex -> MockDeploymentServer.respond(ex, 200, "application/javascript",
                "console.log('ok')", Map.of("cache-control", "public, max-age=31536000, immutable")));
            fe.route("/manifest.webmanifest", 200, "application/manifest+json", "{}");
            fe.route("/sw.js", ex -> MockDeploymentServer.respond(ex, 200, "application/javascript",
                "self.skipWaiting()", Map.of("cache-control", "no-cache")));
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), null, null, null, true));
            assertTrue(out.result().getDiagnoses().stream()
                .anyMatch(d -> "USER_DEVICE_CHECK".equals(d.getConfidence())));
        }
    }

    // ---- overall-status rules ----

    @Test
    void unreachableFrontendIsUnhealthy() throws Exception {
        // point at a closed port on loopback
        VerificationEngine.Outcome out = engine.verify(ctx("http://127.0.0.1:9", null, null, null, false));
        assertEquals(VerificationStatus.UNHEALTHY, out.overallStatus());
    }

    @Test
    void importantUnknownDoesNotBecomeHealthy() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer(); MockDeploymentServer be = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html><body>App</body></html>");
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", 200, "application/json", "{\"status\":\"UP\"}");
            // OPTIONS preflight times out / errors -> CORS UNKNOWN
            be.route("/api/health", ex -> {
                if (ex.getRequestMethod().equals("OPTIONS")) { ex.close(); }
                else MockDeploymentServer.respond(ex, 200, "application/json", "{\"status\":\"UP\"}");
            });
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), be.baseUrl(), "/api/health", null, false));
            assertNotEquals(VerificationStatus.HEALTHY, out.overallStatus(),
                "an important UNKNOWN (CORS) must not be reported as HEALTHY");
        }
    }

    @Test
    void healthEndpoint200AloneIsNotHealthyWithoutFrontendWiring() throws Exception {
        try (MockDeploymentServer fe = new MockDeploymentServer(); MockDeploymentServer be = new MockDeploymentServer()) {
            // frontend bundle points to localhost -> connection broken even though backend health is 200
            fe.route("/", 200, "text/html", "<html><script src=\"/app.js\"></script></html>");
            fe.route("/app.js", 200, "application/javascript", "const a='http://localhost:8080/api'");
            be.route("/", 200, "text/plain", "root");
            be.route("/api/health", 200, "application/json", "{\"status\":\"UP\"}");
            VerificationEngine.Outcome out = engine.verify(ctx(fe.baseUrl(), be.baseUrl(), "/api/health", null, false));
            assertNotEquals(VerificationStatus.HEALTHY, out.overallStatus());
        }
    }
}
