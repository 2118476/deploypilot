package com.deploypilot.security;

import com.deploypilot.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for AES-256-GCM credential encryption. */
class CredentialEncryptionServiceTest {

    private CredentialEncryptionService service(String key) {
        // Non-prod profile: a blank key falls back to the dev key so tests work.
        CredentialEncryptionService s = new CredentialEncryptionService(key, "test");
        s.init();
        return s;
    }

    @Test
    void roundTripsPlaintext() {
        CredentialEncryptionService s = service("a-strong-encryption-key-for-tests-1234567890");
        String secret = "ghp_supersecrettoken_ABC123";
        String ciphertext = s.encrypt(secret);
        assertEquals(secret, s.decrypt(ciphertext));
    }

    @Test
    void ciphertextNeverContainsPlaintext() {
        CredentialEncryptionService s = service("a-strong-encryption-key-for-tests-1234567890");
        String secret = "rnd_top_secret_value";
        String ciphertext = s.encrypt(secret);
        assertFalse(ciphertext.contains(secret), "ciphertext must not embed the plaintext");
        assertTrue(ciphertext.startsWith("v1:"), "ciphertext should be versioned");
    }

    @Test
    void encryptionIsRandomised() {
        CredentialEncryptionService s = service("a-strong-encryption-key-for-tests-1234567890");
        // A fresh random IV each time means identical plaintext yields different ciphertext.
        assertNotEquals(s.encrypt("same"), s.encrypt("same"));
    }

    @Test
    void sameKeyDecryptsAcrossInstances() {
        String key = "shared-encryption-key-value-across-instances-01";
        String ciphertext = service(key).encrypt("token-value");
        assertEquals("token-value", service(key).decrypt(ciphertext));
    }

    @Test
    void differentKeyCannotDecrypt() {
        String ciphertext = service("key-number-one-key-number-one-key-number-one").encrypt("token");
        CredentialEncryptionService other = service("key-number-two-key-number-two-key-number-two");
        assertThrows(IllegalStateException.class, () -> other.decrypt(ciphertext));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        CredentialEncryptionService s = service("a-strong-encryption-key-for-tests-1234567890");
        String ciphertext = s.encrypt("token");
        // Flip a character in the base64 body; GCM authentication must fail.
        String tampered = ciphertext.substring(0, ciphertext.length() - 2)
            + (ciphertext.endsWith("A") ? "B" : "A") + ciphertext.charAt(ciphertext.length() - 1);
        assertThrows(RuntimeException.class, () -> s.decrypt(tampered));
    }

    @Test
    void malformedCiphertextIsRejected() {
        CredentialEncryptionService s = service("a-strong-encryption-key-for-tests-1234567890");
        assertThrows(IllegalArgumentException.class, () -> s.decrypt("not-versioned"));
    }

    @Test
    void blankKeyFallsBackToDevKeyOutsideProduction() {
        // Blank config must still initialise (dev fallback), so local runs need no setup.
        CredentialEncryptionService s = service("");
        assertEquals("hello", s.decrypt(s.encrypt("hello")));
    }

    @Test
    void productionWithoutKeyBootsButFailsClosed() {
        // In prod without a strong key the service initialises (app still boots)
        // but refuses to encrypt/decrypt so nothing is stored under the dev key.
        CredentialEncryptionService s = new CredentialEncryptionService("", "prod");
        s.init();
        assertFalse(s.isSecurelyConfigured());
        assertThrows(ServiceUnavailableException.class, () -> s.encrypt("token"));
        assertThrows(ServiceUnavailableException.class, () -> s.decrypt("v1:whatever"));
    }

    @Test
    void productionWithStrongKeyIsConfigured() {
        CredentialEncryptionService s = new CredentialEncryptionService(
            "a-strong-production-encryption-key-1234567890", "prod");
        s.init();
        assertTrue(s.isSecurelyConfigured());
        assertEquals("v", s.decrypt(s.encrypt("v")));
    }
}
