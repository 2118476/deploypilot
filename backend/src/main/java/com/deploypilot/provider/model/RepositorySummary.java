package com.deploypilot.provider.model;

/** A repository the connected account can access. */
public record RepositorySummary(String fullName, String defaultBranch, boolean privateRepo, String htmlUrl) {}
