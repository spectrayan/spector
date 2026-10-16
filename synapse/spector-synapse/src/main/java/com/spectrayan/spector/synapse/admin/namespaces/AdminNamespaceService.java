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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.admin.audit.AuditLogService;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.AdminNamespaceGrantResponse;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.AdminNamespaceResponse;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.EraseNamespaceRequest;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.NamespaceLegalHoldRequest;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.NamespaceQuotasDto;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.TransferOwnershipRequest;
import com.spectrayan.spector.synapse.admin.namespaces.dto.AdminNamespaceDto.UpdateNamespaceQuotasRequest;
import com.spectrayan.spector.synapse.admin.tenant.AdminAuditEntry;
import com.spectrayan.spector.synapse.admin.tenant.TenantRecord;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantAuditResponse;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantAction;
import com.spectrayan.spector.synapse.catalog.GrantObjectType;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalType;
import com.spectrayan.spector.synapse.catalog.exception.AccountQuotaExceededException;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.catalog.exception.DefaultNamespaceProtectedException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceLegalHoldException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceTombstonedException;
import com.spectrayan.spector.synapse.catalog.exception.TenantQuotaExceededException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import com.spectrayan.spector.synapse.dr.ErasureAuditReport;
import com.spectrayan.spector.synapse.dr.TenantErasureService;
import com.spectrayan.spector.synapse.error.SynapseConflictException;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import com.spectrayan.spector.synapse.security.UserRow;

/**
 * Administrative service managing namespace lifecycle, quotas, archive/restore, legal hold,
 * ownership transfer, grant oversight, and compliance erasure at {@code /api/v1/admin/namespaces}
 * (ADR-0087, Issue #1057).
 *
 * <p>All operations are 100% content-free: no memory text, embeddings, tags, or payloads are ever
 * read or returned.</p>
 */
@Service
@DependsOn("flyway")
public class AdminNamespaceService {

    private static final Logger log = LoggerFactory.getLogger(AdminNamespaceService.class);

    private final JdbcClient jdbc;
    private final AccountCatalog accountCatalog;
    private final UserAccountStore userAccountStore;
    private final TenantService tenantService;
    private final SqlQueryLoader sqlLoader;
    private final ObjectMapper objectMapper;
    private final SynapseProperties synapseProps;
    private final ObjectProvider<MemoryRegistry> memoryRegistryProvider;
    private final ObjectProvider<TenantErasureService> erasureServiceProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final AuditLogService auditLogService;
    private final TsidGenerator tsid = new TsidGenerator(12);

    @Autowired
    public AdminNamespaceService(
            JdbcClient jdbc,
            AccountCatalog accountCatalog,
            UserAccountStore userAccountStore,
            TenantService tenantService,
            SqlQueryLoader sqlLoader,
            ObjectMapper objectMapper,
            @Autowired(required = false) SynapseProperties synapseProps,
            ObjectProvider<MemoryRegistry> memoryRegistryProvider,
            ObjectProvider<TenantErasureService> erasureServiceProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Autowired(required = false) AuditLogService auditLogService) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.accountCatalog = Objects.requireNonNull(accountCatalog, "accountCatalog must not be null");
        this.userAccountStore = Objects.requireNonNull(userAccountStore, "userAccountStore must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        this.sqlLoader = sqlLoader != null ? sqlLoader : new SqlQueryLoader();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.synapseProps = synapseProps;
        this.memoryRegistryProvider = memoryRegistryProvider;
        this.erasureServiceProvider = erasureServiceProvider;
        this.meterRegistryProvider = meterRegistryProvider;
        this.auditLogService = auditLogService != null
                ? auditLogService
                : new AuditLogService(this.jdbc, this.sqlLoader, this.objectMapper, this.userAccountStore, null, null);
    }

    public AdminNamespaceService(
            JdbcClient jdbc,
            AccountCatalog accountCatalog,
            UserAccountStore userAccountStore,
            TenantService tenantService,
            SqlQueryLoader sqlLoader,
            ObjectMapper objectMapper,
            SynapseProperties synapseProps,
            ObjectProvider<MemoryRegistry> memoryRegistryProvider,
            ObjectProvider<TenantErasureService> erasureServiceProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(jdbc, accountCatalog, userAccountStore, tenantService, sqlLoader, objectMapper, synapseProps,
                memoryRegistryProvider, erasureServiceProvider, meterRegistryProvider, null);
    }

    /**
     * Lists namespaces across a tenant ({@code admin}) or across all tenants ({@code super-admin}).
     */
    public List<AdminNamespaceResponse> listNamespaces(
            String tenantIdFilter,
            String ownerAccountIdFilter,
            NamespaceStatus statusFilter,
            NamespaceType typeFilter,
            Boolean legalHoldFilter,
            boolean includeTombstoned) {

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String effectiveTenantFilter;
        if (!isSuperAdmin) {
            if (tenantIdFilter != null && !tenantIdFilter.isBlank() && !tenantIdFilter.trim().equals(callerTenant)) {
                throw new CrossTenantAccessException(callerId, "namespaces", tenantIdFilter.trim());
            }
            effectiveTenantFilter = callerTenant;
        } else {
            effectiveTenantFilter = (tenantIdFilter != null && !tenantIdFilter.isBlank())
                    ? tenantIdFilter.trim()
                    : null;
        }

        String normOwner = (ownerAccountIdFilter != null && !ownerAccountIdFilter.isBlank())
                ? ownerAccountIdFilter.trim()
                : null;

        List<NamespaceAdminRow> rows = jdbc.sql(sqlLoader.load("namespaces/admin/list-namespaces"))
                .query(this::mapNamespaceRow)
                .list();

        return rows.stream()
                .filter(r -> effectiveTenantFilter == null || effectiveTenantFilter.equals(r.tenantId()))
                .filter(r -> normOwner == null || normOwner.equals(r.ownerAccountId()))
                .filter(r -> includeTombstoned || statusFilter == NamespaceStatus.TOMBSTONED || r.status() != NamespaceStatus.TOMBSTONED)
                .filter(r -> statusFilter == null || r.status() == statusFilter)
                .filter(r -> typeFilter == null || r.type() == typeFilter)
                .filter(r -> legalHoldFilter == null || r.legalHold() == legalHoldFilter)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves a single namespace's administrative metadata, quotas, and counters.
     */
    public AdminNamespaceResponse getNamespace(String namespaceId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow row = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !row.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, row.tenantId());
        }
        return toResponse(row);
    }

    /**
     * Updates per-namespace quotas ({@code maxMemories}, {@code maxStorageBytes}, {@code maxOpsPerMinute}),
     * enforcing tenant quota ceilings and recording an append-only audit entry.
     */
    @Transactional
    public AdminNamespaceResponse updateQuotas(String namespaceId, UpdateNamespaceQuotasRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("UpdateNamespaceQuotasRequest must not be null");
        }
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }
        if (existing.status() == NamespaceStatus.TOMBSTONED) {
            throw new NamespaceTombstonedException(namespaceId);
        }

        validateNamespaceQuotas(request.maxMemories(), request.maxStorageBytes(), request.maxOpsPerMinute());

        // Enforce tenant storage ceiling if configured on the namespace's tenant
        if (request.maxStorageBytes() != null && request.maxStorageBytes() > 0) {
            Optional<TenantRecord> tenantOpt = tenantService.findTenantRecord(existing.tenantId());
            if (tenantOpt.isPresent() && tenantOpt.get().maxStorageBytes() > 0
                    && request.maxStorageBytes() > tenantOpt.get().maxStorageBytes()) {
                recordQuotaBreach("tenant", existing.tenantId(), "storage");
                throw new TenantQuotaExceededException(
                        existing.tenantId(),
                        "Namespace maxStorageBytes (" + request.maxStorageBytes()
                                + ") exceeds tenant maxStorageBytes quota (" + tenantOpt.get().maxStorageBytes() + ")");
            }
        }

        Integer effMaxMemories = request.maxMemories() != null
                ? (request.maxMemories() == -1 ? null : request.maxMemories())
                : existing.maxMemories();
        Long effMaxStorage = request.maxStorageBytes() != null
                ? (request.maxStorageBytes() == -1L ? null : request.maxStorageBytes())
                : existing.maxStorageBytes();
        Integer effMaxOps = request.maxOpsPerMinute() != null
                ? (request.maxOpsPerMinute() == -1 ? null : request.maxOpsPerMinute())
                : existing.maxOpsPerMinute();

        jdbc.sql(sqlLoader.load("namespaces/admin/update-namespace-quotas"))
                .param("maxMemories", effMaxMemories)
                .param("maxStorageBytes", effMaxStorage)
                .param("maxOpsPerMinute", effMaxOps)
                .param("namespaceId", existing.namespaceId())
                .update();

        NamespaceAdminRow updated = getNamespaceRowOrThrow(namespaceId);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("maxMemories", effMaxMemories != null ? effMaxMemories : -1);
        auditDetails.put("maxStorageBytes", effMaxStorage != null ? effMaxStorage : -1L);
        auditDetails.put("maxOpsPerMinute", effMaxOps != null ? effMaxOps : -1);
        recordAudit(callerId, callerTenant, "UPDATE_NAMESPACE_QUOTAS", existing.namespaceId(), auditDetails);

        return toResponse(updated);
    }

    /**
     * Archives a namespace into read-only mode ({@code status = ARCHIVED}).
     * Refuses archiving when {@code legalHold = true}.
     */
    @Transactional
    public AdminNamespaceResponse archiveNamespace(String namespaceId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }
        if (existing.status() == NamespaceStatus.TOMBSTONED) {
            throw new NamespaceTombstonedException(namespaceId);
        }
        if (existing.legalHold()) {
            throw new NamespaceLegalHoldException(namespaceId);
        }
        if (isDefaultNamespace(existing)) {
            throw new DefaultNamespaceProtectedException(existing.slug());
        }

        jdbc.sql(sqlLoader.load("namespaces/admin/update-namespace-status"))
                .param("status", NamespaceStatus.ARCHIVED.name())
                .param("namespaceId", existing.namespaceId())
                .update();

        NamespaceAdminRow updated = getNamespaceRowOrThrow(namespaceId);
        recordAudit(callerId, callerTenant, "ARCHIVE_NAMESPACE", existing.namespaceId(), Map.of(
                "previousStatus", existing.status().name(),
                "newStatus", NamespaceStatus.ARCHIVED.name()
        ));
        return toResponse(updated);
    }

    /**
     * Restores an archived namespace back to {@code ACTIVE} status.
     */
    @Transactional
    public AdminNamespaceResponse restoreNamespace(String namespaceId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }
        if (existing.status() == NamespaceStatus.TOMBSTONED) {
            throw new NamespaceTombstonedException(namespaceId);
        }

        jdbc.sql(sqlLoader.load("namespaces/admin/update-namespace-status"))
                .param("status", NamespaceStatus.ACTIVE.name())
                .param("namespaceId", existing.namespaceId())
                .update();

        NamespaceAdminRow updated = getNamespaceRowOrThrow(namespaceId);
        recordAudit(callerId, callerTenant, "RESTORE_NAMESPACE", existing.namespaceId(), Map.of(
                "previousStatus", existing.status().name(),
                "newStatus", NamespaceStatus.ACTIVE.name()
        ));
        return toResponse(updated);
    }

    /**
     * Soft-deletes (tombstones) a namespace. Refuses if under legal hold or if it is the owner's default namespace.
     */
    @Transactional
    public AdminNamespaceResponse tombstoneNamespace(String namespaceId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }
        if (existing.legalHold()) {
            throw new NamespaceLegalHoldException(namespaceId);
        }
        if (isDefaultNamespace(existing)) {
            throw new DefaultNamespaceProtectedException(existing.slug());
        }

        jdbc.sql(sqlLoader.load("namespaces/admin/update-namespace-status"))
                .param("status", NamespaceStatus.TOMBSTONED.name())
                .param("namespaceId", existing.namespaceId())
                .update();

        evictFromHotCache(existing.namespaceId());

        NamespaceAdminRow updated = getNamespaceRowOrThrow(namespaceId);
        recordAudit(callerId, callerTenant, "TOMBSTONE_NAMESPACE", existing.namespaceId(), Map.of(
                "slug", existing.slug(),
                "ownerAccountId", existing.ownerAccountId()
        ));
        return toResponse(updated);
    }

    /**
     * Sets or releases legal hold on a namespace and records an append-only audit entry.
     */
    @Transactional
    public AdminNamespaceResponse setLegalHold(String namespaceId, NamespaceLegalHoldRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("NamespaceLegalHoldRequest must not be null");
        }
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }

        jdbc.sql(sqlLoader.load("namespaces/admin/update-namespace-legal-hold"))
                .param("legalHold", request.legalHold())
                .param("namespaceId", existing.namespaceId())
                .update();

        NamespaceAdminRow updated = getNamespaceRowOrThrow(namespaceId);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("previousLegalHold", existing.legalHold());
        auditDetails.put("legalHold", request.legalHold());
        if (request.reason() != null && !request.reason().isBlank()) {
            auditDetails.put("reason", request.reason().trim());
        }
        recordAudit(callerId, callerTenant, "SET_NAMESPACE_LEGAL_HOLD", existing.namespaceId(), auditDetails);

        return toResponse(updated);
    }

    /**
     * Transfers ownership of a non-default namespace to another account within the same tenant.
     */
    @Transactional
    public AdminNamespaceResponse transferOwnership(String namespaceId, TransferOwnershipRequest request) {
        if (request == null || request.newOwnerAccountId() == null || request.newOwnerAccountId().isBlank()) {
            throw new IllegalArgumentException("newOwnerAccountId is required");
        }
        String newOwnerId = request.newOwnerAccountId().trim();
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }
        if (existing.status() == NamespaceStatus.TOMBSTONED) {
            throw new NamespaceTombstonedException(namespaceId);
        }
        if (existing.legalHold()) {
            throw new NamespaceLegalHoldException(namespaceId);
        }
        if (isDefaultNamespace(existing)) {
            throw new DefaultNamespaceProtectedException(existing.slug());
        }

        if (existing.ownerAccountId().equals(newOwnerId)) {
            return toResponse(existing);
        }

        UserRow newOwnerRow = userAccountStore.findByUserId(newOwnerId)
                .orElseThrow(() -> new SynapseNotFoundException("Account", newOwnerId));
        String newOwnerTenant = (newOwnerRow.tenantId() != null && !newOwnerRow.tenantId().isBlank())
                ? newOwnerRow.tenantId().trim()
                : "default";

        if (!existing.tenantId().equals(newOwnerTenant)) {
            throw new CrossTenantAccessException(newOwnerId, namespaceId, existing.tenantId());
        }

        // Check new owner's maxNamespaces quota
        try {
            Account newOwnerAccount = accountCatalog.getAccount(newOwnerId);
            int maxAllowed = newOwnerAccount.quotas().maxNamespaces();
            if (maxAllowed > 0) {
                Integer currentOwned = jdbc.sql(
                        "SELECT COUNT(*) FROM namespaces WHERE owner_account_id = :ownerId AND status != 'TOMBSTONED'")
                        .param("ownerId", newOwnerId)
                        .query(Integer.class)
                        .single();
                int count = currentOwned != null ? currentOwned : 0;
                if (count >= maxAllowed) {
                    recordQuotaBreach("account", newOwnerTenant, "namespaces");
                    throw new AccountQuotaExceededException(newOwnerId, "maxNamespaces exceeded: " + count + " >= " + maxAllowed);
                }
            }
        } catch (NamespaceNotFoundException ignored) {}

        // Ensure new owner does not already have a namespace with the same slug
        Integer slugCollision = jdbc.sql(
                "SELECT COUNT(*) FROM namespaces WHERE owner_account_id = :ownerId AND slug = :slug")
                .param("ownerId", newOwnerId)
                .param("slug", existing.slug())
                .query(Integer.class)
                .single();
        if (slugCollision != null && slugCollision > 0) {
            throw new SynapseConflictException("slug", existing.slug());
        }

        String previousOwnerId = existing.ownerAccountId();

        // Update owner_account_id on namespaces
        jdbc.sql(sqlLoader.load("namespaces/admin/transfer-namespace-owner"))
                .param("newOwnerAccountId", newOwnerId)
                .param("namespaceId", existing.namespaceId())
                .update();

        // Revoke previous OWNER grants and insert new OWNER grant
        jdbc.sql(sqlLoader.load("namespaces/admin/revoke-owner-grants"))
                .param("namespaceId", existing.namespaceId())
                .update();

        accountCatalog.addGrant(new Grant(
                tsid.generate(),
                GrantObjectType.NAMESPACE,
                existing.namespaceId(),
                newOwnerId,
                PrincipalType.ACCOUNT,
                GrantRole.OWNER,
                Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN, GrantAction.INJECT),
                callerId != null ? callerId : newOwnerId,
                Instant.now(),
                null,
                null
        ));

        if (request.previousOwnerGrantRole() != null
                && !request.previousOwnerGrantRole().isBlank()
                && !"NONE".equalsIgnoreCase(request.previousOwnerGrantRole().trim())) {
            GrantRole retainedRole = GrantRole.valueOf(request.previousOwnerGrantRole().trim().toUpperCase());
            if (retainedRole == GrantRole.OWNER) {
                throw new IllegalArgumentException("previousOwnerGrantRole cannot be OWNER");
            }
            Set<GrantAction> retainedActions = switch (retainedRole) {
                case ADMIN -> Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN, GrantAction.INJECT);
                case WRITER -> Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.INJECT);
                case READER -> Set.of(GrantAction.READ);
                default -> Set.of(GrantAction.READ);
            };
            accountCatalog.addGrant(new Grant(
                    tsid.generate(),
                    GrantObjectType.NAMESPACE,
                    existing.namespaceId(),
                    previousOwnerId,
                    PrincipalType.ACCOUNT,
                    retainedRole,
                    retainedActions,
                    callerId != null ? callerId : newOwnerId,
                    Instant.now(),
                    null,
                    null
            ));
        }

        bumpMembershipVersion(previousOwnerId);
        bumpMembershipVersion(newOwnerId);
        evictFromHotCache(existing.namespaceId());

        NamespaceAdminRow updated = getNamespaceRowOrThrow(namespaceId);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("previousOwnerAccountId", previousOwnerId);
        auditDetails.put("newOwnerAccountId", newOwnerId);
        if (request.previousOwnerGrantRole() != null && !request.previousOwnerGrantRole().isBlank()) {
            auditDetails.put("previousOwnerGrantRole", request.previousOwnerGrantRole().trim().toUpperCase());
        }
        if (request.reason() != null && !request.reason().isBlank()) {
            auditDetails.put("reason", request.reason().trim());
        }
        recordAudit(callerId, callerTenant, "TRANSFER_NAMESPACE_OWNERSHIP", existing.namespaceId(), auditDetails);

        return toResponse(updated);
    }

    /**
     * Executes compliance erasure of a namespace backed by {@link TenantErasureService},
     * strictly refusing namespaces under {@code legalHold} and auditing the erasure to {@code admin_audit_log}.
     */
    @Transactional
    public ErasureAuditReport eraseNamespace(String namespaceId, EraseNamespaceRequest request) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }
        if (existing.legalHold()) {
            throw new NamespaceLegalHoldException(existing.namespaceId());
        }
        if (isDefaultNamespace(existing)) {
            throw new DefaultNamespaceProtectedException(existing.slug());
        }

        boolean propagateReplicas = request != null && Boolean.TRUE.equals(request.propagateReplicas());
        evictFromHotCache(existing.namespaceId());

        TenantErasureService erasureService = resolveErasureService();
        ErasureAuditReport report = erasureService.eraseNamespace(
                existing.ownerAccountId(),
                existing.namespaceId(),
                propagateReplicas,
                callerId
        );

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("ownerAccountId", existing.ownerAccountId());
        auditDetails.put("slug", existing.slug());
        auditDetails.put("localFilesDeleted", report.localFilesDeleted());
        auditDetails.put("localBytesDeleted", report.localBytesDeleted());
        auditDetails.put("objectStoreKeysDeleted", report.objectStoreKeysDeleted());
        auditDetails.put("replicaPropagationDispatched", report.replicaPropagationDispatched());
        auditDetails.put("replicaAcks", report.replicaAcks());
        auditDetails.put("erasureMechanism", report.erasureMechanism());
        if (request != null && request.reason() != null && !request.reason().isBlank()) {
            auditDetails.put("reason", request.reason().trim());
        }
        recordAudit(callerId, callerTenant, "ERASE_NAMESPACE", existing.namespaceId(), auditDetails);

        return report;
    }

    /**
     * Lists active grants on a namespace for administrative oversight without requiring personal ownership.
     */
    public List<AdminNamespaceGrantResponse> listGrants(String namespaceId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }

        return jdbc.sql(sqlLoader.load("namespaces/admin/list-namespace-grants"))
                .param("namespaceId", existing.namespaceId())
                .query(this::mapGrantRow)
                .list();
    }

    /**
     * Revokes a grant on a namespace via administrative oversight and records an append-only audit entry.
     */
    @Transactional
    public void revokeGrant(String namespaceId, String grantId) {
        if (grantId == null || grantId.isBlank()) {
            throw new IllegalArgumentException("grantId is required");
        }
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }

        AdminNamespaceGrantResponse grant = jdbc.sql(sqlLoader.load("namespaces/admin/find-namespace-grant"))
                .param("grantId", grantId.trim())
                .param("namespaceId", existing.namespaceId())
                .query(this::mapGrantRow)
                .optional()
                .orElseThrow(() -> new SynapseNotFoundException("Grant", grantId));

        if ("OWNER".equalsIgnoreCase(grant.role()) && existing.ownerAccountId().equals(grant.principalId())) {
            throw new SynapseConflictException("grant", "Cannot revoke the active OWNER grant; transfer ownership instead");
        }

        accountCatalog.revokeGrant(grant.grantId());

        recordAudit(callerId, callerTenant, "REVOKE_NAMESPACE_GRANT", existing.namespaceId(), Map.of(
                "grantId", grant.grantId(),
                "principalId", grant.principalId(),
                "role", grant.role() != null ? grant.role() : ""
        ));
    }

    /**
     * Lists administrative audit trail entries targeting the given namespace.
     */
    public List<TenantAuditResponse> listNamespaceAuditLogs(String namespaceId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceAdminRow existing = getNamespaceRowOrThrow(namespaceId);
        if (!isSuperAdmin && !existing.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, namespaceId, existing.tenantId());
        }

        return jdbc.sql(sqlLoader.load("tenants/list-audit-logs"))
                .param("targetType", "NAMESPACE")
                .param("targetId", existing.namespaceId())
                .query(this::mapAuditRow)
                .list()
                .stream()
                .map(TenantAuditResponse::from)
                .toList();
    }

    // ── Internal helpers ──

    private TenantErasureService resolveErasureService() {
        TenantErasureService svc = erasureServiceProvider != null ? erasureServiceProvider.getIfAvailable() : null;
        if (svc != null) {
            return svc;
        }
        Path root = synapseProps != null ? synapseProps.remembererRoot() : null;
        return new TenantErasureService(accountCatalog, null, null, root);
    }

    private void evictFromHotCache(String namespaceId) {
        if (memoryRegistryProvider == null) {
            return;
        }
        try {
            MemoryRegistry registry = memoryRegistryProvider.getIfAvailable();
            if (registry != null && registry.namespaceResolver() != null) {
                registry.namespaceResolver().evict(namespaceId);
            }
        } catch (Exception e) {
            log.debug("[AdminNamespaceService] Failed to evict namespace {} from hot cache: {}", namespaceId, e.getMessage());
        }
    }

    private void bumpMembershipVersion(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        try {
            jdbc.sql("UPDATE users SET membership_version = COALESCE(membership_version, 0) + 1 WHERE user_id = :accountId")
                    .param("accountId", accountId)
                    .update();
        } catch (Exception e) {
            log.debug("[AdminNamespaceService] Failed to bump membership_version for {}: {}", accountId, e.getMessage());
        }
    }

    private boolean isDefaultNamespace(NamespaceAdminRow row) {
        if ("default".equalsIgnoreCase(row.slug())
                || row.type() == NamespaceType.DEFAULT
                || row.namespaceId().equals(row.ownerAccountId())) {
            return true;
        }
        try {
            Account owner = accountCatalog.getAccount(row.ownerAccountId());
            return row.namespaceId().equals(owner.defaultNamespaceId());
        } catch (Exception e) {
            return false;
        }
    }

    private static void validateNamespaceQuotas(Integer maxMemories, Long maxStorageBytes, Integer maxOpsPerMinute) {
        if (maxMemories != null && (maxMemories < -1 || maxMemories == 0)) {
            throw new IllegalArgumentException("maxMemories must be -1 (unlimited) or a positive integer");
        }
        if (maxStorageBytes != null && (maxStorageBytes < -1L || maxStorageBytes == 0L)) {
            throw new IllegalArgumentException("maxStorageBytes must be -1 (unlimited) or a positive number of bytes");
        }
        if (maxOpsPerMinute != null && (maxOpsPerMinute < -1 || maxOpsPerMinute == 0)) {
            throw new IllegalArgumentException("maxOpsPerMinute must be -1 (unlimited) or a positive integer");
        }
    }

    private void recordQuotaBreach(String level, String tenantId, String resource) {
        if (meterRegistryProvider == null) {
            return;
        }
        try {
            MeterRegistry mr = meterRegistryProvider.getIfAvailable();
            if (mr != null) {
                mr.counter("spector.quota.breach.total",
                        "level", level,
                        "tenant_id", tenantId != null ? tenantId : "default",
                        "resource", resource).increment();
            }
        } catch (Exception e) {
            log.debug("[AdminNamespaceService] Failed to record quota breach metric: {}", e.getMessage());
        }
    }

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

    private NamespaceAdminRow getNamespaceRowOrThrow(String namespaceId) {
        if (namespaceId == null || namespaceId.isBlank()) {
            throw new NamespaceNotFoundException("");
        }
        return jdbc.sql(sqlLoader.load("namespaces/admin/find-namespace-by-id"))
                .param("namespaceId", namespaceId.trim())
                .query(this::mapNamespaceRow)
                .optional()
                .orElseThrow(() -> new NamespaceNotFoundException(namespaceId));
    }

    private AdminNamespaceResponse toResponse(NamespaceAdminRow row) {
        long memoryCount = computeNamespaceMemoryCount(row.namespaceId());
        long storageBytes = computeNamespaceStorageBytes(row.tenantId(), row.namespaceId());

        int maxMemories = row.maxMemories() != null ? row.maxMemories() : -1;
        long maxStorageBytes = row.maxStorageBytes() != null ? row.maxStorageBytes() : -1L;
        int maxOpsPerMinute = row.maxOpsPerMinute() != null ? row.maxOpsPerMinute() : -1;

        return new AdminNamespaceResponse(
                row.namespaceId(),
                row.slug(),
                row.displayName(),
                row.description(),
                row.type(),
                row.status(),
                row.legalHold(),
                row.tenantId(),
                row.ownerAccountId(),
                row.ownerUsername(),
                row.ownerDisplayName(),
                row.grantsCount(),
                memoryCount,
                storageBytes,
                new NamespaceQuotasDto(maxMemories, maxStorageBytes, maxOpsPerMinute),
                row.createdAt(),
                row.lastWriteAt(),
                row.lastAccessedAt()
        );
    }

    private long computeNamespaceMemoryCount(String namespaceId) {
        if (memoryRegistryProvider != null) {
            try {
                MemoryRegistry registry = memoryRegistryProvider.getIfAvailable();
                if (registry != null && registry.namespaceResolver() != null) {
                    SpectorMemory hot = registry.namespaceResolver().cachedEntries().get(namespaceId);
                    if (hot != null) {
                        return hot.totalMemories();
                    }
                }
            } catch (Exception ignored) {}
        }
        try {
            Optional<Long> snapshotCount = jdbc.sql(
                    "SELECT total_count FROM memory_analytics_snapshot WHERE namespace_id = :namespaceId ORDER BY snapshot_time DESC LIMIT 1")
                    .param("namespaceId", namespaceId)
                    .query(Long.class)
                    .optional();
            if (snapshotCount.isPresent() && snapshotCount.get() != null) {
                return snapshotCount.get();
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private long computeNamespaceStorageBytes(String tenantId, String namespaceId) {
        if (synapseProps == null || namespaceId == null || namespaceId.isBlank()) {
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
        Path tenantedDir = root.resolve("tenants").resolve(effTenant).resolve("namespaces").resolve(namespaceId);
        if (Files.isDirectory(tenantedDir)) {
            total += directorySize(tenantedDir);
        }
        Path flatDir = root.resolve("namespaces").resolve(namespaceId);
        if (Files.isDirectory(flatDir)) {
            total += directorySize(flatDir);
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

    private void recordAudit(
            String actorId,
            String actorTenantId,
            String action,
            String targetNamespaceId,
            Map<String, Object> details) {
        String resourceTenant = actorTenantId;
        try {
            Optional<String> nsTenant = jdbc.sql("""
                    SELECT COALESCE(NULLIF(u.tenant_id, ''), 'default')
                    FROM namespaces n
                    LEFT JOIN users u ON n.owner_account_id = u.user_id
                    WHERE n.namespace_id = :namespaceId
                    """)
                    .param("namespaceId", targetNamespaceId)
                    .query(String.class)
                    .optional();
            if (nsTenant.isPresent() && !nsTenant.get().isBlank()) {
                resourceTenant = nsTenant.get().trim();
            }
        } catch (Exception ignored) {}

        auditLogService.recordAdminAction(
                actorId,
                actorTenantId,
                resourceTenant,
                action,
                "NAMESPACE",
                targetNamespaceId,
                targetNamespaceId,
                null,
                details
        );
    }

    private NamespaceAdminRow mapNamespaceRow(ResultSet rs, int rowNum) throws SQLException {
        String typeStr = rs.getString("type");
        String statusStr = rs.getString("status");
        Integer maxMemories = (Integer) rs.getObject("max_memories");
        Long maxStorageBytes = (Long) rs.getObject("max_storage_bytes");
        Integer maxOpsPerMinute = (Integer) rs.getObject("max_ops_per_minute");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp writeTs = rs.getTimestamp("last_write_at");
        Timestamp accessTs = rs.getTimestamp("last_accessed_at");

        NamespaceType type = NamespaceType.PROJECT;
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                type = NamespaceType.valueOf(typeStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        NamespaceStatus status = NamespaceStatus.ACTIVE;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = NamespaceStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return new NamespaceAdminRow(
                rs.getString("namespace_id"),
                rs.getString("slug"),
                rs.getString("display_name"),
                rs.getString("description"),
                type,
                status,
                rs.getBoolean("legal_hold"),
                rs.getString("tenant_id"),
                rs.getString("owner_account_id"),
                rs.getString("owner_username"),
                rs.getString("owner_display_name"),
                rs.getLong("grants_count"),
                maxMemories,
                maxStorageBytes,
                maxOpsPerMinute,
                createdTs != null ? createdTs.toInstant() : Instant.EPOCH,
                writeTs != null ? writeTs.toInstant() : null,
                accessTs != null ? accessTs.toInstant() : null
        );
    }

    private AdminNamespaceGrantResponse mapGrantRow(ResultSet rs, int rowNum) throws SQLException {
        String actionsCsv = rs.getString("actions");
        List<String> actions = new ArrayList<>();
        if (actionsCsv != null && !actionsCsv.isBlank()) {
            for (String a : actionsCsv.split(",")) {
                if (!a.isBlank()) {
                    actions.add(a.trim());
                }
            }
        }
        Timestamp grantedTs = rs.getTimestamp("granted_at");
        Timestamp expiresTs = rs.getTimestamp("expires_at");

        return new AdminNamespaceGrantResponse(
                rs.getString("grant_id"),
                rs.getString("namespace_id"),
                rs.getString("principal_id"),
                rs.getString("principal_username"),
                rs.getString("principal_type"),
                rs.getString("role"),
                actions,
                rs.getString("granted_by"),
                grantedTs != null ? grantedTs.toInstant() : Instant.EPOCH,
                expiresTs != null ? expiresTs.toInstant() : null
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

    private record NamespaceAdminRow(
            String namespaceId,
            String slug,
            String displayName,
            String description,
            NamespaceType type,
            NamespaceStatus status,
            boolean legalHold,
            String tenantId,
            String ownerAccountId,
            String ownerUsername,
            String ownerDisplayName,
            long grantsCount,
            Integer maxMemories,
            Long maxStorageBytes,
            Integer maxOpsPerMinute,
            Instant createdAt,
            Instant lastWriteAt,
            Instant lastAccessedAt
    ) {}
}
