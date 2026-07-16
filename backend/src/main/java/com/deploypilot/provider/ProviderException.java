package com.deploypilot.provider;

/**
 * Raised when a provider API call fails or returns an unexpected result. The
 * message is safe to surface to users and never contains credentials.
 */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) { super(message); }
    public ProviderException(String message, Throwable cause) { super(message, cause); }

    /** The supplied credential was rejected by the provider (revoked/invalid). */
    public static class BadCredentials extends ProviderException {
        public BadCredentials(String message) { super(message); }
    }

    /** The provider returned something we cannot safely act on; execution must stop. */
    public static class UnexpectedResult extends ProviderException {
        public UnexpectedResult(String message) { super(message); }
    }

    /** A requested resource was not found. */
    public static class NotFound extends ProviderException {
        public NotFound(String message) { super(message); }
    }

    /** The provider requires a paid plan / billing to fulfil the request. */
    public static class BillingRequired extends ProviderException {
        public BillingRequired(String message) { super(message); }
    }

    /** The provider is rate-limiting; the caller may retry with backoff. */
    public static class RateLimited extends ProviderException {
        public RateLimited(String message) { super(message); }
    }
}
