/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Cryptographic engine providing AES-256-GCM envelope encryption with HKDF-SHA256
 * per-tenant key derivation and smart preview masking.
 */
@Component
public class AesGcmCipher {

    private static final Logger log = LoggerFactory.getLogger(AesGcmCipher.class);
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_TAG_LENGTH_BYTES = 16;
    private static final int IV_LENGTH_BYTES = 12;
    private static final byte[] HKDF_SALT = "spector-credentials-vault-v1".getBytes(StandardCharsets.UTF_8);

    private final byte[] masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCipher(@Value("${spector.security.master-key:${SPECTOR_MASTER_ENCRYPTION_KEY:}}") String masterKeyConfig) {
        if (masterKeyConfig != null && !masterKeyConfig.isBlank()) {
            byte[] raw = masterKeyConfig.getBytes(StandardCharsets.UTF_8);
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                this.masterKey = sha256.digest(raw);
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        } else {
            log.warn("[AesGcmCipher] No SPECTOR_MASTER_ENCRYPTION_KEY configured; using deterministic development key");
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                this.masterKey = sha256.digest("spector-dev-default-master-key-32b".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
    }

    public record EncryptedPayload(String ciphertext, String iv, String authTag, String maskedPreview) {}

    /**
     * Encrypts a plaintext secret under a specific tenant's derived key.
     */
    public EncryptedPayload encrypt(String plaintext, String tenantId) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        String effectiveTenant = tenantId != null ? tenantId : "default";

        try {
            byte[] tenantKey = deriveTenantKey(masterKey, effectiveTenant);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(tenantKey, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Extract auth tag (last 16 bytes of GCM output in Java)
            int ciphertextLength = encryptedBytes.length - GCM_TAG_LENGTH_BYTES;
            byte[] tagBytes = Arrays.copyOfRange(encryptedBytes, ciphertextLength, encryptedBytes.length);

            String ciphertextBase64 = Base64.getEncoder().encodeToString(encryptedBytes);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String tagBase64 = Base64.getEncoder().encodeToString(tagBytes);
            String masked = maskSecret(plaintext);

            return new EncryptedPayload(ciphertextBase64, ivBase64, tagBase64, masked);
        } catch (Exception e) {
            log.error("[AesGcmCipher] Encryption failed for tenant '{}'", effectiveTenant, e);
            throw new RuntimeException("Failed to encrypt secret payload: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts a ciphertext payload under a specific tenant's derived key.
     */
    public String decrypt(String ciphertextBase64, String ivBase64, String tenantId) {
        Objects.requireNonNull(ciphertextBase64, "ciphertextBase64 must not be null");
        Objects.requireNonNull(ivBase64, "ivBase64 must not be null");
        String effectiveTenant = tenantId != null ? tenantId : "default";

        try {
            byte[] tenantKey = deriveTenantKey(masterKey, effectiveTenant);
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encryptedBytes = Base64.getDecoder().decode(ciphertextBase64);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(tenantKey, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[AesGcmCipher] Decryption failed for tenant '{}': {}", effectiveTenant, e.getMessage());
            throw new RuntimeException("Failed to decrypt secret payload: " + e.getMessage(), e);
        }
    }

    /**
     * Derives a 256-bit tenant-specific encryption key using HKDF-SHA256.
     */
    public byte[] deriveTenantKey(byte[] masterKeyBytes, String tenantId) {
        try {
            // 1. HKDF-Extract: PRK = HMAC-SHA256(salt, masterKey)
            Mac hmacExtract = Mac.getInstance(HMAC_ALGO);
            hmacExtract.init(new SecretKeySpec(HKDF_SALT, HMAC_ALGO));
            byte[] prk = hmacExtract.doFinal(masterKeyBytes);

            // 2. HKDF-Expand: OKM = HMAC-SHA256(PRK, info || 0x01)
            Mac hmacExpand = Mac.getInstance(HMAC_ALGO);
            hmacExpand.init(new SecretKeySpec(prk, HMAC_ALGO));
            byte[] info = tenantId.getBytes(StandardCharsets.UTF_8);
            hmacExpand.update(info);
            hmacExpand.update((byte) 0x01);

            return hmacExpand.doFinal(); // 32 bytes (256 bits)
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive tenant key via HKDF-SHA256", e);
        }
    }

    /**
     * Generates a smart preview mask for sensitive strings.
     */
    public String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "••••••••";
        }
        String trimmed = secret.trim();

        // Connection string masking: jdbc:postgresql://user:pass@host:port/db
        if (trimmed.contains("://") && trimmed.contains("@")) {
            int schemeEnd = trimmed.indexOf("://") + 3;
            int atIndex = trimmed.indexOf('@');
            String beforeScheme = trimmed.substring(0, schemeEnd);
            String userInfo = trimmed.substring(schemeEnd, atIndex);
            String afterAt = trimmed.substring(atIndex);

            if (userInfo.contains(":")) {
                int colon = userInfo.indexOf(':');
                String username = userInfo.substring(0, colon);
                return beforeScheme + username + ":••••••••" + afterAt;
            } else {
                return beforeScheme + "••••••••" + afterAt;
            }
        }

        // Standard token / API key masking
        int len = trimmed.length();
        if (len <= 8) {
            return "••••••••";
        } else if (len <= 16) {
            return trimmed.substring(0, 2) + "••••••••" + trimmed.substring(len - 2);
        } else if (trimmed.startsWith("sk-proj-")) {
            return "sk-proj-••••••••" + trimmed.substring(len - 4);
        } else if (trimmed.startsWith("sk-") || trimmed.startsWith("ghp_") || trimmed.startsWith("xoxb-")) {
            int prefixEnd = 4;
            if (trimmed.startsWith("xoxb-")) prefixEnd = 5;
            return trimmed.substring(0, prefixEnd) + "••••••••" + trimmed.substring(len - 4);
        } else {
            return trimmed.substring(0, 4) + "••••••••" + trimmed.substring(len - 4);
        }
    }
}
