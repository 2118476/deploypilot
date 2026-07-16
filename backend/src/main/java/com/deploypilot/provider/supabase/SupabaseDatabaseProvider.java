package com.deploypilot.provider.supabase;

import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.DatabaseProvider;
import com.deploypilot.provider.ProviderApiClient;
import com.deploypilot.provider.ProviderApiClient.ApiResult;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderException;
import com.deploypilot.provider.ProviderProperties;
import com.deploypilot.provider.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Supabase adapter over the Management API. Creates projects on the free plan
 * only, reads status, assembles connection details and applies vetted
 * repository-owned migrations. Errors are mapped to clean messages by status
 * code — raw response bodies are never echoed (they are untrusted). Transient
 * 429/5xx responses are retried with bounded backoff.
 */
@Component
public class SupabaseDatabaseProvider implements DatabaseProvider {

    private static final Logger log = LoggerFactory.getLogger(SupabaseDatabaseProvider.class);
    private static final int MAX_ITEMS = 100;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 150;

    private final ProviderApiClient http;
    private final String baseUrl;

    public SupabaseDatabaseProvider(ProviderApiClient http, ProviderProperties properties) {
        this.http = http;
        this.baseUrl = properties.supabaseBaseUrl();
    }

    @Override
    public ProviderType type() { return ProviderType.SUPABASE; }

    @Override
    public ProviderAccount getAccount(ProviderCredential credential) {
        ApiResult r = call(() -> http.get(baseUrl + "/organizations", credential));
        if (r.isUnauthorized()) {
            throw new ProviderException.BadCredentials("Supabase rejected the token. Check it has not been revoked.");
        }
        if (!r.isSuccess() || !r.body().isArray()) {
            throw sanitizedError(r, "read the account");
        }
        String label = r.body().size() > 0 ? r.body().get(0).path("name").asText("Supabase account") : "Supabase account";
        String externalId = r.body().size() > 0 ? r.body().get(0).path("id").asText(null) : null;
        return new ProviderAccount(externalId, label, "Projects, database and configuration (Management API)");
    }

    @Override
    public List<DatabaseOrganization> listOrganizations(ProviderCredential credential) {
        ApiResult r = call(() -> http.get(baseUrl + "/organizations", credential));
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Supabase rejected the token.");
        if (!r.isSuccess() || !r.body().isArray()) throw sanitizedError(r, "list organizations");
        List<DatabaseOrganization> out = new ArrayList<>();
        for (JsonNode n : r.body()) {
            out.add(new DatabaseOrganization(n.path("id").asText(null), n.path("name").asText(null)));
        }
        return out;
    }

    @Override
    public List<DatabaseProject> listProjects(ProviderCredential credential) {
        ApiResult r = call(() -> http.get(baseUrl + "/projects", credential));
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Supabase rejected the token.");
        if (!r.isSuccess() || !r.body().isArray()) throw sanitizedError(r, "list projects");
        List<DatabaseProject> out = new ArrayList<>();
        int count = 0;
        for (JsonNode n : r.body()) {
            if (count++ >= MAX_ITEMS) break;
            out.add(toProject(n));
        }
        return out;
    }

    @Override
    public DatabaseProject getProject(ProviderCredential credential, String ref) {
        ApiResult r = call(() -> http.get(baseUrl + "/projects/" + enc(ref), credential));
        if (r.isNotFound()) throw new ProviderException.NotFound("Supabase project not found: " + ref);
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Supabase rejected the token.");
        if (!r.isSuccess()) throw sanitizedError(r, "read the project");
        return toProject(r.body());
    }

    @Override
    public DatabaseStatus getStatus(ProviderCredential credential, String ref) {
        return getProject(credential, ref).status();
    }

    @Override
    public DatabaseProject createProject(ProviderCredential credential, DatabaseProjectRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", request.name());
        payload.put("organization_id", request.organizationId());
        payload.put("region", request.region());
        payload.put("plan", DatabaseProjectRequest.FREE_PLAN); // never a paid plan
        payload.put("db_pass", request.dbPassword());
        ApiResult r = call(() -> http.post(baseUrl + "/projects", payload, credential));
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Supabase rejected the token.");
        if (r.status() == 402 || looksLikeBilling(r)) {
            throw new ProviderException.BillingRequired(
                "Supabase reports the free tier is unavailable for this organization (an organization can hold a "
                    + "limited number of free projects). DeployPilot will not create a paid project. Free up or select "
                    + "an existing project, or create one manually.");
        }
        if (!r.isSuccess()) throw sanitizedError(r, "create the project");
        return toProject(r.body());
    }

    @Override
    public DatabaseConnectionInfo getConnectionInfo(ProviderCredential credential, String ref, String dbPassword) {
        DatabaseProject project = getProject(credential, ref);
        String host = project.host() != null ? project.host() : "db." + ref + ".supabase.co";
        String restUrl = project.restUrl() != null ? project.restUrl() : "https://" + ref + ".supabase.co";

        String anonKey = null, serviceRoleKey = null;
        ApiResult keys = call(() -> http.get(baseUrl + "/projects/" + enc(ref) + "/api-keys", credential));
        if (keys.isSuccess() && keys.body().isArray()) {
            for (JsonNode k : keys.body()) {
                String name = k.path("name").asText("");
                if ("anon".equals(name)) anonKey = k.path("api_key").asText(null);
                else if ("service_role".equals(name)) serviceRoleKey = k.path("api_key").asText(null);
            }
        }
        String jdbcUrl = "jdbc:postgresql://" + host + ":5432/postgres";
        return new DatabaseConnectionInfo(host, 5432, "postgres", "postgres", dbPassword, jdbcUrl,
            restUrl, anonKey, serviceRoleKey);
    }

    @Override
    public MigrationResult applyMigration(ProviderCredential credential, String ref, String migrationName, String sql) {
        if (sql == null || sql.isBlank()) {
            return new MigrationResult(migrationName, false, "Migration file was empty; nothing applied.");
        }
        ApiResult r = call(() -> http.post(baseUrl + "/projects/" + enc(ref) + "/database/query",
            Map.of("query", sql), credential));
        if (r.isSuccess()) {
            return new MigrationResult(migrationName, true, "Applied.");
        }
        if (r.isUnauthorized()) throw new ProviderException.BadCredentials("Supabase rejected the token.");
        // Sanitised, status-based message only — never echo the raw error body.
        return new MigrationResult(migrationName, false,
            "Supabase rejected the migration (status " + r.status() + "). Review it manually and retry.");
    }

    // ---------- internals ----------

    /** Runs a call with bounded retry/backoff on rate-limit (429) and 5xx. */
    private ApiResult call(Supplier<ApiResult> supplier) {
        ApiResult last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            last = supplier.get();
            if (last.status() != 429 && last.status() < 500) return last;
            if (attempt < MAX_RETRIES) sleep(BASE_BACKOFF_MS * (attempt + 1));
        }
        if (last != null && last.status() == 429) {
            throw new ProviderException.RateLimited("Supabase is rate-limiting requests. Please try again shortly.");
        }
        return last;
    }

    private DatabaseProject toProject(JsonNode n) {
        String ref = firstNonBlank(n.path("ref").asText(null), n.path("id").asText(null));
        String host = n.path("database").path("host").asText(null);
        String restUrl = ref != null ? "https://" + ref + ".supabase.co" : null;
        return new DatabaseProject(
            ref,
            n.path("name").asText(null),
            n.path("organization_id").asText(null),
            n.path("region").asText(null),
            mapStatus(n.path("status").asText("")),
            blankToNull(host),
            restUrl);
    }

    private DatabaseStatus mapStatus(String s) {
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "ACTIVE_HEALTHY" -> DatabaseStatus.ACTIVE_HEALTHY;
            case "COMING_UP", "RESTORING", "UPGRADING", "GOING_UP", "INITIALIZING" -> DatabaseStatus.COMING_UP;
            case "INACTIVE" -> DatabaseStatus.INACTIVE;
            case "PAUSED", "PAUSING", "GOING_DOWN" -> DatabaseStatus.PAUSED;
            case "INIT_FAILED", "RESTORE_FAILED", "UPGRADE_FAILED" -> DatabaseStatus.FAILED;
            case "REMOVED" -> DatabaseStatus.REMOVED;
            default -> DatabaseStatus.UNKNOWN;
        };
    }

    /** Inspects (internally only) whether a 4xx looks like a billing/limit problem. */
    private boolean looksLikeBilling(ApiResult r) {
        if (r.status() != 403 && r.status() != 400 && r.status() != 422) return false;
        String body = r.rawBody() == null ? "" : r.rawBody().toLowerCase(Locale.ROOT);
        return body.contains("free") && (body.contains("limit") || body.contains("exceed"))
            || body.contains("payment") || body.contains("billing") || body.contains("subscription")
            || body.contains("upgrade") || body.contains("paid");
    }

    private ProviderException sanitizedError(ApiResult r, String action) {
        int s = r.status();
        String msg = switch (s) {
            case 401, 403 -> "Supabase denied access to " + action + ". Reconnect with a token that has the right permissions.";
            case 404 -> "The Supabase resource was not found when trying to " + action + ".";
            case 409 -> "Supabase reported a conflict when trying to " + action + " (it may already exist).";
            case 422 -> "Supabase rejected the request to " + action + " as invalid.";
            case 429 -> "Supabase is rate-limiting requests. Please try again shortly.";
            default -> s >= 500
                ? "Supabase had a server error while trying to " + action + ". Try again shortly."
                : "Supabase could not " + action + " (status " + s + ").";
        };
        return new ProviderException.UnexpectedResult(msg);
    }

    private static String enc(String s) { return UriUtils.encodePathSegment(s, StandardCharsets.UTF_8); }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
