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
package com.spectrayan.spector.synapse.admin.breakglass;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.synapse.admin.audit.AuditLogService;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.ApproveBreakGlassRequest;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.BreakGlassResponse;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.CreateBreakGlassRequest;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.RejectBreakGlassRequest;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.RevokeBreakGlassRequest;
import com.spectrayan.spector.synapse.admin.tenant.TenantRecord;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantAction;
import com.spectrayan.spector.synapse.catalog.GrantObjectType;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.PrincipalType;
import com.spectrayan.spector.synapse.catalog.exception.BreakGlassAccessDeniedException;
import com.spectrayan.spector.synapse.catalog.exception.BreakGlassByokProhibitedException;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceTombstonedException;
import com.spectrayan.spector.synapse.error.SynapseConflictException;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import com.spectrayan.spector.synapse.security.UserRow;

/**
 * Service implementing emergency break-glass namespace access for non-BYOK tenants
 * (ADR-0087 §5.4, Issue #1060).
 *
 * <p>Enforces:
 * <ul>
 *   <li><strong>BYOK Hard Deny</strong>: Break-glass requests targeting a BYOK-enabled tenant are
 *       unconditionally rejected with HTTP 403 ({@code SPE-820-004} / {@code SPE-SEC-004} /
 *       {@code SPE-SEC-BYOK-NO-BREAK-GLASS}) even for {@code super-admin}.</li>
 *   <li><strong>Dual-Control Approval</strong>: Self-approval is prohibited ({@code SPE-820-005});
 *       a second distinct administrator must approve the request.</li>
 *   <li><strong>Time-Boxed Read-Only Scope</strong>: Issues a temporary {@link GrantRole#READER}
 *       grant expiring at {@code approvedAt + durationMinutes}. All writes, deletes, exports, and
 *       MCP invocations are blocked.</li>
 *   <li><strong>Per-Read Audit Logging &amp; Owner Notification</strong>: Every memory read during an
 *       active session appends a {@code BREAK_GLASS_READ} event with the accessed memory IDs to
 *       {@code audit_event}, and notifies the namespace owner on request, approval, revocation, and expiry.</li>
 * </ul>
 * </p>
 */
@Service
@DependsOn("flyway")
public class BreakGlassService {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassService.class);

    public static final String GRANT_PREFIX = "bg_";
    public static final int DEFAULT_DURATION_MINUTES = 60;
    public static final int HARD_MAX_DURATION_MINUTES = 1440;

    private final JdbcClient jdbc;
    private final AccountCatalog accountCatalog;
    private final TenantService tenantService;
    private final AuditLogService auditLogService;
    private final UserAccountStore userAccountStore;
    private final EventPublisher eventPublisher;
    private final TsidGenerator tsid = new TsidGenerator();
    private volatile Clock clock = Clock.systemUTC();

    private final List<OwnerNotification> deliveredNotifications = new CopyOnWriteArrayList<>();

    /**
     * Structured notification emitted to a namespace owner on break-glass lifecycle events.
     */
    public record OwnerNotification(
            String eventType,
            String breakGlassId,
            String tenantId,
            String namespaceId,
            String ownerAccountId,
            String requesterAccountId,
            String approverAccountId,
            String justification,
            Instant timestamp,
            Instant expiresAt
    ) {}

    @Autowired
    public BreakGlassService(
            JdbcClient jdbc,
            AccountCatalog accountCatalog,
            TenantService tenantService,
            AuditLogService auditLogService,
            @Autowired(required = false) UserAccountStore userAccountStore,
            @Autowired(required = false) EventPublisher eventPublisher) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.accountCatalog = Objects.requireNonNull(accountCatalog, "accountCatalog must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService must not be null");
        this.userAccountStore = userAccountStore;
        this.eventPublisher = eventPublisher;
        ensureSchema();
    }

    /**
     * Overrides the clock for deterministic expiry testing.
     */
    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Clock getClock() {
        return this.clock;
    }

    private void ensureSchema() {
        try {
            jdbc.sql("""
                    CREATE TABLE IF NOT EXISTS break_glass_requests (
                        id                   VARCHAR(64) PRIMARY KEY,
                        tenant_id            VARCHAR(64) NOT NULL DEFAULT 'default',
                        namespace_id         VARCHAR(128) NOT NULL,
                        owner_account_id     VARCHAR(128) NOT NULL,
                        requester_account_id VARCHAR(128) NOT NULL,
                        requester_role       VARCHAR(64) NOT NULL,
                        justification        VARCHAR(1024) NOT NULL,
                        ticket_ref           VARCHAR(128),
                        duration_minutes     INT NOT NULL DEFAULT 60,
                        status               VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
                        approver_account_id  VARCHAR(128),
                        approval_comment     VARCHAR(512),
                        grant_id             VARCHAR(64),
                        created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        approved_at          TIMESTAMP WITH TIME ZONE,
                        expires_at           TIMESTAMP WITH TIME ZONE,
                        revoked_at           TIMESTAMP WITH TIME ZONE,
                        revoked_by           VARCHAR(128),
                        revoke_reason        VARCHAR(512)
                    )
                    """).update();
        } catch (Exception e) {
            log.debug("[BreakGlassService] Schema initialization check skipped: {}", e.getMessage());
        }
    }

    /**
     * Submits a new emergency break-glass request (`POST /api/v1/admin/break-glass`).
     */
    @Transactional(noRollbackFor = {
            BreakGlassByokProhibitedException.class,
            BreakGlassAccessDeniedException.class,
            CrossTenantAccessException.class
    })
    public BreakGlassResponse createRequest(CreateBreakGlassRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Break-glass request body must not be null");
        }
        if (request.namespaceId() == null || request.namespaceId().isBlank()) {
            throw new IllegalArgumentException("namespaceId is required");
        }
        if (request.justification() == null || request.justification().isBlank()) {
            throw new IllegalArgumentException("justification is required for emergency break-glass access");
        }

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerRole = isSuperAdmin ? "super-admin" : "admin";
        String callerTenant = resolveCallerTenantId(callerId);

        String targetSelector = request.namespaceId().trim();
        String justification = request.justification().trim();
        String ticketRef = (request.ticketRef() != null && !request.ticketRef().isBlank())
                ? request.ticketRef().trim()
                : null;

        NamespaceMetadata nsMeta = resolveNamespaceMetadata(targetSelector);
        if (nsMeta.status() == NamespaceStatus.TOMBSTONED) {
            throw new NamespaceTombstonedException(nsMeta.namespaceId());
        }

        // Enforce tenant boundary for tenant admins
        if (!isSuperAdmin && !Objects.equals(callerTenant, nsMeta.tenantId())) {
            auditLogService.recordBreakGlass(
                    "BREAK_GLASS_REQUESTED",
                    nsMeta.tenantId(),
                    nsMeta.namespaceId(),
                    nsMeta.namespaceId(),
                    justification,
                    AuditLogService.OUTCOME_DENIED,
                    Map.of(
                            "requesterAccountId", callerId,
                            "requesterTenantId", callerTenant,
                            "targetTenantId", nsMeta.tenantId(),
                            "reason", "Cross-tenant break-glass request denied for tenant admin"
                    )
            );
            throw new CrossTenantAccessException(callerId, nsMeta.namespaceId(), nsMeta.tenantId());
        }

        // Critical invariant (ADR-0087 §5.4 / Issue #1060): Hard deny on BYOK-enabled tenants regardless of role
        Optional<TenantRecord> tenantOpt = tenantService.findTenantRecord(nsMeta.tenantId());
        boolean byokEnabled = tenantOpt.map(TenantRecord::byokEnabled).orElse(false);
        if (byokEnabled) {
            Map<String, Object> deniedDetails = new LinkedHashMap<>();
            deniedDetails.put("policyCode", BreakGlassByokProhibitedException.BYOK_POLICY_CODE);
            deniedDetails.put("errorCode", ErrorCode.BREAK_GLASS_BYOK_PROHIBITED.id());
            deniedDetails.put("alias", BreakGlassByokProhibitedException.ERROR_CODE_ALIAS);
            deniedDetails.put("byokEnabled", true);
            deniedDetails.put("requesterAccountId", callerId);
            deniedDetails.put("requesterRole", callerRole);
            if (ticketRef != null) {
                deniedDetails.put("ticketRef", ticketRef);
            }
            auditLogService.recordBreakGlass(
                    "BREAK_GLASS_REQUESTED",
                    nsMeta.tenantId(),
                    nsMeta.namespaceId(),
                    nsMeta.namespaceId(),
                    justification,
                    AuditLogService.OUTCOME_DENIED,
                    deniedDetails
            );
            throw new BreakGlassByokProhibitedException(nsMeta.tenantId(), nsMeta.namespaceId());
        }

        int policyMaxDuration = tenantOpt
                .map(TenantRecord::breakGlassMaxDurationMinutes)
                .filter(m -> m > 0)
                .map(m -> Math.min(m, HARD_MAX_DURATION_MINUTES))
                .orElse(DEFAULT_DURATION_MINUTES);

        int requestedDuration;
        if (request.durationMinutes() == null) {
            requestedDuration = Math.min(DEFAULT_DURATION_MINUTES, policyMaxDuration);
        } else {
            int raw = request.durationMinutes();
            if (raw < 1 || raw > HARD_MAX_DURATION_MINUTES) {
                throw new IllegalArgumentException(
                        "durationMinutes must be between 1 and " + HARD_MAX_DURATION_MINUTES);
            }
            requestedDuration = Math.min(raw, policyMaxDuration);
        }

        String id = "bg_" + tsid.generate();
        Instant now = Instant.now(clock);

        jdbc.sql("""
                INSERT INTO break_glass_requests (
                    id, tenant_id, namespace_id, owner_account_id, requester_account_id,
                    requester_role, justification, ticket_ref, duration_minutes, status,
                    approver_account_id, approval_comment, grant_id, created_at,
                    approved_at, expires_at, revoked_at, revoked_by, revoke_reason
                ) VALUES (
                    :id, :tenantId, :namespaceId, :ownerAccountId, :requesterAccountId,
                    :requesterRole, :justification, :ticketRef, :durationMinutes, :status,
                    NULL, NULL, NULL, :createdAt,
                    NULL, NULL, NULL, NULL, NULL
                )
                """)
                .param("id", id)
                .param("tenantId", nsMeta.tenantId())
                .param("namespaceId", nsMeta.namespaceId())
                .param("ownerAccountId", nsMeta.ownerAccountId())
                .param("requesterAccountId", callerId)
                .param("requesterRole", callerRole)
                .param("justification", justification)
                .param("ticketRef", ticketRef)
                .param("durationMinutes", requestedDuration)
                .param("status", BreakGlassStatus.PENDING_APPROVAL.name())
                .param("createdAt", Timestamp.from(now))
                .update();

        BreakGlassRecord created = getRecordOrThrow(id);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("breakGlassId", id);
        details.put("namespaceId", nsMeta.namespaceId());
        details.put("ownerAccountId", nsMeta.ownerAccountId());
        details.put("requesterAccountId", callerId);
        details.put("requesterRole", callerRole);
        details.put("durationMinutes", requestedDuration);
        details.put("status", created.status().name());
        if (ticketRef != null) {
            details.put("ticketRef", ticketRef);
        }

        auditLogService.recordBreakGlass(
                "BREAK_GLASS_REQUESTED",
                nsMeta.tenantId(),
                nsMeta.namespaceId(),
                id,
                justification,
                AuditLogService.OUTCOME_SUCCESS,
                details
        );

        notifyNamespaceOwner("break_glass.requested", created);

        return BreakGlassResponse.from(created);
    }

    /**
     * Approves a pending break-glass request (`POST /api/v1/admin/break-glass/{id}/approve`).
     * Enforces dual-control: the approver must be a distinct admin (`approver != requester`).
     */
    @Transactional(noRollbackFor = {
            BreakGlassByokProhibitedException.class,
            BreakGlassAccessDeniedException.class,
            CrossTenantAccessException.class
    })
    public BreakGlassResponse approveRequest(String id, ApproveBreakGlassRequest request) {
        BreakGlassRecord existing = getRecordOrThrow(id);

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        if (!isSuperAdmin && !Objects.equals(callerTenant, existing.tenantId())) {
            throw new CrossTenantAccessException(callerId, existing.namespaceId(), existing.tenantId());
        }

        // Re-verify BYOK policy at approval time
        boolean byokEnabled = tenantService.findTenantRecord(existing.tenantId())
                .map(TenantRecord::byokEnabled)
                .orElse(false);
        if (byokEnabled) {
            throw new BreakGlassByokProhibitedException(existing.tenantId(), existing.namespaceId());
        }

        if (existing.status() != BreakGlassStatus.PENDING_APPROVAL) {
            throw new SynapseConflictException(
                    "Break-glass request '" + id + "' is in status " + existing.status() + " and cannot be approved");
        }

        // Dual-control check: requester cannot self-approve
        if (Objects.equals(callerId, existing.requesterAccountId())) {
            auditLogService.recordBreakGlass(
                    "BREAK_GLASS_APPROVED",
                    existing.tenantId(),
                    existing.namespaceId(),
                    id,
                    existing.justification(),
                    AuditLogService.OUTCOME_DENIED,
                    Map.of(
                            "breakGlassId", id,
                            "requesterAccountId", existing.requesterAccountId(),
                            "approverAccountId", callerId,
                            "reason", "Self-approval forbidden"
                    )
            );
            throw new BreakGlassAccessDeniedException(
                    existing.namespaceId(),
                    callerId,
                    "Self-approval of break-glass request '" + id + "' by requester '" + callerId
                            + "' is forbidden; a second distinct administrator must approve"
            );
        }

        Instant approvedAt = Instant.now(clock);
        Instant expiresAt = approvedAt.plus(Duration.ofMinutes(existing.durationMinutes()));
        String grantId = GRANT_PREFIX + tsid.generate().substring(3);
        String comment = (request != null && request.comment() != null && !request.comment().isBlank())
                ? request.comment().trim()
                : null;

        // Ensure requester and approver accounts exist in catalog before issuing temporary READER grant
        accountCatalog.getOrCreateAccount(existing.requesterAccountId());
        accountCatalog.getOrCreateAccount(callerId);

        Grant readOnlyGrant = new Grant(
                grantId,
                GrantObjectType.NAMESPACE,
                existing.namespaceId(),
                existing.requesterAccountId(),
                PrincipalType.ACCOUNT,
                GrantRole.READER,
                Set.of(GrantAction.READ),
                callerId,
                approvedAt,
                expiresAt,
                null
        );
        accountCatalog.addGrant(readOnlyGrant);

        jdbc.sql("""
                UPDATE break_glass_requests
                SET status = :status,
                    approver_account_id = :approverAccountId,
                    approval_comment = :approvalComment,
                    grant_id = :grantId,
                    approved_at = :approvedAt,
                    expires_at = :expiresAt
                WHERE id = :id
                """)
                .param("status", BreakGlassStatus.ACTIVE.name())
                .param("approverAccountId", callerId)
                .param("approvalComment", comment)
                .param("grantId", grantId)
                .param("approvedAt", Timestamp.from(approvedAt))
                .param("expiresAt", Timestamp.from(expiresAt))
                .param("id", id)
                .update();

        BreakGlassRecord approved = getRecordOrThrow(id);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("breakGlassId", id);
        details.put("namespaceId", approved.namespaceId());
        details.put("ownerAccountId", approved.ownerAccountId());
        details.put("requesterAccountId", approved.requesterAccountId());
        details.put("approverAccountId", callerId);
        details.put("grantId", grantId);
        details.put("durationMinutes", approved.durationMinutes());
        details.put("approvedAt", approvedAt.toString());
        details.put("expiresAt", expiresAt.toString());
        if (comment != null) {
            details.put("approvalComment", comment);
        }

        auditLogService.recordBreakGlass(
                "BREAK_GLASS_APPROVED",
                approved.tenantId(),
                approved.namespaceId(),
                id,
                approved.justification(),
                AuditLogService.OUTCOME_SUCCESS,
                details
        );

        notifyNamespaceOwner("break_glass.approved", approved);

        return BreakGlassResponse.from(approved);
    }

    /**
     * Rejects a pending break-glass request (`POST /api/v1/admin/break-glass/{id}/reject`).
     */
    @Transactional
    public BreakGlassResponse rejectRequest(String id, RejectBreakGlassRequest request) {
        BreakGlassRecord existing = getRecordOrThrow(id);

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        if (!isSuperAdmin && !Objects.equals(callerTenant, existing.tenantId())) {
            throw new CrossTenantAccessException(callerId, existing.namespaceId(), existing.tenantId());
        }

        if (existing.status() != BreakGlassStatus.PENDING_APPROVAL) {
            throw new SynapseConflictException(
                    "Break-glass request '" + id + "' is in status " + existing.status() + " and cannot be rejected");
        }

        Instant now = Instant.now(clock);
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Rejected by administrator";

        jdbc.sql("""
                UPDATE break_glass_requests
                SET status = :status,
                    revoked_at = :revokedAt,
                    revoked_by = :revokedBy,
                    revoke_reason = :revokeReason
                WHERE id = :id
                """)
                .param("status", BreakGlassStatus.REJECTED.name())
                .param("revokedAt", Timestamp.from(now))
                .param("revokedBy", callerId)
                .param("revokeReason", reason)
                .param("id", id)
                .update();

        BreakGlassRecord rejected = getRecordOrThrow(id);

        auditLogService.recordBreakGlass(
                "BREAK_GLASS_REJECTED",
                rejected.tenantId(),
                rejected.namespaceId(),
                id,
                reason,
                AuditLogService.OUTCOME_SUCCESS,
                Map.of(
                        "breakGlassId", id,
                        "namespaceId", rejected.namespaceId(),
                        "requesterAccountId", rejected.requesterAccountId(),
                        "rejectedBy", callerId,
                        "reason", reason
                )
        );

        notifyNamespaceOwner("break_glass.rejected", rejected);

        return BreakGlassResponse.from(rejected);
    }

    /**
     * Manually revokes a break-glass session (`POST /api/v1/admin/break-glass/{id}/revoke`).
     * Callable by a tenant admin, super-admin, the requesting admin, or the namespace owner.
     */
    @Transactional
    public BreakGlassResponse revokeRequest(String id, RevokeBreakGlassRequest request) {
        BreakGlassRecord existing = getRecordOrThrow(id);

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        boolean isAdmin = SecurityUtils.isAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        boolean isSameTenantAdmin = isAdmin && Objects.equals(callerTenant, existing.tenantId());
        boolean isRequester = Objects.equals(callerId, existing.requesterAccountId());
        boolean isNamespaceOwner = Objects.equals(callerId, existing.ownerAccountId());

        if (!isSuperAdmin && !isSameTenantAdmin && !isRequester && !isNamespaceOwner) {
            throw new BreakGlassAccessDeniedException(
                    existing.namespaceId(),
                    callerId,
                    "Caller '" + callerId + "' is not authorized to revoke break-glass session '" + id + "'"
            );
        }

        if (existing.status() == BreakGlassStatus.REVOKED || existing.status() == BreakGlassStatus.EXPIRED
                || existing.status() == BreakGlassStatus.REJECTED) {
            return BreakGlassResponse.from(existing);
        }

        if (existing.grantId() != null && !existing.grantId().isBlank()) {
            try {
                accountCatalog.revokeGrant(existing.grantId());
            } catch (Exception e) {
                log.warn("[BreakGlassService] Failed to revoke grant {} for break-glass {}: {}",
                        existing.grantId(), id, e.getMessage());
            }
        }

        Instant now = Instant.now(clock);
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Manually revoked by " + callerId;

        jdbc.sql("""
                UPDATE break_glass_requests
                SET status = :status,
                    revoked_at = :revokedAt,
                    revoked_by = :revokedBy,
                    revoke_reason = :revokeReason
                WHERE id = :id
                """)
                .param("status", BreakGlassStatus.REVOKED.name())
                .param("revokedAt", Timestamp.from(now))
                .param("revokedBy", callerId)
                .param("revokeReason", reason)
                .param("id", id)
                .update();

        BreakGlassRecord revoked = getRecordOrThrow(id);

        auditLogService.recordBreakGlass(
                "BREAK_GLASS_REVOKED",
                revoked.tenantId(),
                revoked.namespaceId(),
                id,
                reason,
                AuditLogService.OUTCOME_SUCCESS,
                Map.of(
                        "breakGlassId", id,
                        "namespaceId", revoked.namespaceId(),
                        "ownerAccountId", revoked.ownerAccountId(),
                        "requesterAccountId", revoked.requesterAccountId(),
                        "revokedBy", callerId,
                        "reason", reason
                )
        );

        notifyNamespaceOwner("break_glass.revoked", revoked);

        return BreakGlassResponse.from(revoked);
    }

    /**
     * Retrieves a single break-glass request by ID, auto-expiring it first if overdue.
     */
    @Transactional
    public BreakGlassResponse getRequest(String id) {
        expireOverdueSessions();
        BreakGlassRecord record = getRecordOrThrow(id);

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        boolean isAdmin = SecurityUtils.isAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        if (!isSuperAdmin) {
            boolean isSameTenantAdmin = isAdmin && Objects.equals(callerTenant, record.tenantId());
            boolean isOwnerOrRequester = Objects.equals(callerId, record.ownerAccountId())
                    || Objects.equals(callerId, record.requesterAccountId());
            if (!isSameTenantAdmin && !isOwnerOrRequester) {
                throw new CrossTenantAccessException(callerId, record.namespaceId(), record.tenantId());
            }
        }

        return BreakGlassResponse.from(record);
    }

    /**
     * Lists break-glass sessions (`GET /api/v1/admin/break-glass`), auto-expiring overdue sessions first.
     */
    @Transactional
    public List<BreakGlassResponse> listRequests(
            String tenantIdFilter,
            String namespaceIdFilter,
            BreakGlassStatus statusFilter) {
        expireOverdueSessions();

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String effectiveTenant;
        if (!isSuperAdmin) {
            if (tenantIdFilter != null && !tenantIdFilter.isBlank()
                    && !tenantIdFilter.trim().equals(callerTenant)) {
                throw new CrossTenantAccessException(callerId, "break-glass", tenantIdFilter.trim());
            }
            effectiveTenant = callerTenant;
        } else {
            effectiveTenant = (tenantIdFilter != null && !tenantIdFilter.isBlank())
                    ? tenantIdFilter.trim()
                    : null;
        }

        String normNamespace = (namespaceIdFilter != null && !namespaceIdFilter.isBlank())
                ? namespaceIdFilter.trim()
                : null;

        List<BreakGlassRecord> records = jdbc.sql("""
                SELECT * FROM break_glass_requests
                ORDER BY created_at DESC
                """)
                .query(this::mapRow)
                .list();

        return records.stream()
                .filter(r -> effectiveTenant == null || Objects.equals(effectiveTenant, r.tenantId()))
                .filter(r -> normNamespace == null || Objects.equals(normNamespace, r.namespaceId()))
                .filter(r -> statusFilter == null || r.status() == statusFilter)
                .map(BreakGlassResponse::from)
                .toList();
    }

    /**
     * Evaluates break-glass session state when {@code accountId} binds to {@code targetNamespaceOrSlug}.
     *
     * <p>If an {@link BreakGlassStatus#ACTIVE} session for {@code (accountId, namespaceId)} has reached
     * {@code expires_at}, it is immediately transitioned to {@link BreakGlassStatus#EXPIRED}, its
     * underlying grant is revoked, {@code BREAK_GLASS_EXPIRED} is written to {@code audit_event},
     * and the namespace owner is notified.</p>
     *
     * @return the active non-expired {@link BreakGlassRecord} if the caller is accessing the namespace
     *         via break-glass, or {@link Optional#empty()} if no active break-glass session applies
     */
    @Transactional
    public Optional<BreakGlassRecord> evaluateSessionOnBind(String accountId, String targetNamespaceOrSlug) {
        if (accountId == null || accountId.isBlank()
                || targetNamespaceOrSlug == null || targetNamespaceOrSlug.isBlank()) {
            return Optional.empty();
        }

        expireOverdueSessions();

        List<BreakGlassRecord> matching = jdbc.sql("""
                SELECT * FROM break_glass_requests
                WHERE requester_account_id = :accountId
                  AND namespace_id = :namespaceId
                ORDER BY created_at DESC
                """)
                .param("accountId", accountId)
                .param("namespaceId", targetNamespaceOrSlug.trim())
                .query(this::mapRow)
                .list();

        if (matching.isEmpty()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        for (BreakGlassRecord record : matching) {
            if (record.status() == BreakGlassStatus.ACTIVE) {
                if (record.isOverdue(now)) {
                    expireSession(record, now);
                } else {
                    return Optional.of(record);
                }
            }
        }

        // If the requester had a break-glass session that is now EXPIRED or REVOKED (and is not the owner),
        // check whether they have any non-break-glass grant; if not, deny with BreakGlassAccessDeniedException (403).
        BreakGlassRecord latest = matching.get(0);
        if (!Objects.equals(accountId, latest.ownerAccountId())
                && (latest.status() == BreakGlassStatus.EXPIRED
                    || latest.status() == BreakGlassStatus.REVOKED
                    || latest.status() == BreakGlassStatus.PENDING_APPROVAL
                    || latest.status() == BreakGlassStatus.REJECTED)) {
            Optional<Grant> otherGrant = Optional.empty();
            try {
                otherGrant = accountCatalog.authorize(accountId, latest.namespaceId(), GrantRole.READER);
            } catch (Exception ignored) {
                otherGrant = Optional.empty();
            }
            boolean hasValidNonBreakGlassGrant = otherGrant.isPresent()
                    && !otherGrant.get().isExpired()
                    && (otherGrant.get().grantId() == null || !otherGrant.get().grantId().startsWith(GRANT_PREFIX));
            if (!hasValidNonBreakGlassGrant) {
                throw new BreakGlassAccessDeniedException(
                        latest.namespaceId(),
                        accountId,
                        "Break-glass session '" + latest.id() + "' is " + latest.status()
                                + " — access to namespace '" + latest.namespaceId() + "' is denied"
                );
            }
        }

        return Optional.empty();
    }

    /**
     * Sweeps all {@link BreakGlassStatus#ACTIVE} sessions whose {@code expires_at <= now}, revoking
     * their temporary grants, transitioning them to {@link BreakGlassStatus#EXPIRED}, recording
     * {@code BREAK_GLASS_EXPIRED} in {@code audit_event}, and notifying namespace owners.
     */
    @Transactional
    public int expireOverdueSessions() {
        Instant now = Instant.now(clock);
        List<BreakGlassRecord> activeSessions = jdbc.sql("""
                SELECT * FROM break_glass_requests
                WHERE status = 'ACTIVE'
                """)
                .query(this::mapRow)
                .list();

        int expiredCount = 0;
        for (BreakGlassRecord record : activeSessions) {
            if (record.isOverdue(now)) {
                expireSession(record, now);
                expiredCount++;
            }
        }
        return expiredCount;
    }

    private void expireSession(BreakGlassRecord record, Instant now) {
        if (record.grantId() != null && !record.grantId().isBlank()) {
            try {
                accountCatalog.revokeGrant(record.grantId());
            } catch (Exception e) {
                log.warn("[BreakGlassService] Failed to revoke grant {} on expiry of {}: {}",
                        record.grantId(), record.id(), e.getMessage());
            }
        }

        jdbc.sql("""
                UPDATE break_glass_requests
                SET status = :status,
                    revoked_at = :revokedAt,
                    revoked_by = 'SYSTEM_AUTO_EXPIRY',
                    revoke_reason = 'Break-glass session expired automatically'
                WHERE id = :id AND status = 'ACTIVE'
                """)
                .param("status", BreakGlassStatus.EXPIRED.name())
                .param("revokedAt", Timestamp.from(now))
                .param("id", record.id())
                .update();

        BreakGlassRecord expired = findById(record.id()).orElse(record);

        auditLogService.recordBreakGlass(
                "BREAK_GLASS_EXPIRED",
                record.tenantId(),
                record.namespaceId(),
                record.id(),
                "Break-glass session auto-expired at " + record.expiresAt(),
                AuditLogService.OUTCOME_SUCCESS,
                Map.of(
                        "breakGlassId", record.id(),
                        "namespaceId", record.namespaceId(),
                        "ownerAccountId", record.ownerAccountId(),
                        "requesterAccountId", record.requesterAccountId(),
                        "expiresAt", record.expiresAt() != null ? record.expiresAt().toString() : now.toString()
                )
        );

        notifyNamespaceOwner("break_glass.expired", expired);
    }

    /**
     * Records an append-only {@code BREAK_GLASS_READ} audit entry for a memory read operation
     * executed under an active break-glass session, including the accessed memory IDs (Issue #1060).
     */
    public void recordReadAccess(
            String breakGlassId,
            String namespaceId,
            String operation,
            List<String> memoryIds) {
        if (breakGlassId == null || breakGlassId.isBlank()) {
            return;
        }
        Optional<BreakGlassRecord> recordOpt = findById(breakGlassId);
        String tenantId = recordOpt.map(BreakGlassRecord::tenantId).orElseGet(SecurityUtils::getTenantId);
        String effNamespaceId = recordOpt.map(BreakGlassRecord::namespaceId).orElse(namespaceId);
        String justification = recordOpt.map(BreakGlassRecord::justification).orElse("Break-glass read access");
        List<String> safeMemoryIds = memoryIds != null ? List.copyOf(memoryIds) : List.of();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("breakGlassId", breakGlassId);
        details.put("namespaceId", effNamespaceId);
        details.put("operation", operation != null ? operation : "MEMORY_READ");
        details.put("memoryIds", safeMemoryIds);
        details.put("memoryCount", safeMemoryIds.size());
        recordOpt.ifPresent(r -> {
            details.put("ownerAccountId", r.ownerAccountId());
            details.put("requesterAccountId", r.requesterAccountId());
            if (r.approverAccountId() != null) {
                details.put("approverAccountId", r.approverAccountId());
            }
            if (r.ticketRef() != null) {
                details.put("ticketRef", r.ticketRef());
            }
        });

        auditLogService.recordBreakGlass(
                "BREAK_GLASS_READ",
                tenantId,
                effNamespaceId,
                breakGlassId,
                justification,
                AuditLogService.OUTCOME_SUCCESS,
                details
        );
    }

    /**
     * Records a denied write/export/MCP attempt during an active break-glass session.
     */
    public void recordDeniedWriteAttempt(String breakGlassId, String namespaceId, String operation) {
        Optional<BreakGlassRecord> recordOpt = findById(breakGlassId);
        String tenantId = recordOpt.map(BreakGlassRecord::tenantId).orElseGet(SecurityUtils::getTenantId);
        String effNamespaceId = recordOpt.map(BreakGlassRecord::namespaceId).orElse(namespaceId);

        auditLogService.recordBreakGlass(
                "BREAK_GLASS_WRITE_DENIED",
                tenantId,
                effNamespaceId,
                breakGlassId != null ? breakGlassId : effNamespaceId,
                "Write/mutation or export blocked under read-only break-glass session",
                AuditLogService.OUTCOME_DENIED,
                Map.of(
                        "breakGlassId", breakGlassId != null ? breakGlassId : "unknown",
                        "namespaceId", effNamespaceId != null ? effNamespaceId : "unknown",
                        "operation", operation != null ? operation : "WRITE"
                )
        );
    }

    /**
     * Alias for {@link #recordReadAccess(String, String, String, List)} used by {@code MemoryService}
     * and {@code MemoryController}.
     */
    public void recordBreakGlassRead(
            String breakGlassId,
            String namespaceId,
            String operation,
            List<String> memoryIds) {
        recordReadAccess(breakGlassId, namespaceId, operation, memoryIds);
    }

    /**
     * Returns notifications emitted to the specified namespace owner (for in-app inspection &amp; verification).
     */
    public List<OwnerNotification> getNotificationsForOwner(String ownerAccountId) {
        if (ownerAccountId == null || ownerAccountId.isBlank()) {
            return List.of();
        }
        return deliveredNotifications.stream()
                .filter(n -> ownerAccountId.trim().equals(n.ownerAccountId()))
                .toList();
    }

    public List<OwnerNotification> getDeliveredNotifications() {
        return List.copyOf(deliveredNotifications);
    }

    public void clearDeliveredNotifications() {
        deliveredNotifications.clear();
    }

    private void notifyNamespaceOwner(String eventType, BreakGlassRecord record) {
        Instant now = Instant.now(clock);
        OwnerNotification notification = new OwnerNotification(
                eventType,
                record.id(),
                record.tenantId(),
                record.namespaceId(),
                record.ownerAccountId(),
                record.requesterAccountId(),
                record.approverAccountId(),
                record.justification(),
                now,
                record.expiresAt()
        );
        deliveredNotifications.add(notification);

        if (eventPublisher != null) {
            try {
                Map<String, Object> eventPayload = new LinkedHashMap<>();
                eventPayload.put("eventType", eventType);
                eventPayload.put("breakGlassId", record.id());
                eventPayload.put("tenantId", record.tenantId());
                eventPayload.put("namespaceId", record.namespaceId());
                eventPayload.put("ownerAccountId", record.ownerAccountId());
                eventPayload.put("requesterAccountId", record.requesterAccountId());
                eventPayload.put("status", record.status().name());
                eventPayload.put("justification", record.justification());
                eventPayload.put("timestamp", now.toEpochMilli());
                if (record.approverAccountId() != null) {
                    eventPayload.put("approverAccountId", record.approverAccountId());
                }
                if (record.expiresAt() != null) {
                    eventPayload.put("expiresAt", record.expiresAt().toString());
                }
                eventPublisher.broadcast(eventType, eventPayload);
            } catch (Exception e) {
                log.debug("[BreakGlassService] EventPublisher broadcast skipped for {}: {}", eventType, e.getMessage());
            }
        }
        log.info("[BreakGlassService] Notified namespace owner '{}' of {} for namespace '{}' (breakGlassId={})",
                record.ownerAccountId(), eventType, record.namespaceId(), record.id());
    }

    public Optional<BreakGlassRecord> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT * FROM break_glass_requests WHERE id = :id")
                .param("id", id.trim())
                .query(this::mapRow)
                .optional();
    }

    private BreakGlassRecord getRecordOrThrow(String id) {
        return findById(id)
                .orElseThrow(() -> new SynapseNotFoundException("BreakGlassRequest", id));
    }

    private NamespaceMetadata resolveNamespaceMetadata(String namespaceSelector) {
        Optional<NamespaceMetadata> byId = jdbc.sql("""
                SELECT n.namespace_id, n.owner_account_id, n.status,
                       COALESCE(NULLIF(u.tenant_id, ''), 'default') AS tenant_id
                FROM namespaces n
                LEFT JOIN users u ON n.owner_account_id = u.user_id
                WHERE n.namespace_id = :selector
                """)
                .param("selector", namespaceSelector)
                .query((rs, rowNum) -> new NamespaceMetadata(
                        rs.getString("namespace_id"),
                        rs.getString("owner_account_id"),
                        NamespaceStatus.valueOf(rs.getString("status")),
                        rs.getString("tenant_id")
                ))
                .optional();

        if (byId.isPresent()) {
            return byId.get();
        }

        return jdbc.sql("""
                SELECT n.namespace_id, n.owner_account_id, n.status,
                       COALESCE(NULLIF(u.tenant_id, ''), 'default') AS tenant_id
                FROM namespaces n
                LEFT JOIN users u ON n.owner_account_id = u.user_id
                WHERE n.slug = :selector
                ORDER BY n.created_at ASC
                LIMIT 1
                """)
                .param("selector", namespaceSelector)
                .query((rs, rowNum) -> new NamespaceMetadata(
                        rs.getString("namespace_id"),
                        rs.getString("owner_account_id"),
                        NamespaceStatus.valueOf(rs.getString("status")),
                        rs.getString("tenant_id")
                ))
                .optional()
                .orElseThrow(() -> new NamespaceNotFoundException(namespaceSelector));
    }

    private String resolveCallerTenantId(String callerId) {
        String claimTenant = SecurityUtils.getTenantId();
        if (claimTenant != null && !claimTenant.isBlank() && !"default".equals(claimTenant)) {
            return claimTenant;
        }
        if (callerId != null && !callerId.isBlank()) {
            try {
                Optional<String> dbTenant = jdbc.sql("SELECT tenant_id FROM users WHERE user_id = :id OR username = :id LIMIT 1")
                        .param("id", callerId)
                        .query(String.class)
                        .optional();
                if (dbTenant.isPresent() && dbTenant.get() != null && !dbTenant.get().isBlank()) {
                    return dbTenant.get();
                }
            } catch (Exception ignored) {}
        }
        return claimTenant != null && !claimTenant.isBlank() ? claimTenant : "default";
    }

    private BreakGlassRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp approvedTs = rs.getTimestamp("approved_at");
        Timestamp expiresTs = rs.getTimestamp("expires_at");
        Timestamp revokedTs = rs.getTimestamp("revoked_at");

        return new BreakGlassRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("namespace_id"),
                rs.getString("owner_account_id"),
                rs.getString("requester_account_id"),
                rs.getString("requester_role"),
                rs.getString("justification"),
                rs.getString("ticket_ref"),
                rs.getInt("duration_minutes"),
                BreakGlassStatus.fromString(rs.getString("status")),
                rs.getString("approver_account_id"),
                rs.getString("approval_comment"),
                rs.getString("grant_id"),
                createdTs != null ? createdTs.toInstant() : Instant.EPOCH,
                approvedTs != null ? approvedTs.toInstant() : null,
                expiresTs != null ? expiresTs.toInstant() : null,
                revokedTs != null ? revokedTs.toInstant() : null,
                rs.getString("revoked_by"),
                rs.getString("revoke_reason")
        );
    }

    private record NamespaceMetadata(
            String namespaceId,
            String ownerAccountId,
            NamespaceStatus status,
            String tenantId
    ) {}
}
