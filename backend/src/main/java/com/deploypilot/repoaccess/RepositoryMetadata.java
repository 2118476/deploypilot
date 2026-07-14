package com.deploypilot.repoaccess;

/** Basic repository facts needed before reading files. */
public record RepositoryMetadata(String fullName, String defaultBranch, boolean isPrivate) {}
