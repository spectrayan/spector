/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    @Test
    @DisplayName("Fail closed: Unset master key in prod profile throws IllegalStateException with SPE-SEC-002")
    void unsetMasterKeyInProdProfileFailsClosed() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new AesGcmCipher(null, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPE-SEC-002")
                .hasMessageContaining("SPE-820-002");

        assertThatThrownBy(() -> new AesGcmCipher("", env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPE-SEC-002")
                .hasMessageContaining("SPE-820-002");
    }

    @Test
    @DisplayName("Fail closed: Unset master key with default profile (no active profile) throws IllegalStateException with SPE-SEC-002")
    void unsetMasterKeyWithDefaultProfileFailsClosed() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();

        assertThatThrownBy(() -> new AesGcmCipher(null, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPE-SEC-002")
                .hasMessageContaining("SPE-820-002");

        assertThatThrownBy(() -> new AesGcmCipher(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPE-SEC-002")
                .hasMessageContaining("SPE-820-002");
    }

    @Test
    @DisplayName("Dev profile: Unset master key falls back to dev key and functions correctly")
    void unsetMasterKeyInDevProfileUsesFallback() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("dev");

        AesGcmCipher devCipher = new AesGcmCipher(null, env);
        String secret = "dev-secret-payload";
        AesGcmCipher.EncryptedPayload encrypted = devCipher.encrypt(secret, "dev-tenant");
        assertThat(devCipher.decrypt(encrypted.ciphertext(), encrypted.iv(), "dev-tenant")).isEqualTo(secret);
    }

    @Test
    @DisplayName("Test profile: Unset master key falls back to test key and functions correctly")
    void unsetMasterKeyInTestProfileUsesFallback() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("test");

        AesGcmCipher testCipher = new AesGcmCipher(null, env);
        String secret = "test-secret-payload";
        AesGcmCipher.EncryptedPayload encrypted = testCipher.encrypt(secret, "test-tenant");
        assertThat(testCipher.decrypt(encrypted.ciphertext(), encrypted.iv(), "test-tenant")).isEqualTo(secret);
    }

    @Test
    @DisplayName("Prod profile: Valid master key configures cipher and functions correctly")
    void validMasterKeyInProdProfileSucceeds() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("prod");

        AesGcmCipher prodCipher = new AesGcmCipher("super-secret-production-master-key-32b", env);
        String secret = "production-credential-secret";
        AesGcmCipher.EncryptedPayload encrypted = prodCipher.encrypt(secret, "prod-tenant");
        assertThat(prodCipher.decrypt(encrypted.ciphertext(), encrypted.iv(), "prod-tenant")).isEqualTo(secret);
    }
}
