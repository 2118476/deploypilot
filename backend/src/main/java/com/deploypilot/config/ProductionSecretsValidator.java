package com.deploypilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * With the "prod" profile, refuses to start when the JWT signing secret is
 * missing or left at its insecure development default. The credential-encryption
 * key is checked too, but only warns (the app still boots; automation fails
 * closed until the key is set).
 */
@Configuration
@Profile("prod")
public class ProductionSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretsValidator.class);

    static final String DEV_DEFAULT_JWT_SECRET = "default-local-jwt-secret-change-in-production";
    static final int MIN_JWT_SECRET_LENGTH = 32;

    @Value("${deploypilot.jwt.secret:}")
    private String jwtSecret;

    @Value("${deploypilot.encryption.key:}")
    private String encryptionKey;

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET is not set. Production refuses to start without it.");
        }
        if (DEV_DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                "JWT_SECRET is still the development default. Set a unique secret for production.");
        }
        if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException(
                "JWT_SECRET is shorter than " + MIN_JWT_SECRET_LENGTH
                    + " characters. Use a longer random secret.");
        }
        // The credential-encryption key is NOT fail-fast: the app still boots
        // without it so unrelated features keep working. When it is absent in
        // production, CredentialEncryptionService fails closed, so provider
        // connections and stored secrets are disabled until it is set. Warn only.
        if (encryptionKey == null || encryptionKey.isBlank()
                || com.deploypilot.security.CredentialEncryptionService.DEV_FALLBACK_KEY.equals(encryptionKey)
                || encryptionKey.length() < MIN_JWT_SECRET_LENGTH) {
            log.warn("DEPLOYPILOT_ENCRYPTION_KEY is not set to a strong value. Provider connections and deployment "
                + "automation are disabled until it is (e.g. `openssl rand -base64 32`).");
        }
    }
}
