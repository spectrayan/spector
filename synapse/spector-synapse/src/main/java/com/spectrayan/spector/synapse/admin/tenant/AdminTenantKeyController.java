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

import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.ConfigureTenantKeyRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.TenantKeyStatusResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.TenantKeyValidationResponse;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST controller for configuring, inspecting, and validating per-tenant Customer-Managed Key
 * (BYOK) KEK references at {@code /api/v1/admin/tenants/{tenantId}/keys} (ADR-0088 §5, Issue #1070).
 *
 * <p><strong>Authorization Invariant</strong>: Accessible to {@code super-admin} (for any tenant)
 * and tenant {@code admin} (for their own tenant only). Cross-tenant access by a tenant
 * {@code admin} is rejected with HTTP 403 Forbidden ({@code SPE-820-001} / {@code SPE-SEC-001}).</p>
 *
 * <p><strong>Content-Free &amp; Key-Safety Invariant</strong>: Key material never leaves the KMS.
 * Responses expose only KEK configuration references, health status, and active DEK counts.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/admin/tenants/{tenantId}/keys", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('admin', 'super-admin', 'ADMIN', 'SUPER_ADMIN')")
public class AdminTenantKeyController {

    private static final Logger log = LoggerFactory.getLogger(AdminTenantKeyController.class);

    private final TenantKeyService tenantKeyService;

    public AdminTenantKeyController(TenantKeyService tenantKeyService) {
        this.tenantKeyService = Objects.requireNonNull(tenantKeyService, "tenantKeyService must not be null");
    }

    /**
     * Inspects the current BYOK KEK configuration and health status for {@code tenantId}.
     */
    @GetMapping
    public ResponseEntity<TenantKeyStatusResponse> getTenantKeyStatus(@PathVariable String tenantId) {
        enforceTenantAccess(tenantId);
        return ResponseEntity.ok(tenantKeyService.getTenantKeyStatus(tenantId));
    }

    /**
     * Registers or updates the BYOK KEK reference for {@code tenantId} after performing a live
     * {@code wrapDek} + {@code unwrapDek} self-test against the configured KMS.
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantKeyStatusResponse> configureTenantKey(
            @PathVariable String tenantId,
            @RequestBody ConfigureTenantKeyRequest request) {
        enforceTenantAccess(tenantId);
        String actorId = SecurityUtils.getUserId();
        String actorTenantId = SecurityUtils.getTenantId();
        log.info("[AdminTenantKeyController] configureTenantKey tenantId={} provider={} by actor={}",
                tenantId, request != null ? request.provider() : null, actorId);
        TenantKeyStatusResponse status = tenantKeyService.configureTenantKey(
                tenantId, request, actorId, actorTenantId);
        return ResponseEntity.ok(status);
    }

    /**
     * Triggers an on-demand KMS health check and live wrap/unwrap verification for {@code tenantId}.
     */
    @PostMapping("/validate")
    public ResponseEntity<TenantKeyValidationResponse> validateTenantKey(@PathVariable String tenantId) {
        enforceTenantAccess(tenantId);
        String actorId = SecurityUtils.getUserId();
        String actorTenantId = SecurityUtils.getTenantId();
        log.info("[AdminTenantKeyController] validateTenantKey tenantId={} by actor={}", tenantId, actorId);
        TenantKeyValidationResponse validation = tenantKeyService.validateTenantKey(
                tenantId, actorId, actorTenantId);
        return ResponseEntity.ok(validation);
    }

    /**
     * Disables and removes the BYOK KEK configuration for {@code tenantId}, evicting cached DEKs.
     */
    @DeleteMapping
    public ResponseEntity<TenantKeyStatusResponse> deleteTenantKey(@PathVariable String tenantId) {
        enforceTenantAccess(tenantId);
        String actorId = SecurityUtils.getUserId();
        String actorTenantId = SecurityUtils.getTenantId();
        log.info("[AdminTenantKeyController] deleteTenantKey tenantId={} by actor={}", tenantId, actorId);
        TenantKeyStatusResponse status = tenantKeyService.deleteTenantKey(
                tenantId, actorId, actorTenantId);
        return ResponseEntity.ok(status);
    }

    private static void enforceTenantAccess(String targetTenantId) {
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        String callerTenantId = SecurityUtils.getTenantId();
        String callerUserId = SecurityUtils.getUserId();
        if (callerTenantId == null || targetTenantId == null
                || !callerTenantId.trim().equalsIgnoreCase(targetTenantId.trim())) {
            throw new CrossTenantAccessException(
                    callerUserId != null ? callerUserId : "unknown",
                    "tenant-keys:" + targetTenantId,
                    targetTenantId);
        }
    }
}
