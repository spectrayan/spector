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
package com.spectrayan.spector.synapse.admin.audit.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Content-free DTOs for the append-only Admin &amp; Privileged Audit Log API
 * at {@code /api/v1/admin/audit} (ADR-0087, Issue #1059).
 *
 * <p><strong>Content-Free Contract</strong>: Audit events contain only principal, resource,
 * action, outcome, correlation, and hash-chain integrity fields. Memory text, vectors, tags,
 * and secret material are strictly prohibited.</p>
 */
public final class AdminAuditDto {

    private AdminAuditDto() {}

    /**
     * Immutable representation of an append-only audit log event with SHA-256 hash chaining.
     */
    public record AuditEventDto(
            String id,
            Instant ts,
            String tenantId,
            String actorAccountId,
            String actorRole,
            String action,
            String targetType,
            String targetId,
            String namespaceId,
            String reason,
            String requestId,
            String outcome,
            Map<String, Object> details,
            String prevHash,
            String entryHash
    ) {}

    /**
     * Result of verifying the SHA-256 tamper-evident hash chain across recorded audit events.
     */
    public record AuditChainVerificationDto(
            boolean valid,
            long checkedCount,
            String lastEntryHash,
            String firstInvalidEventId,
            String message,
            Instant verifiedAt
    ) {}
}
