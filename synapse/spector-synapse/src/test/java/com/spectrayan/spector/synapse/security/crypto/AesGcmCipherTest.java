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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherTest {

    private AesGcmCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new AesGcmCipher("test-master-secret-key-32-bytes-long");
    }

    @Test
    @DisplayName("Encryption & Decryption Round-Trip produces original plaintext")
    void encryptionRoundTrip() {
        String secret = "sk-proj-1234567890abcdefghijklmnopqrstuvwxyz";
        String tenantId = "tenant-alpha";

        AesGcmCipher.EncryptedPayload encrypted = cipher.encrypt(secret, tenantId);

        assertThat(encrypted.ciphertext()).isNotBlank().isNotEqualTo(secret);
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(encrypted.authTag()).isNotBlank();

        String decrypted = cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), tenantId);
        assertThat(decrypted).isEqualTo(secret);
    }

    @Test
    @DisplayName("Tampered ciphertext fails authenticated decryption")
    void tamperedCiphertextFails() {
        String secret = "super-secret-whatsapp-token";
        String tenantId = "tenant-finance";

        AesGcmCipher.EncryptedPayload encrypted = cipher.encrypt(secret, tenantId);

        // Tamper with ciphertext by corrupting Base64 payload
        byte[] decoded = java.util.Base64.getDecoder().decode(encrypted.ciphertext());
        decoded[0] ^= 0xFF; // flip bits
        String tamperedCiphertext = java.util.Base64.getEncoder().encodeToString(decoded);

        assertThatThrownBy(() -> cipher.decrypt(tamperedCiphertext, encrypted.iv(), tenantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("Multi-tenant cryptographic isolation: Tenant B key cannot decrypt Tenant A ciphertext")
    void multiTenantKeyIsolation() {
        String secret = "database-password-prod";
        AesGcmCipher.EncryptedPayload encryptedTenantA = cipher.encrypt(secret, "tenant-a");

        // Attempt decrypt with tenant-b derived key
        assertThatThrownBy(() -> cipher.decrypt(encryptedTenantA.ciphertext(), encryptedTenantA.iv(), "tenant-b"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("Smart preview masking protects sensitive keys and URLs")
    void smartMasking() {
        // OpenAI / API Key format
        String maskedApiKey = cipher.maskSecret("sk-proj-1234567890abcdefghij");
        assertThat(maskedApiKey).startsWith("sk-proj-").endsWith("ghij").contains("••••••••");

        // Slack Bot token format
        String maskedSlack = cipher.maskSecret("xoxb-123456789012-abcdef1234");
        assertThat(maskedSlack).startsWith("xoxb-").endsWith("1234").contains("••••••••");

        // Database connection string format
        String maskedDb = cipher.maskSecret("postgres://admin:superSecretPass123@db.internal.corp:5432/spector_prod");
        assertThat(maskedDb).isEqualTo("postgres://admin:••••••••@db.internal.corp:5432/spector_prod");

        // Short strings
        assertThat(cipher.maskSecret("short")).isEqualTo("••••••••");
        assertThat(cipher.maskSecret(null)).isEqualTo("••••••••");
    }
}
