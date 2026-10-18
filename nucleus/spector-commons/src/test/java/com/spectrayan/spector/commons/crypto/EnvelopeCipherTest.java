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
package com.spectrayan.spector.commons.crypto;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EnvelopeCipher}, {@link DestroyableKeyMaterial}, and {@link WrappedKeyResult}
 * (ADR-0088, Issue #1070).
 */
@DisplayName("EnvelopeCipher & DestroyableKeyMaterial Unit Tests")
class EnvelopeCipherTest {

    @Test
    @DisplayName("AES-256-GCM encrypt and decrypt round-trip succeeds with bound AAD")
    void aes256GcmRoundTripWithAad() {
        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            byte[] plaintext = "Confidential episodic memory payload for acme-corp".getBytes(StandardCharsets.UTF_8);
            byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-finance", 1);

            EnvelopeCipher.CiphertextEnvelope envelope = EnvelopeCipher.encrypt(dek, plaintext, aad);
            assertThat(envelope.iv()).hasSize(EnvelopeCipher.GCM_IV_LENGTH_BYTES);
            assertThat(envelope.authTag()).hasSize(EnvelopeCipher.GCM_TAG_LENGTH_BYTES);
            assertThat(envelope.ciphertextWithTag()).hasSize(plaintext.length + EnvelopeCipher.GCM_TAG_LENGTH_BYTES);

            byte[] decrypted = EnvelopeCipher.decrypt(dek, envelope.iv(), envelope.ciphertextWithTag(), aad);
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }

    @Test
    @DisplayName("AES-256-GCM decryption fails closed with SPE-310-026 (MEMORY_AUTH_TAG_MISMATCH) on AAD mismatch")
    void decryptFailsOnAadMismatch() {
        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            byte[] plaintext = "Tenant A private memory".getBytes(StandardCharsets.UTF_8);
            byte[] aadTenantA = EnvelopeCipher.buildNamespaceDekAad("tenant-a", "ns-1", 1);
            byte[] aadTenantB = EnvelopeCipher.buildNamespaceDekAad("tenant-b", "ns-1", 1);

            EnvelopeCipher.CiphertextEnvelope envelope = EnvelopeCipher.encrypt(dek, plaintext, aadTenantA);

            assertThatThrownBy(() -> EnvelopeCipher.decrypt(dek, envelope.iv(), envelope.ciphertextWithTag(), aadTenantB))
                    .isInstanceOf(SpectorMemoryException.class)
                    .satisfies(ex -> {
                        SpectorMemoryException sme = (SpectorMemoryException) ex;
                        assertThat(sme.errorCode()).isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH);
                        assertThat(sme.codeId()).isEqualTo("SPE-310-026");
                    });
        }
    }

    @Test
    @DisplayName("AES-256-GCM decryption fails closed with SPE-310-026 (MEMORY_AUTH_TAG_MISMATCH) on tampered ciphertext")
    void decryptFailsOnTamperedCiphertext() {
        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            byte[] plaintext = "Untampered payload".getBytes(StandardCharsets.UTF_8);
            byte[] aad = EnvelopeCipher.buildNamespaceDekAad("tenant-a", "ns-1", 1);

            EnvelopeCipher.CiphertextEnvelope envelope = EnvelopeCipher.encrypt(dek, plaintext, aad);
            byte[] tampered = envelope.ciphertextWithTag();
            tampered[0] ^= (byte) 0xFF;

            assertThatThrownBy(() -> EnvelopeCipher.decrypt(dek, envelope.iv(), tampered, aad))
                    .isInstanceOf(SpectorMemoryException.class)
                    .satisfies(ex -> {
                        SpectorMemoryException sme = (SpectorMemoryException) ex;
                        assertThat(sme.errorCode()).isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH);
                    });
        }
    }

    @Test
    @DisplayName("HKDF-SHA256 matches official RFC 5869 Appendix A.1 test vector")
    void hkdfSha256MatchesRfc5869TestVector1() {
        HexFormat hex = HexFormat.of();
        byte[] ikm = hex.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = hex.parseHex("000102030405060708090a0b0c");
        byte[] info = hex.parseHex("f0f1f2f3f4f5f6f7f8f9");
        int length = 42;

        byte[] expectedPrk = hex.parseHex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5");
        byte[] expectedOkm = hex.parseHex(
                "3cb25f25faacd57a90434f64d0362f2a"
                + "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                + "34007208d5b887185865");

        byte[] prk = EnvelopeCipher.hkdfExtract(salt, ikm);
        assertThat(prk).isEqualTo(expectedPrk);

        byte[] okm = EnvelopeCipher.hkdfSha256(ikm, salt, info, length);
        assertThat(okm).isEqualTo(expectedOkm);
    }

    @Test
    @DisplayName("Domain-separated HKDF subkeys are deterministic and cryptographically distinct across domains, tenants, and versions")
    void deriveSubkeyIsolatesDomainsAndTenants() {
        try (DestroyableKeyMaterial masterDek = EnvelopeCipher.generateDek();
             DestroyableKeyMaterial payloadKey1 = EnvelopeCipher.deriveSubkey(
                     masterDek, "acme", "ns-1", 1, EnvelopeCipher.SUBKEY_PAYLOAD);
             DestroyableKeyMaterial payloadKey1Again = EnvelopeCipher.deriveSubkey(
                     masterDek, "acme", "ns-1", 1, EnvelopeCipher.SUBKEY_PAYLOAD);
             DestroyableKeyMaterial vectorKey1 = EnvelopeCipher.deriveSubkey(
                     masterDek, "acme", "ns-1", 1, EnvelopeCipher.SUBKEY_VECTOR);
             DestroyableKeyMaterial walKey1 = EnvelopeCipher.deriveSubkey(
                     masterDek, "acme", "ns-1", 1, EnvelopeCipher.SUBKEY_WAL);
             DestroyableKeyMaterial indexKey1 = EnvelopeCipher.deriveSubkey(
                     masterDek, "acme", "ns-1", 1, EnvelopeCipher.SUBKEY_INDEX);
             DestroyableKeyMaterial payloadKeyV2 = EnvelopeCipher.deriveSubkey(
                     masterDek, "acme", "ns-1", 2, EnvelopeCipher.SUBKEY_PAYLOAD);
             DestroyableKeyMaterial otherTenantKey = EnvelopeCipher.deriveSubkey(
                     masterDek, "globex", "ns-1", 1, EnvelopeCipher.SUBKEY_PAYLOAD)) {

            assertThat(payloadKey1.bytes()).isEqualTo(payloadKey1Again.bytes());
            assertThat(payloadKey1.bytes()).isNotEqualTo(vectorKey1.bytes());
            assertThat(payloadKey1.bytes()).isNotEqualTo(walKey1.bytes());
            assertThat(payloadKey1.bytes()).isNotEqualTo(indexKey1.bytes());
            assertThat(payloadKey1.bytes()).isNotEqualTo(payloadKeyV2.bytes());
            assertThat(payloadKey1.bytes()).isNotEqualTo(otherTenantKey.bytes());
        }
    }

    @Test
    @DisplayName("DestroyableKeyMaterial overwrites key bytes with zeroes upon destroy()")
    void destroyableKeyMaterialZeroizesMemory() {
        byte[] rawSource = new byte[32];
        for (int i = 0; i < rawSource.length; i++) {
            rawSource[i] = (byte) (i + 1);
        }

        DestroyableKeyMaterial material = new DestroyableKeyMaterial(rawSource, true);
        // Source array was wiped on construction
        assertThat(rawSource).containsOnly((byte) 0);
        assertThat(material.isDestroyed()).isFalse();
        assertThat(material.isZeroized()).isFalse();
        assertThat(material.length()).isEqualTo(32);

        material.destroy();

        assertThat(material.isDestroyed()).isTrue();
        assertThat(material.isZeroized()).isTrue();
        assertThatThrownBy(material::bytes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("destroyed");
    }
}
