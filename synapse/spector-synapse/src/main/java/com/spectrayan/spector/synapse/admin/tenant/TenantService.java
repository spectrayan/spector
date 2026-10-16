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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.synapse.admin.audit.AuditLogService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantAuditResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantQuotasDto;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantSettingsDto;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.UpdateTenantRequest;
import com.spectrayan.spector.synapse.catalog.exception.TenantSuspendedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import com.spectrayan.spector.synapse.error.SynapseConflictException;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;

/**
 * Administrative service managing tenant lifecycle, settings, quotas, aggregate resource telemetry,
 * and append-only mutation auditing (ADR-0087, Issue #1055).
 */
@Service
@DependsOn("flyway")
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
    private static final int DEFAULT_BREAK_GLASS_DURATION_MINUTES = 60;
    private static final int MAX_BREAK_GLASS_DURATION_MINUTES = 1440;

    private final JdbcClient jdbc;
    private final SqlQueryLoader sqlLoader;
    private final ObjectMapper objectMapper;
    private final SynapseProperties synapseProps;
    private final AuditLogService auditLogService;
    private final TsidGenerator tsid = new TsidGenerator();
    private final Set<String> byokConfiguredTenants = ConcurrentHashMap.newKeySet();

    @Autowired
    public TenantService(
            JdbcClient jdbc,
            SqlQueryLoader sqlLoader,
            ObjectMapper objectMapper,
            @Autowired(required = false) SynapseProperties synapseProps,
            @Autowired(required = false) AuditLogService auditLogService) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.sqlLoader = sqlLoader != null ? sqlLoader : new SqlQueryLoader();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.synapseProps = synapseProps;
        this.auditLogService = auditLogService != null
                ? auditLogService
                : new AuditLogService(this.jdbc, this.sqlLoader, this.objectMapper);
    }

    public TenantService(
            JdbcClient jdbc,
            SqlQueryLoader sqlLoader,
            ObjectMapper objectMapper,
            SynapseProperties synapseProps) {
        this(jdbc, sqlLoader, objectMapper, synapseProps, null);
    }

    public TenantService(JdbcClient jdbc, SqlQueryLoader sqlLoader, ObjectMapper objectMapper) {
        this(jdbc, sqlLoader, objectMapper, null, null);
    }

    /**
     * Marks a tenant as having BYOK encryption keys configured (used by KMS/BYOK key management).
     */
    public void registerByokKeyConfigured(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            byokConfiguredTenants.add(tenantId.trim());
        }
    }

    /**
     * Checks whether BYOK encryption keys have been configured for the given tenant.
     */
    public boolean hasConfiguredByokKeys(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        if (byokConfiguredTenants.contains(tenantId.trim())) {
            return true;
        }
        try {
            Integer count = jdbc.sql("""
                    SELECT COUNT(*) FROM credentials
                    WHERE tenant_id = :tenantId
                      AND (LOWER(name) LIKE '%byok%' OR LOWER(name) LIKE '%kms%' OR LOWER(provider) LIKE '%kms%')
                    """)
                    .param("tenantId", tenantId.trim())
                    .query(Integer.class)
                    .single();
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a new tenant and records an append-only audit entry.
     */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request, String actorId, String actorTenantId) {
        if (request == null) {
            throw new IllegalArgumentException("Tenant creation request must not be null");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Tenant name is required");
        }

        String tenantId = (request.tenantId() != null && !request.tenantId().isBlank())
                ? request.tenantId().trim()
                : tsid.generate();

        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("Invalid tenantId '" + tenantId + "' — must match " + TENANT_ID_PATTERN.pattern());
        }

        if (findTenantRecord(tenantId).isPresent()) {
            throw new SynapseConflictException("tenantId", tenantId);
        }

        TenantStatus status = request.status() != null ? request.status() : TenantStatus.ACTIVE;
        if (status == TenantStatus.DELETED) {
            throw new IllegalArgumentException("Cannot create a tenant in DELETED status");
        }

        TenantPlan resolvedPlan = TenantPlan.fromStringOrDefault(request.plan());
        String planName = (request.plan() != null && !request.plan().isBlank())
                ? request.plan().trim().toUpperCase()
                : resolvedPlan.name();

        boolean legalHold = request.legalHold() != null && request.legalHold();

        TenantSettingsDto reqSettings = request.settings();
        boolean byokEnabled = reqSettings != null && Boolean.TRUE.equals(reqSettings.byokEnabled());
        if (byokEnabled && !hasConfiguredByokKeys(tenantId)) {
            throw new IllegalArgumentException(
                    "byokEnabled is read-only and cannot be enabled until BYOK encryption keys are configured for tenant '" + tenantId + "'");
        }

        boolean breakGlassApproverRequired = (reqSettings == null || reqSettings.breakGlassApproverRequired() == null)
                ? true
                : reqSettings.breakGlassApproverRequired();

        int breakGlassMaxDuration = (reqSettings == null || reqSettings.breakGlassMaxDurationMinutes() == null)
                ? DEFAULT_BREAK_GLASS_DURATION_MINUTES
                : reqSettings.breakGlassMaxDurationMinutes();
        validateBreakGlassDuration(breakGlassMaxDuration);

        TenantQuotasDto defaultQuotas = resolvedPlan.defaultQuotas();
        TenantQuotasDto reqQuotas = request.quotas();
        int maxAccounts = (reqQuotas != null && reqQuotas.maxAccounts() != null)
                ? reqQuotas.maxAccounts()
                : defaultQuotas.maxAccounts();
        int maxNamespaces = (reqQuotas != null && reqQuotas.maxNamespaces() != null)
                ? reqQuotas.maxNamespaces()
                : defaultQuotas.maxNamespaces();
        long maxStorageBytes = (reqQuotas != null && reqQuotas.maxStorageBytes() != null)
                ? reqQuotas.maxStorageBytes()
                : defaultQuotas.maxStorageBytes();

        validateQuotas(maxAccounts, maxNamespaces, maxStorageBytes);

        Instant now = Instant.now();
        try {
            jdbc.sql(sqlLoader.load("tenants/insert-tenant"))
                    .param("tenantId", tenantId)
                    .param("name", request.name().trim())
                    .param("description", request.description())
                    .param("status", status.name())
                    .param("plan", planName)
                    .param("legalHold", legalHold)
                    .param("byokEnabled", byokEnabled)
                    .param("breakGlassApproverRequired", breakGlassApproverRequired)
                    .param("breakGlassMaxDurationMinutes", breakGlassMaxDuration)
                    .param("maxAccounts", maxAccounts)
                    .param("maxNamespaces", maxNamespaces)
                    .param("maxStorageBytes", maxStorageBytes)
                    .param("createdAt", Timestamp.from(now))
                    .param("updatedAt", Timestamp.from(now))
                    .param("deletedAt", null)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new SynapseConflictException("tenantId", tenantId);
        }

        TenantRecord created = getTenantRecordOrThrow(tenantId);
        recordAudit(actorId, actorTenantId, "CREATE_TENANT", tenantId, Map.of(
                "name", created.name(),
                "plan", created.plan(),
                "status", created.status().name(),
                "legalHold", created.legalHold()
        ));

        return toResponse(created);
    }

    /**
     * Lists all tenants matching the optional status filter.
     */
    public List<TenantResponse> listTenants(TenantStatus statusFilter, boolean includeDeleted) {
        List<TenantRecord> records = jdbc.sql(sqlLoader.load("tenants/list-all"))
                .query(this::mapTenantRow)
                .list();

        return records.stream()
                .filter(r -> includeDeleted || r.status() != TenantStatus.DELETED)
                .filter(r -> statusFilter == null || r.status() == statusFilter)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves a single tenant with aggregate resource counts.
     */
    public TenantResponse getTenant(String tenantId) {
        TenantRecord record = getTenantRecordOrThrow(tenantId);
        return toResponse(record);
    }

    /**
     * Updates a tenant's display name, description, plan, status (ACTIVE/SUSPENDED),
     * legal hold, break-glass policy, or default quotas, and audits the mutation.
     */
    @Transactional
    public TenantResponse updateTenant(String tenantId, UpdateTenantRequest request, String actorId, String actorTenantId) {
        if (request == null) {
            throw new IllegalArgumentException("Tenant update request must not be null");
        }

        TenantRecord existing = getTenantRecordOrThrow(tenantId);
        if (existing.status() == TenantStatus.DELETED) {
            throw new SynapseConflictException("Tenant '" + tenantId + "' is deleted and cannot be modified");
        }

        String newName = (request.name() != null && !request.name().isBlank())
                ? request.name().trim()
                : existing.name();
        String newDescription = request.description() != null
                ? request.description()
                : existing.description();
        String newPlan = (request.plan() != null && !request.plan().isBlank())
                ? request.plan().trim().toUpperCase()
                : existing.plan();

        TenantStatus newStatus = request.status() != null ? request.status() : existing.status();
        if (newStatus == TenantStatus.DELETED) {
            throw new IllegalArgumentException("Use DELETE /api/v1/admin/tenants/" + tenantId + " to soft-delete a tenant");
        }
        if ("default".equalsIgnoreCase(tenantId) && newStatus == TenantStatus.SUSPENDED) {
            throw new SynapseConflictException("Default tenant 'default' cannot be suspended");
        }

        boolean newLegalHold = request.legalHold() != null ? request.legalHold() : existing.legalHold();

        boolean newByokEnabled = existing.byokEnabled();
        boolean newBreakGlassApprover = existing.breakGlassApproverRequired();
        int newBreakGlassDuration = existing.breakGlassMaxDurationMinutes();

        if (request.settings() != null) {
            TenantSettingsDto s = request.settings();
            if (s.byokEnabled() != null && s.byokEnabled() != existing.byokEnabled()) {
                if (s.byokEnabled() && !hasConfiguredByokKeys(tenantId)) {
                    throw new IllegalArgumentException(
                            "byokEnabled is read-only and cannot be enabled until BYOK encryption keys are configured for tenant '" + tenantId + "'");
                }
                newByokEnabled = s.byokEnabled();
            }
            if (s.breakGlassApproverRequired() != null) {
                newBreakGlassApprover = s.breakGlassApproverRequired();
            }
            if (s.breakGlassMaxDurationMinutes() != null) {
                validateBreakGlassDuration(s.breakGlassMaxDurationMinutes());
                newBreakGlassDuration = s.breakGlassMaxDurationMinutes();
            }
        }

        int newMaxAccounts = existing.maxAccounts();
        int newMaxNamespaces = existing.maxNamespaces();
        long newMaxStorageBytes = existing.maxStorageBytes();

        if (request.quotas() != null) {
            TenantQuotasDto q = request.quotas();
            if (q.maxAccounts() != null) {
                newMaxAccounts = q.maxAccounts();
            }
            if (q.maxNamespaces() != null) {
                newMaxNamespaces = q.maxNamespaces();
            }
            if (q.maxStorageBytes() != null) {
                newMaxStorageBytes = q.maxStorageBytes();
            }
            validateQuotas(newMaxAccounts, newMaxNamespaces, newMaxStorageBytes);
        }

        Instant now = Instant.now();
        jdbc.sql(sqlLoader.load("tenants/update-tenant"))
                .param("tenantId", tenantId)
                .param("name", newName)
                .param("description", newDescription)
                .param("status", newStatus.name())
                .param("plan", newPlan)
                .param("legalHold", newLegalHold)
                .param("byokEnabled", newByokEnabled)
                .param("breakGlassApproverRequired", newBreakGlassApprover)
                .param("breakGlassMaxDurationMinutes", newBreakGlassDuration)
                .param("maxAccounts", newMaxAccounts)
                .param("maxNamespaces", newMaxNamespaces)
                .param("maxStorageBytes", newMaxStorageBytes)
                .param("updatedAt", Timestamp.from(now))
                .update();

        TenantRecord updated = getTenantRecordOrThrow(tenantId);

        String auditAction = "UPDATE_TENANT";
        if (existing.status() != newStatus) {
            auditAction = (newStatus == TenantStatus.SUSPENDED) ? "SUSPEND_TENANT" : "ACTIVATE_TENANT";
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("previousStatus", existing.status().name());
        auditDetails.put("status", updated.status().name());
        auditDetails.put("name", updated.name());
        auditDetails.put("plan", updated.plan());
        auditDetails.put("legalHold", updated.legalHold());
        auditDetails.put("byokEnabled", updated.byokEnabled());
        auditDetails.put("breakGlassApproverRequired", updated.breakGlassApproverRequired());
        auditDetails.put("breakGlassMaxDurationMinutes", updated.breakGlassMaxDurationMinutes());
        auditDetails.put("maxAccounts", updated.maxAccounts());
        auditDetails.put("maxNamespaces", updated.maxNamespaces());
        auditDetails.put("maxStorageBytes", updated.maxStorageBytes());

        recordAudit(actorId, actorTenantId, auditAction, tenantId, auditDetails);
        return toResponse(updated);
    }

    /**
     * Soft-deletes a tenant (marks status = DELETED and sets deleted_at) and records an audit entry.
     */
    @Transactional
    public TenantResponse softDeleteTenant(String tenantId, String actorId, String actorTenantId) {
        if ("default".equalsIgnoreCase(tenantId)) {
            throw new SynapseConflictException("Default tenant 'default' cannot be deleted");
        }

        TenantRecord existing = getTenantRecordOrThrow(tenantId);
        if (existing.legalHold()) {
            throw new SynapseConflictException("Tenant '" + tenantId + "' is under legal hold — deletion blocked");
        }

        Instant now = Instant.now();
        jdbc.sql(sqlLoader.load("tenants/soft-delete-tenant"))
                .param("tenantId", tenantId)
                .param("now", Timestamp.from(now))
                .update();

        TenantRecord deleted = getTenantRecordOrThrow(tenantId);
        recordAudit(actorId, actorTenantId, "DELETE_TENANT", tenantId, Map.of(
                "previousStatus", existing.status().name(),
                "status", deleted.status().name(),
                "deletedAt", deleted.deletedAt().toString()
        ));

        return toResponse(deleted);
    }

    /**
     * Lists audit log entries recorded for a specific tenant.
     */
    public List<TenantAuditResponse> listAuditLogs(String tenantId) {
        getTenantRecordOrThrow(tenantId);
        return jdbc.sql(sqlLoader.load("tenants/list-audit-logs"))
                .param("targetType", "TENANT")
                .param("targetId", tenantId)
                .query(this::mapAuditRow)
                .list()
                .stream()
                .map(TenantAuditResponse::from)
                .toList();
    }

    /**
     * Looks up a tenant record by ID.
     */
    public Optional<TenantRecord> findTenantRecord(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        try {
            return jdbc.sql(sqlLoader.load("tenants/find-by-id"))
                    .param("tenantId", tenantId.trim())
                    .query(this::mapTenantRow)
                    .optional();
        } catch (Exception e) {
            log.debug("[TenantService] Lookup failed for tenant '{}': {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns {@code true} if the specified tenant exists and is in {@link TenantStatus#SUSPENDED}
     * or {@link TenantStatus#DELETED} status.
     */
    public boolean isTenantSuspended(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        Optional<TenantRecord> record = findTenantRecord(tenantId.trim());
        if (record.isEmpty()) {
            return false;
        }
        TenantStatus status = record.get().status();
        return status == TenantStatus.SUSPENDED || status == TenantStatus.DELETED;
    }

    /**
     * Throws {@link TenantSuspendedException} ({@code SPE-820-003} / {@code SPE-SEC-003}) if the
     * specified tenant is suspended or soft-deleted.
     */
    public void assertTenantActive(String tenantId) {
        if (isTenantSuspended(tenantId)) {
            throw new TenantSuspendedException(tenantId.trim());
        }
    }

    private TenantRecord getTenantRecordOrThrow(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new SynapseNotFoundException("Tenant", "");
        }
        return findTenantRecord(tenantId)
                .orElseThrow(() -> new SynapseNotFoundException("Tenant", tenantId));
    }

    private TenantResponse toResponse(TenantRecord record) {
        String tenantId = record.tenantId();
        long accountCount = countAccounts(tenantId);
        long namespaceCount = countNamespaces(tenantId);
        long storageBytes = computeStorageBytes(tenantId);
        return TenantResponse.from(record, accountCount, namespaceCount, storageBytes);
    }

    private long countAccounts(String tenantId) {
        try {
            Long count = jdbc.sql(sqlLoader.load("tenants/count-accounts"))
                    .param("tenantId", tenantId)
                    .query(Long.class)
                    .single();
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.debug("[TenantService] Failed to count accounts for tenant '{}': {}", tenantId, e.getMessage());
            return 0L;
        }
    }

    private long countNamespaces(String tenantId) {
        try {
            Long count = jdbc.sql(sqlLoader.load("tenants/count-namespaces"))
                    .param("tenantId", tenantId)
                    .query(Long.class)
                    .single();
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.debug("[TenantService] Failed to count namespaces for tenant '{}': {}", tenantId, e.getMessage());
            return 0L;
        }
    }

    private long computeStorageBytes(String tenantId) {
        if (synapseProps == null) {
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

        long totalBytes = 0L;
        Path tenantRootDir = root.resolve("tenants").resolve(tenantId).resolve("namespaces");
        if (Files.isDirectory(tenantRootDir)) {
            totalBytes += directorySize(tenantRootDir);
        }

        try {
            List<String> nsIds = jdbc.sql(sqlLoader.load("tenants/list-namespace-ids"))
                    .param("tenantId", tenantId)
                    .query(String.class)
                    .list();
            for (String nsId : nsIds) {
                Path flatDir = root.resolve("namespaces").resolve(nsId);
                if (Files.isDirectory(flatDir)) {
                    totalBytes += directorySize(flatDir);
                }
            }
        } catch (Exception e) {
            log.debug("[TenantService] Failed to compute flat namespace storage for tenant '{}': {}", tenantId, e.getMessage());
        }

        return totalBytes;
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
            String targetTenantId,
            Map<String, Object> details) {
        auditLogService.recordAdminAction(
                actorId,
                actorTenantId,
                targetTenantId,
                action,
                "TENANT",
                targetTenantId,
                null,
                null,
                details
        );
    }

    private static void validateBreakGlassDuration(int minutes) {
        if (minutes < 1 || minutes > MAX_BREAK_GLASS_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                    "breakGlassMaxDurationMinutes must be between 1 and " + MAX_BREAK_GLASS_DURATION_MINUTES + " (24 hours)");
        }
    }

    private static void validateQuotas(int maxAccounts, int maxNamespaces, long maxStorageBytes) {
        if (maxAccounts < -1 || maxAccounts == 0) {
            throw new IllegalArgumentException("maxAccounts must be -1 (unlimited) or a positive integer");
        }
        if (maxNamespaces < -1 || maxNamespaces == 0) {
            throw new IllegalArgumentException("maxNamespaces must be -1 (unlimited) or a positive integer");
        }
        if (maxStorageBytes < -1L || maxStorageBytes == 0L) {
            throw new IllegalArgumentException("maxStorageBytes must be -1 (unlimited) or a positive integer");
        }
    }

    private TenantRecord mapTenantRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Timestamp deletedTs = rs.getTimestamp("deleted_at");

        return new TenantRecord(
                rs.getString("tenant_id"),
                rs.getString("name"),
                rs.getString("description"),
                TenantStatus.fromString(rs.getString("status")),
                rs.getString("plan"),
                rs.getBoolean("legal_hold"),
                rs.getBoolean("byok_enabled"),
                rs.getBoolean("break_glass_approver_required"),
                rs.getInt("break_glass_max_duration_minutes"),
                rs.getInt("max_accounts"),
                rs.getInt("max_namespaces"),
                rs.getLong("max_storage_bytes"),
                createdTs != null ? createdTs.toInstant() : Instant.EPOCH,
                updatedTs != null ? updatedTs.toInstant() : Instant.EPOCH,
                deletedTs != null ? deletedTs.toInstant() : null
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
}
