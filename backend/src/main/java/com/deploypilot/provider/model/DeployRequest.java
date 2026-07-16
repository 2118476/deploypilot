package com.deploypilot.provider.model;

/** A request to trigger a deployment of a linked repository at a given commit. */
public record DeployRequest(String branch, String commitSha, boolean clearCache) {}
