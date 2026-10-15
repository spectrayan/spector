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
package com.spectrayan.spector.synapse.admin.namespaces.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;

/**
 * Content-free administrative DTOs for namespace lifecycle, quotas, archive/restore,
 * legal hold, ownership transfer, grant oversight, and compliance erasure
 * at {@code /api/v1/admin/namespaces} (ADR-0087, Issue #1057).
 *
 * <p>No DTO in this class may ever expose memory text, embeddings, tags, payloads, or secrets.</p>
 */
public final class AdminNamespaceDto {

    private AdminNamespaceDto() {}

    /**
     * Per-namespace resource quotas (-1 indicates unlimited / inherit tenant default).
     */
    public record NamespaceQuotasDto(
            int maxMemories,
            long maxStorageBytes,
            int maxOpsPerMinute
    ) {}

    /**
     * Request payload to update per-namespace quotas via {@code PUT /api/v1/admin/namespaces/{namespaceId}/quotas}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateNamespaceQuotasRequest(
            Integer maxMemories,
            Long maxStorageBytes,
            Integer maxOpsPerMinute
    ) {}

    /**
     * Request payload to set or release legal hold on a namespace via
     * {@code POST /api/v1/admin/namespaces/{namespaceId}/legal-hold}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NamespaceLegalHoldRequest(
            boolean legalHold,
            String reason
    ) {}

    /**
     * Request payload to transfer namespace ownership to another account within the same tenant via
     * {@code POST /api/v1/admin/namespaces/{namespaceId}/transfer-ownership}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransferOwnershipRequest(
            String newOwnerAccountId,
            String previousOwnerGrantRole,
            String reason
    ) {}

    /**
     * Optional request payload for compliance erasure via
     * {@code POST /api/v1/admin/namespaces/{namespaceId}/erase}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EraseNamespaceRequest(
            Boolean propagateReplicas,
            String reason
    ) {}

    /**
     * Content-free administrative summary of an active grant on a namespace.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminNamespaceGrantResponse(
            String grantId,
            String namespaceId,
            String principalId,
            String principalUsername,
            String principalType,
            String role,
            List<String> actions,
            String grantedBy,
            Instant grantedAt,
            Instant expiresAt
    ) {}

    /**
     * Content-free administrative view of a namespace with owner linkage, quotas,
     * aggregate resource counts, and activity timestamps.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminNamespaceResponse(
            String namespaceId,
            String slug,
            String displayName,
            String description,
            NamespaceType type,
            NamespaceStatus status,
            boolean legalHold,
            String tenantId,
            String ownerAccountId,
            String ownerUsername,
            String ownerDisplayName,
            long grantsCount,
            long memoryCount,
            long storageBytes,
            NamespaceQuotasDto quotas,
            Instant createdAt,
            Instant lastWriteAt,
            Instant lastRecallAt
    ) {}
}
