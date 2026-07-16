package com.deploypilot.provider.model;

/**
 * Metadata about a single repository-owned migration file. Never carries the SQL
 * itself into a stored plan — the SQL is re-read from the repository only at
 * apply time. {@code safetyClassification} is SAFE or POTENTIALLY_DESTRUCTIVE.
 */
public record MigrationInfo(
    String name,
    String path,
    String checksum,
    int order,
    boolean previouslyApplied,
    boolean destructive,
    String safetyClassification,
    String reason
) {
    public static final String SAFE = "SAFE";
    public static final String POTENTIALLY_DESTRUCTIVE = "POTENTIALLY_DESTRUCTIVE";
}
