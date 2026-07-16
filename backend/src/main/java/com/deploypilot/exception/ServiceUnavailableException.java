package com.deploypilot.exception;

/**
 * A feature is temporarily unavailable due to server configuration (for example,
 * the credential-encryption key has not been set). The message is safe to show
 * to the user and never contains secrets.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
