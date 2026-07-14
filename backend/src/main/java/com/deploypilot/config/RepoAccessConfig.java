package com.deploypilot.config;

import com.deploypilot.repoaccess.FixtureRepositoryReader;
import com.deploypilot.repoaccess.GitHubRepositoryClient;
import com.deploypilot.repoaccess.RepositoryFileReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RepoAccessConfig {

    @Value("${deploypilot.repo-access.mode:github}")
    private String mode;

    @Value("${github.api.base-url:https://api.github.com}")
    private String gitHubBaseUrl;

    @Value("${github.api.token:}")
    private String gitHubToken;

    @Bean
    public RepositoryFileReader repositoryFileReader() {
        if ("fixture".equalsIgnoreCase(mode)) {
            return new FixtureRepositoryReader();
        }
        return new GitHubRepositoryClient(gitHubBaseUrl, gitHubToken);
    }
}
