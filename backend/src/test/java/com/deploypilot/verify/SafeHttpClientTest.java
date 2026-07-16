package com.deploypilot.verify;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SafeHttpClientTest {

    private final SafeHttpClient client = new SafeHttpClient(new SafeUrlValidator());

    @Test
    void fetchesTextBody() throws Exception {
        try (MockDeploymentServer server = new MockDeploymentServer()
                .route("/", 200, "text/html", "<html>ok</html>")) {
            SafeHttpClient.SafeResponse res = client.get(server.baseUrl() + "/", true);
            assertEquals(200, res.status());
            assertTrue(res.body().contains("ok"));
            assertFalse(res.binary());
        }
    }

    @Test
    void rejectsWriteMethods() {
        assertThrows(SafeUrlException.class,
            () -> client.request("POST", "https://example.com", Map.of(), false));
        assertThrows(SafeUrlException.class,
            () -> client.request("DELETE", "https://example.com", Map.of(), false));
    }

    @Test
    void doesNotForwardAuthHeaders() throws Exception {
        StringBuilder seenAuth = new StringBuilder("none");
        try (MockDeploymentServer server = new MockDeploymentServer()) {
            server.route("/", ex -> {
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                if (auth != null) seenAuth.setLength(0);
                MockDeploymentServer.respond(ex, 200, "text/plain", "ok");
            });
            client.request("GET", server.baseUrl() + "/",
                Map.of("Authorization", "Bearer secret", "Origin", "https://app.example.com"), true);
            assertEquals("none", seenAuth.toString(), "Authorization header must never be forwarded");
        }
    }

    @Test
    void capsResponseBodySize() throws Exception {
        String huge = "x".repeat(SafeHttpClient.MAX_BODY_BYTES + 50_000);
        try (MockDeploymentServer server = new MockDeploymentServer()
                .route("/", 200, "text/plain", huge)) {
            SafeHttpClient.SafeResponse res = client.get(server.baseUrl() + "/", true);
            assertTrue(res.bodyTruncated());
            assertTrue(res.body().length() <= SafeHttpClient.MAX_BODY_BYTES);
        }
    }

    @Test
    void doesNotDownloadBinaryBodies() throws Exception {
        try (MockDeploymentServer server = new MockDeploymentServer()
                .route("/img", 200, "image/png", "PNG-binary-data")) {
            SafeHttpClient.SafeResponse res = client.get(server.baseUrl() + "/img", true);
            assertTrue(res.binary());
            assertNull(res.body());
        }
    }

    @Test
    void followsSafeRedirectThenStops() throws Exception {
        try (MockDeploymentServer server = new MockDeploymentServer()) {
            server.route("/start", ex ->
                MockDeploymentServer.respond(ex, 302, null, "", Map.of("Location", "/end")));
            server.route("/end", 200, "text/plain", "arrived");
            SafeHttpClient.SafeResponse res = client.get(server.baseUrl() + "/start", true);
            assertEquals(200, res.status());
            assertTrue(res.body().contains("arrived"));
            assertTrue(res.finalUrl().endsWith("/end"));
        }
    }

    @Test
    void redirectToPrivateAddressIsBlocked() throws Exception {
        try (MockDeploymentServer server = new MockDeploymentServer()) {
            // redirect to cloud metadata address must be refused on revalidation
            server.route("/evil", ex ->
                MockDeploymentServer.respond(ex, 302, null, "",
                    Map.of("Location", "http://169.254.169.254/latest/meta-data/")));
            SafeHttpClient.SafeResponse res = client.get(server.baseUrl() + "/evil", true);
            assertNotEquals(200, res.status());
            assertNotNull(res.errorMessage());
        }
    }

    @Test
    void limitsRedirectChain() throws Exception {
        try (MockDeploymentServer server = new MockDeploymentServer()) {
            server.fallback(302, null, ""); // every path redirects... but Location loops
            server.route("/loop", ex ->
                MockDeploymentServer.respond(ex, 302, null, "", Map.of("Location", "/loop")));
            SafeHttpClient.SafeResponse res = client.get(server.baseUrl() + "/loop", true);
            assertNotNull(res.errorMessage());
            assertTrue(res.errorMessage().toLowerCase().contains("redirect"));
        }
    }
}
