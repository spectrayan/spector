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
package com.spectrayan.spector.synapse.admin.namespaces;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.AdminNamespaceGrantResponse;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.AdminNamespaceResponse;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.EraseNamespaceRequest;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.NamespaceLegalHoldRequest;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.TransferOwnershipRequest;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.UpdateNamespaceQuotasRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantAuditResponse;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.dr.ErasureAuditReport;

/**
 * REST controller for the Admin Namespace Lifecycle API ({@code /api/v1/admin/namespaces}, ADR-0087, Issue #1057).
 *
 * <p>Provides content-free administrative listing, quota management, archive/restore, tombstone,
 * legal hold, ownership transfer, grant oversight, and compliance erasure for Tenant Admins
 * ({@code admin}) and Platform Operators ({@code super-admin}).</p>
 */
@RestController
@RequestMapping(value = "/api/v1/admin/namespaces", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('admin', 'super-admin', 'ADMIN', 'SUPER_ADMIN')")
public class AdminNamespaceController {

    private final AdminNamespaceService adminNamespaceService;

    public AdminNamespaceController(AdminNamespaceService adminNamespaceService) {
        this.adminNamespaceService = adminNamespaceService;
    }

    /**
     * Lists namespaces across a tenant ({@code admin}) or fleet-wide ({@code super-admin}).
     */
    @GetMapping
    public ResponseEntity<List<AdminNamespaceResponse>> listNamespaces(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "ownerAccountId", required = false) String ownerAccountId,
            @RequestParam(name = "status", required = false) NamespaceStatus status,
            @RequestParam(name = "type", required = false) NamespaceType type,
            @RequestParam(name = "legalHold", required = false) Boolean legalHold,
            @RequestParam(name = "includeTombstoned", defaultValue = "false") boolean includeTombstoned) {
        return ResponseEntity.ok(adminNamespaceService.listNamespaces(
                tenantId, ownerAccountId, status, type, legalHold, includeTombstoned));
    }

    /**
     * Retrieves a single namespace's administrative metadata, quotas, and counters.
     */
    @GetMapping("/{namespaceId}")
    public ResponseEntity<AdminNamespaceResponse> getNamespace(@PathVariable String namespaceId) {
        return ResponseEntity.ok(adminNamespaceService.getNamespace(namespaceId));
    }

    /**
     * Updates per-namespace quotas ({@code maxMemories}, {@code maxStorageBytes}, {@code maxOpsPerMinute}).
     */
    @PutMapping(value = "/{namespaceId}/quotas", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminNamespaceResponse> updateQuotas(
            @PathVariable String namespaceId,
            @RequestBody UpdateNamespaceQuotasRequest request) {
        return ResponseEntity.ok(adminNamespaceService.updateQuotas(namespaceId, request));
    }

    /**
     * Archives a namespace into read-only mode ({@code status = ARCHIVED}).
     */
    @PostMapping("/{namespaceId}/archive")
    public ResponseEntity<AdminNamespaceResponse> archiveNamespace(@PathVariable String namespaceId) {
        return ResponseEntity.ok(adminNamespaceService.archiveNamespace(namespaceId));
    }

    /**
     * Restores an archived namespace back to {@code ACTIVE} status.
     */
    @PostMapping("/{namespaceId}/restore")
    public ResponseEntity<AdminNamespaceResponse> restoreNamespace(@PathVariable String namespaceId) {
        return ResponseEntity.ok(adminNamespaceService.restoreNamespace(namespaceId));
    }

    /**
     * Soft-deletes (tombstones) a namespace. Refuses default or legal-hold namespaces.
     */
    @DeleteMapping("/{namespaceId}")
    public ResponseEntity<AdminNamespaceResponse> tombstoneNamespace(@PathVariable String namespaceId) {
        return ResponseEntity.ok(adminNamespaceService.tombstoneNamespace(namespaceId));
    }

    /**
     * Sets or releases legal hold on a namespace.
     */
    @PostMapping(value = "/{namespaceId}/legal-hold", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminNamespaceResponse> setLegalHold(
            @PathVariable String namespaceId,
            @RequestBody NamespaceLegalHoldRequest request) {
        return ResponseEntity.ok(adminNamespaceService.setLegalHold(namespaceId, request));
    }

    /**
     * Transfers ownership of a namespace to another account in the same tenant.
     */
    @PostMapping(value = "/{namespaceId}/transfer-ownership", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminNamespaceResponse> transferOwnership(
            @PathVariable String namespaceId,
            @RequestBody TransferOwnershipRequest request) {
        return ResponseEntity.ok(adminNamespaceService.transferOwnership(namespaceId, request));
    }

    /**
     * Executes compliance erasure of a namespace backed by {@code TenantErasureService}.
     */
    @PostMapping("/{namespaceId}/erase")
    public ResponseEntity<ErasureAuditReport> eraseNamespace(
            @PathVariable String namespaceId,
            @RequestBody(required = false) EraseNamespaceRequest request) {
        return ResponseEntity.ok(adminNamespaceService.eraseNamespace(namespaceId, request));
    }

    /**
     * Lists active grants on a namespace for administrative oversight without content access.
     */
    @GetMapping("/{namespaceId}/grants")
    public ResponseEntity<List<AdminNamespaceGrantResponse>> listGrants(@PathVariable String namespaceId) {
        return ResponseEntity.ok(adminNamespaceService.listGrants(namespaceId));
    }

    /**
     * Revokes a grant on a namespace via administrative oversight.
     */
    @DeleteMapping("/{namespaceId}/grants/{grantId}")
    public ResponseEntity<Void> revokeGrant(
            @PathVariable String namespaceId,
            @PathVariable String grantId) {
        adminNamespaceService.revokeGrant(namespaceId, grantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists append-only administrative audit log entries targeting the namespace.
     */
    @GetMapping("/{namespaceId}/audit")
    public ResponseEntity<List<TenantAuditResponse>> listNamespaceAuditLogs(@PathVariable String namespaceId) {
        return ResponseEntity.ok(adminNamespaceService.listNamespaceAuditLogs(namespaceId));
    }
}
