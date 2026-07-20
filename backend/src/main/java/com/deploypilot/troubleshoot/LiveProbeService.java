package com.deploypilot.troubleshoot;

import com.deploypilot.util.SecretRedactionUtil;
import com.deploypilot.verify.SafeHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Live, read-only probes run at troubleshoot time so the Copilot reasons from the
 * deployment's state <em>right now</em>, not just the state recorded when a step
 * failed. Uses the SSRF-safe {@link SafeHttpClient} (HTTPS-only in production,
 * private/loopback/metadata addresses blocked, GET/HEAD/OPTIONS only) against the
 * URLs DeployPilot itself captured from providers.
 *
 * <p>Strictly evidence-first: a probe that cannot run leaves its flag {@code null}
 * (UNKNOWN) and never guesses. Only statuses and timings are recorded — never
 * response bodies, tokens or headers.
 */
@Service
public class LiveProbeService {

    private static final Logger log = LoggerFactory.getLogger(LiveProbeService.class);
    private static final List<String> HEALTH_PATHS = List.of("/api/health", "/health", "/actuator/health");

    private final SafeHttpClient http;
    private final boolean allowLocal;

    public LiveProbeService(SafeHttpClient http,
                            @Value("${deploypilot.automation.verify-allow-local:false}") boolean allowLocal) {
        this.http = http;
        this.allowLocal = allowLocal;
    }

    /** Populates {@code ctx} live-check facts and flags. Never throws. */
    public void probe(TroubleshootingContext ctx) {
        probeFrontend(ctx);
        probeBackendHealth(ctx);
        probeCors(ctx);
    }

    private void probeFrontend(TroubleshootingContext ctx) {
        String url = ctx.getFrontendUrl();
        if (url == null || url.isBlank()) return;
        try {
            SafeHttpClient.SafeResponse res = http.get(url, allowLocal);
            if (res.errorMessage() != null) {
                ctx.setLiveFrontendOk(false);
                addCheck(ctx, "Frontend right now: not reachable (" + res.errorMessage() + ").");
            } else {
                boolean ok = res.status() >= 200 && res.status() < 400;
                ctx.setLiveFrontendOk(ok);
                addCheck(ctx, "Frontend right now: HTTP " + res.status() + " in " + res.timingMs() + " ms"
                    + (ok ? " (loads)." : "."));
            }
        } catch (Exception e) {
            log.debug("Live frontend probe unavailable: {}", e.getClass().getSimpleName());
            addCheck(ctx, "Frontend right now: could not be checked (unknown).");
        }
    }

    private void probeBackendHealth(TroubleshootingContext ctx) {
        String base = ctx.getBackendUrl();
        if (base == null || base.isBlank()) return;
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        try {
            SafeHttpClient.SafeResponse best = null;
            String bestPath = null;
            for (String path : HEALTH_PATHS) {
                SafeHttpClient.SafeResponse res = http.get(trimmed + path, allowLocal);
                if (res.errorMessage() == null && res.status() >= 200 && res.status() < 300) {
                    best = res; bestPath = path; break;
                }
                if (best == null) { best = res; bestPath = path; }
            }
            boolean ok = best != null && best.errorMessage() == null && best.status() >= 200 && best.status() < 300;
            ctx.setLiveBackendOk(ok);
            addCheck(ctx, "Backend health right now: " + (best == null ? "no response"
                : best.errorMessage() != null ? "not reachable (" + best.errorMessage() + ")"
                : "GET " + bestPath + " -> HTTP " + best.status() + " in " + best.timingMs() + " ms")
                + (ok ? " (healthy)." : "."));
        } catch (Exception e) {
            log.debug("Live backend probe unavailable: {}", e.getClass().getSimpleName());
            addCheck(ctx, "Backend health right now: could not be checked (unknown).");
        }
    }

    private void probeCors(TroubleshootingContext ctx) {
        String backend = ctx.getBackendUrl();
        String frontend = ctx.getFrontendUrl();
        if (backend == null || frontend == null || backend.isBlank() || frontend.isBlank()) return;
        String origin = frontend.endsWith("/") ? frontend.substring(0, frontend.length() - 1) : frontend;
        String trimmed = backend.endsWith("/") ? backend.substring(0, backend.length() - 1) : backend;
        try {
            SafeHttpClient.SafeResponse res = http.corsPreflight(trimmed + "/api/health", origin, "GET", allowLocal);
            if (res.errorMessage() != null) {
                addCheck(ctx, "Frontend→backend connection (CORS) right now: could not be checked (unknown).");
                return;
            }
            String allow = res.header("access-control-allow-origin");
            boolean ok = allow != null && (allow.equals("*") || allow.equalsIgnoreCase(origin));
            ctx.setLiveCorsOk(ok);
            addCheck(ctx, "Frontend→backend connection (CORS) right now: "
                + (ok ? "the backend allows the frontend origin." : "the backend did not allow the frontend origin."));
        } catch (Exception e) {
            log.debug("Live CORS probe unavailable: {}", e.getClass().getSimpleName());
            addCheck(ctx, "Frontend→backend connection (CORS) right now: could not be checked (unknown).");
        }
    }

    private void addCheck(TroubleshootingContext ctx, String text) {
        ctx.getLiveChecks().add(SecretRedactionUtil.redact(text));
    }
}
