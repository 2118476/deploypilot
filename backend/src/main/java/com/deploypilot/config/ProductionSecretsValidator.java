package com.deploypilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start the application with the "prod" profile when required
 * secrets are missing or left at their insecure development defaults.
 */
@Configuration
@Profile("prod")
public class ProductionSecretsValidator {

    static final String DEV_DEFAULT_JWT_SECRET = "default-local-jwt-secret-change-in-production";
    static final int MIN_JWT_SECRET_LENGTH = 32;

    @Value("${deploypilot.jwt.secret:}")
    private String jwtSecret;

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
    }
}
