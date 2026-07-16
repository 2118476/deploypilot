package com.deploypilot.provider.model;

/** A file to add or update in a commit. Content must never contain real secrets. */
public record CommitFile(String path, String content) {}
