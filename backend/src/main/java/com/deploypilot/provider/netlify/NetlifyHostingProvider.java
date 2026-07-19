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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /**
     * Create or update frontend build variables on Netlify, idempotently.
     *
     * <p>Uses the current account environment endpoint
     * {@code POST /api/v1/accounts/{account_id}/env?site_id={site_id}} for new
     * variables and {@code PUT .../env/{key}?site_id=...} for existing ones. The
     * two calls are reconciled with the account listing, and each result is treated
     * as data so a reused site never fails just because a variable already exists:
     * a create-conflict falls back to an update, and an update on a missing key
     * falls back to a create.
     *
     * <p>Every value is validated before any network call so a genuinely missing
     * value fails with a clear, variable-specific message instead of an opaque 400.
     * Netlify's response body is captured (sanitised) so 400s are diagnosable, but
     * variable values, tokens and secrets are never logged or surfaced.
     */
    @Override
    public void setEnvVars(ProviderCredential credential, String siteId, List<EnvVarInput> vars) {
        if (vars == null || vars.isEmpty()) return;

        // Validate up front: never send a null/blank value to Netlify (it is rejected
        // with a generic 400). Fail with the offending variable named instead.
        for (EnvVarInput v : vars) {
            if (v.value() == null || v.value().isBlank()) {
                throw new ProviderException.UnexpectedResult(
                    "Cannot set Netlify environment variable " + v.key()
                        + ": no value is available. Provide a value or remove it from the plan.");
            }
        }

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

        Set<String> existing = existingEnvKeys(credential, accountId, siteId, vars);

        List<Map<String, Object>> toCreate = new ArrayList<>();
        for (EnvVarInput v : vars) {
            if (existing.contains(v.key())) {
                updateEnvVar(credential, accountId, siteId, v);
            } else {
                toCreate.add(envVar(v));
            }
        }
        if (!toCreate.isEmpty()) {
            createEnvVars(credential, accountId, siteId, toCreate, vars);
        }
    }

    private Set<String> existingEnvKeys(ProviderCredential credential, String accountId, String siteId,
                                        List<EnvVarInput> vars) {
        ApiResult current = http.get(envUrl(accountId, siteId), credential);
        if (current.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        if (!current.isSuccess() || !current.body().isArray()) {
            throw new ProviderException.UnexpectedResult(
                "Could not read Netlify environment variables (status " + current.status() + ")."
                    + responseDetail(current, vars));
        }
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode node : current.body()) {
            String key = node.path("key").asText(null);
            if (key != null && !key.isBlank()) keys.add(key);
        }
        return keys;
    }

    private void createEnvVars(ProviderCredential credential, String accountId, String siteId,
                               List<Map<String, Object>> toCreate, List<EnvVarInput> all) {
        ApiResult created = http.post(envUrl(accountId, siteId), toCreate, credential);
        if (created.isSuccess()) return;
        if (created.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        // On a reused site, variables can already exist without appearing in the
        // account listing (e.g. created earlier or under the legacy site-level env
        // system). Netlify then answers the create with a 400/409 "already exists".
        // Recover by updating those keys instead of failing the whole deployment.
        if (isAlreadyExists(created)) {
            for (Map<String, Object> item : toCreate) {
                updateEnvVar(credential, accountId, siteId, byKey(all, String.valueOf(item.get("key"))));
            }
            return;
        }
        throw new ProviderException.UnexpectedResult(
            "Could not create Netlify environment variables (status " + created.status() + ")."
                + responseDetail(created, all));
    }

    private void updateEnvVar(ProviderCredential credential, String accountId, String siteId, EnvVarInput v) {
        List<EnvVarInput> one = List.of(v);
        ApiResult updated = http.put(envUrl(accountId, siteId, v.key()), envVar(v), credential);
        if (updated.isSuccess()) return;
        if (updated.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
        // The variable was listed but is gone now (or never really existed): create it.
        if (updated.isNotFound()) {
            ApiResult created = http.post(envUrl(accountId, siteId), List.of(envVar(v)), credential);
            if (created.isSuccess()) return;
            if (created.isUnauthorized()) throw new ProviderException.BadCredentials("Netlify rejected the token.");
            throw new ProviderException.UnexpectedResult(
                "Could not create Netlify environment variable " + v.key()
                    + " (status " + created.status() + ")." + responseDetail(created, one));
        }
        throw new ProviderException.UnexpectedResult(
            "Could not update Netlify environment variable " + v.key()
                + " (status " + updated.status() + ")." + responseDetail(updated, one));
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
        // Send every supported scope: this keeps free sites (which cannot select a
        // granular scope) working while satisfying the account env API schema.
        env.put("scopes", FREE_PLAN_ENV_SCOPES);
        // One contextual value applied to every deploy context ("all").
        env.put("values", List.of(Map.of("value", v.value(), "context", "all")));
        env.put("is_secret", v.secret());
        return env;
    }

    private String envUrl(String accountId, String siteId) {
        return baseUrl + "/accounts/" + enc(accountId) + "/env?site_id=" + query(siteId);
    }

    private String envUrl(String accountId, String siteId, String key) {
        return baseUrl + "/accounts/" + enc(accountId) + "/env/" + enc(key) + "?site_id=" + query(siteId);
    }

    private static EnvVarInput byKey(List<EnvVarInput> vars, String key) {
        return vars.stream().filter(v -> v.key().equals(key)).findFirst()
            .orElseThrow(() -> new ProviderException.UnexpectedResult(
                "Netlify referenced an unexpected environment variable: " + key));
    }

    private boolean isAlreadyExists(ApiResult r) {
        if (r.status() == 409) return true;
        if (r.status() != 400) return false;
        String body = r.rawBody() == null ? "" : r.rawBody().toLowerCase(Locale.ROOT);
        return body.contains("exist") || body.contains("conflict") || body.contains("duplicate")
            || body.contains("already");
    }

    /**
     * A short, secret-safe fragment of Netlify's response body so a 400 can be
     * diagnosed. Uses the JSON {@code message}/{@code error} field when present,
     * strips known token shapes and redacts the values being set, then truncates.
     * Never includes tokens, authorization headers or environment-variable values.
     */
    private String responseDetail(ApiResult r, List<EnvVarInput> vars) {
        String message = firstNonBlank(r.body().path("message").asText(null),
            r.body().path("error").asText(null), null);
        String raw = message != null ? message : r.rawBody();
        if (raw == null || raw.isBlank()) return "";
        String safe = redactValues(sanitize(raw), vars).trim();
        if (safe.isBlank()) return "";
        if (safe.length() > 200) safe = safe.substring(0, 200) + "…";
        return " Netlify response: " + safe;
    }

    private String redactValues(String s, List<EnvVarInput> vars) {
        String out = s;
        for (EnvVarInput v : vars) {
            if (v.value() != null && v.value().length() >= 4) {
                out = out.replace(v.value(), "[REDACTED]");
            }
        }
        return out;
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
