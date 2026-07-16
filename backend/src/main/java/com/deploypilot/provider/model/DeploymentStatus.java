package com.deploypilot.provider.model;

/** The status of a single deployment, with the normalised state and a live URL when known. */
public record DeploymentStatus(String deploymentId, DeploymentState state, String detail, String url) {}
