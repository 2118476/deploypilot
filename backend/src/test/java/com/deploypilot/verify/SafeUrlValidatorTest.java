package com.deploypilot.verify;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class SafeUrlValidatorTest {

    private final SafeUrlValidator validator = new SafeUrlValidator();

    @Test
    void acceptsPublicHttpsUrl() {
        // example.com resolves to public addresses
        SafeUrlValidator.Validated v = validator.validateProduction("https://example.com/app");
        assertEquals("example.com", v.host());
        assertEquals(443, v.port());
    }

    @Test
    void rejectsHttpForProduction() {
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("http://example.com"));
    }

    @Test
    void allowsHttpOnlyWhenExplicit() {
        assertDoesNotThrow(() -> validator.validate("http://example.com", true));
    }

    @Test
    void rejectsCredentialsInUrl() {
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://user:pass@example.com"));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("ftp://example.com"));
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("file:///etc/passwd"));
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("gopher://example.com"));
    }

    @Test
    void rejectsLocalhostInProduction() {
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://localhost"));
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://foo.local"));
    }

    @Test
    void blocksPrivateAndReservedIpsByLiteral() throws Exception {
        // IP literals resolve to themselves, so validation must reject them
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://127.0.0.1"));
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://10.0.0.5"));
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://192.168.1.1"));
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://172.16.0.1"));
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://169.254.169.254")); // cloud metadata
        assertThrows(SafeUrlException.class, () -> validator.validateProduction("https://[::1]"));
    }

    @Test
    void isBlockedAddressCatchesMetadataAndPrivateRanges() throws Exception {
        assertTrue(validator.isBlockedAddress(InetAddress.getByName("169.254.169.254")));
        assertTrue(validator.isBlockedAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(validator.isBlockedAddress(InetAddress.getByName("10.1.2.3")));
        assertTrue(validator.isBlockedAddress(InetAddress.getByName("192.168.0.1")));
        assertTrue(validator.isBlockedAddress(InetAddress.getByName("100.64.0.1")));   // CGNAT
        assertTrue(validator.isBlockedAddress(InetAddress.getByName("::1")));
        assertTrue(validator.isBlockedAddress(InetAddress.getByName("fc00::1")));       // unique local
        assertFalse(validator.isBlockedAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(validator.isBlockedAddress(InetAddress.getByName("1.1.1.1")));
    }

    @Test
    void redirectDowngradeToHttpRejected() {
        assertThrows(SafeUrlException.class, () -> validator.validateRedirect("http://example.com", false));
    }

    @Test
    void loopbackAllowedOnlyInLocalMode() {
        assertThrows(SafeUrlException.class, () -> validator.validate("http://127.0.0.1:8080", false));
        assertDoesNotThrow(() -> validator.validate("http://127.0.0.1:8080", true));
    }
}
