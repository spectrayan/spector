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
package com.spectrayan.spector.synapse.admin.tenant.dto;

import com.spectrayan.spector.commons.crypto.KeyHealthStatus;

import java.time.Instant;
import java.util.Set;

/**
 * Request and response DTOs for per-tenant BYOK Key-Encryption Key (KEK) configuration
 * and validation at {@code /api/v1/admin/tenants/{tenantId}/keys} (ADR-0088 §5, Issue #1070).
 */
public final class TenantKeyDto {

    private TenantKeyDto() {
    }

    /**
     * Request payload for registering or updating a tenant's BYOK KEK reference.
     *
     * @param provider       KMS provider identifier ({@code aws-kms}, {@code gcp-kms}, {@code azure-kv},
     *                       {@code hashicorp-vault}, {@code local-file})
     * @param kekKeyId       external KMS key ARN, resource path, or key identifier
     * @param credentialName name of the credential in the universal credentials vault ({@code /api/v1/credentials})
     * @param region         optional cloud region (e.g. {@code us-east-1})
     * @param endpoint       optional custom KMS endpoint URL (e.g. Vault URL, Azure Key Vault URL, LocalStack URL)
     */
    public record ConfigureTenantKeyRequest(
            String provider,
            String kekKeyId,
            String credentialName,
            String region,
            String endpoint) {
    }

    /**
     * Content-free response describing a tenant's current BYOK KEK configuration and health.
     * Never exposes raw key material, wrapped DEK ciphertexts, or decrypted credentials.
     */
    public record TenantKeyStatusResponse(
            String tenantId,
            boolean byokEnabled,
            String provider,
            String kekKeyId,
            String credentialName,
            String region,
            String endpoint,
            String keyVersion,
            String status,
            KeyHealthStatus healthStatus,
            int activeNamespaceDekCount,
            Instant lastVerifiedAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Set<String> supportedProviders) {
    }

    /**
     * Result of an on-demand KMS health check and live wrap/unwrap self-test.
     */
    public record TenantKeyValidationResponse(
            String tenantId,
            String provider,
            String kekKeyId,
            boolean valid,
            KeyHealthStatus healthStatus,
            boolean roundTripVerified,
            int evictedCachedDeks,
            Instant verifiedAt,
            String errorCode,
            String message) {
    }
}
