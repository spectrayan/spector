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
package com.spectrayan.spector.synapse.admin.tenant;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantAuditResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.UpdateTenantRequest;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * Platform-Operator ({@code super-admin}) REST controller for managing multi-tenant
 * lifecycle, settings, break-glass policies, and quotas at {@code /api/v1/admin/tenants}
 * (ADR-0087, Issue #1055).
 *
 * <p><strong>Authorization Invariant</strong>: Strictly restricted to {@code super-admin}.
 * Tenant Admins ({@code admin}) and standard users receive HTTP 403 Forbidden.</p>
 *
 * <p><strong>Content-Free Invariant</strong>: Returns only tenant metadata, policy flags,
 * quotas, and aggregate counts (accounts, namespaces, storage bytes). Never exposes memory content.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/admin/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('super-admin', 'SUPER_ADMIN')")
public class AdminTenantController {

    private static final Logger log = LoggerFactory.getLogger(AdminTenantController.class);

    private final TenantService tenantService;

    public AdminTenantController(TenantService tenantService) {
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
    }

    /**
     * Creates a new tenant.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantResponse> createTenant(@RequestBody CreateTenantRequest request) {
        String actorId = SecurityUtils.getUserId();
        String actorTenantId = SecurityUtils.getTenantId();
        log.info("[AdminTenantController] createTenant requested by actor={}", actorId);
        TenantResponse created = tenantService.createTenant(request, actorId, actorTenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all tenants with aggregate counts. Excludes soft-deleted tenants by default unless
     * {@code includeDeleted=true} or {@code status=DELETED} is requested.
     */
    @GetMapping
    public ResponseEntity<List<TenantResponse>> listTenants(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        boolean effIncludeDeleted = includeDeleted || status == TenantStatus.DELETED;
        return ResponseEntity.ok(tenantService.listTenants(status, effIncludeDeleted));
    }

    /**
     * Retrieves a single tenant's metadata, settings, quotas, and aggregate counts.
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.getTenant(tenantId));
    }

    /**
     * Updates a tenant's display name, description, plan, status (ACTIVE/SUSPENDED),
     * legal hold, break-glass policy, or default quotas.
     */
    @PutMapping(value = "/{tenantId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable String tenantId,
            @RequestBody UpdateTenantRequest request) {
        String actorId = SecurityUtils.getUserId();
        String actorTenantId = SecurityUtils.getTenantId();
        log.info("[AdminTenantController] updateTenant tenantId={} by actor={}", tenantId, actorId);
        TenantResponse updated = tenantService.updateTenant(tenantId, request, actorId, actorTenantId);
        return ResponseEntity.ok(updated);
    }

    /**
     * Soft-deletes a tenant (marks status as DELETED).
     */
    @DeleteMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> deleteTenant(@PathVariable String tenantId) {
        String actorId = SecurityUtils.getUserId();
        String actorTenantId = SecurityUtils.getTenantId();
        log.info("[AdminTenantController] deleteTenant tenantId={} by actor={}", tenantId, actorId);
        TenantResponse deleted = tenantService.softDeleteTenant(tenantId, actorId, actorTenantId);
        return ResponseEntity.ok(deleted);
    }

    /**
     * Lists audit log entries for mutations performed on a specific tenant.
     */
    @GetMapping("/{tenantId}/audit")
    public ResponseEntity<List<TenantAuditResponse>> listTenantAuditLogs(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.listAuditLogs(tenantId));
    }
}
