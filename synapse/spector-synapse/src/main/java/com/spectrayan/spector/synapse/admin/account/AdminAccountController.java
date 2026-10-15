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
package com.spectrayan.spector.synapse.admin.account;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.AccountAdminResponse;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.CreateAccountRequest;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.OwnedAgentSummaryDto;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.ReassignTenantRequest;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.ResetCredentialsRequest;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.ResetCredentialsResponse;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.UpdateAccountRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantAuditResponse;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * REST controller for the Admin Account and Agent-Principal Registry at
 * {@code /api/v1/admin/accounts} (ADR-0087, Issue #1056).
 *
 * <p><strong>Authorization Invariant</strong>: Accessible to {@code admin} (strictly scoped to
 * accounts within their authenticated tenant) and {@code super-admin} (fleet-wide across tenants).
 * Cross-tenant access or mutation by a tenant {@code admin} fails closed with HTTP 403 Forbidden
 * ({@code SPE-820-002} / {@code SPE-SEC-001}). Reassigning an account across tenants requires
 * {@code super-admin}.</p>
 *
 * <p><strong>Content-Free Invariant</strong>: Exposes only account identity, owner linkage,
 * namespace grants, and aggregate resource telemetry (namespace count, storage bytes, API-key count,
 * last activity timestamp). Never exposes memory content, vectors, or secrets.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/admin/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('admin', 'super-admin', 'ADMIN', 'SUPER_ADMIN')")
public class AdminAccountController {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountController.class);

    private final AdminAccountService accountService;

    public AdminAccountController(AdminAccountService accountService) {
        this.accountService = Objects.requireNonNull(accountService, "accountService must not be null");
    }

    /**
     * Lists accounts with optional filters for tenant, principal kind ({@code HUMAN}, {@code AGENT},
     * {@code SERVICE}), packaging profile, active status, and owner account ID.
     */
    @GetMapping
    public ResponseEntity<List<AccountAdminResponse>> listAccounts(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) PrincipalKind kind,
            @RequestParam(required = false) AccountProfile profile,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String ownerAccountId) {
        return ResponseEntity.ok(accountService.listAccounts(tenantId, kind, profile, active, ownerAccountId));
    }

    /**
     * Retrieves a single account with owner linkage, owned agent principals, namespace grants,
     * and content-free resource aggregates.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountAdminResponse> getAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    /**
     * Creates a new human, agent, or service account, enforcing tenant account quotas, linking
     * {@code ownerAccountId} for agent/service principals, and optionally issuing initial namespace grants.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountAdminResponse> createAccount(@RequestBody CreateAccountRequest request) {
        log.info("[AdminAccountController] createAccount username={} kind={} requested by actor={}",
                request != null ? request.username() : null,
                request != null ? request.kind() : null,
                SecurityUtils.getUserId());
        AccountAdminResponse created = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an account's display name, email, active state (suspend/reactivate), profile,
     * roles, scopes, legal hold, owner linkage, or namespace quotas.
     */
    @PutMapping(value = "/{accountId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountAdminResponse> updateAccount(
            @PathVariable String accountId,
            @RequestBody UpdateAccountRequest request) {
        log.info("[AdminAccountController] updateAccount accountId={} by actor={}",
                accountId, SecurityUtils.getUserId());
        return ResponseEntity.ok(accountService.updateAccount(accountId, request));
    }

    /**
     * Reassigns an account to another tenant ({@code super-admin} only).
     */
    @PostMapping(value = "/{accountId}/tenant", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('super-admin', 'SUPER_ADMIN')")
    public ResponseEntity<AccountAdminResponse> reassignTenant(
            @PathVariable String accountId,
            @RequestBody ReassignTenantRequest request) {
        log.info("[AdminAccountController] reassignTenant accountId={} to tenant={} by actor={}",
                accountId, request != null ? request.tenantId() : null, SecurityUtils.getUserId());
        return ResponseEntity.ok(accountService.reassignTenant(accountId, request));
    }

    /**
     * Resets an account's credentials: optionally sets a new password, revokes active refresh tokens,
     * and optionally revokes inbound {@code AUTH} API keys in the credentials vault.
     */
    @PostMapping(value = "/{accountId}/reset-credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResetCredentialsResponse> resetCredentials(
            @PathVariable String accountId,
            @RequestBody(required = false) ResetCredentialsRequest request) {
        log.info("[AdminAccountController] resetCredentials accountId={} by actor={}",
                accountId, SecurityUtils.getUserId());
        return ResponseEntity.ok(accountService.resetCredentials(accountId, request));
    }

    /**
     * Lists agent and service principals created/owned by the specified account.
     */
    @GetMapping("/{accountId}/agents")
    public ResponseEntity<List<OwnedAgentSummaryDto>> listOwnedAgents(@PathVariable String accountId) {
        return ResponseEntity.ok(accountService.listOwnedAgents(accountId));
    }

    /**
     * Lists audit log entries for administrative mutations targeting the specified account.
     */
    @GetMapping("/{accountId}/audit")
    public ResponseEntity<List<TenantAuditResponse>> listAccountAuditLogs(@PathVariable String accountId) {
        return ResponseEntity.ok(accountService.listAuditLogs(accountId));
    }
}
