package com.deploypilot.verify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A tiny loopback HTTP server for deterministic verification tests. It binds to
 * 127.0.0.1 (reached via allowLocal=true in the engine) so tests never touch
 * the network.
 */
public class MockDeploymentServer implements AutoCloseable {

    public interface Handler { void handle(HttpExchange exchange) throws IOException; }

    private final HttpServer server;
    private final Map<String, Handler> routes = new HashMap<>();
    private Handler fallback;

    public MockDeploymentServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Handler h = routes.get(exchange.getRequestURI().getPath());
            if (h == null) h = fallback;
            if (h == null) { respond(exchange, 404, "text/plain", "not found"); return; }
            h.handle(exchange);
        });
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    public MockDeploymentServer route(String path, int status, String contentType, String body) {
        routes.put(path, ex -> respond(ex, status, contentType, body));
        return this;
    }

    public MockDeploymentServer route(String path, Handler handler) {
        routes.put(path, handler);
        return this;
    }

    /** Fallback for any unmatched path — useful to simulate SPA index.html fallback. */
    public MockDeploymentServer fallback(int status, String contentType, String body) {
        this.fallback = ex -> respond(ex, status, contentType, body);
        return this;
    }

    public MockDeploymentServer onRequest(BiConsumer<String, HttpExchange> observer) {
        server.createContext("/", ex -> observer.accept(ex.getRequestURI().getPath(), ex));
        return this;
    }

    public static void respond(HttpExchange ex, int status, String contentType, String body) throws IOException {
        respond(ex, status, contentType, body, Map.of());
    }

    public static void respond(HttpExchange ex, int status, String contentType, String body,
                        Map<String, String> headers) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        if (contentType != null) ex.getResponseHeaders().add("Content-Type", contentType);
        headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(status, head ? -1 : bytes.length);
        if (!head) {
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } else {
            ex.close();
        }
    }

    @Override public void close() { server.stop(0); }
}
