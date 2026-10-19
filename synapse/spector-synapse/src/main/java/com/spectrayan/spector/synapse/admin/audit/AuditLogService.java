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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.events.EventBus;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.synapse.admin.audit.dto.AdminAuditDto.AuditChainVerificationDto;
import com.spectrayan.spector.synapse.admin.audit.dto.AdminAuditDto.AuditEventDto;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import com.spectrayan.spector.synapse.security.UserRow;

/**
 * Append-only, tamper-evident audit logging service for administrative mutations,
 * BYOK key operations, break-glass access, quota updates, grant changes, and compliance
 * erasure (ADR-0087, Issue #1059).
 *
 * <p><strong>Append-Only &amp; Tamper-Evident Invariant</strong>: Every audit record computes a
 * SHA-256 {@code entry_hash} over {@code prev_hash} and all canonical record fields. No API
 * method is exposed to modify or delete individual audit entries.</p>
 *
 * <p><strong>Content-Free Invariant</strong>: Only principal IDs, resource IDs, action codes,
 * outcomes, and structural configuration metadata are recorded. Memory text, embeddings, tags,
 * and secret material are strictly stripped/forbidden.</p>
 */
@Service
@DependsOn("flyway")
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    public static final String GENESIS_HASH = "GENESIS";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_DENIED = "DENIED";
    public static final String OUTCOME_FAILURE = "FAILURE";

    private static final int DEFAULT_QUERY_LIMIT = 100;
    private static final int MAX_QUERY_LIMIT = 1000;

    private static final Set<String> REDACTED_DETAIL_KEYS = Set.of(
            "text", "vector", "quantizedvector", "payload", "tags",
            "rawsecret", "secret", "ciphertext", "password", "newpassword"
    );

    private final JdbcClient jdbc;
    private final SqlQueryLoader sqlLoader;
    private final ObjectMapper objectMapper;
    private final UserAccountStore userAccountStore;
    private final AuditProperties auditProperties;
    private final EventPublisher eventPublisher;
    private final EventBus<AuditSpectorEvent> auditEventBus;
    private final TsidGenerator tsid = new TsidGenerator(14);
    private final ReentrantLock chainLock = new ReentrantLock();
    private final AtomicLong lastTimestampMillis = new AtomicLong(0L);

    @Autowired
    public AuditLogService(
            JdbcClient jdbc,
            @Autowired(required = false) SqlQueryLoader sqlLoader,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) UserAccountStore userAccountStore,
            @Autowired(required = false) AuditProperties auditProperties,
            @Autowired(required = false) EventPublisher eventPublisher) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.sqlLoader = sqlLoader != null ? sqlLoader : new SqlQueryLoader();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.userAccountStore = userAccountStore;
        this.auditProperties = auditProperties != null ? auditProperties : new AuditProperties();
        this.eventPublisher = eventPublisher;
        this.auditEventBus = EventBus.scoped(AuditSpectorEvent::scope);
    }

    public AuditLogService(JdbcClient jdbc, SqlQueryLoader sqlLoader, ObjectMapper objectMapper) {
        this(jdbc, sqlLoader, objectMapper, null, null, null);
    }

    /**
     * Returns the dedicated {@link EventBus} streaming {@link AuditSpectorEvent} notifications
     * for SIEM connectors and audit subscribers.
     */
    public EventBus<AuditSpectorEvent> auditEventBus() {
        return auditEventBus;
    }

    /**
     * Records an administrative mutation into both the hash-chained {@code audit_event} table
     * and the legacy {@code admin_audit_log} table.
     */
    public AuditEventDto recordAdminAction(
            String actorId,
            String actorTenantId,
            String resourceTenantId,
            String action,
            String targetType,
            String targetId,
            String namespaceId,
            String reason,
            Map<String, Object> details) {

        String effActor = resolveActorAccountId(actorId);
        String effActorTenant = (actorTenantId != null && !actorTenantId.isBlank())
                ? actorTenantId.trim()
                : resolveCallerTenantId(effActor);
        String effResourceTenant = (resourceTenantId != null && !resourceTenantId.isBlank())
                ? resourceTenantId.trim()
                : effActorTenant;

        String effReason = reason;
        if ((effReason == null || effReason.isBlank()) && details != null && details.get("reason") instanceof String r && !r.isBlank()) {
            effReason = r.trim();
        }

        Map<String, Object> sanitizedDetails = sanitizeDetails(details);
        String detailsJson = serializeDetails(sanitizedDetails);

        AuditEventDto recorded = recordInternal(
                effResourceTenant,
                effActor,
                resolveActorRole(null),
                action,
                targetType,
                targetId,
                namespaceId,
                effReason,
                resolveRequestId(null),
                OUTCOME_SUCCESS,
                sanitizedDetails,
                detailsJson
        );

        // Mirror to admin_audit_log for backward compatibility with per-entity /audit endpoints
        try {
            jdbc.sql(sqlLoader.load("tenants/insert-audit-log"))
                    .param("auditId", recorded.id())
                    .param("timestamp", Timestamp.from(recorded.ts()))
                    .param("actorId", effActor)
                    .param("actorTenantId", effActorTenant)
                    .param("action", action)
                    .param("targetType", targetType)
                    .param("targetId", targetId)
                    .param("detailsJson", detailsJson)
                    .update();
        } catch (Exception e) {
            log.debug("[AuditLogService] Mirror insert to admin_audit_log skipped: {}", e.getMessage());
        }

        return recorded;
    }

    /**
     * Records a privileged action using the current authenticated security context.
     */
    public AuditEventDto recordAction(
            String action,
            String targetType,
            String targetId,
            String namespaceId,
            String reason,
            Map<String, Object> details) {
        String actorId = resolveActorAccountId(null);
        String tenantId = resolveCallerTenantId(actorId);
        return record(
                tenantId,
                actorId,
                resolveActorRole(null),
                action,
                targetType,
                targetId,
                namespaceId,
                reason,
                resolveRequestId(null),
                OUTCOME_SUCCESS,
                details
        );
    }

    /**
     * Records a privileged action for an explicit resource tenant using the current authenticated actor.
     */
    public AuditEventDto recordActionForTenant(
            String tenantId,
            String action,
            String targetType,
            String targetId,
            String namespaceId,
            String reason,
            Map<String, Object> details) {
        String actorId = resolveActorAccountId(null);
        String effTenant = (tenantId != null && !tenantId.isBlank())
                ? tenantId.trim()
                : resolveCallerTenantId(actorId);
        return record(
                effTenant,
                actorId,
                resolveActorRole(null),
                action,
                targetType,
                targetId,
                namespaceId,
                reason,
                resolveRequestId(null),
                OUTCOME_SUCCESS,
                details
        );
    }

    /**
     * Records a break-glass lifecycle or memory-read audit event (supporting Issue #1059 &amp; #1060).
     */
    public AuditEventDto recordBreakGlass(
            String action,
            String tenantId,
            String namespaceId,
            String targetId,
            String reason,
            String outcome,
            Map<String, Object> details) {
        String actorId = resolveActorAccountId(null);
        String effTenant = (tenantId != null && !tenantId.isBlank())
                ? tenantId.trim()
                : resolveCallerTenantId(actorId);
        return record(
                effTenant,
                actorId,
                resolveActorRole(null),
                action,
                "BREAK_GLASS",
                targetId != null ? targetId : (namespaceId != null ? namespaceId : "unknown"),
                namespaceId,
                reason,
                resolveRequestId(null),
                outcome != null ? outcome : OUTCOME_SUCCESS,
                details
        );
    }

    /**
     * Appends a new tamper-evident audit event to {@code audit_event} and publishes it to the SIEM {@link EventBus}.
     */
    public AuditEventDto record(
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
            Map<String, Object> details) {

        String effActor = resolveActorAccountId(actorAccountId);
        String effTenant = (tenantId != null && !tenantId.isBlank())
                ? tenantId.trim()
                : resolveCallerTenantId(effActor);
        String effRole = resolveActorRole(actorRole);
        String effAction = (action != null && !action.isBlank()) ? action.trim() : "UNKNOWN_ACTION";
        String effTargetType = (targetType != null && !targetType.isBlank()) ? targetType.trim() : "UNKNOWN";
        String effTargetId = (targetId != null && !targetId.isBlank()) ? targetId.trim() : "unknown";
        String effNamespaceId = (namespaceId != null && !namespaceId.isBlank()) ? namespaceId.trim() : null;
        String effReason = (reason != null && !reason.isBlank()) ? reason.trim() : null;
        String effRequestId = resolveRequestId(requestId);
        String effOutcome = (outcome != null && !outcome.isBlank()) ? outcome.trim().toUpperCase() : OUTCOME_SUCCESS;

        Map<String, Object> sanitizedDetails = sanitizeDetails(details);
        String detailsJson = serializeDetails(sanitizedDetails);

        return recordInternal(
                effTenant,
                effActor,
                effRole,
                effAction,
                effTargetType,
                effTargetId,
                effNamespaceId,
                effReason,
                effRequestId,
                effOutcome,
                sanitizedDetails,
                detailsJson
        );
    }

    private AuditEventDto recordInternal(
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
            Map<String, Object> sanitizedDetails,
            String detailsJson) {

        AuditEventDto dto;
        chainLock.lock();
        try {
            String id = tsid.generate();
            long nowMillis = lastTimestampMillis.updateAndGet(prev -> Math.max(System.currentTimeMillis(), prev + 1));
            Instant ts = Instant.ofEpochMilli(nowMillis);

            String prevHash = findLatestEntryHash().orElse(GENESIS_HASH);
            String entryHash = computeEntryHash(
                    prevHash,
                    id,
                    nowMillis,
                    tenantId,
                    actorAccountId,
                    actorRole,
                    action,
                    targetType,
                    targetId,
                    namespaceId,
                    reason,
                    requestId,
                    outcome,
                    detailsJson
            );

            jdbc.sql("""
                    INSERT INTO audit_event (
                        id, ts, tenant_id, actor_account_id, actor_role, action,
                        target_type, target_id, namespace_id, reason, request_id,
                        outcome, details_json, prev_hash, entry_hash
                    ) VALUES (
                        :id, :ts, :tenantId, :actorAccountId, :actorRole, :action,
                        :targetType, :targetId, :namespaceId, :reason, :requestId,
                        :outcome, :detailsJson, :prevHash, :entryHash
                    )
                    """)
                    .param("id", id)
                    .param("ts", Timestamp.from(ts))
                    .param("tenantId", tenantId)
                    .param("actorAccountId", actorAccountId)
                    .param("actorRole", actorRole)
                    .param("action", action)
                    .param("targetType", targetType)
                    .param("targetId", targetId)
                    .param("namespaceId", namespaceId)
                    .param("reason", reason)
                    .param("requestId", requestId)
                    .param("outcome", outcome)
                    .param("detailsJson", detailsJson)
                    .param("prevHash", prevHash)
                    .param("entryHash", entryHash)
                    .update();

            dto = new AuditEventDto(
                    id,
                    ts,
                    tenantId,
                    actorAccountId,
                    actorRole,
                    action,
                    targetType,
                    targetId,
                    namespaceId,
                    reason,
                    requestId,
                    outcome,
                    sanitizedDetails,
                    prevHash,
                    entryHash
            );
        } finally {
            chainLock.unlock();
        }

        log.info("[AUDIT_EVENT] id={} tenant={} actor={} role={} action={} targetType={} targetId={} ns={} outcome={} hash={}",
                dto.id(), dto.tenantId(), dto.actorAccountId(), dto.actorRole(),
                dto.action(), dto.targetType(), dto.targetId(), dto.namespaceId(),
                dto.outcome(), dto.entryHash());

        if (auditProperties == null || auditProperties.isSiemExportEnabled()) {
            try {
                AuditSpectorEvent spectorEvent = AuditSpectorEvent.from(dto);
                auditEventBus.publish(spectorEvent);
                if (eventPublisher != null) {
                    eventPublisher.systemEvent(spectorEvent.eventType(), dto);
                }
            } catch (Exception e) {
                log.debug("[AuditLogService] SIEM event bus publish skipped: {}", e.getMessage());
            }
        }

        return dto;
    }

    /**
     * Queries audit events with tenant-isolation enforcement:
     * <ul>
     *   <li>{@code super-admin} can query fleet-wide or filter by any {@code tenantId}.</li>
     *   <li>Tenant {@code admin} is strictly scoped to their own tenant; specifying another
     *       {@code tenantId} throws {@link CrossTenantAccessException} ({@code SPE-820-001}, HTTP 403).</li>
     * </ul>
     */
    public List<AuditEventDto> queryAuditEvents(
            String tenantIdFilter,
            String actorAccountIdFilter,
            String actionFilter,
            String targetTypeFilter,
            String targetIdFilter,
            String namespaceIdFilter,
            String outcomeFilter,
            Instant from,
            Instant to,
            Integer limit) {

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String effectiveTenant;
        if (!isSuperAdmin) {
            if (tenantIdFilter != null && !tenantIdFilter.isBlank() && !tenantIdFilter.trim().equals(callerTenant)) {
                record(
                        callerTenant,
                        callerId,
                        resolveActorRole(null),
                        "QUERY_AUDIT_LOG",
                        "TENANT",
                        tenantIdFilter.trim(),
                        null,
                        "Cross-tenant audit query denied",
                        resolveRequestId(null),
                        OUTCOME_DENIED,
                        Map.of("requestedTenantId", tenantIdFilter.trim())
                );
                throw new CrossTenantAccessException(callerId, "audit", tenantIdFilter.trim());
            }
            effectiveTenant = callerTenant;
        } else {
            effectiveTenant = (tenantIdFilter != null && !tenantIdFilter.isBlank())
                    ? tenantIdFilter.trim()
                    : null;
        }

        int effLimit = normalizeLimit(limit);

        List<AuditEventDto> all = jdbc.sql("""
                SELECT id, ts, tenant_id, actor_account_id, actor_role, action,
                       target_type, target_id, namespace_id, reason, request_id,
                       outcome, details_json, prev_hash, entry_hash
                FROM audit_event
                ORDER BY ts DESC, id DESC
                """)
                .query(this::mapAuditEventRow)
                .list();

        return all.stream()
                .filter(e -> effectiveTenant == null || effectiveTenant.equals(e.tenantId()))
                .filter(e -> actorAccountIdFilter == null || actorAccountIdFilter.isBlank()
                        || actorAccountIdFilter.trim().equals(e.actorAccountId()))
                .filter(e -> actionFilter == null || actionFilter.isBlank()
                        || actionFilter.trim().equalsIgnoreCase(e.action()))
                .filter(e -> targetTypeFilter == null || targetTypeFilter.isBlank()
                        || targetTypeFilter.trim().equalsIgnoreCase(e.targetType()))
                .filter(e -> targetIdFilter == null || targetIdFilter.isBlank()
                        || targetIdFilter.trim().equals(e.targetId()))
                .filter(e -> namespaceIdFilter == null || namespaceIdFilter.isBlank()
                        || namespaceIdFilter.trim().equals(e.namespaceId()))
                .filter(e -> outcomeFilter == null || outcomeFilter.isBlank()
                        || outcomeFilter.trim().equalsIgnoreCase(e.outcome()))
                .filter(e -> from == null || !e.ts().isBefore(from))
                .filter(e -> to == null || !e.ts().isAfter(to))
                .limit(effLimit)
                .toList();
    }

    /**
     * Lists audit events targeting a specific namespace (for namespace owners and {@code ADMIN} grant holders).
     */
    public List<AuditEventDto> listNamespaceAuditEvents(String namespaceId, Integer limit) {
        if (namespaceId == null || namespaceId.isBlank()) {
            return List.of();
        }
        String normNs = namespaceId.trim();
        int effLimit = normalizeLimit(limit);

        return jdbc.sql("""
                SELECT id, ts, tenant_id, actor_account_id, actor_role, action,
                       target_type, target_id, namespace_id, reason, request_id,
                       outcome, details_json, prev_hash, entry_hash
                FROM audit_event
                WHERE namespace_id = :namespaceId
                   OR (target_type = 'NAMESPACE' AND target_id = :namespaceId)
                ORDER BY ts DESC, id DESC
                """)
                .param("namespaceId", normNs)
                .query(this::mapAuditEventRow)
                .list()
                .stream()
                .limit(effLimit)
                .toList();
    }

    /**
     * Verifies the cryptographic SHA-256 hash chain across recorded audit events.
     */
    public AuditChainVerificationDto verifyChain(String tenantIdFilter) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String effectiveTenant;
        if (!isSuperAdmin) {
            if (tenantIdFilter != null && !tenantIdFilter.isBlank() && !tenantIdFilter.trim().equals(callerTenant)) {
                record(
                        callerTenant,
                        callerId,
                        resolveActorRole(null),
                        "VERIFY_AUDIT_CHAIN",
                        "TENANT",
                        tenantIdFilter.trim(),
                        null,
                        "Cross-tenant audit chain verification denied",
                        resolveRequestId(null),
                        OUTCOME_DENIED,
                        Map.of("requestedTenantId", tenantIdFilter.trim())
                );
                throw new CrossTenantAccessException(callerId, "audit", tenantIdFilter.trim());
            }
            effectiveTenant = callerTenant;
        } else {
            effectiveTenant = (tenantIdFilter != null && !tenantIdFilter.isBlank())
                    ? tenantIdFilter.trim()
                    : null;
        }

        List<RawAuditRow> rows = jdbc.sql("""
                SELECT id, ts, tenant_id, actor_account_id, actor_role, action,
                       target_type, target_id, namespace_id, reason, request_id,
                       outcome, details_json, prev_hash, entry_hash
                FROM audit_event
                ORDER BY ts ASC, id ASC
                """)
                .query(this::mapRawAuditRow)
                .list();

        String expectedPrevHash = GENESIS_HASH;
        String lastVerifiedHash = GENESIS_HASH;
        long checkedCount = 0L;

        for (RawAuditRow row : rows) {
            boolean matchesScope = (effectiveTenant == null || effectiveTenant.equals(row.tenantId()));

            String recomputed = computeEntryHash(
                    row.prevHash(),
                    row.id(),
                    row.ts().toEpochMilli(),
                    row.tenantId(),
                    row.actorAccountId(),
                    row.actorRole(),
                    row.action(),
                    row.targetType(),
                    row.targetId(),
                    row.namespaceId(),
                    row.reason(),
                    row.requestId(),
                    row.outcome(),
                    row.detailsJson()
            );

            boolean prevLinkValid = Objects.equals(expectedPrevHash, row.prevHash());
            boolean hashValid = Objects.equals(recomputed, row.entryHash());

            if (!prevLinkValid || !hashValid) {
                if (matchesScope || effectiveTenant == null) {
                    String reasonMsg = !hashValid
                            ? "Entry hash mismatch at audit event '" + row.id() + "'"
                            : "Broken prev_hash link at audit event '" + row.id() + "'";
                    return new AuditChainVerificationDto(
                            false,
                            checkedCount,
                            lastVerifiedHash,
                            row.id(),
                            reasonMsg,
                            Instant.now()
                    );
                }
            }

            expectedPrevHash = row.entryHash();
            if (matchesScope) {
                checkedCount++;
                lastVerifiedHash = row.entryHash();
            }
        }

        return new AuditChainVerificationDto(
                true,
                checkedCount,
                lastVerifiedHash,
                null,
                "Audit hash chain verified intact (" + checkedCount + " events)",
                Instant.now()
        );
    }

    /**
     * Prunes audit records older than {@link AuditProperties#getRetentionDays()} when retention pruning is enabled.
     */
    public int enforceRetentionPolicy() {
        if (auditProperties == null || auditProperties.getRetentionDays() <= 0) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(auditProperties.getRetentionDays(), ChronoUnit.DAYS);
        return jdbc.sql("DELETE FROM audit_event WHERE ts < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }

    /**
     * Computes the deterministic SHA-256 hex digest for an audit entry.
     */
    public static String computeEntryHash(
            String prevHash,
            String id,
            long tsEpochMilli,
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
            String detailsJson) {
        String canonical = nullToEmpty(prevHash)
                + "|" + nullToEmpty(id)
                + "|" + tsEpochMilli
                + "|" + nullToEmpty(tenantId)
                + "|" + nullToEmpty(actorAccountId)
                + "|" + nullToEmpty(actorRole)
                + "|" + nullToEmpty(action)
                + "|" + nullToEmpty(targetType)
                + "|" + nullToEmpty(targetId)
                + "|" + nullToEmpty(namespaceId)
                + "|" + nullToEmpty(reason)
                + "|" + nullToEmpty(requestId)
                + "|" + nullToEmpty(outcome)
                + "|" + nullToEmpty(detailsJson);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private Optional<String> findLatestEntryHash() {
        try {
            return jdbc.sql("SELECT entry_hash FROM audit_event ORDER BY ts DESC, id DESC LIMIT 1")
                    .query(String.class)
                    .optional();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String resolveCallerTenantId(String callerId) {
        String securityTenant = SecurityUtils.getTenantId();
        if (securityTenant != null && !securityTenant.isBlank() && !"default".equals(securityTenant)) {
            return securityTenant.trim();
        }
        if (userAccountStore != null && callerId != null && !callerId.isBlank() && !"default".equals(callerId)) {
            try {
                Optional<UserRow> userRow = userAccountStore.findByUserId(callerId)
                        .or(() -> userAccountStore.findByUsername(callerId));
                if (userRow.isPresent() && userRow.get().tenantId() != null && !userRow.get().tenantId().isBlank()) {
                    return userRow.get().tenantId().trim();
                }
            } catch (Exception ignored) {}
        }
        return (securityTenant != null && !securityTenant.isBlank()) ? securityTenant.trim() : "default";
    }

    private static String resolveActorAccountId(String explicitActorId) {
        if (explicitActorId != null && !explicitActorId.isBlank()) {
            return explicitActorId.trim();
        }
        String secUser = SecurityUtils.getUserId();
        return (secUser != null && !secUser.isBlank()) ? secUser.trim() : "system";
    }

    private static String resolveActorRole(String explicitRole) {
        if (explicitRole != null && !explicitRole.isBlank()) {
            return explicitRole.trim();
        }
        if (SecurityUtils.isSuperAdmin()) {
            return "super-admin";
        }
        if (SecurityUtils.hasRole("admin")) {
            return "admin";
        }
        Set<String> roles = SecurityUtils.getRoles();
        if (roles != null && !roles.isEmpty()) {
            return String.join(",", roles);
        }
        return SecurityUtils.isAuthenticated() ? "user" : "system";
    }

    private static String resolveRequestId(String explicitRequestId) {
        if (explicitRequestId != null && !explicitRequestId.isBlank()) {
            return explicitRequestId.trim();
        }
        for (String mdcKey : new String[]{"requestId", "traceId", "X-Request-ID", "correlationId"}) {
            String val = MDC.get(mdcKey);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                for (String header : new String[]{"X-Request-ID", "X-Correlation-ID"}) {
                    String h = req.getHeader(header);
                    if (h != null && !h.isBlank()) {
                        return h.trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, Object> sanitizeDetails(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String normKey = key.trim().toLowerCase().replace("_", "").replace("-", "");
            if (REDACTED_DETAIL_KEYS.contains(normKey)) {
                continue;
            }
            clean.put(key, entry.getValue());
        }
        return clean;
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            log.warn("[AuditLogService] Failed to serialize audit details: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> deserializeDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return sanitizeDetails(parsed);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_QUERY_LIMIT;
        }
        return Math.min(limit, MAX_QUERY_LIMIT);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private AuditEventDto mapAuditEventRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("ts");
        String detailsJson = rs.getString("details_json");
        return new AuditEventDto(
                rs.getString("id"),
                ts != null ? Instant.ofEpochMilli(ts.toInstant().toEpochMilli()) : Instant.EPOCH,
                rs.getString("tenant_id"),
                rs.getString("actor_account_id"),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("namespace_id"),
                rs.getString("reason"),
                rs.getString("request_id"),
                rs.getString("outcome"),
                deserializeDetails(detailsJson),
                rs.getString("prev_hash"),
                rs.getString("entry_hash")
        );
    }

    private RawAuditRow mapRawAuditRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("ts");
        return new RawAuditRow(
                rs.getString("id"),
                ts != null ? Instant.ofEpochMilli(ts.toInstant().toEpochMilli()) : Instant.EPOCH,
                rs.getString("tenant_id"),
                rs.getString("actor_account_id"),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("namespace_id"),
                rs.getString("reason"),
                rs.getString("request_id"),
                rs.getString("outcome"),
                rs.getString("details_json"),
                rs.getString("prev_hash"),
                rs.getString("entry_hash")
        );
    }

    private record RawAuditRow(
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
            String detailsJson,
            String prevHash,
            String entryHash
    ) {}
}
