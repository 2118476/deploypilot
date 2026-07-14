package com.deploypilot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretRedactionUtilTest {

    @Test
    void redactsPasswordAssignments() {
        String redacted = SecretRedactionUtil.redact("DATABASE_PASSWORD=hunter2secret");
        assertFalse(redacted.contains("hunter2secret"));
        assertTrue(redacted.contains("[REDACTED]"));
    }

    @Test
    void redactsJwtTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123signature";
        String redacted = SecretRedactionUtil.redact("error with token " + jwt);
        assertFalse(redacted.contains(jwt));
    }

    @Test
    void redactsGitHubTokens() {
        String token = "ghp_" + "a".repeat(36);
        String redacted = SecretRedactionUtil.redact("Authorization failed for " + token);
        assertFalse(redacted.contains(token));
    }

    @Test
    void redactsDatabaseUrlCredentials() {
        String redacted = SecretRedactionUtil.redact("postgres://admin:s3cr3tpw@db.example.com:5432/app");
        assertFalse(redacted.contains("s3cr3tpw"));
    }

    @Test
    void redactsApiKeys() {
        String redacted = SecretRedactionUtil.redact("GEMINI_API_KEY=AIzaSyFakeKey1234567890abcd");
        assertFalse(redacted.contains("AIzaSyFakeKey1234567890abcd"));
    }

    @Test
    void leavesOrdinaryErrorTextAlone() {
        String input = "NullPointerException at com.example.Main.run(Main.java:42)";
        assertEquals(input, SecretRedactionUtil.redact(input));
    }

    @Test
    void handlesNullAndEmpty() {
        assertNull(SecretRedactionUtil.redact(null));
        assertEquals("", SecretRedactionUtil.redact(""));
    }
}
