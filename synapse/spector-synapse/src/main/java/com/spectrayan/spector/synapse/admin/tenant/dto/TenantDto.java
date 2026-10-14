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

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.admin.tenant.AdminAuditEntry;
import com.spectrayan.spector.synapse.admin.tenant.TenantRecord;
import com.spectrayan.spector.synapse.admin.tenant.TenantStatus;

/**
 * Content-free request and response DTOs for the tenant management API
 * at {@code /api/v1/admin/tenants} (ADR-0087, Issue #1055).
 *
 * <p><strong>Privacy Contract</strong>: Under ADR-0087, no DTO in this class may expose
 * memory text, vector embeddings, tags, labels, raw secrets, or user-supplied memory metadata.</p>
 */
public final class TenantDto {

    private TenantDto() {}

    /**
     * Tenant security and emergency break-glass policy settings.
     *
     * @param byokEnabled                  whether Bring Your Own Key (BYOK) encryption is active (read-only until BYOK keys are configured)
     * @param breakGlassApproverRequired   whether emergency break-glass requires secondary approver authorization
     * @param breakGlassMaxDurationMinutes maximum duration (in minutes, 1..1440) for an emergency break-glass session
     */
    public record TenantSettingsDto(
            Boolean byokEnabled,
            Boolean breakGlassApproverRequired,
            Integer breakGlassMaxDurationMinutes
    ) {
        public static TenantSettingsDto defaults() {
            return new TenantSettingsDto(false, true, 60);
        }
    }

    /**
     * Default resource quotas enforced at the tenant boundary (-1 indicates unlimited).
     *
     * @param maxAccounts     maximum user/agent accounts permitted in the tenant (-1 for unlimited)
     * @param maxNamespaces   maximum active namespaces permitted across the tenant (-1 for unlimited)
     * @param maxStorageBytes maximum aggregate storage bytes permitted across the tenant (-1 for unlimited)
     */
    public record TenantQuotasDto(
            Integer maxAccounts,
            Integer maxNamespaces,
            Long maxStorageBytes
    ) {
        public static TenantQuotasDto unlimited() {
            return new TenantQuotasDto(-1, -1, -1L);
        }
    }

    /**
     * Request payload for creating a new tenant.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateTenantRequest(
            String tenantId,
            String name,
            String description,
            String plan,
            TenantStatus status,
            Boolean legalHold,
            TenantSettingsDto settings,
            TenantQuotasDto quotas
    ) {}

    /**
     * Request payload for updating an existing tenant's metadata, status, settings, or quotas.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateTenantRequest(
            String name,
            String description,
            String plan,
            TenantStatus status,
            Boolean legalHold,
            TenantSettingsDto settings,
            TenantQuotasDto quotas
    ) {}

    /**
     * Content-free tenant representation combining catalog metadata, policy settings,
     * quotas, and aggregate resource counts.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TenantResponse(
            String tenantId,
            String name,
            String description,
            TenantStatus status,
            String plan,
            boolean legalHold,
            TenantSettingsDto settings,
            TenantQuotasDto quotas,
            long accountCount,
            long namespaceCount,
            long storageBytes,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
        public static TenantResponse from(
                TenantRecord record,
                long accountCount,
                long namespaceCount,
                long storageBytes) {
            return new TenantResponse(
                    record.tenantId(),
                    record.name(),
                    record.description(),
                    record.status(),
                    record.plan(),
                    record.legalHold(),
                    new TenantSettingsDto(
                            record.byokEnabled(),
                            record.breakGlassApproverRequired(),
                            record.breakGlassMaxDurationMinutes()
                    ),
                    new TenantQuotasDto(
                            record.maxAccounts(),
                            record.maxNamespaces(),
                            record.maxStorageBytes()
                    ),
                    accountCount,
                    namespaceCount,
                    storageBytes,
                    record.createdAt(),
                    record.updatedAt(),
                    record.deletedAt()
            );
        }
    }

    /**
     * Content-free audit entry DTO for tenant mutations.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TenantAuditResponse(
            String auditId,
            Instant timestamp,
            String actorId,
            String actorTenantId,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> details
    ) {
        public static TenantAuditResponse from(AdminAuditEntry entry) {
            return new TenantAuditResponse(
                    entry.auditId(),
                    entry.timestamp(),
                    entry.actorId(),
                    entry.actorTenantId(),
                    entry.action(),
                    entry.targetType(),
                    entry.targetId(),
                    entry.details()
            );
        }
    }
}
