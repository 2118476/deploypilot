package com.deploypilot.verify;

/** Thrown when a URL is rejected by SSRF/safety validation before any connection is made. */
public class SafeUrlException extends RuntimeException {
    public SafeUrlException(String message) { super(message); }
}
