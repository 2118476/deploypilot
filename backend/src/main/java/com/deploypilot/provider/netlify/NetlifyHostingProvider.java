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
    // Bounded re-read to absorb Netlify's eventual consistency after a repo PATCH.
    // Small and finite: no long sleeps, no unbounded polling.
    private static final int BINDING_VERIFY_MAX_RETRIES = 3;
    private static final long BINDING_VERIFY_BACKOFF_MS = 250;

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
        JsonNode buildSettings = current.body().path("build_settings");

        // Only unlink on positive evidence that the existing binding is wrong.
        // Absent optional fields (public_repo, deploy_key_id) mean "unknown", never
        // "broken": a valid manual GitHub App connection legitimately omits them, and
        // unlinking it would destroy a working link. Repair only when Netlify reports
        // a different repository, an explicitly stale public binding, or a lingering
        // deploy key (the classic "Host key verification failed" cause).
        boolean wrongRepository = hasText(buildSettings, "repo_path")
            && !repoPathMatches(buildSettings, request);
        boolean explicitlyStalePublicBinding = request.publicRepository()
            && isExplicitlyFalse(buildSettings, "public_repo");
        boolean lingeringDeployKey = hasText(buildSettings, "deploy_key_id");
        boolean wrongPublicRepoUrl = request.publicRepository()
            && hasText(buildSettings, "repo_url")
            && !publicRepoUrlMatches(buildSettings, request);
        boolean needsRepair = wrongRepository || explicitlyStalePublicBinding
            || lingeringDeployKey || wrongPublicRepoUrl;

        if (needsRepair) {
            unlinkRepository(credential, siteId);
        }

        return applyRepositoryConfiguration(credential, siteId, request);
    }

    /**
     * A failed Netlify deploy is stronger evidence than the site metadata response:
     * GitHub App connections legitimately omit repo_url/public_repo/deploy_key_id,
     * but a "Host key verification failed" build proves the hidden clone binding is
     * stale. In that case explicitly unlink first (which removes deploy keys), then
     * relink the public HTTPS repository.
     */
    @Override
    public HostingSite repairRepositoryBinding(ProviderCredential credential, String siteId,
                                               CreateSiteRequest request) {
        unlinkRepository(credential, siteId);
        return applyRepositoryConfiguration(credential, siteId, request);
    }

    private void unlinkRepository(ProviderCredential credential, String siteId) {
        ApiResult unlinked = http.put(baseUrl + "/sites/" + enc(siteId) + "/unlink_repo", credential);
        requireSiteSuccess(unlinked, siteId, "clear the stale repository binding from");
    }

    private HostingSite applyRepositoryConfiguration(ProviderCredential credential, String siteId,
                                                     CreateSiteRequest request) {
        // Re-assert the intended repository configuration. This is idempotent for an
        // already-correct site and never carries build_settings/env (the removed
        // legacy contract). For a private repository, repoInfo omits public_repo so
        // an authorised GitHub App connection is preserved, not forced public.
        ApiResult patched = http.patch(baseUrl + "/sites/" + enc(siteId),
            Map.of("repo", repoInfo(request)), credential);
        requireSiteSuccess(patched, siteId, "update repository settings for");

        // Netlify may return a partial or eventually-consistent object from the PATCH.
        // Confirm the binding on the stable repo_path/repo_branch fields with a short,
        // bounded re-read instead of trusting the immediate body or an absent optional
        // field. Optional fields being absent is tolerated; only a genuinely wrong or
        // missing repo_path (after the retries) is a real failure.
        return verifyRepositoryBinding(credential, siteId, request, patched.body());
    }

    private HostingSite verifyRepositoryBinding(ProviderCredential credential, String siteId,
                                                CreateSiteRequest request, JsonNode initial) {
        JsonNode body = initial;
        for (int attempt = 0; attempt <= BINDING_VERIFY_MAX_RETRIES; attempt++) {
            JsonNode bs = body.path("build_settings");
            if (repoPathMatches(bs, request) && branchCompatible(bs, request)) {
                return toSite(body);
            }
            if (attempt == BINDING_VERIFY_MAX_RETRIES) break;
            if (attempt > 0) sleep(BINDING_VERIFY_BACKOFF_MS); // no wait before the first re-read
            ApiResult reread = http.get(baseUrl + "/sites/" + enc(siteId), credential);
            requireSiteSuccess(reread, siteId, "re-read");
            body = reread.body();
        }
        JsonNode bs = body.path("build_settings");
        throw new ProviderException.UnexpectedResult(
            "Netlify site " + siteId + " is not linked to " + request.repoFullName()
                + " after configuration (repo_path " + presence(bs, "repo_path")
                + ", repo_branch " + presence(bs, "repo_branch")
                + "). Retry once Netlify finishes refreshing the site.");
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
        requireEnvironmentAccess(site, "read the site before setting environment variables", vars);
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
        requireEnvironmentAccess(current, "read environment variables", vars);
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
        requireEnvironmentAccess(created, "create environment variables", all);
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
        requireEnvironmentAccess(updated, "update environment variable " + v.key(), one);
        // The variable was listed but is gone now (or never really existed): create it.
        if (updated.isNotFound()) {
            ApiResult created = http.post(envUrl(accountId, siteId), List.of(envVar(v)), credential);
            if (created.isSuccess()) return;
            requireEnvironmentAccess(created, "create environment variable " + v.key(), one);
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
        // Do not send `scopes`: Netlify documents granular scopes as a Pro-plan
        // feature. Omitting the field applies the variable to the free site's
        // default scopes; sending it makes a valid free-plan token receive 403.
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

    /**
     * A 401 means the credential itself is invalid. A 403 is deliberately kept
     * separate: Netlify also uses it for plan/operation restrictions (including
     * paid-only environment scopes), so calling every 403 a rejected token sends
     * users to rotate a perfectly valid credential and hides the real response.
     */
    private void requireEnvironmentAccess(ApiResult r, String operation, List<EnvVarInput> vars) {
        if (r.isUnauthenticated()) {
            throw new ProviderException.BadCredentials(
                "Netlify authentication failed (status 401). Reconnect Netlify with a current personal access token.");
        }
        if (r.isForbidden()) {
            throw new ProviderException.UnexpectedResult(
                "Netlify refused to " + operation + " (status 403)."
                    + responseDetail(r, vars));
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

    // ---------- repository-binding inspection (field-presence aware) ----------

    private static boolean repoPathMatches(JsonNode buildSettings, CreateSiteRequest request) {
        String linked = text(buildSettings, "repo_path");
        return linked != null && linked.equalsIgnoreCase(request.repoFullName());
    }

    /** Public GitHub repositories must use HTTPS, never a stale SSH/deploy-key URL. */
    private static boolean publicRepoUrlMatches(JsonNode buildSettings, CreateSiteRequest request) {
        String linked = text(buildSettings, "repo_url");
        if (linked == null) return true; // optional field absent: no evidence of a broken link
        String normalized = linked.trim().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
        String expected = ("https://github.com/" + request.repoFullName()).toLowerCase(Locale.ROOT);
        return normalized.equals(expected) || normalized.equals(expected + ".git");
    }

    /** A configured branch is compatible when it matches, or when either side is unspecified. */
    private static boolean branchCompatible(JsonNode buildSettings, CreateSiteRequest request) {
        String linked = text(buildSettings, "repo_branch");
        String wanted = request.branch();
        return linked == null || wanted == null || wanted.isBlank() || linked.equalsIgnoreCase(wanted);
    }

    /** A field carrying a non-blank string value, or null when absent/blank. */
    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static boolean hasText(JsonNode node, String field) {
        return text(node, field) != null;
    }

    /** True only when the field is present AND explicitly the boolean {@code false}. */
    private static boolean isExplicitlyFalse(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isBoolean() && !v.booleanValue();
    }

    /** "present"/"absent" — safe to surface (never a token, secret or value). */
    private static String presence(JsonNode node, String field) {
        return hasText(node, field) ? "present" : "absent";
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
