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
package com.spectrayan.spector.kernel.storage;

import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;

/**
 * Service Provider Interface for byte payload encryption at rest in storage layers.
 */
@FunctionalInterface
public interface PayloadEncryptor {

    /**
     * Encrypts a plaintext payload.
     *
     * @param plaintext unencrypted payload bytes
     * @return ciphertext bytes
     */
    byte[] encrypt(byte[] plaintext);

    /**
     * Decrypts a ciphertext payload.
     *
     * @param ciphertext encrypted payload bytes
     * @return plaintext bytes
     */
    default byte[] decrypt(byte[] ciphertext) {
        return ciphertext;
    }

    /**
     * Encrypts UTF-8 text bytes.
     *
     * @param plaintext unencrypted UTF-8 text bytes
     * @return ciphertext bytes
     */
    default byte[] encryptText(byte[] plaintext) {
        return encrypt(plaintext);
    }

    /**
     * Decrypts UTF-8 text bytes.
     *
     * @param ciphertext encrypted text bytes
     * @return decrypted UTF-8 text bytes
     */
    default byte[] decryptText(byte[] ciphertext) {
        return decrypt(ciphertext);
    }

    /**
     * Returns true if encryption is active and enabled.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Returns the tenant ID bound into authenticated encryption AAD.
     */
    default String tenantId() {
        return "default";
    }

    /**
     * Returns the namespace ID bound into authenticated encryption AAD.
     */
    default String namespaceId() {
        return "default";
    }

    /**
     * Returns the active DEK version (>= 1).
     */
    default int dekVersion() {
        return 1;
    }

    /**
     * Returns the encryption mode byte written to {@link EncryptedSegmentHeader}.
     */
    default byte encryptionMode() {
        return isEnabled() ? EncryptedSegmentHeader.MODE_PLATFORM_ENVELOPE : EncryptedSegmentHeader.MODE_PLAINTEXT;
    }

    /**
     * Returns the 16-byte DEK identifier written to {@link EncryptedSegmentHeader}.
     */
    default byte[] dekIdBytes() {
        return new byte[16];
    }

    /**
     * Returns the 32-byte KEK key URI hash written to {@link EncryptedSegmentHeader}.
     */
    default byte[] kekKeyIdHashBytes() {
        return new byte[32];
    }

    /**
     * Derives a fresh 256-bit segment subkey ({@code SDK}) for the given segment type.
     * The caller is responsible for closing/destroying the returned {@link DestroyableKeyMaterial}.
     */
    default DestroyableKeyMaterial segmentSubkey(byte segmentType) {
        return null;
    }
}

