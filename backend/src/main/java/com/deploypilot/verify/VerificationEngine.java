package com.deploypilot.verify;

import com.deploypilot.dto.VerificationResult;
import com.deploypilot.dto.VerificationResult.CheckResult;
import com.deploypilot.dto.VerificationResult.Diagnosis;
import com.deploypilot.dto.VerificationResult.VersionComparison;
import com.deploypilot.model.enums.VerificationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, read-only deployment checks. Every conclusion carries
 * evidence; anything that cannot be established from a response is reported
 * as UNKNOWN rather than guessed. Only GET/HEAD/OPTIONS are ever used.
 */
@Component
public class VerificationEngine {

    public record Context(
        String frontendUrl,
        String backendUrl,
        String healthPath,
        String expectedCommit,
        boolean allowLocal,
        boolean spaExpected,
        String platformHint // e.g. "Render", used only to phrase cold-start advice
    ) {}

    public record Outcome(VerificationResult result, VerificationStatus overallStatus) {}

    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]+src=[\"']([^\"']+)[\"']");
    private static final Pattern CSS_HREF = Pattern.compile("<link[^>]+href=[\"']([^\"']+\\.css[^\"']*)[\"']");
    private static final Pattern MANIFEST_HREF = Pattern.compile("<link[^>]+rel=[\"']manifest[\"'][^>]*href=[\"']([^\"']+)[\"']|<link[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"']manifest[\"']");
    private static final Pattern META_COMMIT = Pattern.compile("<meta[^>]+name=[\"']build-commit[\"'][^>]+content=[\"']([0-9a-fA-F]{7,40})[\"']");
    private static final Pattern BUILD_STAMP = Pattern.compile("build[\\s:]+([0-9a-f]{7,40})\\b");
    private static final Pattern STACK_TRACE = Pattern.compile("(Exception:|\\bat java\\.|\\bat org\\.springframework|Traceback \\(most recent call last\\))");
    private static final Set<String> SECURITY_HEADERS = Set.of("x-content-type-options", "strict-transport-security");

    static final String SUGGESTED_BUILD_METADATA = """
        {
          "version": "1.0.0",
          "commitSha": "<short-or-full-commit>",
          "buildTime": "<ISO-8601 timestamp>",
          "environment": "production"
        }""";

    private final SafeHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public VerificationEngine(SafeHttpClient http) { this.http = http; }

    public Outcome verify(Context ctx) {
        VerificationResult r = new VerificationResult();

        FrontendFacts frontend = ctx.frontendUrl() != null && !ctx.frontendUrl().isBlank()
            ? checkFrontend(ctx, r) : null;
        if (frontend == null) r.getSkippedChecks().add("Frontend checks skipped: no frontend URL provided");

        BackendFacts backend = ctx.backendUrl() != null && !ctx.backendUrl().isBlank()
            ? checkBackend(ctx, r, frontend) : null;
        if (backend == null) {
            r.getSkippedChecks().add("Backend checks skipped: no backend URL provided");
            r.setCorsResult("SKIPPED");
        }

        checkConnection(ctx, r, frontend, backend);
        checkVersion(ctx, r, frontend, backend);

        VerificationStatus overall = overallStatus(r, frontend, backend);
        r.setSummary(summarize(overall, r));
        return new Outcome(r, overall);
    }

    // ==================== frontend ====================

    private static class FrontendFacts {
        boolean reachable;
        String html;
        String htmlCacheControl;
        String mainBundleUrl;
        String bundle;
        boolean bundleHasLocalhost;
        boolean bundleHasPlaceholder;
        Boolean bundleReferencesBackend; // null = could not check
        boolean bundleReferencesHttpBackend;
        String liveCommit;
        boolean isPwa;
    }

    private FrontendFacts checkFrontend(Context ctx, VerificationResult r) {
        FrontendFacts f = new FrontendFacts();
        String base = trimSlash(ctx.frontendUrl());

        SafeHttpClient.SafeResponse home = http.get(base + "/", ctx.allowLocal());
        String contentType = home.header("content-type") == null ? "" : home.header("content-type");
        f.htmlCacheControl = home.header("cache-control");

        if (home.errorMessage() != null) {
            add(r, "frontend.reachable", "FRONTEND", "Frontend is reachable over HTTPS", "FAIL",
                home.errorMessage(), home.timingMs());
            diagnose(r, "BLOCKER", "CONFIRMED", "FRONTEND", "Frontend URL is not reachable",
                home.errorMessage(),
                home.timedOut() ? "The host did not respond within the time budget." : "DNS, TLS or connection failure.",
                "Confirm the URL is the deployed production address and the site is published.", "PROVIDER_SETTINGS");
            return f;
        }
        String providerError = providerErrorPage(home);
        if (providerError != null) {
            add(r, "frontend.reachable", "FRONTEND", "Frontend is reachable over HTTPS", "FAIL",
                providerError + " (HTTP " + home.status() + ")", home.timingMs());
            diagnose(r, "BLOCKER", "CONFIRMED", "FRONTEND", "A provider error page is being served",
                providerError, "The platform has no successfully published deployment at this URL.",
                "Open the hosting dashboard, check the latest deploy's build log, and publish a successful build.",
                "PROVIDER_SETTINGS");
            return f;
        }
        boolean htmlOk = home.status() >= 200 && home.status() < 300 && contentType.contains("html");
        add(r, "frontend.reachable", "FRONTEND", "Frontend serves an HTML page", htmlOk ? "PASS" : "FAIL",
            "HTTP " + home.status() + ", content-type " + (contentType.isEmpty() ? "(none)" : contentType),
            home.timingMs());
        if (!htmlOk) return f;
        f.reachable = true;
        f.html = home.body() == null ? "" : home.body();

        // assets referenced by the served HTML
        List<String> assets = extractAssets(f.html, base);
        if (assets.isEmpty()) {
            add(r, "frontend.assets", "FRONTEND", "Main JS/CSS assets are reachable", "UNKNOWN",
                "No script/css references found in the HTML", 0);
        } else {
            int checked = 0, failed = 0;
            StringBuilder ev = new StringBuilder();
            for (String asset : assets) {
                if (checked >= 2) break;
                SafeHttpClient.SafeResponse a = http.get(asset, ctx.allowLocal());
                checked++;
                if (!a.ok() || a.status() >= 400) {
                    failed++;
                    ev.append(asset).append(" -> ").append(a.errorMessage() != null ? a.errorMessage() : "HTTP " + a.status()).append("; ");
                } else {
                    ev.append(shortPath(asset)).append(" -> HTTP ").append(a.status()).append("; ");
                    if (f.bundle == null && asset.endsWith(".js")) {
                        f.mainBundleUrl = asset;
                        f.bundle = a.body();
                    }
                }
            }
            add(r, "frontend.assets", "FRONTEND", "Main JS/CSS assets are reachable",
                failed == 0 ? "PASS" : "FAIL", ev.toString().trim(), 0);
            if (failed > 0) {
                diagnose(r, "BLOCKER", "CONFIRMED", "FRONTEND", "The served HTML references missing assets",
                    ev.toString(), "The HTML being served is older or newer than the deployed assets — often a stale cached app shell or an interrupted deploy.",
                    "Redeploy the frontend; if it persists, review CDN/cache headers for index.html.", "REBUILD");
            }
        }

        analyzeBundle(ctx, r, f);
        checkSpaFallback(ctx, r, base);
        checkSecurityHeaders(r, home);
        checkPwa(ctx, r, f, base, home);
        return f;
    }

    private void analyzeBundle(Context ctx, VerificationResult r, FrontendFacts f) {
        if (f.bundle == null) {
            add(r, "frontend.bundle", "FRONTEND", "Production bundle content checks", "UNKNOWN",
                "Main JavaScript bundle could not be downloaded for inspection", 0);
            f.bundleReferencesBackend = null;
            return;
        }
        List<String> problems = new ArrayList<>();
        String backendHost = ctx.backendUrl() == null || ctx.backendUrl().isBlank()
            ? null : hostOf(ctx.backendUrl());
        if (backendHost != null) {
            f.bundleReferencesBackend = f.bundle.contains(backendHost);
        } else {
            f.bundleReferencesBackend = null;
        }

        // Third-party browser libraries can contain dormant localhost constants.
        // Supabase Auth, for example, currently ships a localhost:9999 fallback in
        // its production bundle. Do not call the deployment broken when the same
        // bundle demonstrably contains the intended production backend. A localhost
        // reference remains a blocker when the expected backend is absent (or no
        // backend was supplied), which preserves detection of real dev configuration.
        boolean containsLocalhost = f.bundle.contains("http://localhost")
            || f.bundle.contains("https://localhost")
            || f.bundle.matches("(?s).*localhost:\\d{2,5}.*");
        if (containsLocalhost && !Boolean.TRUE.equals(f.bundleReferencesBackend)) {
            f.bundleHasLocalhost = true;
            problems.add("bundle references localhost");
            diagnose(r, "BLOCKER", "CONFIRMED", "CONNECTION", "The production frontend calls localhost",
                "The deployed JavaScript bundle contains a localhost address.",
                "The frontend was built without a production API URL, so browsers try to call the visitor's own machine.",
                "Set the API base URL environment variable on the frontend host to the backend's public URL, then rebuild and redeploy the frontend.",
                "REBUILD");
        }
        if (f.bundle.contains("${BACKEND_PUBLIC_URL}") || f.bundle.contains("${FRONTEND_PUBLIC_URL}")) {
            f.bundleHasPlaceholder = true;
            problems.add("unreplaced blueprint placeholder present");
            diagnose(r, "BLOCKER", "CONFIRMED", "CONNECTION", "Blueprint placeholders were never replaced",
                "The bundle contains a literal ${...} placeholder.",
                "A placeholder value from the deployment plan was pasted as-is into an environment variable.",
                "Replace the placeholder with the real URL in the frontend host's environment settings, then rebuild.",
                "REBUILD");
        }
        if (ctx.backendUrl() != null && !ctx.backendUrl().isBlank()) {
            if (Boolean.FALSE.equals(f.bundleReferencesBackend) && !f.bundleHasLocalhost && !f.bundleHasPlaceholder) {
                problems.add("bundle does not mention the backend host " + backendHost);
                diagnose(r, "WARNING", "LIKELY", "CONNECTION",
                    "The frontend bundle does not reference the given backend",
                    "Host '" + backendHost + "' does not appear in the deployed JavaScript.",
                    "The frontend may point at a different backend, use a relative API path, or it was built before the backend URL was configured.",
                    "Verify the frontend's API base URL variable and rebuild the frontend if it was changed after the last build.",
                    "REBUILD");
            }
            if ("https".equalsIgnoreCase(schemeOf(ctx.frontendUrl()))
                && backendHost != null && f.bundle.contains("http://" + backendHost)) {
                f.bundleReferencesHttpBackend = true;
                problems.add("HTTPS frontend references the backend over plain http");
                diagnose(r, "BLOCKER", "CONFIRMED", "CONNECTION", "HTTPS page calls an HTTP backend",
                    "The bundle contains http://" + backendHost,
                    "Browsers block mixed content, so these API calls will fail.",
                    "Change the frontend's API base URL to https:// and rebuild.", "REBUILD");
            }
        }
        Matcher stamp = BUILD_STAMP.matcher(f.bundle);
        if (stamp.find()) f.liveCommit = stamp.group(1);

        add(r, "frontend.bundle", "FRONTEND", "Production bundle content checks",
            problems.isEmpty() ? "PASS" : "FAIL",
            problems.isEmpty()
                ? "No localhost references or unreplaced placeholders in " + shortPath(f.mainBundleUrl)
                : String.join("; ", problems),
            0);
    }

    private void checkSpaFallback(Context ctx, VerificationResult r, String base) {
        if (!ctx.spaExpected()) {
            r.getSkippedChecks().add("SPA route check skipped: frontend is not a single-page app");
            return;
        }
        String probe = base + "/deploypilot-route-check-" + UUID.randomUUID().toString().substring(0, 8);
        SafeHttpClient.SafeResponse res = http.get(probe, ctx.allowLocal());
        String ct = res.header("content-type") == null ? "" : res.header("content-type");
        if (res.errorMessage() != null) {
            add(r, "frontend.spa", "FRONTEND", "SPA routes survive a direct refresh", "UNKNOWN", res.errorMessage(), res.timingMs());
            return;
        }
        boolean fallbackWorks = res.status() == 200 && ct.contains("html");
        add(r, "frontend.spa", "FRONTEND", "SPA routes survive a direct refresh",
            fallbackWorks ? "PASS" : "WARNING",
            "GET " + shortPath(probe) + " -> HTTP " + res.status(), res.timingMs());
        if (!fallbackWorks) {
            diagnose(r, "WARNING", "CONFIRMED", "FRONTEND", "Deep links return " + res.status() + " instead of the app",
                "A direct request to an app route did not fall back to index.html.",
                "The SPA redirect (e.g. /* -> /index.html) is missing on the hosting platform.",
                "Add the SPA redirect to netlify.toml (or the platform's redirect settings) and redeploy.",
                "CODE_CHANGE");
        }
    }

    private void checkSecurityHeaders(VerificationResult r, SafeHttpClient.SafeResponse home) {
        List<String> missing = new ArrayList<>();
        for (String h : SECURITY_HEADERS) {
            if (home.header(h) == null) missing.add(h);
        }
        CheckResult c = new CheckResult("frontend.securityHeaders", "FRONTEND",
            "Recommended security headers", missing.isEmpty() ? "PASS" : "WARNING",
            missing.isEmpty() ? "x-content-type-options and strict-transport-security present"
                : "Missing: " + String.join(", ", missing), 0);
        for (String h : SECURITY_HEADERS) {
            String v = home.header(h);
            if (v != null) c.getSafeHeaders().put(h, v);
        }
        r.getChecks().add(c);
    }

    private void checkPwa(Context ctx, VerificationResult r, FrontendFacts f, String base,
                          SafeHttpClient.SafeResponse home) {
        Matcher m = MANIFEST_HREF.matcher(f.html);
        String manifestHref = null;
        if (m.find()) manifestHref = m.group(1) != null ? m.group(1) : m.group(2);
        boolean mentionsSw = f.html.contains("serviceWorker") || (f.bundle != null && f.bundle.contains("serviceWorker"));
        if (manifestHref == null && !mentionsSw) {
            r.getSkippedChecks().add("PWA checks skipped: no manifest or service-worker registration detected");
            return;
        }
        f.isPwa = true;

        if (manifestHref != null) {
            SafeHttpClient.SafeResponse man = http.get(resolve(base, manifestHref), ctx.allowLocal());
            boolean ok = man.ok() && man.status() < 400;
            add(r, "pwa.manifest", "PWA", "PWA manifest is available",
                ok ? "PASS" : "FAIL",
                "GET " + manifestHref + " -> " + (man.errorMessage() != null ? man.errorMessage() : "HTTP " + man.status()),
                man.timingMs());
        } else {
            add(r, "pwa.manifest", "PWA", "PWA manifest is available", "UNKNOWN",
                "Service worker registration found but no manifest link in HTML", 0);
        }

        SafeHttpClient.SafeResponse sw = http.get(base + "/sw.js", ctx.allowLocal());
        String swCt = sw.header("content-type") == null ? "" : sw.header("content-type");
        String swBody = sw.body() == null ? "" : sw.body().stripLeading().toLowerCase(Locale.ROOT);
        boolean swIsHtml = swCt.contains("html") || swBody.startsWith("<!doctype") || swBody.startsWith("<html");
        if (sw.errorMessage() != null || sw.status() >= 400) {
            add(r, "pwa.serviceWorker", "PWA", "Service worker is served correctly", "UNKNOWN",
                "GET /sw.js -> " + (sw.errorMessage() != null ? sw.errorMessage() : "HTTP " + sw.status()), sw.timingMs());
        } else if (swIsHtml) {
            add(r, "pwa.serviceWorker", "PWA", "Service worker is served correctly", "FAIL",
                "/sw.js responded with HTML (content-type " + swCt + ") instead of JavaScript", sw.timingMs());
            diagnose(r, "BLOCKER", "CONFIRMED", "PWA", "sw.js is rewritten to index.html",
                "/sw.js returns HTML.",
                "The SPA catch-all redirect also rewrites the service-worker path, so browsers execute HTML as a worker and updates break.",
                "Exclude existing files from the SPA redirect (Netlify serves real files before redirects) or fix the redirect rule ordering.",
                "CODE_CHANGE");
        } else {
            add(r, "pwa.serviceWorker", "PWA", "Service worker is served correctly", "PASS",
                "/sw.js -> HTTP " + sw.status() + ", content-type " + swCt, sw.timingMs());
        }

        // cache headers
        String htmlCache = f.htmlCacheControl == null ? "" : f.htmlCacheControl.toLowerCase(Locale.ROOT);
        boolean htmlRevalidates = htmlCache.contains("no-cache") || htmlCache.contains("no-store")
            || htmlCache.contains("max-age=0") || htmlCache.contains("must-revalidate");
        add(r, "pwa.htmlCache", "PWA", "HTML revalidates on every load",
            htmlRevalidates ? "PASS" : (htmlCache.isEmpty() ? "UNKNOWN" : "FAIL"),
            "cache-control for /: " + (htmlCache.isEmpty() ? "(none)" : htmlCache), 0);
        if (!htmlRevalidates && !htmlCache.isEmpty()) {
            diagnose(r, "WARNING", "LIKELY", "PWA", "The app shell can be cached past deployments",
                "cache-control for the HTML is '" + htmlCache + "'.",
                "Browsers may keep serving an old index.html (and therefore an old app) after you deploy.",
                "Serve index.html with no-cache / must-revalidate headers.", "PROVIDER_SETTINGS");
        }
        String swCache = sw.header("cache-control") == null ? "" : sw.header("cache-control").toLowerCase(Locale.ROOT);
        boolean swFresh = swCache.contains("no-cache") || swCache.contains("no-store") || swCache.contains("max-age=0");
        add(r, "pwa.swCache", "PWA", "Service worker is never long-term cached",
            sw.errorMessage() != null ? "UNKNOWN" : (swFresh ? "PASS" : "WARNING"),
            "cache-control for /sw.js: " + (swCache.isEmpty() ? "(none)" : swCache), 0);

        if (f.mainBundleUrl != null) {
            SafeHttpClient.SafeResponse asset = http.head(f.mainBundleUrl, ctx.allowLocal());
            String assetCache = asset.header("cache-control") == null ? "" : asset.header("cache-control").toLowerCase(Locale.ROOT);
            boolean immutable = assetCache.contains("immutable") || assetCache.contains("max-age=3");
            add(r, "pwa.assetCache", "PWA", "Hashed assets use long-term caching",
                asset.errorMessage() != null ? "UNKNOWN" : (immutable ? "PASS" : "WARNING"),
                "cache-control for " + shortPath(f.mainBundleUrl) + ": " + (assetCache.isEmpty() ? "(none)" : assetCache), 0);
        }

        boolean allPwaPass = r.getChecks().stream()
            .filter(c -> c.getCategory().equals("PWA"))
            .allMatch(c -> c.getStatus().equals("PASS"));
        if (allPwaPass) {
            diagnose(r, "INFO", "USER_DEVICE_CHECK", "PWA",
                "Server-side PWA delivery looks correct",
                "Manifest, service worker and cache headers all check out.",
                "If a specific device still shows an old version, its installed service worker predates these fixes.",
                "On that device: close all tabs/windows of the app, reopen it, and reload once; or clear the site's storage in browser settings.",
                "USER_DEVICE");
        }
    }

    // ==================== backend ====================

    private static class BackendFacts {
        boolean reachable;
        boolean healthOk;
        String workingHealthPath;
        String liveCommit;
    }

    private BackendFacts checkBackend(Context ctx, VerificationResult r, FrontendFacts frontend) {
        BackendFacts b = new BackendFacts();
        String base = trimSlash(ctx.backendUrl());

        SafeHttpClient.SafeResponse root = http.get(base, ctx.allowLocal());
        if (root.errorMessage() != null) {
            boolean coldStartSuspect = root.timedOut()
                || (ctx.platformHint() != null && ctx.platformHint().toLowerCase(Locale.ROOT).contains("render"));
            add(r, "backend.reachable", "BACKEND", "Backend responds to HTTPS requests", "FAIL",
                root.errorMessage(), root.timingMs());
            diagnose(r, "BLOCKER", root.timedOut() ? "LIKELY" : "CONFIRMED", "BACKEND",
                "Backend URL is not responding",
                root.errorMessage(),
                coldStartSuspect
                    ? "Free-tier services sleep after inactivity; the first request can take up to a minute, or the service may be down."
                    : "DNS, TLS or connection failure.",
                coldStartSuspect
                    ? "Wait ~60 seconds and re-run verification. If it still fails, check the service's status and logs in the hosting dashboard."
                    : "Confirm the backend URL and that the service is deployed and live.",
                "PROVIDER_SETTINGS");
            r.setCorsResult("UNKNOWN");
            return b;
        }
        b.reachable = true;
        add(r, "backend.reachable", "BACKEND", "Backend responds to HTTPS requests", "PASS",
            "HTTP " + root.status() + " from base URL in " + root.timingMs() + " ms", root.timingMs());
        if (root.timingMs() > 8_000) {
            diagnose(r, "INFO", "LIKELY", "BACKEND", "Slow first response (possible cold start)",
                "Base URL answered after " + root.timingMs() + " ms.",
                "Free-tier instances sleep after inactivity and wake slowly.",
                "Expect the first request after idle periods to be slow on free tiers; consider a paid instance if this matters.",
                "PROVIDER_SETTINGS");
        }
        if (root.body() != null && STACK_TRACE.matcher(root.body()).find()) {
            add(r, "backend.errors", "BACKEND", "No internal errors exposed publicly", "WARNING",
                "The base URL response contains what looks like a stack trace", 0);
            diagnose(r, "WARNING", "CONFIRMED", "BACKEND", "Stack traces are publicly visible",
                "A stack-trace pattern appears in the response body.",
                "Detailed errors leak implementation details to visitors.",
                "Disable detailed error pages in production configuration.", "CODE_CHANGE");
        } else {
            add(r, "backend.errors", "BACKEND", "No internal errors exposed publicly", "PASS",
                "No stack-trace patterns in the sampled response", 0);
        }

        checkHealth(ctx, r, b, base);
        checkCors(ctx, r, b, base);
        return b;
    }

    private void checkHealth(Context ctx, VerificationResult r, BackendFacts b, String base) {
        List<String> candidates = new ArrayList<>();
        if (ctx.healthPath() != null && !ctx.healthPath().isBlank()) {
            candidates.add(ctx.healthPath().startsWith("/") ? ctx.healthPath() : "/" + ctx.healthPath());
        }
        for (String fallback : List.of("/health", "/api/health", "/actuator/health")) {
            if (!candidates.contains(fallback)) candidates.add(fallback);
        }
        SafeHttpClient.SafeResponse best = null;
        String bestPath = null;
        for (String path : candidates) {
            SafeHttpClient.SafeResponse res = http.get(base + path, ctx.allowLocal());
            if (res.errorMessage() == null && res.status() >= 200 && res.status() < 300) {
                best = res; bestPath = path; break;
            }
            if (best == null) { best = res; bestPath = path; }
        }
        boolean ok = best != null && best.errorMessage() == null && best.status() >= 200 && best.status() < 300;
        String ct = best == null || best.header("content-type") == null ? "" : best.header("content-type");
        b.healthOk = ok;
        b.workingHealthPath = ok ? bestPath : null;
        add(r, "backend.health", "BACKEND", "Health endpoint responds successfully",
            ok ? "PASS" : "FAIL",
            "GET " + bestPath + " -> " + (best == null ? "no response"
                : best.errorMessage() != null ? best.errorMessage()
                : "HTTP " + best.status() + (ct.isEmpty() ? "" : ", " + ct)),
            best == null ? 0 : best.timingMs());
        if (ok && !ct.contains("json")) {
            add(r, "backend.healthContentType", "BACKEND", "Health endpoint returns JSON", "WARNING",
                "content-type is " + (ct.isEmpty() ? "(none)" : ct), 0);
        }
        if (!ok) {
            diagnose(r, "WARNING", "LIKELY", "BACKEND", "No working health endpoint found",
                "Tried: " + String.join(", ", candidates),
                "The health path differs from the configured one, or the API prefix is different than expected.",
                "Confirm the health endpoint path (including any /api prefix) and set it on the verification form.",
                "PROVIDER_SETTINGS");
        } else if (ctx.healthPath() != null && !ctx.healthPath().isBlank()
            && !bestPath.equals(ctx.healthPath().startsWith("/") ? ctx.healthPath() : "/" + ctx.healthPath())) {
            diagnose(r, "INFO", "CONFIRMED", "BACKEND", "Health endpoint found at a different path",
                "Configured " + ctx.healthPath() + " failed but " + bestPath + " works.",
                "The configured health path is wrong — likely a missing or extra /api prefix.",
                "Update the health path to " + bestPath + " everywhere it is configured (including the hosting platform's health check).",
                "PROVIDER_SETTINGS");
        }
        // api prefix sanity from the URL itself
        String basePath = URI.create(base).getPath() == null ? "" : URI.create(base).getPath();
        if (basePath.contains("/api/api")) {
            add(r, "backend.apiPrefix", "BACKEND", "API prefix is not duplicated", "FAIL",
                "Backend URL path contains /api/api", 0);
            diagnose(r, "BLOCKER", "CONFIRMED", "CONNECTION", "Duplicated /api in the backend URL",
                "The configured URL contains /api/api.",
                "The base URL already includes /api and the app appends another one.",
                "Remove the duplicate segment from the frontend's API base URL and rebuild.", "REBUILD");
        } else if (ok && bestPath.startsWith("/api/") && basePath.endsWith("/api")) {
            add(r, "backend.apiPrefix", "BACKEND", "API prefix is not duplicated", "WARNING",
                "Base URL already ends with /api and health also needed /api", 0);
        } else {
            add(r, "backend.apiPrefix", "BACKEND", "API prefix looks consistent", "PASS",
                "Base path '" + (basePath.isEmpty() ? "/" : basePath) + "', health at " + bestPath, 0);
        }
    }

    private void checkCors(Context ctx, VerificationResult r, BackendFacts b, String base) {
        if (ctx.frontendUrl() == null || ctx.frontendUrl().isBlank()) {
            add(r, "backend.cors", "CONNECTION", "Backend accepts the production frontend origin", "SKIPPED",
                "No frontend URL provided", 0);
            r.setCorsResult("SKIPPED");
            return;
        }
        String origin = originOf(ctx.frontendUrl());
        String target = base + (b.workingHealthPath != null ? b.workingHealthPath : "/");
        SafeHttpClient.SafeResponse pre = http.corsPreflight(target, origin, "GET", ctx.allowLocal());
        if (pre.errorMessage() != null) {
            add(r, "backend.cors", "CONNECTION", "Backend accepts the production frontend origin", "UNKNOWN",
                "Preflight failed: " + pre.errorMessage(), pre.timingMs());
            r.setCorsResult("UNKNOWN");
            return;
        }
        String allowOrigin = pre.header("access-control-allow-origin");
        String allowCreds = pre.header("access-control-allow-credentials");
        CheckResult c = new CheckResult("backend.cors", "CONNECTION",
            "Backend accepts the production frontend origin", "UNKNOWN", "", pre.timingMs());
        if (allowOrigin != null) c.getSafeHeaders().put("access-control-allow-origin", allowOrigin);
        if (allowCreds != null) c.getSafeHeaders().put("access-control-allow-credentials", allowCreds);

        if (allowOrigin == null || pre.status() >= 400) {
            c.setStatus("FAIL");
            c.setEvidence("Preflight from " + origin + " -> HTTP " + pre.status() + ", no access-control-allow-origin header");
            r.setCorsResult("REJECTED");
            diagnose(r, "BLOCKER", "CONFIRMED", "CONNECTION", "CORS rejects the production frontend",
                "OPTIONS with Origin " + origin + " returned HTTP " + pre.status() + " without CORS approval.",
                "The backend's allowed-origin setting does not include the production frontend URL.",
                "Set the backend's frontend-origin variable (e.g. FRONTEND_URL) to " + origin + " and redeploy or restart the backend.",
                "PROVIDER_SETTINGS");
        } else if (allowOrigin.equals("*")) {
            if ("true".equalsIgnoreCase(allowCreds)) {
                c.setStatus("FAIL");
                c.setEvidence("allow-origin is * while allow-credentials is true — browsers reject this combination");
                r.setCorsResult("WILDCARD_CREDENTIALS_CONFLICT");
                diagnose(r, "BLOCKER", "CONFIRMED", "CONNECTION", "Wildcard origin with credentials",
                    "access-control-allow-origin: * plus access-control-allow-credentials: true.",
                    "Browsers refuse credentialed requests when the origin is a wildcard.",
                    "Configure the exact frontend origin instead of *.", "CODE_CHANGE");
            } else {
                c.setStatus("PASS");
                c.setEvidence("Wildcard origin allowed (no credentials)");
                r.setCorsResult("ACCEPTED");
            }
        } else if (allowOrigin.equalsIgnoreCase(origin)) {
            c.setStatus("PASS");
            c.setEvidence("Origin " + origin + " explicitly allowed");
            r.setCorsResult("ACCEPTED");
        } else {
            c.setStatus("FAIL");
            c.setEvidence("Backend allows '" + allowOrigin + "' but the production frontend is " + origin);
            r.setCorsResult("WRONG_ORIGIN");
            boolean previewLike = allowOrigin.contains("--") || allowOrigin.contains("deploy-preview");
            diagnose(r, "BLOCKER", "CONFIRMED", "CONNECTION", "CORS allows the wrong frontend origin",
                "Allowed: " + allowOrigin + "; production frontend: " + origin,
                previewLike
                    ? "The backend allows a deploy-preview URL instead of the production URL."
                    : "The backend's frontend-origin setting points at a different domain.",
                "Update the backend's frontend-origin variable to " + origin + " and redeploy or restart the backend.",
                "PROVIDER_SETTINGS");
        }
        r.getChecks().add(c);
    }

    // ==================== connection + version ====================

    private void checkConnection(Context ctx, VerificationResult r, FrontendFacts f, BackendFacts b) {
        if (f == null || b == null) {
            add(r, "connection.wiring", "CONNECTION", "Frontend is wired to this backend", "SKIPPED",
                "Requires both a frontend and a backend URL", 0);
            return;
        }
        if (f.bundleHasLocalhost || f.bundleHasPlaceholder || f.bundleReferencesHttpBackend) {
            add(r, "connection.wiring", "CONNECTION", "Frontend is wired to this backend", "FAIL",
                "The bundle points at localhost, an unreplaced placeholder, or an http:// backend", 0);
        } else if (Boolean.TRUE.equals(f.bundleReferencesBackend)) {
            boolean corsOk = "ACCEPTED".equals(r.getCorsResult());
            add(r, "connection.wiring", "CONNECTION", "Frontend is wired to this backend",
                corsOk ? "PASS" : "WARNING",
                "Bundle references " + hostOf(ctx.backendUrl())
                    + (corsOk ? " and CORS accepts the frontend origin" : ", but CORS result is " + r.getCorsResult()), 0);
        } else if (f.bundleReferencesBackend == null) {
            add(r, "connection.wiring", "CONNECTION", "Frontend is wired to this backend", "UNKNOWN",
                "The bundle could not be inspected", 0);
        } else {
            add(r, "connection.wiring", "CONNECTION", "Frontend is wired to this backend", "WARNING",
                "The bundle does not reference the backend host", 0);
        }
    }

    private void checkVersion(Context ctx, VerificationResult r, FrontendFacts f, BackendFacts b) {
        VersionComparison v = new VersionComparison();
        v.setExpectedCommit(ctx.expectedCommit());
        List<String> evidence = new ArrayList<>();

        if (f != null && f.html != null) {
            Matcher meta = META_COMMIT.matcher(f.html);
            if (meta.find()) { f.liveCommit = meta.group(1); evidence.add("HTML meta build-commit"); }
            else if (f.liveCommit != null) evidence.add("build stamp in bundle");
            else {
                Matcher stamp = BUILD_STAMP.matcher(f.html);
                if (stamp.find()) { f.liveCommit = stamp.group(1); evidence.add("build stamp in HTML"); }
            }
        }
        if (f != null) v.setLiveFrontendCommit(f.liveCommit);

        if (b != null && b.reachable && ctx.backendUrl() != null) {
            String base = trimSlash(ctx.backendUrl());
            for (String path : List.of("/version", "/api/version", "/version.json")) {
                SafeHttpClient.SafeResponse res = http.get(base + path, ctx.allowLocal());
                if (res.errorMessage() == null && res.status() == 200 && res.body() != null) {
                    String commit = commitFromJson(res.body());
                    if (commit != null) {
                        b.liveCommit = commit;
                        evidence.add("backend " + path);
                        break;
                    }
                }
            }
            v.setLiveBackendCommit(b.liveCommit);
        }

        String fe = v.getLiveFrontendCommit(), be = v.getLiveBackendCommit(), exp = ctx.expectedCommit();
        String state;
        if (fe == null && be == null) {
            state = "UNKNOWN";
            v.setSuggestion("Expose safe build metadata so DeployPilot can verify versions, e.g. a public /version.json:\n"
                + SUGGESTED_BUILD_METADATA);
        } else if (fe != null && be != null && !sameCommit(fe, be)) {
            state = "MISMATCHED";
        } else if (exp != null && !exp.isBlank()) {
            String live = fe != null ? fe : be;
            state = sameCommit(live, exp) ? "CURRENT" : "OUTDATED";
        } else {
            state = "UNKNOWN";
            evidence.add("no expected commit configured to compare against");
        }
        v.setState(state);
        v.setEvidence(evidence.isEmpty() ? "No build metadata found" : String.join("; ", evidence));
        r.setVersion(v);

        String status = switch (state) {
            case "CURRENT" -> "PASS";
            case "OUTDATED", "MISMATCHED" -> "WARNING";
            default -> "UNKNOWN";
        };
        add(r, "version.match", "VERSION", "Deployed version matches the expected commit", status,
            "state " + state
                + (fe != null ? ", frontend " + fe : "")
                + (be != null ? ", backend " + be : "")
                + (exp != null && !exp.isBlank() ? ", expected " + exp : ""), 0);
        if ("OUTDATED".equals(state)) {
            diagnose(r, "WARNING", "CONFIRMED", "VERSION", "The live deployment is not the expected version",
                "Live commit differs from the expected commit " + exp + ".",
                "The latest repository version has not been deployed, or an older deploy was published.",
                "Trigger a fresh deploy of the latest commit and clear any stale caches.", "REBUILD");
        } else if ("MISMATCHED".equals(state)) {
            diagnose(r, "WARNING", "CONFIRMED", "VERSION", "Frontend and backend run different versions",
                "Frontend " + fe + " vs backend " + be + ".",
                "One side was redeployed without the other.",
                "Redeploy both components from the same commit.", "REBUILD");
        } else if ("UNKNOWN".equals(state) && fe == null && be == null) {
            diagnose(r, "INFO", "CONFIRMED", "VERSION", "No build metadata is exposed",
                "Neither the frontend nor the backend exposes a commit stamp or version endpoint.",
                "Without build metadata, version verification cannot be automated.",
                "Add a visible build stamp or a public /version.json using the suggested format.", "CODE_CHANGE");
        }
    }

    // ==================== overall ====================

    private VerificationStatus overallStatus(VerificationResult r, FrontendFacts f, BackendFacts b) {
        boolean anyFail = r.getChecks().stream().anyMatch(c -> c.getStatus().equals("FAIL"));
        boolean reachFail = r.getChecks().stream().anyMatch(c ->
            (c.getId().equals("frontend.reachable") || c.getId().equals("backend.reachable"))
                && c.getStatus().equals("FAIL"));
        boolean connectionBroken = "REJECTED".equals(r.getCorsResult())
            || "WRONG_ORIGIN".equals(r.getCorsResult())
            || "WILDCARD_CREDENTIALS_CONFLICT".equals(r.getCorsResult())
            || r.getChecks().stream().anyMatch(c -> c.getId().equals("connection.wiring") && c.getStatus().equals("FAIL"));
        boolean importantUnknown = r.getChecks().stream().anyMatch(c ->
            c.getStatus().equals("UNKNOWN")
                && (c.getId().equals("backend.cors") || c.getId().equals("connection.wiring")
                    || c.getId().equals("frontend.reachable") || c.getId().equals("backend.reachable")));
        boolean anyWarning = r.getChecks().stream().anyMatch(c -> c.getStatus().equals("WARNING"));

        if (reachFail || connectionBroken) return VerificationStatus.UNHEALTHY;
        if (anyFail) return VerificationStatus.DEGRADED;
        if (importantUnknown) return VerificationStatus.INCONCLUSIVE;
        if (anyWarning) return VerificationStatus.DEGRADED;
        return VerificationStatus.HEALTHY;
    }

    private String summarize(VerificationStatus status, VerificationResult r) {
        Diagnosis top = r.getDiagnoses().stream()
            .filter(d -> d.getSeverity().equals("BLOCKER")).findFirst()
            .orElse(r.getDiagnoses().stream().filter(d -> d.getSeverity().equals("WARNING")).findFirst().orElse(null));
        return switch (status) {
            case HEALTHY -> "Everything checked out: the deployment is reachable, wired together and answering correctly."
                + ("CURRENT".equals(r.getVersion() != null ? r.getVersion().getState() : null)
                    ? " The live version matches the expected commit." : "");
            case DEGRADED -> top != null
                ? "The deployment is online but has an issue: " + top.getTitle() + ". " + top.getRecommendedAction()
                : "The deployment is online but some checks reported warnings — review them below.";
            case UNHEALTHY -> top != null
                ? top.getTitle() + ". " + top.getRecommendedAction()
                : "A critical check failed — review the failed checks below.";
            case INCONCLUSIVE -> "Some important checks could not be completed, so no overall health claim can be made. "
                + "Re-run the verification; if it persists, check the URLs.";
            default -> "Verification did not complete.";
        };
    }

    // ==================== helpers ====================

    private void add(VerificationResult r, String id, String category, String title,
                     String status, String evidence, long timingMs) {
        r.getChecks().add(new CheckResult(id, category, title, status, evidence, timingMs));
    }

    private void diagnose(VerificationResult r, String severity, String confidence, String component,
                          String title, String evidence, String cause, String action, String actionType) {
        Diagnosis d = new Diagnosis();
        d.setSeverity(severity); d.setConfidence(confidence); d.setAffectedComponent(component);
        d.setTitle(title); d.setEvidence(evidence); d.setLikelyCause(cause);
        d.setRecommendedAction(action); d.setActionType(actionType);
        r.getDiagnoses().add(d);
    }

    private String providerErrorPage(SafeHttpClient.SafeResponse res) {
        String body = res.body() == null ? "" : res.body();
        if (res.status() == 404 && res.header("x-nf-request-id") != null
            && (body.contains("Not Found") || body.contains("not found"))) {
            return "Netlify 'Page not found' page (site has no published deploy or the path is unpublished)";
        }
        if ("no-server".equals(res.header("x-render-routing"))) {
            return "Render 'no-server' response (no live service at this URL)";
        }
        if (body.contains("DEPLOYMENT_NOT_FOUND") || body.contains("NOT_FOUND") && res.header("x-vercel-id") != null) {
            return "Vercel deployment-not-found page";
        }
        return null;
    }

    private List<String> extractAssets(String html, String base) {
        List<String> assets = new ArrayList<>();
        Matcher js = SCRIPT_SRC.matcher(html);
        while (js.find() && assets.size() < 3) assets.add(resolve(base, js.group(1)));
        Matcher css = CSS_HREF.matcher(html);
        while (css.find() && assets.size() < 4) assets.add(resolve(base, css.group(1)));
        return assets;
    }

    private String commitFromJson(String body) {
        try {
            JsonNode node = json.readTree(body);
            for (String field : List.of("commitSha", "commit", "sha", "gitCommit", "build")) {
                String value = node.path(field).asText(null);
                if (value != null && value.matches("[0-9a-fA-F]{7,40}")) return value;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean sameCommit(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.toLowerCase(Locale.ROOT), y = b.toLowerCase(Locale.ROOT);
        return x.startsWith(y) || y.startsWith(x);
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String resolve(String base, String href) {
        try {
            return URI.create(base + "/").resolve(href).toString();
        } catch (IllegalArgumentException e) {
            return base + "/" + href;
        }
    }

    private String hostOf(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return null; }
    }

    private String schemeOf(String url) {
        try { return URI.create(url).getScheme(); } catch (Exception e) { return null; }
    }

    private String originOf(String url) {
        URI u = URI.create(url);
        int port = u.getPort();
        return u.getScheme() + "://" + u.getHost()
            + (port == -1 || port == 80 && "http".equals(u.getScheme()) || port == 443 && "https".equals(u.getScheme())
                ? "" : ":" + port);
    }

    private String shortPath(String url) {
        if (url == null) return "(none)";
        try {
            URI u = URI.create(url);
            return u.getPath() == null || u.getPath().isEmpty() ? url : u.getPath();
        } catch (Exception e) {
            return url;
        }
    }
}
