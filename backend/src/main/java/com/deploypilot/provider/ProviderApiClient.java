package com.deploypilot.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Shared JSON-over-HTTP client for provider adapters. It attaches the bearer
 * credential, applies timeouts and treats non-2xx responses as data (so adapters
 * can map provider-specific status semantics) rather than throwing.
 *
 * <p>Credentials and request/response bodies are never logged. Only the method,
 * a redacted path and the response status are ever traced.
 */
@Component
public class ProviderApiClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderApiClient.class);

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final String USER_AGENT = "DeployPilot-Automation/1.0";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProviderApiClient() {
        // The JDK HttpClient factory supports PATCH (which HttpURLConnection does not),
        // needed for provider environment-variable updates.
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));
        this.restTemplate = new RestTemplate(factory);
        // Never throw on non-2xx; callers inspect the status.
        this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) { return false; }
            @Override public void handleError(ClientHttpResponse response) { /* handled by caller */ }
        });
    }

    public record ApiResult(int status, JsonNode body, String rawBody) {
        public boolean isSuccess() { return status >= 200 && status < 300; }
        public boolean isUnauthorized() { return status == 401 || status == 403; }
        public boolean isNotFound() { return status == 404; }
    }

    public ApiResult get(String url, ProviderCredential credential) {
        return exchange("GET", url, null, credential, null);
    }

    public ApiResult post(String url, Object body, ProviderCredential credential) {
        return exchange("POST", url, body, credential, null);
    }

    public ApiResult put(String url, Object body, ProviderCredential credential) {
        return exchange("PUT", url, body, credential, null);
    }

    public ApiResult patch(String url, Object body, ProviderCredential credential) {
        return exchange("PATCH", url, body, credential, null);
    }

    public ApiResult delete(String url, ProviderCredential credential) {
        return exchange("DELETE", url, null, credential, null);
    }

    /** Full control, e.g. GitHub's {@code Accept} media type override. */
    public ApiResult exchange(String method, String url, Object body, ProviderCredential credential, String accept) {
        try {
            org.springframework.http.HttpMethod httpMethod = org.springframework.http.HttpMethod.valueOf(method);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, accept != null ? accept : MediaType.APPLICATION_JSON_VALUE);
            headers.setBearerAuth(credential.token());
            headers.set("X-GitHub-Api-Version", "2022-11-28"); // ignored by non-GitHub hosts
            org.springframework.http.HttpEntity<Object> entity;
            if (body != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
                entity = new org.springframework.http.HttpEntity<>(objectMapper.writeValueAsBytes(body), headers);
            } else {
                entity = new org.springframework.http.HttpEntity<>(headers);
            }
            org.springframework.http.ResponseEntity<byte[]> response =
                restTemplate.exchange(url, httpMethod, entity, byte[].class);
            HttpStatusCode statusCode = response.getStatusCode();
            byte[] raw = response.getBody();
            String rawBody = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
            JsonNode json = parse(rawBody);
            log.debug("Provider call {} {} -> {}", method, redactPath(url), statusCode.value());
            return new ApiResult(statusCode.value(), json, rawBody);
        } catch (ResourceAccessException e) {
            throw new ProviderException("Could not reach the provider (timeout or network error). Try again.", e);
        } catch (IOException e) {
            throw new ProviderException("Could not serialise the provider request.", e);
        }
    }

    private JsonNode parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(rawBody);
        } catch (IOException e) {
            // Non-JSON response body; return an empty node rather than leaking it.
            return objectMapper.createObjectNode();
        }
    }

    /** Strips query strings so tokens passed as query params (should not happen) never reach logs. */
    private String redactPath(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }
}
