package com.deploypilot.provider.model;

/**
 * The outcome of preparing repository changes. {@code created} is false when an
 * equivalent branch/PR already existed (idempotent re-run) or when no file
 * changes were required at all.
 */
public record PullRequestResult(String branch, String url, Integer number, boolean created, String note) {}
