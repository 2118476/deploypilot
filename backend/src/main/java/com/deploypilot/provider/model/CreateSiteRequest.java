package com.deploypilot.provider.model;

/**
 * Everything a hosting provider needs to create (or reuse) a site/service linked
 * to a repository. {@code paidPlan} is always false in this stage — DeployPilot
 * never selects a paid plan automatically.
 */
public record CreateSiteRequest(
    String name,
    String repoFullName,
    String branch,
    String rootDirectory,
    String buildCommand,
    String publishDirectory,
    String startCommand,
    String runtime,
    String healthCheckPath
) {
    /** Paid plans are never auto-selected; this is always false in this stage. */
    public boolean paidPlan() { return false; }
}
