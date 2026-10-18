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

/**
 * Service Provider Interface (SPI) for Customer-Managed Key (BYOK) and platform
 * Key-Encryption Key (KEK) providers (ADR-0088 §5.1, Issue #1070).
 *
 * <p>Implementations reside in the adapter layer ({@code synapse/spector-synapse}) while
 * the core memory engine and {@code spector-commons} depend exclusively on this JDK-only SPI.
 * Raw KEK material never leaves the external KMS.</p>
 */
public interface KeyProvider {

    /**
     * Canonical provider identifier (e.g. {@code "aws-kms"}, {@code "gcp-kms"},
     * {@code "azure-kv"}, {@code "hashicorp-vault"}, {@code "local-file"}, {@code "platform-hkdf"}).
     */
    String providerId();

    /**
     * Wraps a 256-bit plaintext Data-Encryption Key (DEK) under the tenant's configured KEK.
     *
     * @param tenantId     owning tenant identifier
     * @param kekKeyId     external KMS key ARN, resource path, or key identifier
     * @param plaintextDek 32-byte plaintext DEK to wrap
     * @param aad          additional authenticated data (e.g. {@code tenantId:namespaceId:dekVersion})
     * @return the wrapped DEK ciphertext and KEK version metadata
     */
    WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aad);

    /**
     * Unwraps a previously wrapped DEK using the tenant's configured KEK.
     *
     * @param tenantId   owning tenant identifier
     * @param kekKeyId   external KMS key ARN, resource path, or key identifier
     * @param wrappedDek ciphertext produced by {@link #wrapDek}
     * @param aad        additional authenticated data bound during {@link #wrapDek}
     * @return zeroizable plaintext DEK material
     */
    DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrappedDek, byte[] aad);

    /**
     * Verifies whether the tenant's KEK is reachable, enabled, or revoked/disabled in the KMS.
     *
     * @param tenantId owning tenant identifier
     * @param kekKeyId external KMS key ARN, resource path, or key identifier
     * @return current {@link KeyHealthStatus}
     */
    KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId);
}
