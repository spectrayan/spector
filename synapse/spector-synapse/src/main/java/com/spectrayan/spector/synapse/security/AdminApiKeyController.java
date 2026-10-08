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
package com.spectrayan.spector.synapse.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.memory.MemoryDto.ErrorResponse;
import com.spectrayan.spector.synapse.security.AuthDto.ApiKeySummary;

/**
 * REST controller providing administrative oversight of API keys at {@code /api/v1/admin/api-keys}
 * (Requirement R3, Issue #1050).
 *
 * <p>Accessible to {@code admin} (tenant-scoped) and {@code super-admin} (fleet-wide).
 * Returns key metadata only, maintaining the content-free contract.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/api-keys")
@PreAuthorize("hasAnyRole('admin', 'super-admin', 'ADMIN', 'SUPER_ADMIN')")
public class AdminApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyController.class);

    private final ApiKeyStore apiKeyStore;
    private final UserAccountStore userAccountStore;

    public AdminApiKeyController(ApiKeyStore apiKeyStore, UserAccountStore userAccountStore) {
        this.apiKeyStore = apiKeyStore;
        this.userAccountStore = userAccountStore;
    }

    /**
     * Lists API keys for administrative oversight (Requirement R3).
     *
     * <ul>
     *   <li>{@code super-admin}: fleet-wide; may optionally filter by {@code ?accountId=}.</li>
     *   <li>{@code admin}: tenant-scoped oversight; may filter by {@code ?accountId=} within their tenant only.</li>
     * </ul>
     *
     * @param accountId optional account ID filter
     * @param userId    optional alias for accountId
     * @return list of API key metadata records
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listApiKeys(
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "userId", required = false) String userId) {

        String filterAccountId = (accountId != null && !accountId.isBlank())
                ? accountId.trim()
                : (userId != null && !userId.isBlank() ? userId.trim() : null);

        String callerUserId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();

        String callerTenant = userAccountStore.findByUserId(callerUserId)
                .map(UserRow::tenantId)
                .filter(t -> t != null && !t.isBlank())
                .orElseGet(SecurityUtils::getTenantId);
        if (callerTenant == null || callerTenant.isBlank()) {
            callerTenant = "default";
        }

        // Fleet-wide access for Platform Operators (super-admin)
        if (isSuperAdmin) {
            if (filterAccountId != null) {
                List<ApiKeySummary> keys = apiKeyStore.findByUserId(filterAccountId).stream()
                        .map(ApiKeySummary::from)
                        .toList();
                return ResponseEntity.ok(keys);
            } else {
                List<ApiKeySummary> keys = apiKeyStore.findAll().stream()
                        .map(ApiKeySummary::from)
                        .toList();
                return ResponseEntity.ok(keys);
            }
        }

        // Tenant-scoped access for Tenant Admins (admin)
        String finalCallerTenant = callerTenant;
        Set<String> tenantUserIds = userAccountStore.listUsers().stream()
                .filter(u -> {
                    String ut = u.tenantId();
                    if (ut == null || ut.isBlank()) {
                        ut = "default";
                    }
                    return finalCallerTenant.equalsIgnoreCase(ut);
                })
                .map(UserRow::userId)
                .collect(Collectors.toSet());

        if (filterAccountId != null) {
            Optional<UserRow> targetUser = userAccountStore.findByUserId(filterAccountId);
            if (targetUser.isPresent()) {
                String targetTenant = targetUser.get().tenantId();
                if (targetTenant == null || targetTenant.isBlank()) {
                    targetTenant = "default";
                }
                if (!finalCallerTenant.equalsIgnoreCase(targetTenant)) {
                    log.warn("[AdminAuth] Tenant admin {} (tenant={}) attempted cross-tenant oversight of account {} (tenant={})",
                            callerUserId, finalCallerTenant, filterAccountId, targetTenant);
                    return forbidden("[" + ErrorCode.CROSS_TENANT_ACCESS_DENIED.id() + " / "
                            + CrossTenantAccessException.ERROR_CODE_ALIAS + "] Cross-tenant access denied: account '"
                            + callerUserId + "' cannot oversee account '" + filterAccountId
                            + "' belonging to tenant '" + targetTenant + "'");
                }
            } else if (!tenantUserIds.contains(filterAccountId)) {
                return notFound("User not found: " + filterAccountId);
            }
            List<ApiKeySummary> keys = apiKeyStore.findByUserId(filterAccountId).stream()
                    .map(ApiKeySummary::from)
                    .toList();
            return ResponseEntity.ok(keys);
        }

        List<ApiKeySummary> keys = apiKeyStore.findAll().stream()
                .filter(k -> tenantUserIds.contains(k.userId()))
                .map(ApiKeySummary::from)
                .toList();
        return ResponseEntity.ok(keys);
    }

    private static ResponseEntity<ErrorResponse> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", message));
    }

    private static ResponseEntity<ErrorResponse> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        ErrorCode.CROSS_TENANT_ACCESS_DENIED.id(),
                        message,
                        Map.of(
                                "alias", CrossTenantAccessException.ERROR_CODE_ALIAS,
                                "code", ErrorCode.CROSS_TENANT_ACCESS_DENIED.id()
                        )
                ));
    }
}
