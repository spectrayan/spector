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

import com.spectrayan.spector.commons.crypto.DekCache;
import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.crypto.KeyHealthStatus;
import com.spectrayan.spector.commons.crypto.KeyProvider;
import com.spectrayan.spector.commons.crypto.WrappedKeyResult;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.synapse.admin.audit.AuditLogService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.ConfigureTenantKeyRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.TenantKeyStatusResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.TenantKeyValidationResponse;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import com.spectrayan.spector.synapse.security.kms.KeyProviderRegistry;
import com.spectrayan.spector.synapse.security.kms.KmsConnectionContext;
import com.spectrayan.spector.synapse.security.kms.KmsContextResolver;
import com.spectrayan.spector.synapse.security.kms.PlatformHkdfKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing per-tenant Customer-Managed Key (BYOK) KEK registration, live KMS
 * wrap/unwrap self-test validation, namespace DEK envelope provisioning, and fail-closed
 * cache eviction on revocation (ADR-0088, Issue #1070).
 */
@Service
@DependsOn("flyway")
public class TenantKeyService {

    private static final Logger log = LoggerFactory.getLogger(TenantKeyService.class);

    public record TenantKekConfigRow(
            String tenantId,
            String provider,
            String kekKeyId,
            String credentialName,
            String region,
            String endpoint,
            String keyVersion,
            String status,
            Instant lastVerifiedAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record NamespaceDekRow(
            String tenantId,
            String namespaceId,
            int dekVersion,
            String kekProvider,
            String kekKeyId,
            String kekVersion,
            byte[] wrappedDek,
            byte[] wrapIv,
            byte[] wrapAad,
            String status,
            Instant createdAt,
            Instant rotatedAt) {
    }

    private final JdbcClient jdbc;
    private final TenantService tenantService;
    private final KeyProviderRegistry keyProviderRegistry;
    private final KmsContextResolver kmsContextResolver;
    private final DekCache dekCache;
    private final AuditLogService auditLogService;

    @Autowired
    public TenantKeyService(
            JdbcClient jdbc,
            TenantService tenantService,
            KeyProviderRegistry keyProviderRegistry,
            KmsContextResolver kmsContextResolver,
            DekCache dekCache,
            @Autowired(required = false) AuditLogService auditLogService) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        this.keyProviderRegistry = Objects.requireNonNull(keyProviderRegistry, "keyProviderRegistry must not be null");
        this.kmsContextResolver = Objects.requireNonNull(kmsContextResolver, "kmsContextResolver must not be null");
        this.dekCache = Objects.requireNonNull(dekCache, "dekCache must not be null");
        this.auditLogService = auditLogService;
    }

    /**
     * Retrieves the current BYOK KEK status and metadata for {@code tenantId}.
     */
    public TenantKeyStatusResponse getTenantKeyStatus(String tenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        Optional<TenantKekConfigRow> configOpt = findKekConfig(tenant.tenantId());
        int activeDekCount = countActiveNamespaceDeks(tenant.tenantId());

        if (configOpt.isEmpty()) {
            return new TenantKeyStatusResponse(
                    tenant.tenantId(),
                    tenant.byokEnabled(),
                    PlatformHkdfKeyProvider.PROVIDER_ID,
                    null,
                    null,
                    null,
                    null,
                    "v1",
                    "UNCONFIGURED",
                    KeyHealthStatus.HEALTHY,
                    activeDekCount,
                    null,
                    null,
                    null,
                    null,
                    keyProviderRegistry.supportedProviderIds());
        }

        TenantKekConfigRow cfg = configOpt.get();
        KeyHealthStatus healthStatus = mapStatusToHealth(cfg.status());
        return new TenantKeyStatusResponse(
                tenant.tenantId(),
                tenant.byokEnabled(),
                cfg.provider(),
                cfg.kekKeyId(),
                cfg.credentialName(),
                cfg.region(),
                cfg.endpoint(),
                cfg.keyVersion(),
                cfg.status(),
                healthStatus,
                activeDekCount,
                cfg.lastVerifiedAt(),
                cfg.lastError(),
                cfg.createdAt(),
                cfg.updatedAt(),
                keyProviderRegistry.supportedProviderIds());
    }

    /**
     * Registers or updates a tenant's BYOK KEK reference after performing a live
     * {@code wrapDek} + {@code unwrapDek} round-trip verification against the target KMS.
     */
    @Transactional
    public TenantKeyStatusResponse configureTenantKey(
            String tenantId,
            ConfigureTenantKeyRequest request,
            String actorId,
            String actorTenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        if (request == null) {
            throw new IllegalArgumentException("ConfigureTenantKeyRequest must not be null");
        }
        if (request.provider() == null || request.provider().isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (request.kekKeyId() == null || request.kekKeyId().isBlank()) {
            throw new IllegalArgumentException("kekKeyId is required");
        }

        String canonicalProvider = KeyProviderRegistry.normalizeProviderId(request.provider());
        KeyProvider provider = keyProviderRegistry.getRequiredProvider(canonicalProvider);
        String trimmedKeyId = request.kekKeyId().trim();
        String trimmedCred = (request.credentialName() != null && !request.credentialName().isBlank())
                ? request.credentialName().trim()
                : null;
        String trimmedRegion = (request.region() != null && !request.region().isBlank())
                ? request.region().trim()
                : null;
        String trimmedEndpoint = (request.endpoint() != null && !request.endpoint().isBlank())
                ? request.endpoint().trim()
                : null;

        KmsConnectionContext candidateCtx = kmsContextResolver.buildContext(
                tenant.tenantId(),
                canonicalProvider,
                trimmedKeyId,
                trimmedCred,
                trimmedRegion,
                trimmedEndpoint);

        // Execute live wrap/unwrap self-test with ephemeral 32-byte test DEK (immediately zeroized)
        final WrappedKeyResult selfTestWrap;
        try {
            selfTestWrap = kmsContextResolver.withCandidateContext(candidateCtx, () ->
                    performLiveSelfTest(provider, tenant.tenantId(), trimmedKeyId));
        } catch (SpectorException se) {
            recordAudit(actorId, actorTenantId, tenant.tenantId(), "CONFIGURE_TENANT_BYOK_KEY_FAILED", Map.of(
                    "provider", canonicalProvider,
                    "kekKeyId", trimmedKeyId,
                    "errorCode", se.codeId(),
                    "error", se.getMessage()
            ));
            throw se;
        } catch (Exception e) {
            recordAudit(actorId, actorTenantId, tenant.tenantId(), "CONFIGURE_TENANT_BYOK_KEY_FAILED", Map.of(
                    "provider", canonicalProvider,
                    "kekKeyId", trimmedKeyId,
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
            throw new SpectorMemoryException(
                    ErrorCode.KMS_CONFIGURATION_INVALID,
                    e,
                    tenant.tenantId(),
                    e.getMessage());
        }

        String resolvedVersion = selfTestWrap.kekVersion() != null ? selfTestWrap.kekVersion() : "v1";
        Instant now = Instant.now();

        upsertKekConfig(
                tenant.tenantId(),
                canonicalProvider,
                trimmedKeyId,
                trimmedCred,
                trimmedRegion,
                trimmedEndpoint,
                resolvedVersion,
                "ACTIVE",
                now,
                null,
                now);

        // Mark BYOK enabled on the tenant record
        jdbc.sql("UPDATE tenants SET byok_enabled = TRUE, updated_at = :now WHERE tenant_id = :tenantId")
                .param("now", Timestamp.from(now))
                .param("tenantId", tenant.tenantId())
                .update();
        tenantService.registerByokKeyConfigured(tenant.tenantId());

        // Invalidate any previously cached DEKs for this tenant
        int evicted = dekCache.invalidateTenant(tenant.tenantId());

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("provider", canonicalProvider);
        auditDetails.put("kekKeyId", trimmedKeyId);
        if (trimmedCred != null) {
            auditDetails.put("credentialName", trimmedCred);
        }
        if (trimmedRegion != null) {
            auditDetails.put("region", trimmedRegion);
        }
        auditDetails.put("keyVersion", resolvedVersion);
        auditDetails.put("evictedCachedDeks", evicted);
        recordAudit(actorId, actorTenantId, tenant.tenantId(), "CONFIGURE_TENANT_BYOK_KEY", auditDetails);

        log.info("[TenantKeyService] Configured BYOK KEK for tenant='{}' provider='{}' keyId='{}' version='{}'",
                tenant.tenantId(), canonicalProvider, trimmedKeyId, resolvedVersion);

        return getTenantKeyStatus(tenant.tenantId());
    }

    /**
     * Performs an on-demand health check and live wrap/unwrap self-test for the tenant's configured KEK.
     * If the key is found to be {@link KeyHealthStatus#REVOKED} or {@link KeyHealthStatus#DISABLED},
     * all cached DEKs for the tenant are immediately evicted and zeroized.
     */
    @Transactional
    public TenantKeyValidationResponse validateTenantKey(
            String tenantId,
            String actorId,
            String actorTenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        TenantKekConfigRow cfg = findKekConfig(tenant.tenantId())
                .orElseThrow(() -> new SynapseNotFoundException("TenantKekConfig", tenant.tenantId()));

        KeyProvider provider = keyProviderRegistry.getRequiredProvider(cfg.provider());
        Instant now = Instant.now();
        KeyHealthStatus health = provider.checkKeyHealth(tenant.tenantId(), cfg.kekKeyId());

        if (health == KeyHealthStatus.REVOKED || health == KeyHealthStatus.DISABLED) {
            int evicted = dekCache.invalidateTenant(tenant.tenantId());
            String newStatus = health == KeyHealthStatus.REVOKED ? "REVOKED" : "DISABLED";
            String errMsg = ErrorCode.BYOK_KEY_REVOKED.format(cfg.kekKeyId(), tenant.tenantId());
            updateKekVerificationState(tenant.tenantId(), newStatus, now, errMsg);

            recordAudit(actorId, actorTenantId, tenant.tenantId(), "BYOK_KEY_REVOCATION_DETECTED", Map.of(
                    "provider", cfg.provider(),
                    "kekKeyId", cfg.kekKeyId(),
                    "healthStatus", health.name(),
                    "evictedCachedDeks", evicted
            ));

            return new TenantKeyValidationResponse(
                    tenant.tenantId(),
                    cfg.provider(),
                    cfg.kekKeyId(),
                    false,
                    health,
                    false,
                    evicted,
                    now,
                    ErrorCode.BYOK_KEY_REVOKED.id(),
                    errMsg);
        }

        try {
            performLiveSelfTest(provider, tenant.tenantId(), cfg.kekKeyId());
            updateKekVerificationState(tenant.tenantId(), "ACTIVE", now, null);

            recordAudit(actorId, actorTenantId, tenant.tenantId(), "VALIDATE_TENANT_BYOK_KEY", Map.of(
                    "provider", cfg.provider(),
                    "kekKeyId", cfg.kekKeyId(),
                    "healthStatus", KeyHealthStatus.HEALTHY.name(),
                    "valid", true
            ));

            return new TenantKeyValidationResponse(
                    tenant.tenantId(),
                    cfg.provider(),
                    cfg.kekKeyId(),
                    true,
                    KeyHealthStatus.HEALTHY,
                    true,
                    0,
                    now,
                    null,
                    "KMS KEK health and wrap/unwrap round-trip verified");
        } catch (SpectorException se) {
            int evicted = dekCache.invalidateTenant(tenant.tenantId());
            boolean revoked = se.errorCode() == ErrorCode.BYOK_KEY_REVOKED;
            KeyHealthStatus failedHealth = revoked ? KeyHealthStatus.REVOKED : KeyHealthStatus.UNREACHABLE;
            String newStatus = revoked ? "REVOKED" : "UNREACHABLE";
            updateKekVerificationState(tenant.tenantId(), newStatus, now, se.getMessage());

            recordAudit(actorId, actorTenantId, tenant.tenantId(), "VALIDATE_TENANT_BYOK_KEY_FAILED", Map.of(
                    "provider", cfg.provider(),
                    "kekKeyId", cfg.kekKeyId(),
                    "healthStatus", failedHealth.name(),
                    "errorCode", se.codeId(),
                    "evictedCachedDeks", evicted
            ));

            return new TenantKeyValidationResponse(
                    tenant.tenantId(),
                    cfg.provider(),
                    cfg.kekKeyId(),
                    false,
                    failedHealth,
                    false,
                    evicted,
                    now,
                    se.codeId(),
                    se.getMessage());
        }
    }

    /**
     * Disables and removes the BYOK KEK configuration for {@code tenantId}, evicting and zeroizing
     * all cached DEKs in {@link DekCache}.
     */
    @Transactional
    public TenantKeyStatusResponse deleteTenantKey(
            String tenantId,
            String actorId,
            String actorTenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        Optional<TenantKekConfigRow> existing = findKekConfig(tenant.tenantId());

        int evicted = dekCache.invalidateTenant(tenant.tenantId());
        jdbc.sql("DELETE FROM tenant_kek_config WHERE tenant_id = :tenantId")
                .param("tenantId", tenant.tenantId())
                .update();
        jdbc.sql("UPDATE tenants SET byok_enabled = FALSE, updated_at = :now WHERE tenant_id = :tenantId")
                .param("now", Timestamp.from(Instant.now()))
                .param("tenantId", tenant.tenantId())
                .update();

        recordAudit(actorId, actorTenantId, tenant.tenantId(), "REMOVE_TENANT_BYOK_KEY", Map.of(
                "previousProvider", existing.map(TenantKekConfigRow::provider).orElse("NONE"),
                "evictedCachedDeks", evicted
        ));

        return getTenantKeyStatus(tenant.tenantId());
    }

    /**
     * Provisions or retrieves the active wrapped DEK for {@code (tenantId, namespaceId)} and returns
     * its unwrapped {@link DestroyableKeyMaterial} via {@link DekCache} (ADR-0088 §3, §7).
     */
    @Transactional
    public DestroyableKeyMaterial getOrProvisionNamespaceDek(String tenantId, String namespaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        String normTenant = tenantId.trim();
        String normNs = namespaceId.trim();

        Optional<NamespaceDekRow> existingOpt = findActiveNamespaceDek(normTenant, normNs);
        if (existingOpt.isPresent()) {
            NamespaceDekRow row = existingOpt.get();
            KeyProvider provider = keyProviderRegistry.getRequiredProvider(row.kekProvider());
            return dekCache.getOrUnwrap(
                    normTenant,
                    normNs,
                    row.dekVersion(),
                    row.kekKeyId(),
                    row.wrappedDek(),
                    row.wrapAad(),
                    provider);
        }

        // Provision new v1 DEK wrapped under tenant KEK (or platform HKDF if tenant has no BYOK KEK)
        Optional<TenantKekConfigRow> kekOpt = findKekConfig(normTenant);
        String providerId = kekOpt.map(TenantKekConfigRow::provider).orElse(PlatformHkdfKeyProvider.PROVIDER_ID);
        String kekKeyId = kekOpt.map(TenantKekConfigRow::kekKeyId).orElse("platform-master-v1");
        KeyProvider provider = keyProviderRegistry.getRequiredProvider(providerId);

        int dekVersion = 1;
        byte[] aad = EnvelopeCipher.buildNamespaceDekAad(normTenant, normNs, dekVersion);

        try (DestroyableKeyMaterial freshDek = EnvelopeCipher.generateDek()) {
            byte[] rawBytes = freshDek.bytes();
            final WrappedKeyResult wrapped;
            try {
                wrapped = provider.wrapDek(normTenant, kekKeyId, rawBytes, aad);
            } finally {
                java.util.Arrays.fill(rawBytes, (byte) 0);
            }

            Instant now = Instant.now();
            jdbc.sql("""
                    INSERT INTO namespace_encryption_key (
                        tenant_id, namespace_id, dek_version, kek_provider, kek_key_id, kek_version,
                        wrapped_dek, wrap_iv, wrap_aad, status, created_at
                    ) VALUES (
                        :tenantId, :namespaceId, :dekVersion, :kekProvider, :kekKeyId, :kekVersion,
                        :wrappedDek, :wrapIv, :wrapAad, 'ACTIVE', :createdAt
                    )
                    """)
                    .param("tenantId", normTenant)
                    .param("namespaceId", normNs)
                    .param("dekVersion", dekVersion)
                    .param("kekProvider", providerId)
                    .param("kekKeyId", kekKeyId)
                    .param("kekVersion", wrapped.kekVersion())
                    .param("wrappedDek", Base64.getEncoder().encodeToString(wrapped.wrappedDek()))
                    .param("wrapIv", wrapped.wrapIv().length > 0 ? Base64.getEncoder().encodeToString(wrapped.wrapIv()) : null)
                    .param("wrapAad", Base64.getEncoder().encodeToString(aad))
                    .param("createdAt", Timestamp.from(now))
                    .update();

            dekCache.put(normTenant, normNs, dekVersion, freshDek);
            return freshDek.copy();
        }
    }

    public Optional<TenantKekConfigRow> findKekConfig(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                SELECT tenant_id, provider, kek_key_id, credential_name, region, endpoint,
                       key_version, status, last_verified_at, last_error, created_at, updated_at
                FROM tenant_kek_config
                WHERE tenant_id = :tenantId
                """)
                .param("tenantId", tenantId.trim())
                .query(this::mapKekConfigRow)
                .optional();
    }

    public Optional<NamespaceDekRow> findActiveNamespaceDek(String tenantId, String namespaceId) {
        return jdbc.sql("""
                SELECT tenant_id, namespace_id, dek_version, kek_provider, kek_key_id, kek_version,
                       wrapped_dek, wrap_iv, wrap_aad, status, created_at, rotated_at
                FROM namespace_encryption_key
                WHERE tenant_id = :tenantId AND namespace_id = :namespaceId AND status = 'ACTIVE'
                ORDER BY dek_version DESC
                FETCH FIRST 1 ROWS ONLY
                """)
                .param("tenantId", tenantId.trim())
                .param("namespaceId", namespaceId.trim())
                .query(this::mapNamespaceDekRow)
                .optional();
    }

    private WrappedKeyResult performLiveSelfTest(KeyProvider provider, String tenantId, String kekKeyId) {
        byte[] aad = (tenantId + ":__byok_self_test__:v1").getBytes(StandardCharsets.UTF_8);
        try (DestroyableKeyMaterial testDek = EnvelopeCipher.generateDek()) {
            byte[] originalBytes = testDek.bytes();
            try {
                WrappedKeyResult wrapped = provider.wrapDek(tenantId, kekKeyId, originalBytes, aad);
                try (DestroyableKeyMaterial unwrapped = provider.unwrapDek(
                        tenantId, kekKeyId, wrapped.wrappedDek(), aad)) {
                    byte[] roundTripBytes = unwrapped.bytes();
                    try {
                        if (!MessageDigest.isEqual(originalBytes, roundTripBytes)) {
                            throw new SpectorMemoryException(
                                    ErrorCode.KMS_CONFIGURATION_INVALID,
                                    tenantId,
                                    "KMS self-test round-trip byte mismatch");
                        }
                    } finally {
                        java.util.Arrays.fill(roundTripBytes, (byte) 0);
                    }
                }
                return wrapped;
            } finally {
                java.util.Arrays.fill(originalBytes, (byte) 0);
            }
        }
    }

    private void upsertKekConfig(
            String tenantId,
            String provider,
            String kekKeyId,
            String credentialName,
            String region,
            String endpoint,
            String keyVersion,
            String status,
            Instant lastVerifiedAt,
            String lastError,
            Instant now) {
        int updated = jdbc.sql("""
                UPDATE tenant_kek_config
                SET provider = :provider,
                    kek_key_id = :kekKeyId,
                    credential_name = :credentialName,
                    region = :region,
                    endpoint = :endpoint,
                    key_version = :keyVersion,
                    status = :status,
                    last_verified_at = :lastVerifiedAt,
                    last_error = :lastError,
                    updated_at = :updatedAt
                WHERE tenant_id = :tenantId
                """)
                .param("provider", provider)
                .param("kekKeyId", kekKeyId)
                .param("credentialName", credentialName)
                .param("region", region)
                .param("endpoint", endpoint)
                .param("keyVersion", keyVersion)
                .param("status", status)
                .param("lastVerifiedAt", lastVerifiedAt != null ? Timestamp.from(lastVerifiedAt) : null)
                .param("lastError", lastError)
                .param("updatedAt", Timestamp.from(now))
                .param("tenantId", tenantId)
                .update();

        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO tenant_kek_config (
                        tenant_id, provider, kek_key_id, credential_name, region, endpoint,
                        key_version, status, last_verified_at, last_error, created_at, updated_at
                    ) VALUES (
                        :tenantId, :provider, :kekKeyId, :credentialName, :region, :endpoint,
                        :keyVersion, :status, :lastVerifiedAt, :lastError, :createdAt, :updatedAt
                    )
                    """)
                    .param("tenantId", tenantId)
                    .param("provider", provider)
                    .param("kekKeyId", kekKeyId)
                    .param("credentialName", credentialName)
                    .param("region", region)
                    .param("endpoint", endpoint)
                    .param("keyVersion", keyVersion)
                    .param("status", status)
                    .param("lastVerifiedAt", lastVerifiedAt != null ? Timestamp.from(lastVerifiedAt) : null)
                    .param("lastError", lastError)
                    .param("createdAt", Timestamp.from(now))
                    .param("updatedAt", Timestamp.from(now))
                    .update();
        }
    }

    private void updateKekVerificationState(String tenantId, String status, Instant verifiedAt, String lastError) {
        jdbc.sql("""
                UPDATE tenant_kek_config
                SET status = :status,
                    last_verified_at = :verifiedAt,
                    last_error = :lastError,
                    updated_at = :updatedAt
                WHERE tenant_id = :tenantId
                """)
                .param("status", status)
                .param("verifiedAt", Timestamp.from(verifiedAt))
                .param("lastError", lastError)
                .param("updatedAt", Timestamp.from(verifiedAt))
                .param("tenantId", tenantId)
                .update();
    }

    private int countActiveNamespaceDeks(String tenantId) {
        Integer count = jdbc.sql("""
                SELECT COUNT(*) FROM namespace_encryption_key
                WHERE tenant_id = :tenantId AND status = 'ACTIVE'
                """)
                .param("tenantId", tenantId)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    private TenantRecord requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new SynapseNotFoundException("Tenant", "");
        }
        return tenantService.findTenantRecord(tenantId.trim())
                .orElseThrow(() -> new SynapseNotFoundException("Tenant", tenantId.trim()));
    }

    private void recordAudit(
            String actorId,
            String actorTenantId,
            String resourceTenantId,
            String action,
            Map<String, Object> details) {
        if (auditLogService == null) {
            return;
        }
        try {
            auditLogService.recordAdminAction(
                    actorId != null ? actorId : "system",
                    actorTenantId != null ? actorTenantId : resourceTenantId,
                    resourceTenantId,
                    action,
                    "TENANT_KEY",
                    resourceTenantId,
                    null,
                    action,
                    details);
        } catch (Exception e) {
            log.warn("[TenantKeyService] Failed recording audit action '{}': {}", action, e.getMessage());
        }
    }

    private static KeyHealthStatus mapStatusToHealth(String status) {
        if (status == null) {
            return KeyHealthStatus.HEALTHY;
        }
        return switch (status.toUpperCase()) {
            case "REVOKED" -> KeyHealthStatus.REVOKED;
            case "DISABLED" -> KeyHealthStatus.DISABLED;
            case "UNREACHABLE" -> KeyHealthStatus.UNREACHABLE;
            default -> KeyHealthStatus.HEALTHY;
        };
    }

    private TenantKekConfigRow mapKekConfigRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp verifiedTs = rs.getTimestamp("last_verified_at");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        return new TenantKekConfigRow(
                rs.getString("tenant_id"),
                rs.getString("provider"),
                rs.getString("kek_key_id"),
                rs.getString("credential_name"),
                rs.getString("region"),
                rs.getString("endpoint"),
                rs.getString("key_version"),
                rs.getString("status"),
                verifiedTs != null ? verifiedTs.toInstant() : null,
                rs.getString("last_error"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null);
    }

    private NamespaceDekRow mapNamespaceDekRow(ResultSet rs, int rowNum) throws SQLException {
        String wrappedB64 = rs.getString("wrapped_dek");
        String ivB64 = rs.getString("wrap_iv");
        String aadB64 = rs.getString("wrap_aad");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp rotatedTs = rs.getTimestamp("rotated_at");
        return new NamespaceDekRow(
                rs.getString("tenant_id"),
                rs.getString("namespace_id"),
                rs.getInt("dek_version"),
                rs.getString("kek_provider"),
                rs.getString("kek_key_id"),
                rs.getString("kek_version"),
                Base64.getDecoder().decode(wrappedB64),
                ivB64 != null && !ivB64.isBlank() ? Base64.getDecoder().decode(ivB64) : new byte[0],
                aadB64 != null && !aadB64.isBlank() ? Base64.getDecoder().decode(aadB64) : new byte[0],
                rs.getString("status"),
                createdTs != null ? createdTs.toInstant() : null,
                rotatedTs != null ? rotatedTs.toInstant() : null);
    }
}
