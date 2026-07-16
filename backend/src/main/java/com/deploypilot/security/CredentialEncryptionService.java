package com.deploypilot.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Symmetric authenticated encryption (AES-256-GCM) for provider credentials and
 * user-supplied deployment secrets held at rest.
 *
 * <p>The key comes from {@code DEPLOYPILOT_ENCRYPTION_KEY}. Outside production a
 * fixed development key is derived so local runs and tests work without
 * configuration. In production without a strong key the app still boots (so
 * unrelated features keep working), but this service <em>fails closed</em>: any
 * attempt to encrypt or decrypt is refused, so no credential is ever stored
 * under the weak development key.
 *
 * <p>This class must never log plaintext or ciphertext material. Callers must
 * never log the values passed in or returned.
 */
@Service
public class CredentialEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(CredentialEncryptionService.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String VERSION = "v1";
    // Used only outside production so local development and tests need no setup.
    public static final String DEV_FALLBACK_KEY = "deploypilot-dev-only-encryption-key-not-for-production";

    private final String configuredKey;
    private final boolean prodProfile;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec key;
    // True in production without a strong key: the app boots but connection and
    // secret operations are refused so nothing is encrypted under the dev key.
    private boolean secureKeyMissing;

    public CredentialEncryptionService(
            @Value("${deploypilot.encryption.key:}") String configuredKey,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.configuredKey = configuredKey;
        this.prodProfile = activeProfiles != null && activeProfiles.contains("prod");
    }

    @PostConstruct
    void init() {
        boolean weak = configuredKey == null || configuredKey.isBlank() || DEV_FALLBACK_KEY.equals(configuredKey);
        this.secureKeyMissing = prodProfile && weak;
        String material = weak ? DEV_FALLBACK_KEY : configuredKey;
        this.key = new SecretKeySpec(deriveKeyBytes(material), "AES");
        // Deliberately never logs the key or any hash of it.
        if (secureKeyMissing) {
            log.warn("DEPLOYPILOT_ENCRYPTION_KEY is not configured for production. Provider connections and stored "
                + "deployment secrets are disabled until a strong key is set; all other features work normally.");
        } else {
            log.info("Credential encryption initialised (AES-256-GCM)");
        }
    }

    /** Whether a strong key is configured, so connections and secrets can be used. */
    public boolean isSecurelyConfigured() {
        return !secureKeyMissing;
    }

    private void ensureConfigured() {
        if (secureKeyMissing) {
            throw new com.deploypilot.exception.ServiceUnavailableException(
                "Provider connections and deployment secrets are disabled until DEPLOYPILOT_ENCRYPTION_KEY is set on the server.");
        }
    }

    /**
     * Turns the configured secret into 32 key bytes. A base64 value that decodes
     * to a valid AES key length is used directly; anything else is hashed with
     * SHA-256 so any sufficiently strong passphrase is accepted.
     */
    private static byte[] deriveKeyBytes(String material) {
        try {
            byte[] decoded = Base64.getDecoder().decode(material);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64 — fall through to hashing
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Encrypts UTF-8 plaintext, returning {@code v1:base64(iv||ciphertext||tag)}. */
    public String encrypt(String plaintext) {
        ensureConfigured();
        if (plaintext == null) {
            throw new IllegalArgumentException("Cannot encrypt null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return VERSION + ":" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Message intentionally carries no secret material.
            throw new IllegalStateException("Encryption failed");
        }
    }

    /** Reverses {@link #encrypt}. Throws if the ciphertext is malformed or the tag fails. */
    public String decrypt(String token) {
        ensureConfigured();
        if (token == null || !token.startsWith(VERSION + ":")) {
            throw new IllegalArgumentException("Unrecognised ciphertext format");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(token.substring(VERSION.length() + 1));
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed");
        }
    }
}
