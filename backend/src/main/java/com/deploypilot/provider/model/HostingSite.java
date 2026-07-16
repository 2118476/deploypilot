package com.deploypilot.provider.model;

/**
 * An existing hosting resource: a Netlify site or a Render service. {@code url}
 * is the production URL when known. {@code linkedRepo} is the repository the
 * resource is already connected to (null if none) — used to avoid creating a
 * duplicate when an appropriate linked resource already exists.
 */
public record HostingSite(String id, String name, String url, String linkedRepo) {}
