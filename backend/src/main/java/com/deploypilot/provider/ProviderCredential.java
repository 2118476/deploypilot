package com.deploypilot.provider;

import com.deploypilot.model.enums.ConnectionType;
import com.deploypilot.model.enums.ProviderType;

/**
 * A decrypted provider credential passed to an adapter for the duration of a
 * single call. Instances are short-lived, never persisted and never logged.
 */
public record ProviderCredential(ProviderType provider, ConnectionType type, String token) {

    public ProviderCredential {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Provider credential token is required");
        }
    }

    // Guard against the token leaking into logs via accidental toString().
    @Override
    public String toString() {
        return "ProviderCredential[provider=" + provider + ", type=" + type + ", token=***]";
    }
}
