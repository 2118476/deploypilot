package com.deploypilot.repoaccess;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifies a repository as owner + name, e.g. "octocat/hello-world". */
public record RepositoryRef(String owner, String name) {

    private static final Pattern OWNER_NAME = Pattern.compile("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$");
    private static final Pattern GITHUB_URL =
        Pattern.compile("^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$");

    public String fullName() { return owner + "/" + name; }

    /**
     * Accepts "owner/name" or a github.com URL. Throws IllegalArgumentException
     * for anything else so callers can surface a clear validation error.
     */
    public static RepositoryRef parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Repository is required");
        }
        String trimmed = input.trim();
        Matcher url = GITHUB_URL.matcher(trimmed);
        if (url.matches()) {
            return new RepositoryRef(url.group(1), url.group(2));
        }
        Matcher plain = OWNER_NAME.matcher(trimmed);
        if (plain.matches()) {
            return new RepositoryRef(plain.group(1), plain.group(2));
        }
        throw new IllegalArgumentException(
            "Repository must be in the form owner/name or a github.com repository URL");
    }
}
