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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.security.SpectorRoles;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.AccountAdminResponse;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.AccountNamespaceGrantDto;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.AccountQuotaSummaryDto;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.CreateAccountRequest;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.InitialNamespaceGrantDto;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.OwnedAgentSummaryDto;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.ReassignTenantRequest;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.ResetCredentialsRequest;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.ResetCredentialsResponse;
import com.spectrayan.spector.synapse.admin.account.dto.AdminAccountDto.UpdateAccountRequest;
import com.spectrayan.spector.synapse.admin.tenant.AdminAuditEntry;
import com.spectrayan.spector.synapse.admin.tenant.TenantRecord;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantAuditResponse;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantObjectType;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.PrincipalType;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.catalog.exception.TenantQuotaExceededException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.synapse.error.SynapseConflictException;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import com.spectrayan.spector.synapse.security.UserRow;

/**
 * Administrative service managing human, agent, and service accounts, owner linkage,
 * namespace grant summaries, content-free resource aggregates, and append-only audit trails
 * at {@code /api/v1/admin/accounts} (ADR-0087, Issue #1056).
 */
@Service
@DependsOn("flyway")
public class AdminAccountService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

    private final JdbcClient jdbc;
    private final UserAccountStore userAccountStore;
    private final AccountCatalog accountCatalog;
    private final CredentialRepository credentialRepository;
    private final TenantService tenantService;
    private final SqlQueryLoader sqlLoader;
    private final ObjectMapper objectMapper;
    private final SynapseProperties synapseProps;
    private final TsidGenerator tsid;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AdminAccountService(
            JdbcClient jdbc,
            UserAccountStore userAccountStore,
            AccountCatalog accountCatalog,
            CredentialRepository credentialRepository,
            TenantService tenantService,
            SqlQueryLoader sqlLoader,
            ObjectMapper objectMapper,
            @Autowired(required = false) TsidGenerator tsid,
            @Autowired(required = false) SynapseProperties synapseProps) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.userAccountStore = Objects.requireNonNull(userAccountStore, "userAccountStore must not be null");
        this.accountCatalog = Objects.requireNonNull(accountCatalog, "accountCatalog must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        this.sqlLoader = sqlLoader != null ? sqlLoader : new SqlQueryLoader();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.tsid = new TsidGenerator(11);
        this.synapseProps = synapseProps;
    }

    /**
     * Lists accounts matching the optional filters.
     *
     * <p>Platform Operators ({@code super-admin}) may list accounts across all tenants or filter
     * by a specific {@code tenantId}. Tenant Admins ({@code admin}) are strictly scoped to their
     * own tenant; attempting to filter by another tenant throws {@link CrossTenantAccessException}.</p>
     */
    public List<AccountAdminResponse> listAccounts(
            String tenantIdFilter,
            PrincipalKind kindFilter,
            AccountProfile profileFilter,
            Boolean activeFilter,
            String ownerAccountIdFilter) {

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String effectiveTenantFilter;
        if (!isSuperAdmin) {
            if (tenantIdFilter != null && !tenantIdFilter.isBlank() && !tenantIdFilter.trim().equals(callerTenant)) {
                throw new CrossTenantAccessException(callerId, "accounts", tenantIdFilter.trim());
            }
            effectiveTenantFilter = callerTenant;
        } else {
            effectiveTenantFilter = (tenantIdFilter != null && !tenantIdFilter.isBlank())
                    ? tenantIdFilter.trim()
                    : null;
        }

        String normOwnerFilter = (ownerAccountIdFilter != null && !ownerAccountIdFilter.isBlank())
                ? ownerAccountIdFilter.trim()
                : null;

        List<AccountRow> rows = jdbc.sql(sqlLoader.load("accounts/list-accounts"))
                .query(this::mapAccountRow)
                .list();

        return rows.stream()
                .filter(r -> effectiveTenantFilter == null || effectiveTenantFilter.equals(r.tenantId()))
                .filter(r -> kindFilter == null || r.kind() == kindFilter)
                .filter(r -> profileFilter == null || r.profile() == profileFilter)
                .filter(r -> activeFilter == null || r.active() == activeFilter)
                .filter(r -> normOwnerFilter == null || normOwnerFilter.equals(r.ownerAccountId()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves a single account with owner linkage, owned agent principals, active namespace grants,
     * and content-free resource aggregates.
     */
    public AccountAdminResponse getAccount(String accountId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        AccountRow row = getAccountRowOrThrow(accountId);
        if (!isSuperAdmin && !row.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, accountId, row.tenantId());
        }
        return toResponse(row);
    }

    /**
     * Lists agent and service principals created/owned by the given account.
     */
    public List<OwnedAgentSummaryDto> listOwnedAgents(String accountId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        AccountRow ownerRow = getAccountRowOrThrow(accountId);
        if (!isSuperAdmin && !ownerRow.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, accountId, ownerRow.tenantId());
        }
        return queryOwnedAgents(ownerRow.accountId());
    }

    /**
     * Creates a new human, agent, or service account, enforces tenant account quotas, provisions
     * its default namespace, optionally grants initial namespace access, and records an audit entry.
     */
    @Transactional
    @CacheEvict(value = SynapseCacheConstants.CACHE_USER_ACCOUNTS, allEntries = true)
    public AccountAdminResponse createAccount(CreateAccountRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Account creation request must not be null");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String targetTenantId;
        if (request.tenantId() != null && !request.tenantId().isBlank()) {
            String reqTenant = request.tenantId().trim();
            if (!isSuperAdmin && !reqTenant.equals(callerTenant)) {
                throw new CrossTenantAccessException(callerId, "accounts", reqTenant);
            }
            targetTenantId = reqTenant;
        } else {
            targetTenantId = callerTenant;
        }

        tenantService.assertTenantActive(targetTenantId);
        enforceTenantAccountQuota(targetTenantId);

        PrincipalKind kind = request.kind() != null ? request.kind() : PrincipalKind.HUMAN;
        AccountProfile profile = resolveDefaultProfile(request.profile(), kind);

        Set<String> roles = resolveRoles(request.roles(), kind);
        Set<String> scopes = resolveScopes(request.scopes(), kind);
        enforceNoPrivilegeEscalation(roles, scopes, isSuperAdmin);

        String ownerAccountId = null;
        if (request.ownerAccountId() != null && !request.ownerAccountId().isBlank()) {
            AccountRow ownerRow = getAccountRowOrThrow(request.ownerAccountId().trim());
            if (!ownerRow.tenantId().equals(targetTenantId)) {
                throw new CrossTenantAccessException(callerId, ownerRow.accountId(), ownerRow.tenantId());
            }
            ownerAccountId = ownerRow.accountId();
        } else if (kind == PrincipalKind.AGENT || kind == PrincipalKind.SERVICE) {
            Optional<AccountRow> callerRow = findAccountRow(callerId);
            if (callerRow.isPresent() && callerRow.get().tenantId().equals(targetTenantId)) {
                ownerAccountId = callerRow.get().accountId();
            }
        }

        validateAccountQuotas(request.maxNamespaces(), request.maxHotNamespaces());

        String rawPassword = (request.password() != null && !request.password().isBlank())
                ? request.password()
                : generateRandomPassword();

        boolean mustChangePassword = request.mustChangePassword() != null
                ? request.mustChangePassword()
                : (kind == PrincipalKind.HUMAN && (request.password() == null || request.password().isBlank()));

        boolean legalHold = Boolean.TRUE.equals(request.legalHold());
        String displayName = (request.displayName() != null && !request.displayName().isBlank())
                ? request.displayName().trim()
                : request.username().trim();

        String accountId = userAccountStore.createUser(
                request.username().trim(),
                rawPassword,
                request.email(),
                displayName,
                roles,
                scopes,
                mustChangePassword,
                targetTenantId
        );

        Instant now = Instant.now();
        jdbc.sql(sqlLoader.load("accounts/update-account-catalog-fields"))
                .param("kind", kind.name())
                .param("profile", profile.name())
                .param("defaultNamespaceId", accountId)
                .param("maxNamespaces", request.maxNamespaces())
                .param("maxHotNamespaces", request.maxHotNamespaces())
                .param("legalHold", legalHold)
                .param("ownerAccountId", ownerAccountId)
                .param("tenantId", targetTenantId)
                .param("now", Timestamp.from(now))
                .param("accountId", accountId)
                .update();

        // Provision default namespace and implicit OWNER grant in catalog
        accountCatalog.getOrCreateAccount(accountId, profile, kind, targetTenantId);

        // Apply optional initial namespace grants
        if (request.initialGrants() != null && !request.initialGrants().isEmpty()) {
            applyInitialNamespaceGrants(accountId, targetTenantId, ownerAccountId, callerId, request.initialGrants());
        }

        AccountRow created = getAccountRowOrThrow(accountId);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("username", created.username());
        auditDetails.put("kind", created.kind().name());
        auditDetails.put("profile", created.profile().name());
        auditDetails.put("tenantId", created.tenantId());
        if (created.ownerAccountId() != null) {
            auditDetails.put("ownerAccountId", created.ownerAccountId());
        }
        recordAudit(callerId, callerTenant, "CREATE_ACCOUNT", accountId, auditDetails);

        return toResponse(created);
    }

    /**
     * Updates an account's display name, email, active state (suspend/reactivate), profile,
     * roles, scopes, legal hold, owner linkage, or namespace quotas.
     */
    @Transactional
    @CacheEvict(value = SynapseCacheConstants.CACHE_USER_ACCOUNTS, allEntries = true)
    public AccountAdminResponse updateAccount(String accountId, UpdateAccountRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Account update request must not be null");
        }

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        AccountRow existing = getAccountRowOrThrow(accountId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, accountId, existing.tenantId());
        }

        Set<String> newRoles = request.roles() != null ? cleanTokens(request.roles()) : existing.roles();
        Set<String> newScopes = request.scopes() != null ? cleanTokens(request.scopes()) : existing.scopes();
        if (request.roles() != null || request.scopes() != null) {
            enforceNoPrivilegeEscalation(newRoles, newScopes, isSuperAdmin);
        }
        if (!isSuperAdmin && containsSuperAdmin(existing.roles(), existing.scopes())) {
            throw new AccessDeniedException("Tenant admin cannot modify a super-admin account");
        }

        String newDisplayName = request.displayName() != null ? request.displayName().trim() : existing.displayName();
        String newEmail = request.email() != null ? request.email().trim() : existing.email();
        boolean newActive = request.active() != null ? request.active() : existing.active();
        AccountProfile newProfile = request.profile() != null ? request.profile() : existing.profile();
        boolean newLegalHold = request.legalHold() != null ? request.legalHold() : existing.legalHold();

        String newOwnerAccountId = existing.ownerAccountId();
        if (request.ownerAccountId() != null) {
            if (request.ownerAccountId().isBlank()) {
                newOwnerAccountId = null;
            } else {
                String candidateOwnerId = request.ownerAccountId().trim();
                if (candidateOwnerId.equals(accountId)) {
                    throw new IllegalArgumentException("An account cannot be its own owner");
                }
                AccountRow ownerRow = getAccountRowOrThrow(candidateOwnerId);
                if (!ownerRow.tenantId().equals(existing.tenantId())) {
                    throw new CrossTenantAccessException(callerId, ownerRow.accountId(), ownerRow.tenantId());
                }
                newOwnerAccountId = ownerRow.accountId();
            }
        }

        Integer newMaxNamespaces = request.maxNamespaces() != null ? request.maxNamespaces() : existing.maxNamespaces();
        Integer newMaxHotNamespaces = request.maxHotNamespaces() != null ? request.maxHotNamespaces() : existing.maxHotNamespaces();
        validateAccountQuotas(newMaxNamespaces, newMaxHotNamespaces);

        Instant now = Instant.now();
        jdbc.sql(sqlLoader.load("accounts/update-account-admin"))
                .param("displayName", newDisplayName)
                .param("email", newEmail)
                .param("active", newActive)
                .param("roles", String.join(",", newRoles))
                .param("scopes", String.join(",", newScopes))
                .param("profile", newProfile.name())
                .param("legalHold", newLegalHold)
                .param("ownerAccountId", newOwnerAccountId)
                .param("maxNamespaces", newMaxNamespaces)
                .param("maxHotNamespaces", newMaxHotNamespaces)
                .param("now", Timestamp.from(now))
                .param("accountId", accountId)
                .update();

        // If account was suspended (active transitioned true -> false), revoke active refresh tokens
        if (existing.active() && !newActive) {
            jdbc.sql(sqlLoader.load("accounts/revoke-all-refresh-tokens"))
                    .param("accountId", accountId)
                    .update();
        }

        AccountRow updated = getAccountRowOrThrow(accountId);
        String auditAction = "UPDATE_ACCOUNT";
        if (existing.active() != newActive) {
            auditAction = newActive ? "REACTIVATE_ACCOUNT" : "SUSPEND_ACCOUNT";
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("previousActive", existing.active());
        auditDetails.put("active", updated.active());
        auditDetails.put("previousProfile", existing.profile().name());
        auditDetails.put("profile", updated.profile().name());
        auditDetails.put("legalHold", updated.legalHold());
        if (updated.ownerAccountId() != null) {
            auditDetails.put("ownerAccountId", updated.ownerAccountId());
        }

        recordAudit(callerId, callerTenant, auditAction, accountId, auditDetails);
        return toResponse(updated);
    }

    /**
     * Reassigns an account to another tenant ({@code super-admin} only).
     */
    @Transactional
    @CacheEvict(value = SynapseCacheConstants.CACHE_USER_ACCOUNTS, allEntries = true)
    public AccountAdminResponse reassignTenant(String accountId, ReassignTenantRequest request) {
        if (!SecurityUtils.isSuperAdmin()) {
            throw new AccessDeniedException("Only super-admin may reassign an account across tenants");
        }
        if (request == null || request.tenantId() == null || request.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }

        String callerId = SecurityUtils.getUserId();
        String callerTenant = resolveCallerTenantId(callerId);
        String targetTenantId = request.tenantId().trim();

        AccountRow existing = getAccountRowOrThrow(accountId);
        if (existing.legalHold()) {
            throw new SynapseConflictException("Account '" + accountId + "' is under legal hold — tenant reassignment blocked");
        }

        if (!existing.tenantId().equals(targetTenantId)) {
            if (!"default".equalsIgnoreCase(targetTenantId)) {
                tenantService.getTenant(targetTenantId);
                tenantService.assertTenantActive(targetTenantId);
            }
            enforceTenantAccountQuota(targetTenantId);

            Instant now = Instant.now();
            jdbc.sql(sqlLoader.load("accounts/reassign-account-tenant"))
                    .param("tenantId", targetTenantId)
                    .param("now", Timestamp.from(now))
                    .param("accountId", accountId)
                    .update();

            jdbc.sql("UPDATE credentials SET tenant_id = :tenantId WHERE user_id = :accountId")
                    .param("tenantId", targetTenantId)
                    .param("accountId", accountId)
                    .update();
        }

        AccountRow updated = getAccountRowOrThrow(accountId);
        recordAudit(callerId, callerTenant, "REASSIGN_ACCOUNT_TENANT", accountId, Map.of(
                "previousTenantId", existing.tenantId(),
                "tenantId", updated.tenantId()
        ));
        return toResponse(updated);
    }

    /**
     * Resets an account's credentials: optionally sets a new password, revokes active refresh tokens,
     * and optionally revokes inbound {@code AUTH} API keys in the universal credentials vault.
     */
    @Transactional
    @CacheEvict(value = SynapseCacheConstants.CACHE_USER_ACCOUNTS, allEntries = true)
    public ResetCredentialsResponse resetCredentials(String accountId, ResetCredentialsRequest request) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        AccountRow existing = getAccountRowOrThrow(accountId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, accountId, existing.tenantId());
        }
        if (!isSuperAdmin && containsSuperAdmin(existing.roles(), existing.scopes())) {
            throw new AccessDeniedException("Tenant admin cannot reset credentials of a super-admin account");
        }

        boolean passwordReset = false;
        if (request != null && request.newPassword() != null && !request.newPassword().isBlank()) {
            userAccountStore.forceResetPassword(accountId, request.newPassword());
            boolean mustChange = request.mustChangePassword() == null || request.mustChangePassword();
            jdbc.sql("UPDATE users SET must_change_password = :mustChange, updated_at = :now WHERE user_id = :accountId")
                    .param("mustChange", mustChange)
                    .param("now", Timestamp.from(Instant.now()))
                    .param("accountId", accountId)
                    .update();
            passwordReset = true;
        }

        boolean revokeRefresh = request == null
                || request.revokeRefreshTokens() == null
                || request.revokeRefreshTokens();
        if (revokeRefresh) {
            jdbc.sql(sqlLoader.load("accounts/revoke-all-refresh-tokens"))
                    .param("accountId", accountId)
                    .update();
        }

        int revokedApiKeyCount = 0;
        if (request != null && Boolean.TRUE.equals(request.revokeApiKeys())) {
            List<CredentialRecord> userCreds = credentialRepository.findAllByUserId(accountId);
            for (CredentialRecord cred : userCreds) {
                if (cred.category() == CredentialCategory.AUTH || (cred.keyHash() != null && !cred.keyHash().isBlank())) {
                    if (credentialRepository.deleteByName(cred.tenantId(), cred.name())) {
                        revokedApiKeyCount++;
                    }
                }
            }
        }

        Instant now = Instant.now();
        recordAudit(callerId, callerTenant, "RESET_ACCOUNT_CREDENTIALS", accountId, Map.of(
                "passwordReset", passwordReset,
                "refreshTokensRevoked", revokeRefresh,
                "revokedApiKeyCount", revokedApiKeyCount
        ));

        return new ResetCredentialsResponse(
                accountId,
                passwordReset,
                revokeRefresh,
                revokedApiKeyCount,
                now
        );
    }

    /**
     * Lists audit log entries for mutations targeting the specified account.
     */
    public List<TenantAuditResponse> listAuditLogs(String accountId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        AccountRow existing = getAccountRowOrThrow(accountId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, accountId, existing.tenantId());
        }

        return jdbc.sql(sqlLoader.load("tenants/list-audit-logs"))
                .param("targetType", "ACCOUNT")
                .param("targetId", accountId)
                .query(this::mapAuditRow)
                .list()
                .stream()
                .map(TenantAuditResponse::from)
                .toList();
    }

    // ── Internal helpers ──

    private String resolveCallerTenantId(String callerId) {
        String securityTenant = SecurityUtils.getTenantId();
        if (securityTenant != null && !securityTenant.isBlank() && !"default".equals(securityTenant)) {
            return securityTenant.trim();
        }
        if (callerId != null && !callerId.isBlank() && !"default".equals(callerId)) {
            Optional<UserRow> userRow = userAccountStore.findByUserId(callerId)
                    .or(() -> userAccountStore.findByUsername(callerId));
            if (userRow.isPresent() && userRow.get().tenantId() != null && !userRow.get().tenantId().isBlank()) {
                return userRow.get().tenantId().trim();
            }
        }
        return "default";
    }

    private void enforceTenantAccountQuota(String tenantId) {
        Optional<TenantRecord> tenantOpt = tenantService.findTenantRecord(tenantId);
        if (tenantOpt.isEmpty()) {
            return;
        }
        int maxAccounts = tenantOpt.get().maxAccounts();
        if (maxAccounts > 0) {
            Long currentCount = jdbc.sql(sqlLoader.load("tenants/count-accounts"))
                    .param("tenantId", tenantId)
                    .query(Long.class)
                    .single();
            long count = currentCount != null ? currentCount : 0L;
            if (count >= maxAccounts) {
                throw new TenantQuotaExceededException(
                        tenantId,
                        "Tenant maxAccounts quota exceeded: " + count + " >= " + maxAccounts);
            }
        }
    }

    private void applyInitialNamespaceGrants(
            String granteeAccountId,
            String targetTenantId,
            String ownerAccountId,
            String grantedByAccountId,
            List<InitialNamespaceGrantDto> initialGrants) {

        for (InitialNamespaceGrantDto grantDto : initialGrants) {
            if (grantDto == null || grantDto.namespaceId() == null || grantDto.namespaceId().isBlank()) {
                continue;
            }
            String selector = grantDto.namespaceId().trim();
            String resolvedNamespaceId = resolveTargetNamespaceInTenant(selector, ownerAccountId, targetTenantId, grantedByAccountId);

            GrantRole role = parseGrantRole(grantDto.role());
            if (role == GrantRole.OWNER) {
                throw new IllegalArgumentException("Initial grant role cannot be OWNER");
            }

            Grant grant = new Grant(
                    tsid.generate(),
                    GrantObjectType.NAMESPACE,
                    resolvedNamespaceId,
                    granteeAccountId,
                    PrincipalType.ACCOUNT,
                    role,
                    null,
                    grantedByAccountId,
                    Instant.now(),
                    grantDto.expiresAt(),
                    null
            );
            accountCatalog.addGrant(grant);
        }
    }

    private String resolveTargetNamespaceInTenant(
            String slugOrId,
            String ownerAccountId,
            String targetTenantId,
            String callerId) {

        // 1. Check by namespace_id directly
        Optional<NamespaceOwnerPair> byId = jdbc.sql("""
                SELECT n.namespace_id, COALESCE(NULLIF(u.tenant_id, ''), 'default') AS owner_tenant
                FROM namespaces n
                LEFT JOIN users u ON n.owner_account_id = u.user_id
                WHERE n.namespace_id = :namespaceId AND n.status != 'TOMBSTONED'
                """)
                .param("namespaceId", slugOrId)
                .query((rs, rowNum) -> new NamespaceOwnerPair(
                        rs.getString("namespace_id"),
                        rs.getString("owner_tenant")))
                .optional();

        if (byId.isPresent()) {
            if (!targetTenantId.equals(byId.get().ownerTenant())) {
                throw new CrossTenantAccessException(callerId, slugOrId, byId.get().ownerTenant());
            }
            return byId.get().namespaceId();
        }

        // 2. If ownerAccountId is known, check by ownerAccountId + slug
        if (ownerAccountId != null && !ownerAccountId.isBlank()) {
            Optional<String> byOwnerSlug = jdbc.sql("""
                    SELECT namespace_id
                    FROM namespaces
                    WHERE owner_account_id = :ownerAccountId
                      AND slug = :slug
                      AND status != 'TOMBSTONED'
                    """)
                    .param("ownerAccountId", ownerAccountId)
                    .param("slug", slugOrId)
                    .query(String.class)
                    .optional();
            if (byOwnerSlug.isPresent()) {
                return byOwnerSlug.get();
            }
        }

        throw new NamespaceNotFoundException(slugOrId);
    }

    private record NamespaceOwnerPair(String namespaceId, String ownerTenant) {}

    private AccountAdminResponse toResponse(AccountRow row) {
        String accountId = row.accountId();
        List<String> ownedNamespaceIds = jdbc.sql(sqlLoader.load("accounts/list-owned-namespace-ids"))
                .param("accountId", accountId)
                .query(String.class)
                .list();

        long namespaceCount = ownedNamespaceIds.size();
        long storageBytes = computeAccountStorageBytes(row.tenantId(), ownedNamespaceIds);
        long apiKeyCount = countAccountApiKeys(accountId);
        Instant lastActivityAt = computeLastActivityAt(accountId, row.lastLoginAt());
        List<OwnedAgentSummaryDto> ownedAgents = queryOwnedAgents(accountId);
        List<AccountNamespaceGrantDto> grants = queryPrincipalGrants(accountId);

        Instant now = Instant.now();
        boolean locked = row.lockedUntil() != null && row.lockedUntil().isAfter(now);

        return new AccountAdminResponse(
                row.accountId(),
                row.username(),
                row.displayName(),
                row.email(),
                row.tenantId(),
                row.kind(),
                row.profile(),
                row.roles(),
                row.scopes(),
                row.active(),
                locked,
                row.mustChangePassword(),
                row.legalHold(),
                row.defaultNamespaceId() != null ? row.defaultNamespaceId() : row.accountId(),
                row.ownerAccountId(),
                row.ownerUsername(),
                row.ownerDisplayName(),
                new AccountQuotaSummaryDto(row.maxNamespaces(), row.maxHotNamespaces()),
                namespaceCount,
                storageBytes,
                apiKeyCount,
                lastActivityAt,
                ownedAgents,
                grants,
                row.createdAt(),
                row.updatedAt()
        );
    }

    private List<OwnedAgentSummaryDto> queryOwnedAgents(String ownerAccountId) {
        return jdbc.sql(sqlLoader.load("accounts/list-owned-agents"))
                .param("ownerAccountId", ownerAccountId)
                .query((rs, rowNum) -> {
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    return new OwnedAgentSummaryDto(
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            parseKind(rs.getString("kind")),
                            parseProfile(rs.getString("profile")),
                            rs.getBoolean("active"),
                            createdTs != null ? createdTs.toInstant() : Instant.EPOCH
                    );
                })
                .list();
    }

    private List<AccountNamespaceGrantDto> queryPrincipalGrants(String accountId) {
        return jdbc.sql(sqlLoader.load("accounts/list-principal-grants"))
                .param("accountId", accountId)
                .query((rs, rowNum) -> {
                    String actionsCsv = rs.getString("actions");
                    List<String> actions = new ArrayList<>();
                    if (actionsCsv != null && !actionsCsv.isBlank()) {
                        for (String token : actionsCsv.split(",")) {
                            if (!token.isBlank()) {
                                actions.add(token.trim());
                            }
                        }
                    }
                    Timestamp grantedTs = rs.getTimestamp("granted_at");
                    Timestamp expiresTs = rs.getTimestamp("expires_at");
                    return new AccountNamespaceGrantDto(
                            rs.getString("grant_id"),
                            rs.getString("namespace_id"),
                            rs.getString("namespace_slug"),
                            rs.getString("namespace_display_name"),
                            rs.getString("namespace_owner_id"),
                            rs.getString("role"),
                            actions,
                            rs.getString("granted_by"),
                            grantedTs != null ? grantedTs.toInstant() : Instant.EPOCH,
                            expiresTs != null ? expiresTs.toInstant() : null
                    );
                })
                .list();
    }

    private long countAccountApiKeys(String accountId) {
        try {
            Long count = jdbc.sql(sqlLoader.load("accounts/count-api-keys"))
                    .param("accountId", accountId)
                    .query(Long.class)
                    .single();
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Instant computeLastActivityAt(String accountId, Instant lastLoginAt) {
        Instant latest = lastLoginAt;
        try {
            Optional<Instant> activityOpt = jdbc.sql(sqlLoader.load("accounts/max-last-activity"))
                    .param("accountId", accountId)
                    .query((rs, rowNum) -> {
                        Timestamp nsTs = rs.getTimestamp("max_ns_access");
                        Timestamp credTs = rs.getTimestamp("max_cred_use");
                        Instant a = nsTs != null ? nsTs.toInstant() : null;
                        Instant b = credTs != null ? credTs.toInstant() : null;
                        if (a != null && b != null) {
                            return a.isAfter(b) ? a : b;
                        }
                        return a != null ? a : b;
                    })
                    .optional();
            if (activityOpt.isPresent()) {
                Instant dbMax = activityOpt.get();
                if (latest == null || dbMax.isAfter(latest)) {
                    latest = dbMax;
                }
            }
        } catch (Exception ignored) {}
        return latest;
    }

    private long computeAccountStorageBytes(String tenantId, List<String> namespaceIds) {
        if (synapseProps == null || namespaceIds == null || namespaceIds.isEmpty()) {
            return 0L;
        }
        Path root;
        try {
            root = synapseProps.remembererRoot();
        } catch (Exception e) {
            return 0L;
        }
        if (root == null || !Files.isDirectory(root)) {
            return 0L;
        }

        long total = 0L;
        String effTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        for (String nsId : namespaceIds) {
            Path tenantedDir = root.resolve("tenants").resolve(effTenant).resolve("namespaces").resolve(nsId);
            if (Files.isDirectory(tenantedDir)) {
                total += directorySize(tenantedDir);
            }
            Path flatDir = root.resolve("namespaces").resolve(nsId);
            if (Files.isDirectory(flatDir)) {
                total += directorySize(flatDir);
            }
        }
        return total;
    }

    private static long directorySize(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private AccountRow getAccountRowOrThrow(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new SynapseNotFoundException("Account", "");
        }
        return findAccountRow(accountId)
                .orElseThrow(() -> new SynapseNotFoundException("Account", accountId));
    }

    private Optional<AccountRow> findAccountRow(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql(sqlLoader.load("accounts/find-account-by-id"))
                .param("accountId", accountId.trim())
                .query(this::mapAccountRow)
                .optional();
    }

    private void recordAudit(
            String actorId,
            String actorTenantId,
            String action,
            String targetAccountId,
            Map<String, Object> details) {
        String auditId = tsid.generate();
        Instant now = Instant.now();
        String effActor = (actorId != null && !actorId.isBlank()) ? actorId : "system";
        String detailsJson = "{}";
        try {
            detailsJson = objectMapper.writeValueAsString(details != null ? details : Map.of());
        } catch (Exception e) {
            log.warn("[AdminAccountService] Failed to serialize audit details for {}: {}", targetAccountId, e.getMessage());
        }

        jdbc.sql(sqlLoader.load("tenants/insert-audit-log"))
                .param("auditId", auditId)
                .param("timestamp", Timestamp.from(now))
                .param("actorId", effActor)
                .param("actorTenantId", actorTenantId)
                .param("action", action)
                .param("targetType", "ACCOUNT")
                .param("targetId", targetAccountId)
                .param("detailsJson", detailsJson)
                .update();

        log.info("[ADMIN_AUDIT] auditId={} actor={} action={} targetType=ACCOUNT targetId={} details={}",
                auditId, effActor, action, targetAccountId, detailsJson);
    }

    private static AccountProfile resolveDefaultProfile(AccountProfile requested, PrincipalKind kind) {
        if (requested != null) {
            return requested;
        }
        if (kind == PrincipalKind.AGENT || kind == PrincipalKind.SERVICE) {
            return AccountProfile.AGENT;
        }
        return AccountProfile.HUMAN_SOLO;
    }

    private static Set<String> resolveRoles(Set<String> requested, PrincipalKind kind) {
        if (requested != null && !requested.isEmpty()) {
            return cleanTokens(requested);
        }
        if (kind == PrincipalKind.AGENT || kind == PrincipalKind.SERVICE) {
            return Set.of(SpectorRoles.AGENT);
        }
        return Set.of(SpectorRoles.EDITOR);
    }

    private static Set<String> resolveScopes(Set<String> requested, PrincipalKind kind) {
        if (requested != null && !requested.isEmpty()) {
            return cleanTokens(requested);
        }
        if (kind == PrincipalKind.AGENT || kind == PrincipalKind.SERVICE) {
            return SpectorRoles.AGENT_SCOPES;
        }
        return SpectorRoles.EDITOR_SCOPES;
    }

    private static void enforceNoPrivilegeEscalation(Set<String> roles, Set<String> scopes, boolean isSuperAdmin) {
        if (!isSuperAdmin && containsSuperAdmin(roles, scopes)) {
            throw new AccessDeniedException("Only super-admin may grant super-admin roles or scopes");
        }
    }

    private static boolean containsSuperAdmin(Set<String> roles, Set<String> scopes) {
        if (roles != null) {
            for (String r : roles) {
                if (r != null) {
                    String norm = r.trim().toLowerCase().replace('_', '-');
                    if (norm.equals("super-admin") || norm.equals("role-super-admin")) {
                        return true;
                    }
                }
            }
        }
        if (scopes != null) {
            for (String s : scopes) {
                if (s != null) {
                    String norm = s.trim().toLowerCase().replace('_', '-');
                    if (norm.contains("super-admin")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void validateAccountQuotas(Integer maxNamespaces, Integer maxHotNamespaces) {
        if (maxNamespaces != null && (maxNamespaces < -1 || maxNamespaces == 0)) {
            throw new IllegalArgumentException("maxNamespaces must be -1 (unlimited) or a positive integer");
        }
        if (maxHotNamespaces != null && (maxHotNamespaces < -1 || maxHotNamespaces == 0)) {
            throw new IllegalArgumentException("maxHotNamespaces must be -1 (unlimited) or a positive integer");
        }
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static Set<String> cleanTokens(Set<String> tokens) {
        Set<String> result = new LinkedHashSet<>();
        if (tokens == null) {
            return result;
        }
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                result.add(token.trim());
            }
        }
        return result;
    }

    private static Set<String> fromCsv(String csv) {
        Set<String> result = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static PrincipalKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return PrincipalKind.HUMAN;
        }
        try {
            return PrincipalKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PrincipalKind.HUMAN;
        }
    }

    private static AccountProfile parseProfile(String raw) {
        if (raw == null || raw.isBlank()) {
            return AccountProfile.HUMAN_SOLO;
        }
        try {
            return AccountProfile.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AccountProfile.HUMAN_SOLO;
        }
    }

    private static GrantRole parseGrantRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return GrantRole.WRITER;
        }
        String norm = raw.trim().toUpperCase();
        return switch (norm) {
            case "AGENT", "EDITOR", "READ_WRITE" -> GrantRole.WRITER;
            case "VIEWER", "READ_ONLY" -> GrantRole.READER;
            default -> GrantRole.valueOf(norm);
        };
    }

    private AccountRow mapAccountRow(ResultSet rs, int rowNum) throws SQLException {
        Integer maxNs = (Integer) rs.getObject("max_namespaces");
        Integer maxHotNs = (Integer) rs.getObject("max_hot_namespaces");
        Timestamp lockedTs = rs.getTimestamp("locked_until");
        Timestamp loginTs = rs.getTimestamp("last_login_at");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");

        return new AccountRow(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("tenant_id"),
                parseKind(rs.getString("kind")),
                parseProfile(rs.getString("profile")),
                fromCsv(rs.getString("roles")),
                fromCsv(rs.getString("scopes")),
                rs.getBoolean("active"),
                rs.getBoolean("must_change_password"),
                rs.getBoolean("legal_hold"),
                rs.getString("default_namespace_id"),
                rs.getString("owner_account_id"),
                rs.getString("owner_username"),
                rs.getString("owner_display_name"),
                maxNs,
                maxHotNs,
                lockedTs != null ? lockedTs.toInstant() : null,
                loginTs != null ? loginTs.toInstant() : null,
                createdTs != null ? createdTs.toInstant() : Instant.EPOCH,
                updatedTs != null ? updatedTs.toInstant() : Instant.EPOCH
        );
    }

    private AdminAuditEntry mapAuditRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("timestamp");
        String json = rs.getString("details_json");
        Map<String, Object> details = Map.of();
        if (json != null && !json.isBlank()) {
            try {
                details = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }
        return new AdminAuditEntry(
                rs.getString("audit_id"),
                ts != null ? ts.toInstant() : Instant.EPOCH,
                rs.getString("actor_id"),
                rs.getString("actor_tenant_id"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                details
        );
    }

    private record AccountRow(
            String accountId,
            String username,
            String displayName,
            String email,
            String tenantId,
            PrincipalKind kind,
            AccountProfile profile,
            Set<String> roles,
            Set<String> scopes,
            boolean active,
            boolean mustChangePassword,
            boolean legalHold,
            String defaultNamespaceId,
            String ownerAccountId,
            String ownerUsername,
            String ownerDisplayName,
            Integer maxNamespaces,
            Integer maxHotNamespaces,
            Instant lockedUntil,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
