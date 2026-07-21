package com.deploypilot.verify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal, defensive HTTP client for verifying user-supplied deployment URLs.
 *
 * Safety properties:
 *  - only GET, HEAD and OPTIONS (no writes),
 *  - every URL and every redirect hop is re-validated by {@link SafeUrlValidator}
 *    immediately before connecting (SSRF guard, limits DNS-rebinding window),
 *  - connect/read timeouts and a total-time budget,
 *  - response body capped; bytes beyond the cap are discarded (never buffered),
 *  - binary content types are not decoded,
 *  - DeployPilot never forwards its own auth/cookies; only explicitly passed
 *    safe headers (e.g. Origin, Accept) are sent.
 */
@Component
public class SafeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SafeHttpClient.class);

    static final int CONNECT_TIMEOUT_MS = 5_000;
    static final int READ_TIMEOUT_MS = 8_000;
    static final long TOTAL_BUDGET_MS = 15_000;
    static final int MAX_REDIRECTS = 5;
    // Modern SPA bundles routinely exceed 512 KB; a cap that truncates the bundle
    // makes content checks (backend wiring, localhost detection) unreliable.
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final String USER_AGENT = "DeployPilot-Verifier/1.0 (+read-only deployment check)";

    // HttpURLConnection silently DROPS restricted request headers — including
    // Origin — so a CORS preflight sent through it arrives without an Origin and
    // the backend never answers with Access-Control-Allow-Origin. Requests that
    // must carry such headers go through java.net.http.HttpClient instead, which
    // transmits Origin. Redirects stay manual so every hop is re-validated.
    private static final java.net.http.HttpClient HEADER_CLIENT = java.net.http.HttpClient.newBuilder()
        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
        .build();

    public record SafeResponse(
        int status,
        Map<String, String> headers,
        String body,
        boolean bodyTruncated,
        boolean binary,
        String finalUrl,
        long timingMs,
        boolean timedOut,
        String errorMessage
    ) {
        public boolean ok() { return status >= 200 && status < 400 && errorMessage == null; }
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private final SafeUrlValidator validator;

    public SafeHttpClient(SafeUrlValidator validator) { this.validator = validator; }

    public SafeResponse get(String url, boolean allowHttp) { return request("GET", url, Map.of(), allowHttp); }

    public SafeResponse head(String url, boolean allowHttp) { return request("HEAD", url, Map.of(), allowHttp); }

    /**
     * A safe CORS preflight: OPTIONS with the given production origin and a
     * simple requested method. Never sends credentials.
     */
    public SafeResponse corsPreflight(String url, String origin, String requestMethod, boolean allowHttp) {
        return request("OPTIONS", url, Map.of(
            "Origin", origin,
            "Access-Control-Request-Method", requestMethod,
            "Access-Control-Request-Headers", "content-type"), allowHttp);
    }

    public SafeResponse request(String method, String url, Map<String, String> extraHeaders, boolean allowHttp) {
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
            throw new SafeUrlException("Only GET, HEAD and OPTIONS are permitted");
        }
        long start = System.currentTimeMillis();
        long deadline = start + TOTAL_BUDGET_MS;
        String current = url;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                SafeUrlValidator.Validated v = hop == 0
                    ? validator.validate(current, allowHttp)
                    : validator.validateRedirect(current, allowHttp);

                if (!extraHeaders.isEmpty()) {
                    // Header-carrying requests (e.g. the CORS preflight with Origin)
                    // must use java.net.http — HttpURLConnection drops Origin silently.
                    HttpResponse<byte[]> res = sendWithHeaders(v.uri(), method, extraHeaders, deadline);
                    int status = res.statusCode();
                    if (isRedirect(status)) {
                        String location = res.headers().firstValue("Location")
                            .or(() -> res.headers().firstValue("location")).orElse(null);
                        if (location == null) {
                            return error(status, url, start, "Redirect without a Location header");
                        }
                        current = resolveLocation(v.uri(), location);
                        if (hop == MAX_REDIRECTS) {
                            return error(status, current, start, "Too many redirects");
                        }
                        continue;
                    }
                    return readResponse(res, method, current, start);
                }

                HttpURLConnection conn = open(v.uri(), method, extraHeaders, deadline);
                int status = conn.getResponseCode();

                if (isRedirect(status)) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null) {
                        return error(status, url, start, "Redirect without a Location header");
                    }
                    current = resolveLocation(v.uri(), location);
                    if (hop == MAX_REDIRECTS) {
                        return error(status, current, start, "Too many redirects");
                    }
                    continue;
                }
                return readResponse(conn, status, current, start);
            }
            return error(0, url, start, "Redirect handling failed");
        } catch (SafeUrlException e) {
            return error(0, current, start, e.getMessage());
        } catch (java.net.SocketTimeoutException e) {
            return new SafeResponse(0, Map.of(), null, false, false, current,
                System.currentTimeMillis() - start, true, "Request timed out");
        } catch (IOException e) {
            return error(0, current, start, "Connection failed: " + shortReason(e));
        }
    }

    /** Sends via java.net.http so restricted-but-safe headers (Origin) are transmitted. */
    private HttpResponse<byte[]> sendWithHeaders(URI uri, String method, Map<String, String> extraHeaders, long deadline)
            throws IOException {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) throw new java.net.SocketTimeoutException("Time budget exhausted");
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
            .method(method, HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofMillis(Math.min(READ_TIMEOUT_MS, remaining)))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*");
        for (Map.Entry<String, String> h : extraHeaders.entrySet()) {
            String key = h.getKey();
            // Never forward DeployPilot's own credentials.
            if (key.equalsIgnoreCase("authorization") || key.equalsIgnoreCase("cookie")) continue;
            b.header(key, h.getValue());
        }
        try {
            return HEADER_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new java.net.SocketTimeoutException("Request timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted");
        } catch (IllegalArgumentException e) {
            throw new IOException("Unsupported request header");
        }
    }

    private SafeResponse readResponse(HttpResponse<byte[]> res, String method, String finalUrl, long start) {
        Map<String, String> headers = new TreeMap<>();
        res.headers().map().forEach((k, values) -> {
            if (k != null && values != null && !values.isEmpty()) {
                headers.put(k.toLowerCase(Locale.ROOT), String.join(", ", values));
            }
        });
        String contentType = headers.getOrDefault("content-type", "");
        boolean binary = isBinary(contentType);
        String body = null;
        boolean truncated = false;
        if (!binary && !"HEAD".equals(method) && !"OPTIONS".equals(method)) {
            byte[] raw = res.body() == null ? new byte[0] : res.body();
            if (raw.length > MAX_BODY_BYTES) {
                raw = Arrays.copyOf(raw, MAX_BODY_BYTES);
                truncated = true;
            }
            body = new String(raw, StandardCharsets.UTF_8);
        }
        return new SafeResponse(res.statusCode(), headers, body, truncated, binary, finalUrl,
            System.currentTimeMillis() - start, false, null);
    }

    private HttpURLConnection open(URI uri, String method, Map<String, String> extraHeaders, long deadline)
            throws IOException {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) throw new java.net.SocketTimeoutException("Time budget exhausted");

        HttpURLConnection conn = (HttpURLConnection) new URL(uri.toString()).openConnection();
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout((int) Math.min(CONNECT_TIMEOUT_MS, remaining));
        conn.setReadTimeout((int) Math.min(READ_TIMEOUT_MS, Math.max(1, deadline - System.currentTimeMillis())));
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        // Only forward the explicitly-provided safe headers; never Authorization/Cookie.
        for (Map.Entry<String, String> h : extraHeaders.entrySet()) {
            String key = h.getKey();
            if (key.equalsIgnoreCase("authorization") || key.equalsIgnoreCase("cookie")) continue;
            conn.setRequestProperty(key, h.getValue());
        }
        conn.connect();
        return conn;
    }

    private SafeResponse readResponse(HttpURLConnection conn, int status, String finalUrl, long start)
            throws IOException {
        Map<String, String> headers = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
            headers.put(e.getKey().toLowerCase(Locale.ROOT), String.join(", ", e.getValue()));
        }
        String contentType = headers.getOrDefault("content-type", "");
        boolean binary = isBinary(contentType);

        String body = null;
        boolean truncated = false;
        if (!binary && !"HEAD".equals(conn.getRequestMethod()) && !"OPTIONS".equals(conn.getRequestMethod())) {
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                int total = 0;
                try (in) {
                    while ((read = in.read(chunk)) != -1) {
                        int room = MAX_BODY_BYTES - total;
                        if (room <= 0) { truncated = true; break; }
                        buf.write(chunk, 0, Math.min(read, room));
                        total += Math.min(read, room);
                        if (read > room) { truncated = true; break; }
                    }
                }
                body = buf.toString(StandardCharsets.UTF_8);
            }
        }
        long timing = System.currentTimeMillis() - start;
        conn.disconnect();
        return new SafeResponse(status, headers, body, truncated, binary, finalUrl, timing, false, null);
    }

    private String resolveLocation(URI base, String location) {
        try {
            return base.resolve(location).toString();
        } catch (IllegalArgumentException e) {
            throw new SafeUrlException("Invalid redirect location");
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private boolean isBinary(String contentType) {
        String c = contentType.toLowerCase(Locale.ROOT);
        if (c.isEmpty()) return false;
        return !(c.startsWith("text/") || c.contains("json") || c.contains("xml")
            || c.contains("javascript") || c.contains("html") || c.contains("+text")
            || c.contains("manifest") || c.contains("csv") || c.contains("yaml"));
    }

    private SafeResponse error(int status, String url, long start, String message) {
        return new SafeResponse(status, Map.of(), null, false, false, url,
            System.currentTimeMillis() - start, false, message);
    }

    private String shortReason(IOException e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 120 ? m.substring(0, 120) : m;
    }
}
