package com.deploypilot.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Base URLs for provider APIs. Overridable via configuration so tests point the
 * adapters at loopback mock servers and never touch the real providers.
 */
@Component
public class ProviderProperties {

    private final String gitHubBaseUrl;
    private final String netlifyBaseUrl;
    private final String renderBaseUrl;

    public ProviderProperties(
        @Value("${deploypilot.providers.github-api-base-url:https://api.github.com}") String gitHubBaseUrl,
        @Value("${deploypilot.providers.netlify-api-base-url:https://api.netlify.com/api/v1}") String netlifyBaseUrl,
        @Value("${deploypilot.providers.render-api-base-url:https://api.render.com/v1}") String renderBaseUrl) {
        this.gitHubBaseUrl = trimTrailingSlash(gitHubBaseUrl);
        this.netlifyBaseUrl = trimTrailingSlash(netlifyBaseUrl);
        this.renderBaseUrl = trimTrailingSlash(renderBaseUrl);
    }

    public String gitHubBaseUrl() { return gitHubBaseUrl; }
    public String netlifyBaseUrl() { return netlifyBaseUrl; }
    public String renderBaseUrl() { return renderBaseUrl; }

    private static String trimTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
