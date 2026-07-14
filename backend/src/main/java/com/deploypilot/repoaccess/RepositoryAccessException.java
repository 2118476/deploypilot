package com.deploypilot.repoaccess;

/** Base class for repository access failures (network errors, timeouts, unexpected responses). */
public class RepositoryAccessException extends RuntimeException {
    public RepositoryAccessException(String message) { super(message); }
    public RepositoryAccessException(String message, Throwable cause) { super(message, cause); }

    /** Repository does not exist, or is private and invisible to the current credentials. */
    public static class NotFound extends RepositoryAccessException {
        public NotFound(String message) { super(message); }
    }

    /** Credentials are valid but do not grant access to this repository. */
    public static class AccessDenied extends RepositoryAccessException {
        public AccessDenied(String message) { super(message); }
    }

    /** The provider's API rate limit has been exhausted. */
    public static class RateLimited extends RepositoryAccessException {
        public RateLimited(String message) { super(message); }
    }

    /** The configured credentials were rejected. */
    public static class BadCredentials extends RepositoryAccessException {
        public BadCredentials(String message) { super(message); }
    }
}
