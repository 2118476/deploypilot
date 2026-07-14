package com.deploypilot.repoaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only GitHub REST API client.
 *
 * Authentication: an optional token (fine-grained PAT with read-only Contents
 * permission, or a GitHub App installation token) supplied via configuration.
 * Without a token, only public repositories are readable and GitHub's
 * unauthenticated rate limit (60 requests/hour) applies.
 *
 * This client never performs writes.
 */
public class GitHubRepositoryClient implements RepositoryFileReader {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String token;

    public GitHubRepositoryClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public RepositoryMetadata fetchMetadata(RepositoryRef ref) {
        JsonNode repo = getJson(baseUrl + "/repos/" + ref.owner() + "/" + ref.name(), ref);
        return new RepositoryMetadata(
            repo.path("full_name").asText(ref.fullName()),
            repo.path("default_branch").asText("main"),
            repo.path("private").asBoolean(false));
    }

    @Override
    public FileListing listFiles(RepositoryRef ref, String branch) {
        String url = baseUrl + "/repos/" + ref.owner() + "/" + ref.name()
            + "/git/trees/" + UriUtils.encodePathSegment(branch, StandardCharsets.UTF_8) + "?recursive=1";
        JsonNode tree = getJson(url, ref);
        List<RepositoryFileEntry> entries = new ArrayList<>();
        for (JsonNode node : tree.path("tree")) {
            if ("blob".equals(node.path("type").asText())) {
                entries.add(new RepositoryFileEntry(
                    node.path("path").asText(),
                    node.path("size").asLong(-1)));
            }
        }
        return new FileListing(entries, tree.path("truncated").asBoolean(false));
    }

    @Override
    public String readTextFile(RepositoryRef ref, String branch, String path, int maxBytes) {
        String url = baseUrl + "/repos/" + ref.owner() + "/" + ref.name()
            + "/contents/" + UriUtils.encodePath(path, StandardCharsets.UTF_8)
            + "?ref=" + UriUtils.encodeQueryParam(branch, StandardCharsets.UTF_8);
        try {
            HttpHeaders headers = commonHeaders();
            headers.set(HttpHeaders.ACCEPT, "application/vnd.github.raw+json");
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            if (body == null) return "";
            if (body.length > maxBytes) {
                body = java.util.Arrays.copyOf(body, maxBytes);
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (HttpClientErrorException e) {
            throw translate(e, ref, path);
        } catch (ResourceAccessException e) {
            throw new RepositoryAccessException("GitHub request timed out or failed for " + path, e);
        }
    }

    private JsonNode getJson(String url, RepositoryRef ref) {
        try {
            HttpHeaders headers = commonHeaders();
            headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (HttpClientErrorException e) {
            throw translate(e, ref, null);
        } catch (ResourceAccessException e) {
            throw new RepositoryAccessException("GitHub request timed out or failed", e);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new RepositoryAccessException("GitHub returned an unreadable response", e);
        }
    }

    private HttpHeaders commonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.set(HttpHeaders.USER_AGENT, "DeployPilot-Analyzer");
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private RepositoryAccessException translate(HttpClientErrorException e, RepositoryRef ref, String path) {
        String target = ref.fullName() + (path != null ? "/" + path : "");
        int status = e.getStatusCode().value();
        if (status == 404) {
            return new RepositoryAccessException.NotFound(
                "Repository or file not found: " + target
                    + ". Private repositories require a GitHub token with read access.");
        }
        if (status == 401) {
            return new RepositoryAccessException.BadCredentials(
                "GitHub rejected the configured token. Check GITHUB_API_TOKEN.");
        }
        if (status == 403 || status == 429) {
            String remaining = e.getResponseHeaders() != null
                ? e.getResponseHeaders().getFirst("X-RateLimit-Remaining") : null;
            if ("0".equals(remaining) || status == 429) {
                return new RepositoryAccessException.RateLimited(
                    "GitHub API rate limit reached. Try again later or configure a token for a higher limit.");
            }
            return new RepositoryAccessException.AccessDenied(
                "Access denied to " + target + ". The configured token lacks permission for this repository.");
        }
        return new RepositoryAccessException("GitHub API error " + status + " for " + target);
    }
}
