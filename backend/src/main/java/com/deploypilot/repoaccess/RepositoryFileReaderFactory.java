package com.deploypilot.repoaccess;

import com.deploypilot.provider.ProviderCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link RepositoryFileReader} scoped to a specific user's connected
 * GitHub credential, so repository reads (migration/schema discovery) use the
 * user's token rather than only the server-level token. In fixture mode it
 * always returns the bundled fixture reader (tests and local dev).
 */
@Component
public class RepositoryFileReaderFactory {

    private final String mode;
    private final String baseUrl;
    private final String serverToken;

    public RepositoryFileReaderFactory(
            @Value("${deploypilot.repo-access.mode:github}") String mode,
            @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
            @Value("${github.api.token:}") String serverToken) {
        this.mode = mode;
        this.baseUrl = baseUrl;
        this.serverToken = serverToken;
    }

    /**
     * A reader using the given user's connected GitHub credential; falls back to
     * the server-level token only when no user credential is available.
     */
    public RepositoryFileReader forCredentialOrDefault(ProviderCredential githubCredential) {
        if ("fixture".equalsIgnoreCase(mode)) {
            return new FixtureRepositoryReader();
        }
        String token = serverToken;
        if (githubCredential != null && githubCredential.token() != null && !githubCredential.token().isBlank()) {
            token = githubCredential.token();
        }
        return new GitHubRepositoryClient(baseUrl, token);
    }
}
