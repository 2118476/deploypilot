package com.deploypilot.automation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A single loopback HTTP server that stands in for GitHub (/gh), Netlify (/nf)
 * and Render (/rd), plus the "live" deployment targets used by Stage 3
 * verification (/live-frontend, /live-backend). It is stateful (created sites and
 * services are remembered so idempotent reuse can be exercised) and records every
 * request so tests can assert what was — and was not — sent to each provider.
 *
 * It never touches the network beyond 127.0.0.1 and holds no real credentials.
 */
public class MockProviderServer implements AutoCloseable {

    public record Recorded(String method, String path, String body) {
        public boolean isTo(String prefix) { return path.startsWith(prefix); }
    }

    private final HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    // Simple in-memory provider state.
    private final Map<String, Map<String, Object>> netlifySites = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> renderServices = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> supabaseProjects = new LinkedHashMap<>();
    private int netlifySeq = 0;
    private int renderSeq = 0;
    private int supabaseSeq = 0;
    // When true, GitHub reports the config files already match (no PR needed).
    private volatile boolean gitHubFilesAlreadyPresent = false;
    private volatile boolean gitHubRepoInaccessible = false;   // token cannot see the repo at all
    private volatile boolean gitHubMetadataFailure = false;    // repo metadata 500s; default branch is master
    private volatile int netlifyEnvFailuresRemaining = 0;
    // Supabase simulation switches.
    public static final String SUPABASE_SERVICE_ROLE_MARKER = "service-role-SECRET-key";
    private volatile boolean supabaseBillingRequired = false;
    private volatile boolean supabaseQueryFail = false;
    private volatile int supabaseRateLimitRemaining = 0;

    public MockProviderServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::dispatch);
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public String githubBaseUrl() { return baseUrl() + "/gh"; }
    public String netlifyBaseUrl() { return baseUrl() + "/nf"; }
    public String renderBaseUrl() { return baseUrl() + "/rd"; }
    public String supabaseBaseUrl() { return baseUrl() + "/sb"; }
    public String liveFrontendUrl() { return baseUrl() + "/live-frontend"; }
    public String liveBackendUrl() { return baseUrl() + "/live-backend"; }

    public void setSupabaseBillingRequired(boolean v) { this.supabaseBillingRequired = v; }
    public void setSupabaseQueryFail(boolean v) { this.supabaseQueryFail = v; }
    public void setSupabaseRateLimit(int calls) { this.supabaseRateLimitRemaining = calls; }
    /** Pre-seed an existing Supabase project so it can be selected/inspected. */
    public void seedSupabaseProject(String ref, String name) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("ref", ref); p.put("id", ref); p.put("name", name);
        p.put("organization_id", "org-1"); p.put("region", "us-east-1");
        p.put("status", "ACTIVE_HEALTHY"); p.put("host", "db." + ref + ".supabase.co");
        supabaseProjects.put(ref, p);
    }

    public List<Recorded> requests() { return new ArrayList<>(requests); }
    public long count(String method, String pathPrefix) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).count();
    }
    /** Counts requests whose path (ignoring any query string) equals exactly {@code path}. */
    public long countExact(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && pathOnly(r.path()).equals(path)).count();
    }
    private static String pathOnly(String uri) {
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }
    public List<Recorded> to(String pathPrefix) {
        return requests.stream().filter(r -> r.path().startsWith(pathPrefix)).toList();
    }
    public void setGitHubFilesAlreadyPresent(boolean present) { this.gitHubFilesAlreadyPresent = present; }
    public void setGitHubRepoInaccessible(boolean v) { this.gitHubRepoInaccessible = v; }
    public void setGitHubMetadataFailure(boolean v) { this.gitHubMetadataFailure = v; }
    public void setNetlifyEnvFailures(int calls) { this.netlifyEnvFailuresRemaining = calls; }
    public void markNetlifyRepoBindingStale(String siteId) {
        Map<String, Object> site = netlifySites.get(siteId);
        if (site != null) {
            site.put("public_repo", false);
            site.put("deploy_key_id", "nf-stale-deploy-key");
        }
    }
    /** Clears only the recorded requests, keeping provider state (created sites/services). */
    public void clearRequests() { requests.clear(); }
    /** Full reset: requests and provider state. */
    public void reset() {
        requests.clear();
        netlifySites.clear();
        renderServices.clear();
        supabaseProjects.clear();
        netlifySeq = 0;
        renderSeq = 0;
        supabaseSeq = 0;
        gitHubFilesAlreadyPresent = false;
        gitHubRepoInaccessible = false;
        gitHubMetadataFailure = false;
        netlifyEnvFailuresRemaining = 0;
        supabaseBillingRequired = false;
        supabaseQueryFail = false;
        supabaseRateLimitRemaining = 0;
    }

    @Override public void close() { server.stop(0); }

    // ---------- dispatch ----------

    private void dispatch(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Recorded(method, ex.getRequestURI().toString(), body));
        try {
            if (path.startsWith("/gh")) handleGitHub(ex, method, path.substring(3), body);
            else if (path.startsWith("/nf")) handleNetlify(ex, method, path.substring(3), body);
            else if (path.startsWith("/rd")) handleRender(ex, method, path.substring(3), body);
            else if (path.startsWith("/sb")) handleSupabase(ex, method, path.substring(3), body);
            else if (path.startsWith("/live-frontend")) handleLiveFrontend(ex, method);
            else if (path.startsWith("/live-backend")) handleLiveBackend(ex, method);
            else json(ex, 404, "{}");
        } catch (Exception e) {
            json(ex, 500, "{\"error\":\"mock failure\"}");
        }
    }

    // ---------- GitHub ----------

    private void handleGitHub(HttpExchange ex, String method, String p, String body) throws IOException {
        if (gitHubRepoInaccessible && p.startsWith("/repos/")) {
            json(ex, 404, "{\"message\":\"Not Found\"}");
            return;
        }
        if (gitHubMetadataFailure) {
            if (p.matches("/repos/[^/]+/[^/]+")) { json(ex, 500, "{\"message\":\"boom\"}"); return; }
            boolean mainRef = p.endsWith("/git/ref/heads/main") || p.endsWith("/branches/main") || p.endsWith("/commits/main");
            boolean masterRef = p.endsWith("/git/ref/heads/master") || p.endsWith("/branches/master") || p.endsWith("/commits/master");
            if (mainRef) { json(ex, 404, "{\"message\":\"Not Found\"}"); return; }
            if (masterRef) {
                if (p.contains("/git/ref/")) json(ex, 200, "{\"object\":{\"sha\":\"mastercommitsha000000000000000000000000\"}}");
                else if (p.contains("/commits/")) json(ex, 200, "{\"sha\":\"mastercommitsha000000000000000000000000\"}");
                else json(ex, 200, "{\"commit\":{\"sha\":\"mastercommitsha000000000000000000000000\"}}");
                return;
            }
        }
        if (p.equals("/user")) {
            json(ex, 200, "{\"login\":\"octo-user\",\"id\":42}");
        } else if (p.equals("/user/repos")) {
            json(ex, 200, "[{\"full_name\":\"demo/sample-monorepo\",\"default_branch\":\"main\",\"private\":false,\"html_url\":\"https://github.com/demo/sample-monorepo\"}]");
        } else if (p.matches("/repos/[^/]+/[^/]+")) {
            json(ex, 200, "{\"full_name\":\"demo/sample-monorepo\",\"default_branch\":\"main\",\"private\":false,\"html_url\":\"https://github.com/demo/sample-monorepo\"}");
        } else if (p.matches("/repos/[^/]+/[^/]+/branches/.+")) {
            json(ex, 200, "{\"commit\":{\"sha\":\"abc123def4567890abc123def4567890abcd1234\"}}");
        } else if (p.matches("/repos/[^/]+/[^/]+/contents/.*")) {
            if (gitHubFilesAlreadyPresent) json(ex, 200, "mock file content that already matches");
            else json(ex, 404, "{\"message\":\"Not Found\"}");
        } else if (p.matches("/repos/[^/]+/[^/]+/git/ref/heads/main")) {
            json(ex, 200, "{\"object\":{\"sha\":\"basecommitsha00000000000000000000000000\"}}");
        } else if (p.matches("/repos/[^/]+/[^/]+/git/ref/heads/.+")) {
            json(ex, 404, "{\"message\":\"Not Found\"}"); // new branch does not exist yet
        } else if (p.matches("/repos/[^/]+/[^/]+/git/trees") && method.equals("POST")) {
            json(ex, 201, "{\"sha\":\"tree000000000000000000000000000000000000\"}");
        } else if (p.matches("/repos/[^/]+/[^/]+/git/commits") && method.equals("POST")) {
            json(ex, 201, "{\"sha\":\"commit0000000000000000000000000000000000\"}");
        } else if (p.matches("/repos/[^/]+/[^/]+/git/refs") && method.equals("POST")) {
            json(ex, 201, "{\"ref\":\"refs/heads/deploypilot/deployment-config\"}");
        } else if (p.matches("/repos/[^/]+/[^/]+/pulls") && method.equals("POST")) {
            json(ex, 201, "{\"html_url\":\"https://github.com/demo/sample-monorepo/pull/7\",\"number\":7}");
        } else if (p.matches("/repos/[^/]+/[^/]+/pulls")) { // GET existing
            json(ex, 200, "[]");
        } else {
            json(ex, 404, "{}");
        }
    }

    // ---------- Netlify ----------

    private void handleNetlify(HttpExchange ex, String method, String p, String body) throws IOException {
        if (p.equals("/user")) {
            json(ex, 200, "{\"id\":\"nf-acct-1\",\"full_name\":\"Netlify User\",\"email\":\"nf@example.com\"}");
        } else if (p.equals("/sites") && method.equals("POST")) {
            if (!validNetlifyRepoPayload(body)) {
                json(ex, 400, "{\"message\":\"invalid repository payload\"}");
                return;
            }
            String id = "nf-site-" + (++netlifySeq);
            String repoPath = extract(body, "\"repo_path\"\\s*:\\s*\"([^\"]+)\"");
            Map<String, Object> site = new LinkedHashMap<>();
            site.put("id", id);
            site.put("name", extractOr(body, "\"name\"\\s*:\\s*\"([^\"]+)\"", id));
            site.put("account_id", "nf-acct-1");
            site.put("ssl_url", liveFrontendUrl());
            site.put("repo_path", repoPath);
            site.put("public_repo", body.contains("\"public_repo\":true"));
            site.put("deploy_key_id", "");
            site.put("env", new LinkedHashMap<String, Object>());
            netlifySites.put(id, site);
            json(ex, 201, netlifySiteJson(site));
        } else if (p.equals("/sites")) { // GET list
            json(ex, 200, netlifyListJson());
        } else if (p.matches("/sites/[^/]+") && method.equals("PATCH")) {
            String id = p.substring("/sites/".length());
            Map<String, Object> site = netlifySites.get(id);
            if (site == null) { json(ex, 404, "{}"); return; }
            if (!validNetlifyRepoPayload(body) || body.contains("\"build_settings\"")) {
                json(ex, 400, "{\"message\":\"legacy site update rejected\"}");
                return;
            }
            site.put("repo_path", extract(body, "\"repo_path\"\\s*:\\s*\"([^\"]+)\""));
            site.put("public_repo", body.contains("\"public_repo\":true"));
            if (Boolean.TRUE.equals(site.get("public_repo"))) site.put("deploy_key_id", "");
            json(ex, 200, netlifySiteJson(site));
        } else if (p.matches("/sites/[^/]+/unlink_repo") && method.equals("PUT")) {
            String id = p.substring("/sites/".length(), p.length() - "/unlink_repo".length());
            Map<String, Object> site = netlifySites.get(id);
            if (site == null) { json(ex, 404, "{}"); return; }
            site.put("repo_path", "");
            site.put("public_repo", false);
            site.put("deploy_key_id", "");
            json(ex, 200, netlifySiteJson(site));
        } else if (p.matches("/sites/[^/]+") && method.equals("GET")) {
            String id = p.substring("/sites/".length());
            Map<String, Object> site = netlifySites.get(id);
            json(ex, site == null ? 404 : 200, site == null ? "{}" : netlifySiteJson(site));
        } else if (p.equals("/accounts/nf-acct-1/env") && method.equals("GET")) {
            Map<String, Object> site = siteFromQuery(ex);
            if (site == null) { json(ex, 404, "{}"); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> env = (Map<String, Object>) site.get("env");
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (String key : env.keySet()) {
                if (!first) out.append(',');
                out.append("{\"key\":\"").append(key).append("\",\"values\":[]}");
                first = false;
            }
            json(ex, 200, out.append(']').toString());
        } else if (p.equals("/accounts/nf-acct-1/env") && method.equals("POST")) {
            if (netlifyEnvFailuresRemaining > 0) {
                netlifyEnvFailuresRemaining--;
                json(ex, 400, "{\"message\":\"simulated Netlify env failure\"}");
                return;
            }
            Map<String, Object> site = siteFromQuery(ex);
            if (site == null) { json(ex, 404, "{}"); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> env = (Map<String, Object>) site.get("env");
            java.util.regex.Matcher keys = java.util.regex.Pattern.compile("\"key\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            while (keys.find()) env.put(keys.group(1), true);
            json(ex, 201, body);
        } else if (p.matches("/accounts/nf-acct-1/env/[^/]+") && method.equals("PUT")) {
            if (netlifyEnvFailuresRemaining > 0) {
                netlifyEnvFailuresRemaining--;
                json(ex, 400, "{\"message\":\"simulated Netlify env failure\"}");
                return;
            }
            Map<String, Object> site = siteFromQuery(ex);
            if (site == null) { json(ex, 404, "{}"); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> env = (Map<String, Object>) site.get("env");
            String key = p.substring("/accounts/nf-acct-1/env/".length());
            env.put(key, true);
            json(ex, 200, body);
        } else if (p.matches("/sites/[^/]+/builds") && method.equals("POST")) {
            json(ex, 201, "{\"id\":\"nf-build-1\",\"deploy_id\":\"nf-deploy-1\"}");
        } else if (p.matches("/sites/[^/]+/deploys/[^/]+")) {
            json(ex, 200, "{\"state\":\"ready\",\"ssl_url\":\"" + liveFrontendUrl() + "\",\"summary\":{\"status\":\"ready\"}}");
        } else if (p.matches("/deploys/[^/]+/cancel")) {
            json(ex, 200, "{}");
        } else {
            json(ex, 404, "{}");
        }
    }

    private String netlifyListJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map<String, Object> s : netlifySites.values()) {
            if (!first) sb.append(",");
            sb.append(netlifySiteJson(s));
            first = false;
        }
        return sb.append("]").toString();
    }

    private String netlifySiteJson(Map<String, Object> s) {
        return "{\"id\":\"" + s.get("id") + "\",\"name\":\"" + s.get("name") + "\",\"ssl_url\":\"" + s.get("ssl_url")
            + "\",\"account_id\":\"" + s.get("account_id") + "\",\"build_settings\":{\"repo_path\":\""
            + str(s.get("repo_path")) + "\",\"public_repo\":" + Boolean.TRUE.equals(s.get("public_repo"))
            + ",\"deploy_key_id\":\"" + str(s.get("deploy_key_id")) + "\"}}";
    }

    private boolean validNetlifyRepoPayload(String body) {
        return body.contains("\"repo_path\"")
            && body.contains("\"repo_branch\"")
            && body.contains("\"repo_url\"")
            && !body.matches("(?s).*\"repo\"\\s*:\\s*\".*")
            && !body.matches("(?s).*\"branch\"\\s*:\\s*\".*");
    }

    private Map<String, Object> siteFromQuery(HttpExchange ex) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null || !query.startsWith("site_id=")) return null;
        String id = java.net.URLDecoder.decode(query.substring("site_id=".length()), StandardCharsets.UTF_8);
        return netlifySites.get(id);
    }

    // ---------- Render ----------

    private void handleRender(HttpExchange ex, String method, String p, String body) throws IOException {
        if (p.startsWith("/owners")) {
            json(ex, 200, "[{\"owner\":{\"id\":\"rd-owner-1\",\"name\":\"Render User\",\"email\":\"rd@example.com\"}}]");
        } else if (p.equals("/services") && method.equals("POST")) {
            String id = "rd-srv-" + (++renderSeq);
            Map<String, Object> svc = new LinkedHashMap<>();
            svc.put("id", id);
            svc.put("name", extractOr(body, "\"name\"\\s*:\\s*\"([^\"]+)\"", id));
            svc.put("repo", extractOr(body, "\"repo\"\\s*:\\s*\"([^\"]+)\"", ""));
            svc.put("url", liveBackendUrl());
            renderServices.put(id, svc);
            json(ex, 201, renderServiceJson(svc));
        } else if (p.equals("/services")) { // GET list
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Map<String, Object> s : renderServices.values()) {
                if (!first) sb.append(",");
                sb.append("{\"service\":").append(renderServiceJson(s)).append("}");
                first = false;
            }
            json(ex, 200, sb.append("]").toString());
        } else if (p.matches("/services/[^/]+/env-vars/[^/]+") && method.equals("PUT")) {
            String key = p.substring(p.lastIndexOf('/') + 1);
            json(ex, 200, "{\"key\":\"" + key + "\"}");
        } else if (p.matches("/services/[^/]+/deploys") && method.equals("POST")) {
            json(ex, 201, "{\"id\":\"rd-deploy-1\",\"status\":\"created\"}");
        } else if (p.matches("/services/[^/]+/deploys/[^/]+/cancel")) {
            json(ex, 200, "{}");
        } else if (p.matches("/services/[^/]+/deploys/[^/]+")) {
            // The commit message intentionally embeds a token-shaped string so tests can
            // confirm the adapter sanitises provider logs before returning them.
            json(ex, 200, "{\"status\":\"live\",\"commit\":{\"message\":\"deploy rnd_abcdefghij1234567890abcd\"}}");
        } else if (p.matches("/services/[^/]+")) {
            String id = p.substring("/services/".length());
            Map<String, Object> svc = renderServices.get(id);
            json(ex, svc == null ? 404 : 200, svc == null ? "{}" : "{\"service\":" + renderServiceJson(svc) + "}");
        } else {
            json(ex, 404, "{}");
        }
    }

    private String renderServiceJson(Map<String, Object> s) {
        return "{\"id\":\"" + s.get("id") + "\",\"name\":\"" + s.get("name") + "\",\"repo\":\"" + str(s.get("repo"))
            + "\",\"serviceDetails\":{\"url\":\"" + s.get("url") + "\"}}";
    }

    // ---------- Supabase (Management API) ----------

    private void handleSupabase(HttpExchange ex, String method, String p, String body) throws IOException {
        // Rate-limit simulation: the first N calls get 429 so retry/backoff can be exercised.
        if (supabaseRateLimitRemaining > 0) {
            supabaseRateLimitRemaining--;
            json(ex, 429, "{\"message\":\"Too Many Requests\"}");
            return;
        }
        if (p.equals("/organizations")) {
            json(ex, 200, "[{\"id\":\"org-1\",\"name\":\"My Org\"}]");
        } else if (p.equals("/projects") && method.equals("POST")) {
            if (supabaseBillingRequired) {
                json(ex, 402, "{\"message\":\"free tier project limit reached; upgrade required\"}");
                return;
            }
            String ref = "proj-" + (++supabaseSeq);
            Map<String, Object> proj = new LinkedHashMap<>();
            proj.put("ref", ref); proj.put("id", ref);
            proj.put("name", extractOr(body, "\"name\"\\s*:\\s*\"([^\"]+)\"", ref));
            proj.put("organization_id", extractOr(body, "\"organization_id\"\\s*:\\s*\"([^\"]+)\"", "org-1"));
            proj.put("region", extractOr(body, "\"region\"\\s*:\\s*\"([^\"]+)\"", "us-east-1"));
            proj.put("status", "ACTIVE_HEALTHY");
            proj.put("host", "db." + ref + ".supabase.co");
            supabaseProjects.put(ref, proj);
            json(ex, 201, supabaseProjectJson(proj));
        } else if (p.equals("/projects")) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Map<String, Object> pr : supabaseProjects.values()) {
                if (!first) sb.append(",");
                sb.append(supabaseProjectJson(pr));
                first = false;
            }
            json(ex, 200, sb.append("]").toString());
        } else if (p.matches("/projects/[^/]+/api-keys")) {
            json(ex, 200, "[{\"name\":\"anon\",\"api_key\":\"anon-public-key\"},"
                + "{\"name\":\"service_role\",\"api_key\":\"" + SUPABASE_SERVICE_ROLE_MARKER + "\"}]");
        } else if (p.matches("/projects/[^/]+/database/query") && method.equals("POST")) {
            if (supabaseQueryFail) { json(ex, 400, "{\"message\":\"sql error\"}"); return; }
            json(ex, 201, "[]");
        } else if (p.matches("/projects/[^/]+")) {
            String ref = p.substring("/projects/".length());
            Map<String, Object> pr = supabaseProjects.get(ref);
            json(ex, pr == null ? 404 : 200, pr == null ? "{\"message\":\"Not Found\"}" : supabaseProjectJson(pr));
        } else {
            json(ex, 404, "{}");
        }
    }

    private String supabaseProjectJson(Map<String, Object> p) {
        return "{\"ref\":\"" + p.get("ref") + "\",\"id\":\"" + p.get("id") + "\",\"name\":\"" + p.get("name")
            + "\",\"organization_id\":\"" + str(p.get("organization_id")) + "\",\"region\":\"" + str(p.get("region"))
            + "\",\"status\":\"" + p.get("status") + "\",\"database\":{\"host\":\"" + str(p.get("host")) + "\"}}";
    }

    // ---------- live deployment targets (for verification) ----------

    private void handleLiveFrontend(HttpExchange ex, String method) throws IOException {
        respond(ex, 200, "text/html",
            "<!doctype html><html><head><title>App</title></head><body>DeployPilot test app</body></html>", Map.of());
    }

    private void handleLiveBackend(HttpExchange ex, String method) throws IOException {
        if (method.equals("OPTIONS")) {
            String origin = ex.getRequestHeaders().getFirst("Origin");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Access-Control-Allow-Origin", origin == null ? "*" : origin);
            headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "content-type, authorization");
            respond(ex, 204, null, "", headers);
            return;
        }
        respond(ex, 200, "application/json", "{\"status\":\"UP\"}", Map.of());
    }

    // ---------- helpers ----------

    private static String extract(String body, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static String extractOr(String body, String regex, String fallback) {
        String v = extract(body, regex);
        return v == null ? fallback : v;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private void json(HttpExchange ex, int status, String body) throws IOException {
        respond(ex, status, "application/json", body, Map.of());
    }

    private void respond(HttpExchange ex, int status, String contentType, String body, Map<String, String> headers)
            throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        if (contentType != null) ex.getResponseHeaders().add("Content-Type", contentType);
        headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        boolean bodyless = ex.getRequestMethod().equals("HEAD") || status == 204;
        ex.sendResponseHeaders(status, bodyless ? -1 : bytes.length);
        if (!bodyless) {
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } else {
            ex.close();
        }
    }

    // Suppress unused warnings for Locale import parity with other mocks.
    @SuppressWarnings("unused")
    private static final Locale L = Locale.ROOT;
}
