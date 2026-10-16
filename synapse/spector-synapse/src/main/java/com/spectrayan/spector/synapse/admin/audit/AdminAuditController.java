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
package com.spectrayan.spector.synapse.admin.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.admin.audit.dto.AdminAuditDto.AuditChainVerificationDto;
import com.spectrayan.spector.synapse.admin.audit.dto.AdminAuditDto.AuditEventDto;

/**
 * REST controller for querying and verifying the append-only Admin &amp; Privileged Audit Log
 * at {@code /api/v1/admin/audit} (ADR-0087, Issue #1059).
 *
 * <p><strong>Authorization &amp; Tenant Isolation Invariants</strong>:
 * <ul>
 *   <li>Accessible to {@code admin} (strictly scoped to their authenticated tenant) and
 *       {@code super-admin} (fleet-wide or filtered by any {@code tenantId}).</li>
 *   <li>Cross-tenant query attempts by a tenant {@code admin} fail closed with HTTP 403 Forbidden
 *       ({@code SPE-820-001} / {@code SPE-SEC-001}).</li>
 *   <li>All endpoints are read-only and 100% content-free.</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api/v1/admin/audit", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('admin', 'super-admin', 'ADMIN', 'SUPER_ADMIN')")
public class AdminAuditController {

    private final AuditLogService auditLogService;

    public AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService must not be null");
    }

    /**
     * Queries append-only audit events with optional filters for tenant, actor, action,
     * target type/ID, namespace ID, outcome, and time range.
     */
    @GetMapping
    public ResponseEntity<List<AuditEventDto>> queryAuditEvents(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "actorId", required = false) String actorId,
            @RequestParam(name = "actorAccountId", required = false) String actorAccountId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "targetId", required = false) String targetId,
            @RequestParam(name = "namespaceId", required = false) String namespaceId,
            @RequestParam(name = "outcome", required = false) String outcome,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "limit", required = false) Integer limit) {

        String effectiveActorFilter = (actorAccountId != null && !actorAccountId.isBlank())
                ? actorAccountId
                : actorId;

        return ResponseEntity.ok(auditLogService.queryAuditEvents(
                tenantId,
                effectiveActorFilter,
                action,
                targetType,
                targetId,
                namespaceId,
                outcome,
                from,
                to,
                limit
        ));
    }

    /**
     * Verifies the cryptographic SHA-256 hash chain across recorded audit events.
     */
    @GetMapping("/verify")
    public ResponseEntity<AuditChainVerificationDto> verifyAuditChain(
            @RequestParam(name = "tenantId", required = false) String tenantId) {
        return ResponseEntity.ok(auditLogService.verifyChain(tenantId));
    }
}
