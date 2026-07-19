package com.deploypilot.provider.netlify;

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
 * Netlify adapter (frontend hosting). Creates sites on the free plan, sets build
 * environment variables, triggers builds and reads deploy status/logs. Never
 * receives backend secrets (the caller decides which variables are frontend-safe).
 */
@Component
public class NetlifyHostingProvider implements HostingProvider {

    private static final int MAX_SITES = 100;
    /**
     * Netlify free accounts cannot select a granular environment-variable scope.
     * Sending every supported scope preserves the free-plan behaviour while also
     * satisfying the current account environment API schema.
     */
    private static final List<String> FREE_PLAN_ENV_SCOPES =
        List.of("builds", "functions", "runtime", "post-processing");

    private final ProviderApiClient http;
    private final String baseUrl;
    private final LogSanitizer logSanitizer;

    public NetlifyHostingProvider(ProviderApiClient http, ProviderProperties properties, LogSanitizer logSanitizer) {
        this.http = http;
        this.baseUrl = properties.netlifyBaseUrl();
        this.logSanitizer = logSanitizer;
    }

    @Override
    public ProviderType type() { return ProviderType.NETLIFY; }

    @Override
    public ProviderAccount getAccount(ProviderCredential credential) {
        ApiResult r = http.get(baseUrl + "/user", credential);
        if (r.isUnauthorized()) {
            throw new ProviderException.BadCredentials("Netlify rejected the token. Check it has not expired.");
        }
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Netlify returned status " + r.status() + " for the account.");
        }
        String label = firstNonBlank(r.body().path("full_name").asText(null), r.body().path("email").asText(null), "Netlify account");
        return new ProviderAccount(r.body().path("id").asText(null), label, "Sites, builds and environment variables");
    }

    @Override
    public List<HostingSite> listSites(ProviderCredential credential) {
        ApiResult r = http.get(baseUrl + "/sites?per_page=" + MAX_SITES, credential);
        if (r.isUnauthorized()) {
            throw new ProviderException.BadCredentials("Netlify rejected the token.");
        }
        if (!r.isSuccess() || !r.body().isArray()) {
            throw new ProviderException.UnexpectedResult("Could not list Netlify sites (status " + r.status() + ").");
        }
        List<HostingSite> sites = new ArrayList<>();
        for (JsonNode node : r.body()) {
            sites.add(toSite(node));
        }
        return sites;
    }

    @Override
    public HostingSite getSite(ProviderCredential credential, String siteId) {
        ApiResult r = http.get(baseUrl + "/sites/" + enc(siteId), credential);
        if (r.isNotFound()) throw new ProviderException.NotFound("Netlify site not found: " + siteId);
        if (!r.isSuccess()) throw new ProviderException.UnexpectedResult("Could not read Netlify site (status " + r.status() + ").");
        return toSite(r.body());
    }

    @Override
    public HostingSite createSite(ProviderCredential credential, CreateSiteRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.name() != null) payload.put("name", request.name());
        payload.put("repo", repoInfo(request));

        ApiResult r = http.post(baseUrl + "/sites", payload, credential);
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not create the Netlify site (status " + r.status() + ").");
        }
        return toSite(r.body());
    }

    @Override
    public HostingSite configureSite(ProviderCredential credential, String siteId, CreateSiteRequest request) {
        ApiResult current = http.get(baseUrl + "/sites/" + enc(siteId), credential);
        requireSiteSuccess(current, siteId, "read");

        JsonNode currentRepo = current.body().path("build_settings");
        boolean stalePublicBinding = request.publicRepository()
            && (!currentRepo.path("public_repo").asBoolean(false)
                || !currentRepo.path("deploy_key_id").asText("").isBlank());
        if (stalePublicBinding) {
            // Netlify can retain a deploy key from a previous/invalid repository
            // link and rewrite an HTTPS clone through SSH, causing "Host key
            // verification failed" even for a public GitHub repository. The
            // official unlink operation removes that deploy key and its hooks;
            // the PATCH below immediately relinks the same site as public.
            ApiResult unlinked = http.put(baseUrl + "/sites/" + enc(siteId) + "/unlink_repo", credential);
            requireSiteSuccess(unlinked, siteId, "clear stale repository credentials from");
        }

        ApiResult r = http.patch(baseUrl + "/sites/" + enc(siteId),
            Map.of("repo", repoInfo(request)), credential);
        requireSiteSuccess(r, siteId, "update repository settings for");
        if (request.publicRepository()) {
            JsonNode configured = r.body().path("build_settings");
            if (!configured.path("public_repo").asBoolean(false)
                || !configured.path("deploy_key_id").asText("").isBlank()) {
                throw new ProviderException.UnexpectedResult(
                    "Netlify did not relink the site as a public repository. Retry after the site configuration refreshes.");
            }
        }
        return toSite(r.body());
    }

    @Override
    public void setEnvVars(ProviderCredential credential, String siteId, List<EnvVarInput> vars) {
        if (vars.isEmpty()) return;

        ApiResult site = http.get(baseUrl + "/sites/" + enc(siteId), credential);
        if (site.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        if (site.isNotFound()) throw new ProviderException.NotFound("Netlify site not found: " + siteId);
        if (!site.isSuccess()) {
            throw new ProviderException.UnexpectedResult(
                "Could not read the Netlify site before setting variables (status " + site.status() + ").");
        }
        String accountId = site.body().path("account_id").asText(null);
        if (accountId == null || accountId.isBlank()) {
            throw new ProviderException.UnexpectedResult("Could not determine the Netlify account for the site.");
        }

        String envUrl = baseUrl + "/accounts/" + enc(accountId) + "/env?site_id=" + query(siteId);
        ApiResult current = http.get(envUrl, credential);
        if (current.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        if (!current.isSuccess() || !current.body().isArray()) {
            throw new ProviderException.UnexpectedResult(
                "Could not read Netlify environment variables (status " + current.status() + ").");
        }

        Map<String, Boolean> existing = new LinkedHashMap<>();
        for (JsonNode node : current.body()) {
            String key = node.path("key").asText(null);
            if (key != null) existing.put(key, true);
        }

        List<Map<String, Object>> create = new ArrayList<>();
        for (EnvVarInput v : vars) {
            Map<String, Object> value = envVar(v);
            if (existing.containsKey(v.key())) {
                ApiResult updated = http.put(baseUrl + "/accounts/" + enc(accountId) + "/env/" + enc(v.key())
                    + "?site_id=" + query(siteId), value, credential);
                requireEnvSuccess(updated, "update", v.key());
            } else {
                create.add(value);
            }
        }
        if (!create.isEmpty()) {
            ApiResult created = http.post(envUrl, create, credential);
            requireEnvSuccess(created, "create", null);
        }
    }

    @Override
    public DeploymentStatus triggerDeploy(ProviderCredential credential, String siteId, DeployRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.branch() != null) payload.put("branch", request.branch());
        payload.put("clear_cache", request.clearCache());
        ApiResult r = http.post(baseUrl + "/sites/" + enc(siteId) + "/builds", payload, credential);
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not trigger a Netlify build (status " + r.status() + ").");
        }
        String deployId = firstNonBlank(r.body().path("deploy_id").asText(null), r.body().path("id").asText(null), null);
        return new DeploymentStatus(deployId, DeploymentState.QUEUED, "Build queued", null);
    }

    @Override
    public DeploymentStatus getDeploymentStatus(ProviderCredential credential, String siteId, String deploymentId) {
        ApiResult r = http.get(baseUrl + "/sites/" + enc(siteId) + "/deploys/" + enc(deploymentId), credential);
        if (r.isNotFound()) throw new ProviderException.NotFound("Netlify deploy not found: " + deploymentId);
        if (!r.isSuccess()) throw new ProviderException.UnexpectedResult("Could not read Netlify deploy status (status " + r.status() + ").");
        String state = r.body().path("state").asText("");
        String url = firstNonBlank(r.body().path("ssl_url").asText(null), r.body().path("url").asText(null), null);
        String detail = r.body().path("error_message").asText(null);
        return new DeploymentStatus(deploymentId, mapState(state), detail != null ? sanitize(detail) : state, url);
    }

    @Override
    public String getSanitizedLogs(ProviderCredential credential, String siteId, String deploymentId) {
        ApiResult r = http.get(baseUrl + "/sites/" + enc(siteId) + "/deploys/" + enc(deploymentId), credential);
        if (!r.isSuccess()) return "No logs available (status " + r.status() + ").";
        StringBuilder sb = new StringBuilder();
        sb.append("state: ").append(r.body().path("state").asText("unknown")).append('\n');
        String summary = r.body().path("summary").path("status").asText(null);
        if (summary != null) sb.append(summary).append('\n');
        String error = r.body().path("error_message").asText(null);
        if (error != null) sb.append(error).append('\n');
        return sanitize(sb.toString());
    }

    @Override
    public void cancelDeploy(ProviderCredential credential, String siteId, String deploymentId) {
        ApiResult r = http.post(baseUrl + "/deploys/" + enc(deploymentId) + "/cancel", Map.of(), credential);
        if (!r.isSuccess() && !r.isNotFound()) {
            throw new ProviderException.UnexpectedResult("Could not cancel the Netlify deploy (status " + r.status() + ").");
        }
    }

    @Override
    public DeploymentStatus restart(ProviderCredential credential, String siteId) {
        return triggerDeploy(credential, siteId, new DeployRequest(null, null, true));
    }

    // ---------- internals ----------

    private Map<String, Object> repoInfo(CreateSiteRequest request) {
        Map<String, Object> repo = new LinkedHashMap<>();
        repo.put("provider", "github");
        repo.put("repo_path", request.repoFullName());
        repo.put("repo_branch", firstNonBlank(request.branch(), null, "main"));
        repo.put("repo_url", "https://github.com/" + request.repoFullName() + ".git");
        if (request.publicRepository()) repo.put("public_repo", true);
        if (request.buildCommand() != null) repo.put("cmd", request.buildCommand());
        if (request.publishDirectory() != null) repo.put("dir", request.publishDirectory());
        return repo;
    }

    private Map<String, Object> envVar(EnvVarInput v) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("key", v.key());
        env.put("scopes", FREE_PLAN_ENV_SCOPES);
        env.put("values", List.of(Map.of("value", v.value(), "context", "all")));
        env.put("is_secret", v.secret());
        return env;
    }

    private void requireEnvSuccess(ApiResult r, String operation, String key) {
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        if (!r.isSuccess()) {
            String suffix = key == null ? "" : " " + key;
            throw new ProviderException.UnexpectedResult(
                "Could not " + operation + " Netlify environment variable" + suffix + " (status " + r.status() + ").");
        }
    }

    private void requireSiteSuccess(ApiResult r, String siteId, String operation) {
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        if (r.isNotFound()) throw new ProviderException.NotFound("Netlify site not found: " + siteId);
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult(
                "Could not " + operation + " the Netlify site (status " + r.status() + ").");
        }
    }

    private HostingSite toSite(JsonNode node) {
        String url = firstNonBlank(node.path("ssl_url").asText(null), node.path("url").asText(null), null);
        String linkedRepo = node.path("build_settings").path("repo_path").asText(null);
        return new HostingSite(node.path("id").asText(null), node.path("name").asText(null), url, blankToNull(linkedRepo));
    }

    private DeploymentState mapState(String state) {
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "ready", "current" -> DeploymentState.LIVE;
            case "error", "rejected" -> DeploymentState.FAILED;
            case "canceled", "cancelled" -> DeploymentState.CANCELED;
            case "new", "enqueued", "building", "processing", "uploading", "uploaded", "preparing", "prepared" -> DeploymentState.BUILDING;
            default -> DeploymentState.UNKNOWN;
        };
    }

    private String sanitize(String s) {
        try {
            return logSanitizer.sanitize(s).content();
        } catch (IllegalArgumentException e) {
            return "(logs unavailable)";
        }
    }

    private static String enc(String s) { return UriUtils.encodePathSegment(s, StandardCharsets.UTF_8); }

    private static String query(String s) { return UriUtils.encodeQueryParam(s, StandardCharsets.UTF_8); }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return c;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
