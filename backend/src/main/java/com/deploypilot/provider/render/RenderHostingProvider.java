package com.deploypilot.provider.render;

import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.HostingProvider;
import com.deploypilot.provider.ProviderApiClient;
import com.deploypilot.provider.ProviderApiClient.ApiResult;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderException;
import com.deploypilot.provider.ProviderProperties;
import com.deploypilot.provider.model.*;
import com.deploypilot.verify.LogSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Render adapter (backend web services). Creates web services on the free plan,
 * upserts environment variables one key at a time (never wiping others),
 * triggers deploys and reads status/logs. Never selects a paid plan.
 */
@Component
public class RenderHostingProvider implements HostingProvider {

    private static final int MAX_SERVICES = 100;
    static final String FREE_PLAN = "free";

    private final ProviderApiClient http;
    private final String baseUrl;
    private final LogSanitizer logSanitizer;

    public RenderHostingProvider(ProviderApiClient http, ProviderProperties properties, LogSanitizer logSanitizer) {
        this.http = http;
        this.baseUrl = properties.renderBaseUrl();
        this.logSanitizer = logSanitizer;
    }

    @Override
    public ProviderType type() { return ProviderType.RENDER; }

    @Override
    public ProviderAccount getAccount(ProviderCredential credential) {
        ApiResult r = http.get(baseUrl + "/owners?limit=1", credential);
        if (r.isUnauthorized()) {
            throw new ProviderException.BadCredentials("Render rejected the API key. Check it has not been revoked.");
        }
        if (!r.isSuccess() || !r.body().isArray()) {
            throw new ProviderException.UnexpectedResult("Render returned status " + r.status() + " for the account.");
        }
        JsonNode owner = r.body().size() > 0 ? r.body().get(0).path("owner") : null;
        String id = owner != null ? owner.path("id").asText(null) : null;
        String label = owner != null ? firstNonBlank(owner.path("name").asText(null), owner.path("email").asText(null), "Render account") : "Render account";
        return new ProviderAccount(id, label, "Web services, environment variables and deploys");
    }

    @Override
    public List<HostingSite> listSites(ProviderCredential credential) {
        ApiResult r = http.get(baseUrl + "/services?limit=" + MAX_SERVICES, credential);
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Render rejected the API key.");
        if (!r.isSuccess() || !r.body().isArray()) {
            throw new ProviderException.UnexpectedResult("Could not list Render services (status " + r.status() + ").");
        }
        List<HostingSite> sites = new ArrayList<>();
        for (JsonNode node : r.body()) {
            JsonNode svc = node.has("service") ? node.path("service") : node;
            sites.add(toSite(svc));
        }
        return sites;
    }

    @Override
    public HostingSite getSite(ProviderCredential credential, String siteId) {
        ApiResult r = http.get(baseUrl + "/services/" + enc(siteId), credential);
        if (r.isNotFound()) throw new ProviderException.NotFound("Render service not found: " + siteId);
        if (!r.isSuccess()) throw new ProviderException.UnexpectedResult("Could not read Render service (status " + r.status() + ").");
        JsonNode svc = r.body().has("service") ? r.body().path("service") : r.body();
        return toSite(svc);
    }

    @Override
    public HostingSite createSite(ProviderCredential credential, CreateSiteRequest request) {
        String ownerId = getAccount(credential).externalId();
        if (ownerId == null) {
            throw new ProviderException.UnexpectedResult("Could not determine the Render owner for the new service.");
        }
        Map<String, Object> envDetails = new LinkedHashMap<>();
        if (request.buildCommand() != null) envDetails.put("buildCommand", request.buildCommand());
        if (request.startCommand() != null) envDetails.put("startCommand", request.startCommand());

        Map<String, Object> serviceDetails = new LinkedHashMap<>();
        String runtime = request.runtime() == null ? "docker" : request.runtime();
        serviceDetails.put("env", runtime);
        serviceDetails.put("plan", FREE_PLAN); // never a paid plan
        serviceDetails.put("region", "oregon");
        if (request.healthCheckPath() != null) serviceDetails.put("healthCheckPath", request.healthCheckPath());
        if (!"docker".equalsIgnoreCase(runtime) && !envDetails.isEmpty()) {
            serviceDetails.put("envSpecificDetails", envDetails);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "web_service");
        payload.put("name", request.name());
        payload.put("ownerId", ownerId);
        payload.put("repo", "https://github.com/" + request.repoFullName());
        payload.put("branch", request.branch());
        if (request.rootDirectory() != null && !request.rootDirectory().isBlank()) {
            payload.put("rootDir", request.rootDirectory());
        }
        payload.put("serviceDetails", serviceDetails);

        ApiResult r = http.post(baseUrl + "/services", payload, credential);
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Render rejected the API key.");
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not create the Render service (status " + r.status() + ").");
        }
        JsonNode svc = r.body().has("service") ? r.body().path("service") : r.body();
        return toSite(svc);
    }

    @Override
    public void setEnvVars(ProviderCredential credential, String siteId, List<EnvVarInput> vars) {
        // Upsert one key at a time so existing variables are never wiped.
        for (EnvVarInput v : vars) {
            ApiResult r = http.put(baseUrl + "/services/" + enc(siteId) + "/env-vars/" + enc(v.key()),
                Map.of("value", v.value()), credential);
            if (!r.isSuccess()) {
                throw new ProviderException.UnexpectedResult(
                    "Could not set Render environment variable " + v.key() + " (status " + r.status() + ").");
            }
        }
    }

    @Override
    public DeploymentStatus triggerDeploy(ProviderCredential credential, String siteId, DeployRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clearCache", request.clearCache() ? "clear" : "do_not_clear");
        if (request.commitSha() != null) payload.put("commitId", request.commitSha());
        ApiResult r = http.post(baseUrl + "/services/" + enc(siteId) + "/deploys", payload, credential);
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not trigger a Render deploy (status " + r.status() + ").");
        }
        return new DeploymentStatus(r.body().path("id").asText(null),
            mapState(r.body().path("status").asText("")), "Deploy started", null);
    }

    @Override
    public DeploymentStatus getDeploymentStatus(ProviderCredential credential, String siteId, String deploymentId) {
        ApiResult r = http.get(baseUrl + "/services/" + enc(siteId) + "/deploys/" + enc(deploymentId), credential);
        if (r.isNotFound()) throw new ProviderException.NotFound("Render deploy not found: " + deploymentId);
        if (!r.isSuccess()) throw new ProviderException.UnexpectedResult("Could not read Render deploy status (status " + r.status() + ").");
        String status = r.body().path("status").asText("");
        return new DeploymentStatus(deploymentId, mapState(status), status, null);
    }

    @Override
    public String getSanitizedLogs(ProviderCredential credential, String siteId, String deploymentId) {
        ApiResult r = http.get(baseUrl + "/services/" + enc(siteId) + "/deploys/" + enc(deploymentId), credential);
        if (!r.isSuccess()) return "No logs available (status " + r.status() + ").";
        StringBuilder sb = new StringBuilder();
        sb.append("status: ").append(r.body().path("status").asText("unknown")).append('\n');
        String commit = r.body().path("commit").path("message").asText(null);
        if (commit != null) sb.append("commit: ").append(commit).append('\n');
        return sanitize(sb.toString());
    }

    @Override
    public void cancelDeploy(ProviderCredential credential, String siteId, String deploymentId) {
        ApiResult r = http.post(baseUrl + "/services/" + enc(siteId) + "/deploys/" + enc(deploymentId) + "/cancel",
            Map.of(), credential);
        if (!r.isSuccess() && !r.isNotFound()) {
            throw new ProviderException.UnexpectedResult("Could not cancel the Render deploy (status " + r.status() + ").");
        }
    }

    @Override
    public DeploymentStatus restart(ProviderCredential credential, String siteId) {
        return triggerDeploy(credential, siteId, new DeployRequest(null, null, false));
    }

    // ---------- internals ----------

    private HostingSite toSite(JsonNode svc) {
        String url = svc.path("serviceDetails").path("url").asText(null);
        String repo = svc.path("repo").asText(null);
        return new HostingSite(svc.path("id").asText(null), svc.path("name").asText(null), blankToNull(url), repoPath(repo));
    }

    private DeploymentState mapState(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "live" -> DeploymentState.LIVE;
            case "build_failed", "update_failed", "pre_deploy_failed", "deactivated" -> DeploymentState.FAILED;
            case "canceled", "cancelled" -> DeploymentState.CANCELED;
            case "created", "queued", "build_in_progress", "update_in_progress", "pre_deploy_in_progress" -> DeploymentState.BUILDING;
            default -> DeploymentState.UNKNOWN;
        };
    }

    /** Extracts "owner/name" from a GitHub repo URL so linked-repo comparison works. */
    private static String repoPath(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) return null;
        String s = repoUrl.replaceFirst("(?i)^https?://github.com/", "").replaceFirst("(?i)\\.git$", "");
        return s.isBlank() ? null : s;
    }

    private String sanitize(String s) {
        try {
            return logSanitizer.sanitize(s).content();
        } catch (IllegalArgumentException e) {
            return "(logs unavailable)";
        }
    }

    private static String enc(String s) { return UriUtils.encodePathSegment(s, StandardCharsets.UTF_8); }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return c;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
