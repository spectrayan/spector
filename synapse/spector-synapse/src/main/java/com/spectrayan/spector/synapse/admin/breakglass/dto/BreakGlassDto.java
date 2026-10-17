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
package com.spectrayan.spector.synapse.admin.breakglass.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.admin.breakglass.BreakGlassRecord;
import com.spectrayan.spector.synapse.admin.breakglass.BreakGlassStatus;

/**
 * Content-free request and response DTOs for the emergency break-glass API at
 * {@code /api/v1/admin/break-glass} (ADR-0087 §5.4, Issue #1060).
 */
public final class BreakGlassDto {

    private BreakGlassDto() {}

    /**
     * Request payload for initiating an emergency break-glass session (`POST /api/v1/admin/break-glass`).
     *
     * @param namespaceId     target namespace ID or slug
     * @param justification   mandatory human-readable reason for requesting emergency access
     * @param ticketRef       optional incident/support ticket reference (e.g. INC-4821)
     * @param durationMinutes requested access duration in minutes (default 60, capped by tenant policy and 1440 max)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateBreakGlassRequest(
            @JsonAlias({"namespace_id", "namespace"}) String namespaceId,
            String justification,
            @JsonAlias({"ticket_ref"}) String ticketRef,
            @JsonAlias({"duration_minutes"}) Integer durationMinutes
    ) {}

    /**
     * Optional payload when approving a break-glass request (`POST /api/v1/admin/break-glass/{id}/approve`).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApproveBreakGlassRequest(
            String comment
    ) {}

    /**
     * Optional payload when rejecting a pending break-glass request (`POST /api/v1/admin/break-glass/{id}/reject`).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RejectBreakGlassRequest(
            String reason
    ) {}

    /**
     * Optional payload when revoking a break-glass session (`POST /api/v1/admin/break-glass/{id}/revoke`).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RevokeBreakGlassRequest(
            String reason
    ) {}

    /**
     * Content-free representation of a break-glass request and its lifecycle state.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BreakGlassResponse(
            String id,
            String tenantId,
            String namespaceId,
            String ownerAccountId,
            String requesterAccountId,
            String requesterRole,
            String justification,
            String ticketRef,
            int durationMinutes,
            BreakGlassStatus status,
            String approverAccountId,
            String approvalComment,
            String grantId,
            String accessScope,
            boolean exportPermitted,
            boolean mcpPermitted,
            Instant createdAt,
            Instant approvedAt,
            Instant expiresAt,
            Instant revokedAt,
            String revokedBy,
            String revokeReason
    ) {
        public static BreakGlassResponse from(BreakGlassRecord record) {
            return new BreakGlassResponse(
                    record.id(),
                    record.tenantId(),
                    record.namespaceId(),
                    record.ownerAccountId(),
                    record.requesterAccountId(),
                    record.requesterRole(),
                    record.justification(),
                    record.ticketRef(),
                    record.durationMinutes(),
                    record.status(),
                    record.approverAccountId(),
                    record.approvalComment(),
                    record.grantId(),
                    "READ_ONLY",
                    false,
                    false,
                    record.createdAt(),
                    record.approvedAt(),
                    record.expiresAt(),
                    record.revokedAt(),
                    record.revokedBy(),
                    record.revokeReason()
            );
        }
    }
}
