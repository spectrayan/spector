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
import com.spectrayan.spector.memory.persist.EnvelopeDataEncryptor;
import com.spectrayan.spector.memory.persist.migration.NamespaceEncryptionMigrator;
import com.spectrayan.spector.synapse.admin.audit.AuditLogService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.ConfigureTenantKeyRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.DekRotationItem;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.DekRotationResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.KekRotationResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.RevokeTenantKeyRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.RotateNamespaceDekRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.RotateTenantKekRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.TenantKeyRevocationResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.TenantKeyStatusResponse;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantKeyDto.TenantKeyValidationResponse;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.synapse.memory.NamespaceResolver;
import com.spectrayan.spector.synapse.security.kms.KeyProviderRegistry;
import com.spectrayan.spector.synapse.security.kms.KmsConnectionContext;
import com.spectrayan.spector.synapse.security.kms.KmsContextResolver;
import com.spectrayan.spector.synapse.security.kms.PlatformHkdfKeyProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing per-tenant Customer-Managed Key (BYOK) KEK registration, live KMS
 * wrap/unwrap self-test validation, namespace DEK envelope provisioning, KEK/DEK rotation,
 * and fail-closed crypto-shredding on revocation and erasure (ADR-0088, Issues #1070, #1072).
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
    private final ObjectProvider<NamespaceResolver> namespaceResolverProvider;
    private final SynapseProperties synapseProperties;
    private final java.util.concurrent.ConcurrentHashMap<String, List<java.lang.ref.WeakReference<EnvelopeDataEncryptor>>> activeEncryptorsByTenant =
            new java.util.concurrent.ConcurrentHashMap<>();

    public TenantKeyService(
            JdbcClient jdbc,
            TenantService tenantService,
            KeyProviderRegistry keyProviderRegistry,
            KmsContextResolver kmsContextResolver,
            DekCache dekCache,
            AuditLogService auditLogService) {
        this(jdbc, tenantService, keyProviderRegistry, kmsContextResolver, dekCache, auditLogService, null, null);
    }

    @Autowired
    public TenantKeyService(
            JdbcClient jdbc,
            TenantService tenantService,
            KeyProviderRegistry keyProviderRegistry,
            KmsContextResolver kmsContextResolver,
            DekCache dekCache,
            @Autowired(required = false) AuditLogService auditLogService,
            @Autowired(required = false) ObjectProvider<NamespaceResolver> namespaceResolverProvider,
            @Autowired(required = false) SynapseProperties synapseProperties) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        this.keyProviderRegistry = Objects.requireNonNull(keyProviderRegistry, "keyProviderRegistry must not be null");
        this.kmsContextResolver = Objects.requireNonNull(kmsContextResolver, "kmsContextResolver must not be null");
        this.dekCache = Objects.requireNonNull(dekCache, "dekCache must not be null");
        this.auditLogService = auditLogService;
        this.namespaceResolverProvider = namespaceResolverProvider;
        this.synapseProperties = synapseProperties;
    }

    @PostConstruct
    void bindNamespaceEncryptorResolver() {
        if (namespaceResolverProvider != null) {
            NamespaceResolver resolver = namespaceResolverProvider.getIfAvailable();
            if (resolver != null) {
                resolver.setDataEncryptorResolver(this::resolveOptionalNamespaceDataEncryptor);
            }
        }
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
     * Rotates the tenant's KEK (`POST /api/v1/admin/tenants/{tenantId}/keys/rotate-kek`).
     * Unwraps all {@code ACTIVE} {@code namespace_encryption_key} rows using the prior KEK and
     * re-wraps the identical 32-byte DEKs under the new KEK version/ID without rewriting any
     * on-disk segment or WAL files (ADR-0088 §8.1, Issue #1072).
     */
    @Transactional
    public KekRotationResponse rotateTenantKek(
            String tenantId,
            RotateTenantKekRequest request,
            String actorId,
            String actorTenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        assertKeyNotRevoked(tenant.tenantId(), null);

        Optional<TenantKekConfigRow> cfgOpt = findKekConfig(tenant.tenantId());
        String previousProvider = cfgOpt.map(TenantKekConfigRow::provider).orElse(PlatformHkdfKeyProvider.PROVIDER_ID);
        String previousKekKeyId = cfgOpt.map(TenantKekConfigRow::kekKeyId).orElse("platform-master-v1");
        String previousKekVersion = cfgOpt.map(TenantKekConfigRow::keyVersion).orElse("v1");
        String previousCred = cfgOpt.map(TenantKekConfigRow::credentialName).orElse(null);
        String previousRegion = cfgOpt.map(TenantKekConfigRow::region).orElse(null);
        String previousEndpoint = cfgOpt.map(TenantKekConfigRow::endpoint).orElse(null);

        String targetProvider = (request != null && request.provider() != null && !request.provider().isBlank())
                ? KeyProviderRegistry.normalizeProviderId(request.provider())
                : previousProvider;
        String targetKekKeyId = (request != null && request.newKekKeyId() != null && !request.newKekKeyId().isBlank())
                ? request.newKekKeyId().trim()
                : previousKekKeyId;
        String targetCred = (request != null && request.credentialName() != null && !request.credentialName().isBlank())
                ? request.credentialName().trim()
                : previousCred;
        String targetRegion = (request != null && request.region() != null && !request.region().isBlank())
                ? request.region().trim()
                : previousRegion;
        String targetEndpoint = (request != null && request.endpoint() != null && !request.endpoint().isBlank())
                ? request.endpoint().trim()
                : previousEndpoint;

        KeyProvider newProvider = keyProviderRegistry.getRequiredProvider(targetProvider);
        KmsConnectionContext candidateCtx = kmsContextResolver.buildContext(
                tenant.tenantId(),
                targetProvider,
                targetKekKeyId,
                targetCred,
                targetRegion,
                targetEndpoint);

        // Verify the target KEK via live wrap/unwrap self-test before touching any namespace DEKs
        WrappedKeyResult selfTestWrap = kmsContextResolver.withCandidateContext(candidateCtx, () ->
                performLiveSelfTest(newProvider, tenant.tenantId(), targetKekKeyId));

        String targetKekVersion;
        if (request != null && request.newKekVersion() != null && !request.newKekVersion().isBlank()) {
            targetKekVersion = request.newKekVersion().trim();
        } else if (selfTestWrap.kekVersion() != null
                && !selfTestWrap.kekVersion().equalsIgnoreCase(previousKekVersion)) {
            targetKekVersion = selfTestWrap.kekVersion();
        } else {
            targetKekVersion = incrementVersionLabel(previousKekVersion);
        }

        List<NamespaceDekRow> activeDeks = findAllActiveNamespaceDeks(tenant.tenantId());
        Instant now = Instant.now();
        int rewrappedCount = 0;
        int refreshedCacheCount = 0;

        for (NamespaceDekRow row : activeDeks) {
            KeyProvider oldProvider = keyProviderRegistry.getRequiredProvider(row.kekProvider());
            try (DestroyableKeyMaterial unwrappedDek = oldProvider.unwrapDek(
                    tenant.tenantId(), row.kekKeyId(), row.wrappedDek(), row.wrapAad())) {
                byte[] rawDekBytes = unwrappedDek.bytes();
                final WrappedKeyResult rewrapped;
                try {
                    rewrapped = kmsContextResolver.withCandidateContext(candidateCtx, () ->
                            newProvider.wrapDek(tenant.tenantId(), targetKekKeyId, rawDekBytes, row.wrapAad()));
                } finally {
                    java.util.Arrays.fill(rawDekBytes, (byte) 0);
                }

                jdbc.sql("""
                        UPDATE namespace_encryption_key
                        SET kek_provider = :kekProvider,
                            kek_key_id = :kekKeyId,
                            kek_version = :kekVersion,
                            wrapped_dek = :wrappedDek,
                            wrap_iv = :wrapIv,
                            rotated_at = :rotatedAt
                        WHERE tenant_id = :tenantId
                          AND namespace_id = :namespaceId
                          AND dek_version = :dekVersion
                          AND status = 'ACTIVE'
                        """)
                        .param("kekProvider", targetProvider)
                        .param("kekKeyId", targetKekKeyId)
                        .param("kekVersion", targetKekVersion)
                        .param("wrappedDek", Base64.getEncoder().encodeToString(rewrapped.wrappedDek()))
                        .param("wrapIv", rewrapped.wrapIv().length > 0
                                ? Base64.getEncoder().encodeToString(rewrapped.wrapIv()) : null)
                        .param("rotatedAt", Timestamp.from(now))
                        .param("tenantId", tenant.tenantId())
                        .param("namespaceId", row.namespaceId())
                        .param("dekVersion", row.dekVersion())
                        .update();

                dekCache.put(tenant.tenantId(), row.namespaceId(), row.dekVersion(), unwrappedDek);
                rewrappedCount++;
                refreshedCacheCount++;
            }
        }

        upsertKekConfig(
                tenant.tenantId(),
                targetProvider,
                targetKekKeyId,
                targetCred,
                targetRegion,
                targetEndpoint,
                targetKekVersion,
                "ACTIVE",
                now,
                null,
                now);
        jdbc.sql("UPDATE tenants SET byok_enabled = TRUE, updated_at = :now WHERE tenant_id = :tenantId")
                .param("now", Timestamp.from(now))
                .param("tenantId", tenant.tenantId())
                .update();

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("provider", targetProvider);
        auditDetails.put("previousKekKeyId", previousKekKeyId);
        auditDetails.put("previousKekVersion", previousKekVersion);
        auditDetails.put("newKekKeyId", targetKekKeyId);
        auditDetails.put("newKekVersion", targetKekVersion);
        auditDetails.put("rewrappedDekCount", rewrappedCount);
        auditDetails.put("dataFilesRewritten", false);
        recordAudit(actorId, actorTenantId, tenant.tenantId(), "ROTATE_TENANT_KEK", auditDetails);

        log.info("[TenantKeyService] Rotated KEK for tenant='{}' from {}:{} to {}:{} (rewrappedDeks={}, dataFilesRewritten=false)",
                tenant.tenantId(), previousKekKeyId, previousKekVersion, targetKekKeyId, targetKekVersion, rewrappedCount);

        return new KekRotationResponse(
                tenant.tenantId(),
                targetProvider,
                previousKekKeyId,
                previousKekVersion,
                targetKekKeyId,
                targetKekVersion,
                rewrappedCount,
                refreshedCacheCount,
                false,
                now);
    }

    /**
     * Rotates the Data-Encryption Key (DEK) for one or all namespaces in {@code tenantId}
     * (`POST /api/v1/admin/tenants/{tenantId}/keys/rotate-dek`).
     * Provisions {@code dek_version = N + 1}, re-encrypts on-disk bundles, {@code ENC1:} text blobs,
     * 128-bit keyed synaptic tags, and WAL chunks from {@code dek_vN} to {@code dek_v(N+1)},
     * marks {@code dek_vN} as {@code ROTATED} (shredding its wrapped ciphertext), and evicts
     * {@code dek_vN} from {@link DekCache} (ADR-0088 §8.1, Issue #1072).
     */
    @Transactional
    public DekRotationResponse rotateNamespaceDek(
            String tenantId,
            RotateNamespaceDekRequest request,
            String actorId,
            String actorTenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        String requestedNs = (request != null && request.namespaceId() != null && !request.namespaceId().isBlank())
                ? request.namespaceId().trim()
                : null;
        assertKeyNotRevoked(tenant.tenantId(), requestedNs);

        List<String> targetNamespaces;
        if (requestedNs != null) {
            targetNamespaces = List.of(requestedNs);
        } else {
            targetNamespaces = jdbc.sql("""
                    SELECT DISTINCT namespace_id FROM namespace_encryption_key
                    WHERE tenant_id = :tenantId AND status = 'ACTIVE'
                    ORDER BY namespace_id
                    """)
                    .param("tenantId", tenant.tenantId())
                    .query(String.class)
                    .list();
        }

        List<DekRotationItem> rotatedItems = new ArrayList<>();
        for (String nsId : targetNamespaces) {
            rotatedItems.add(rotateSingleNamespaceDek(tenant.tenantId(), nsId));
        }

        Instant now = Instant.now();
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("rotatedNamespaceCount", rotatedItems.size());
        auditDetails.put("namespaces", rotatedItems.stream().map(DekRotationItem::namespaceId).toList());
        recordAudit(actorId, actorTenantId, tenant.tenantId(), "ROTATE_NAMESPACE_DEK", auditDetails);

        return new DekRotationResponse(
                tenant.tenantId(),
                rotatedItems.size(),
                rotatedItems,
                now);
    }

    private DekRotationItem rotateSingleNamespaceDek(String tenantId, String namespaceId) {
        // Ensure a v1 DEK exists if none was provisioned yet
        Optional<NamespaceDekRow> currentOpt = findActiveNamespaceDek(tenantId, namespaceId);
        if (currentOpt.isEmpty()) {
            try (DestroyableKeyMaterial ignored = getOrProvisionNamespaceDek(tenantId, namespaceId)) {
                currentOpt = findActiveNamespaceDek(tenantId, namespaceId);
            }
        }
        NamespaceDekRow oldRow = currentOpt.orElseThrow(() ->
                new SynapseNotFoundException("NamespaceEncryptionKey", tenantId + ":" + namespaceId));

        int oldVersion = oldRow.dekVersion();
        int newVersion = oldVersion + 1;

        Optional<TenantKekConfigRow> kekOpt = findKekConfig(tenantId);
        String providerId = kekOpt.map(TenantKekConfigRow::provider).orElse(oldRow.kekProvider());
        String kekKeyId = kekOpt.map(TenantKekConfigRow::kekKeyId).orElse(oldRow.kekKeyId());
        KeyProvider provider = keyProviderRegistry.getRequiredProvider(providerId);
        KeyProvider oldProvider = keyProviderRegistry.getRequiredProvider(oldRow.kekProvider());

        byte mode = PlatformHkdfKeyProvider.PROVIDER_ID.equalsIgnoreCase(providerId)
                ? com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.MODE_PLATFORM_ENVELOPE
                : com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.MODE_BYOK_ENVELOPE;

        // Evict any hot SpectorMemory instance before re-encrypting its on-disk files
        evictNamespaceFromResolver(namespaceId);

        byte[] newAad = EnvelopeCipher.buildNamespaceDekAad(tenantId, namespaceId, newVersion);
        int partitionBundlesEncrypted = 0;
        int recordsReKeyed = 0;
        int walEventsEncrypted = 0;
        boolean runtimeEncrypted = false;

        try (DestroyableKeyMaterial oldDek = dekCache.getOrUnwrap(
                tenantId, namespaceId, oldVersion, oldRow.kekKeyId(), oldRow.wrappedDek(), oldRow.wrapAad(), oldProvider);
             DestroyableKeyMaterial newDek = EnvelopeCipher.generateDek();
             EnvelopeDataEncryptor oldEncryptor = new EnvelopeDataEncryptor(
                     tenantId, namespaceId, oldVersion, mode, oldRow.kekKeyId(), oldDek.copy());
             EnvelopeDataEncryptor newEncryptor = new EnvelopeDataEncryptor(
                     tenantId, namespaceId, newVersion, mode, kekKeyId, newDek.copy())) {

            Path nsDir = resolveNamespaceDir(tenantId, namespaceId);
            if (nsDir != null && Files.exists(nsDir)) {
                try {
                    NamespaceEncryptionMigrator.MigrationSummary summary;
                    if (Files.exists(nsDir.resolve(".encryption-ready"))) {
                        summary = NamespaceEncryptionMigrator.rekey(nsDir, oldEncryptor, newEncryptor);
                    } else {
                        summary = NamespaceEncryptionMigrator.migrate(nsDir, newEncryptor);
                    }
                    partitionBundlesEncrypted = summary.partitionBundlesEncrypted();
                    recordsReKeyed = summary.recordsReKeyed();
                    walEventsEncrypted = summary.walEventsEncrypted();
                    runtimeEncrypted = summary.runtimeBundleEncrypted();
                } catch (IOException e) {
                    throw new SpectorMemoryException(
                            ErrorCode.WAL_WRITE_FAILED,
                            e,
                            "Failed re-encrypting namespace " + namespaceId + " to DEK v" + newVersion);
                }
            }

            byte[] rawNewBytes = newDek.bytes();
            final WrappedKeyResult wrappedNew;
            try {
                wrappedNew = provider.wrapDek(tenantId, kekKeyId, rawNewBytes, newAad);
            } finally {
                java.util.Arrays.fill(rawNewBytes, (byte) 0);
            }

            Instant now = Instant.now();
            // Mark previous version ROTATED and shred its wrapped DEK ciphertext so vN cannot be unwrapped
            jdbc.sql("""
                    UPDATE namespace_encryption_key
                    SET status = 'ROTATED',
                        wrapped_dek = :shreddedMarker,
                        wrap_iv = NULL,
                        rotated_at = :rotatedAt
                    WHERE tenant_id = :tenantId
                      AND namespace_id = :namespaceId
                      AND dek_version = :oldVersion
                    """)
                    .param("shreddedMarker", Base64.getEncoder().encodeToString(new byte[0]))
                    .param("rotatedAt", Timestamp.from(now))
                    .param("tenantId", tenantId)
                    .param("namespaceId", namespaceId)
                    .param("oldVersion", oldVersion)
                    .update();

            // Insert new ACTIVE DEK version (N + 1)
            jdbc.sql("""
                    INSERT INTO namespace_encryption_key (
                        tenant_id, namespace_id, dek_version, kek_provider, kek_key_id, kek_version,
                        wrapped_dek, wrap_iv, wrap_aad, status, created_at
                    ) VALUES (
                        :tenantId, :namespaceId, :dekVersion, :kekProvider, :kekKeyId, :kekVersion,
                        :wrappedDek, :wrapIv, :wrapAad, 'ACTIVE', :createdAt
                    )
                    """)
                    .param("tenantId", tenantId)
                    .param("namespaceId", namespaceId)
                    .param("dekVersion", newVersion)
                    .param("kekProvider", providerId)
                    .param("kekKeyId", kekKeyId)
                    .param("kekVersion", wrappedNew.kekVersion())
                    .param("wrappedDek", Base64.getEncoder().encodeToString(wrappedNew.wrappedDek()))
                    .param("wrapIv", wrappedNew.wrapIv().length > 0
                            ? Base64.getEncoder().encodeToString(wrappedNew.wrapIv()) : null)
                    .param("wrapAad", Base64.getEncoder().encodeToString(newAad))
                    .param("createdAt", Timestamp.from(now))
                    .update();

            dekCache.invalidateNamespace(tenantId, namespaceId);
            dekCache.put(tenantId, namespaceId, newVersion, newDek);
        }

        log.info("[TenantKeyService] Rotated DEK for tenant='{}' namespace='{}' from v{} to v{} (recordsReKeyed={})",
                tenantId, namespaceId, oldVersion, newVersion, recordsReKeyed);

        return new DekRotationItem(
                namespaceId,
                oldVersion,
                newVersion,
                partitionBundlesEncrypted,
                recordsReKeyed,
                walEventsEncrypted,
                runtimeEncrypted);
    }

    /**
     * Revokes / crypto-shreds a tenant's BYOK key (`POST /api/v1/admin/tenants/{tenantId}/keys/revoke`).
     * Immediately evicts and zeroizes all cached DEKs in {@link DekCache}, closes and zeroizes all
     * open {@link com.spectrayan.spector.memory.SpectorMemory} instances for the tenant in
     * {@link NamespaceResolver}, shreds wrapped DEK ciphertexts, and transitions the tenant and
     * namespace keys to {@code KEY_REVOKED} so all subsequent operations fail closed with
     * {@link ErrorCode#BYOK_KEY_REVOKED} ({@code SPE-820-010} / {@code SPE-SEC-010}) (ADR-0088 §8.2, Issue #1072).
     */
    @Transactional
    public TenantKeyRevocationResponse revokeTenantKey(
            String tenantId,
            RevokeTenantKeyRequest request,
            String actorId,
            String actorTenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        Optional<TenantKekConfigRow> cfgOpt = findKekConfig(tenant.tenantId());
        String providerId = cfgOpt.map(TenantKekConfigRow::provider).orElse(PlatformHkdfKeyProvider.PROVIDER_ID);
        String kekKeyId = cfgOpt.map(TenantKekConfigRow::kekKeyId).orElse("platform-master-v1");
        String kekVersion = cfgOpt.map(TenantKekConfigRow::keyVersion).orElse("v1");
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Administrative BYOK key revocation (crypto-shred)";

        // 1. Evict and zeroize all cached DEKs for the tenant in DekCache and any live EnvelopeDataEncryptors
        int evictedDeks = dekCache.invalidateTenant(tenant.tenantId());
        destroyActiveEncryptorsForTenant(tenant.tenantId());

        // 2. Evict and close all open SpectorMemory instances for the tenant (zeroizing off-heap bundles)
        int evictedMemories = evictTenantFromResolver(tenant.tenantId());

        Instant now = Instant.now();
        String errMsg = ErrorCode.BYOK_KEY_REVOKED.format(kekKeyId, tenant.tenantId());

        // 3. Shred wrapped DEK ciphertexts and mark all tenant namespace DEKs as KEY_REVOKED
        int revokedDekRows = jdbc.sql("""
                UPDATE namespace_encryption_key
                SET status = 'KEY_REVOKED',
                    wrapped_dek = :shreddedMarker,
                    wrap_iv = NULL,
                    rotated_at = :rotatedAt
                WHERE tenant_id = :tenantId
                  AND status = 'ACTIVE'
                """)
                .param("shreddedMarker", Base64.getEncoder().encodeToString(new byte[0]))
                .param("rotatedAt", Timestamp.from(now))
                .param("tenantId", tenant.tenantId())
                .update();

        // 4. Transition tenant_kek_config to KEY_REVOKED
        upsertKekConfig(
                tenant.tenantId(),
                providerId,
                kekKeyId,
                cfgOpt.map(TenantKekConfigRow::credentialName).orElse(null),
                cfgOpt.map(TenantKekConfigRow::region).orElse(null),
                cfgOpt.map(TenantKekConfigRow::endpoint).orElse(null),
                kekVersion,
                "KEY_REVOKED",
                now,
                errMsg,
                now);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("provider", providerId);
        auditDetails.put("kekKeyId", kekKeyId);
        auditDetails.put("status", "KEY_REVOKED");
        auditDetails.put("revokedNamespaceDekCount", revokedDekRows);
        auditDetails.put("evictedCachedDeks", evictedDeks);
        auditDetails.put("evictedMemoryInstances", evictedMemories);
        auditDetails.put("reason", reason);
        recordAudit(actorId, actorTenantId, tenant.tenantId(), "REVOKE_TENANT_BYOK_KEY", auditDetails);

        log.warn("[TenantKeyService] Crypto-shredded / revoked BYOK key for tenant='{}' keyId='{}' "
                        + "(revokedDekRows={}, evictedCachedDeks={}, evictedMemories={})",
                tenant.tenantId(), kekKeyId, revokedDekRows, evictedDeks, evictedMemories);

        return new TenantKeyRevocationResponse(
                tenant.tenantId(),
                "KEY_REVOKED",
                KeyHealthStatus.REVOKED,
                revokedDekRows,
                evictedDeks,
                evictedMemories,
                reason,
                now);
    }

    /**
     * Permanently destroys all {@code namespace_encryption_key} rows for {@code namespaceId}
     * and evicts cached DEKs during GDPR / compliance erasure (ADR-0088 §8.2, Issue #1072).
     *
     * @param accountId   owning account ID
     * @param namespaceId namespace ID being erased
     * @param operator    operator performing the erasure
     * @return list of destroyed key identifiers recorded in {@link com.spectrayan.spector.synapse.dr.ErasureAuditReport}
     */
    @Transactional
    public List<String> destroyNamespaceDeksForErasure(String accountId, String namespaceId, String operator) {
        if (namespaceId == null || namespaceId.isBlank()) {
            return List.of();
        }
        String normNs = namespaceId.trim();
        List<NamespaceDekRow> rows = jdbc.sql("""
                SELECT tenant_id, namespace_id, dek_version, kek_provider, kek_key_id, kek_version,
                       wrapped_dek, wrap_iv, wrap_aad, status, created_at, rotated_at
                FROM namespace_encryption_key
                WHERE namespace_id = :namespaceId
                ORDER BY dek_version ASC
                """)
                .param("namespaceId", normNs)
                .query(this::mapNamespaceDekRow)
                .list();

        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> destroyedKeyIds = new ArrayList<>(rows.size());
        for (NamespaceDekRow row : rows) {
            dekCache.invalidateNamespace(row.tenantId(), row.namespaceId());
            destroyActiveEncryptorsForNamespace(row.tenantId(), row.namespaceId());
            destroyedKeyIds.add(row.tenantId() + ":" + row.namespaceId() + ":dek-v" + row.dekVersion()
                    + "@" + row.kekKeyId());
        }

        jdbc.sql("DELETE FROM namespace_encryption_key WHERE namespace_id = :namespaceId")
                .param("namespaceId", normNs)
                .update();

        String tenantId = rows.getFirst().tenantId();
        recordAudit(
                operator != null ? operator : "system",
                tenantId,
                tenantId,
                "CRYPTO_SHRED_NAMESPACE_DEK",
                Map.of(
                        "namespaceId", normNs,
                        "accountId", accountId != null ? accountId : "",
                        "destroyedKeyIds", destroyedKeyIds
                ));

        log.info("[TenantKeyService] Destroyed {} namespace DEK(s) for erased namespace='{}': {}",
                destroyedKeyIds.size(), normNs, destroyedKeyIds);
        return List.copyOf(destroyedKeyIds);
    }

    /**
     * Checks whether the tenant's KEK or the namespace's DEK has been revoked (`KEY_REVOKED` or `REVOKED`),
     * or if the underlying {@link KeyProvider} reports {@link KeyHealthStatus#REVOKED} / {@link KeyHealthStatus#DISABLED}.
     */
    public boolean isTenantKeyRevoked(String tenantId, String namespaceId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        String normTenant = tenantId.trim();
        Optional<TenantKekConfigRow> cfgOpt = findKekConfig(normTenant);
        if (cfgOpt.isPresent()) {
            TenantKekConfigRow cfg = cfgOpt.get();
            if ("REVOKED".equalsIgnoreCase(cfg.status())
                    || "KEY_REVOKED".equalsIgnoreCase(cfg.status())
                    || "DISABLED".equalsIgnoreCase(cfg.status())) {
                return true;
            }
            try {
                KeyProvider provider = keyProviderRegistry.getRequiredProvider(cfg.provider());
                KeyHealthStatus health = provider.checkKeyHealth(normTenant, cfg.kekKeyId());
                if (health == KeyHealthStatus.REVOKED || health == KeyHealthStatus.DISABLED) {
                    dekCache.invalidateTenant(normTenant);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        if (namespaceId != null && !namespaceId.isBlank()) {
            Integer revokedCount = jdbc.sql("""
                    SELECT COUNT(*) FROM namespace_encryption_key
                    WHERE tenant_id = :tenantId AND namespace_id = :namespaceId
                      AND status IN ('KEY_REVOKED', 'REVOKED')
                    """)
                    .param("tenantId", normTenant)
                    .param("namespaceId", namespaceId.trim())
                    .query(Integer.class)
                    .single();
            if (revokedCount != null && revokedCount > 0 && findActiveNamespaceDek(normTenant, namespaceId.trim()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void assertKeyNotRevoked(String tenantId, String namespaceId) {
        if (isTenantKeyRevoked(tenantId, namespaceId)) {
            Optional<TenantKekConfigRow> cfgOpt = findKekConfig(tenantId);
            String kekKeyId = cfgOpt.map(TenantKekConfigRow::kekKeyId).orElse("revoked-kek");
            dekCache.invalidateTenant(tenantId);
            throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
        }
    }

    private com.spectrayan.spector.memory.persist.DataEncryptor resolveOptionalNamespaceDataEncryptor(
            String tenantId,
            String namespaceId) {
        if (tenantId == null || tenantId.isBlank() || namespaceId == null || namespaceId.isBlank()) {
            return com.spectrayan.spector.memory.persist.DataEncryptor.NOOP;
        }
        String normTenant = tenantId.trim();
        String normNs = namespaceId.trim();
        assertKeyNotRevoked(normTenant, normNs);

        Optional<TenantKekConfigRow> cfgOpt = findKekConfig(normTenant);
        Optional<NamespaceDekRow> activeDekOpt = findActiveNamespaceDek(normTenant, normNs);
        if (cfgOpt.isEmpty() && activeDekOpt.isEmpty()) {
            return com.spectrayan.spector.memory.persist.DataEncryptor.NOOP;
        }
        return createNamespaceDataEncryptor(normTenant, normNs);
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

        assertKeyNotRevoked(normTenant, normNs);

        Optional<NamespaceDekRow> existingOpt = findActiveNamespaceDek(normTenant, normNs);
        if (existingOpt.isPresent()) {
            NamespaceDekRow row = existingOpt.get();
            KeyProvider provider = keyProviderRegistry.getRequiredProvider(row.kekProvider());
            try {
                return dekCache.getOrUnwrap(
                        normTenant,
                        normNs,
                        row.dekVersion(),
                        row.kekKeyId(),
                        row.wrappedDek(),
                        row.wrapAad(),
                        provider);
            } catch (SpectorException se) {
                if (se.errorCode() == ErrorCode.BYOK_KEY_REVOKED) {
                    dekCache.invalidateTenant(normTenant);
                    evictTenantFromResolver(normTenant);
                }
                throw se;
            }
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

    /**
     * Provisions or retrieves the active namespace DEK and constructs an ADR-0088
     * {@link com.spectrayan.spector.memory.persist.EnvelopeDataEncryptor} bound to
     * {@code (tenantId, namespaceId)}.
     */
    @Transactional
    public com.spectrayan.spector.memory.persist.EnvelopeDataEncryptor createNamespaceDataEncryptor(
            String tenantId,
            String namespaceId) {
        String normTenant = Objects.requireNonNull(tenantId, "tenantId must not be null").trim();
        String normNs = Objects.requireNonNull(namespaceId, "namespaceId must not be null").trim();
        DestroyableKeyMaterial dek = getOrProvisionNamespaceDek(normTenant, normNs);
        try {
            Optional<NamespaceDekRow> rowOpt = findActiveNamespaceDek(normTenant, normNs);
            int dekVersion = rowOpt.map(NamespaceDekRow::dekVersion).orElse(1);
            String kekProvider = rowOpt.map(NamespaceDekRow::kekProvider).orElse(PlatformHkdfKeyProvider.PROVIDER_ID);
            String kekKeyId = rowOpt.map(NamespaceDekRow::kekKeyId).orElse("platform-master-v1");
            byte mode = PlatformHkdfKeyProvider.PROVIDER_ID.equalsIgnoreCase(kekProvider)
                    ? com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.MODE_PLATFORM_ENVELOPE
                    : com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.MODE_BYOK_ENVELOPE;
            EnvelopeDataEncryptor encryptor = new com.spectrayan.spector.memory.persist.EnvelopeDataEncryptor(
                    normTenant,
                    normNs,
                    dekVersion,
                    mode,
                    kekKeyId,
                    dek,
                    () -> isTenantKeyRevoked(normTenant, normNs));
            registerActiveEncryptor(normTenant, encryptor);
            return encryptor;
        } catch (RuntimeException e) {
            dek.close();
            throw e;
        }
    }

    private void registerActiveEncryptor(String tenantId, EnvelopeDataEncryptor encryptor) {
        activeEncryptorsByTenant.compute(tenantId, (k, existing) -> {
            List<java.lang.ref.WeakReference<EnvelopeDataEncryptor>> next = new ArrayList<>();
            if (existing != null) {
                for (java.lang.ref.WeakReference<EnvelopeDataEncryptor> ref : existing) {
                    EnvelopeDataEncryptor enc = ref.get();
                    if (enc != null && !enc.isDestroyed()) {
                        next.add(ref);
                    }
                }
            }
            next.add(new java.lang.ref.WeakReference<>(encryptor));
            return next;
        });
    }

    private void destroyActiveEncryptorsForTenant(String tenantId) {
        List<java.lang.ref.WeakReference<EnvelopeDataEncryptor>> refs = activeEncryptorsByTenant.remove(tenantId);
        if (refs != null) {
            for (java.lang.ref.WeakReference<EnvelopeDataEncryptor> ref : refs) {
                EnvelopeDataEncryptor enc = ref.get();
                if (enc != null) {
                    enc.destroy();
                }
            }
        }
    }

    private void destroyActiveEncryptorsForNamespace(String tenantId, String namespaceId) {
        List<java.lang.ref.WeakReference<EnvelopeDataEncryptor>> refs = activeEncryptorsByTenant.get(tenantId);
        if (refs != null) {
            for (java.lang.ref.WeakReference<EnvelopeDataEncryptor> ref : refs) {
                EnvelopeDataEncryptor enc = ref.get();
                if (enc != null && namespaceId.equals(enc.namespaceId())) {
                    enc.destroy();
                }
            }
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

    public List<NamespaceDekRow> findAllActiveNamespaceDeks(String tenantId) {
        return jdbc.sql("""
                SELECT tenant_id, namespace_id, dek_version, kek_provider, kek_key_id, kek_version,
                       wrapped_dek, wrap_iv, wrap_aad, status, created_at, rotated_at
                FROM namespace_encryption_key
                WHERE tenant_id = :tenantId AND status = 'ACTIVE'
                ORDER BY namespace_id ASC, dek_version DESC
                """)
                .param("tenantId", tenantId.trim())
                .query(this::mapNamespaceDekRow)
                .list();
    }

    private void evictNamespaceFromResolver(String namespaceId) {
        if (namespaceResolverProvider == null || namespaceId == null) {
            return;
        }
        try {
            NamespaceResolver resolver = namespaceResolverProvider.getIfAvailable();
            if (resolver != null) {
                resolver.evict(namespaceId);
            }
        } catch (Exception e) {
            log.debug("[TenantKeyService] Failed to evict namespace '{}' from NamespaceResolver: {}", namespaceId, e.getMessage());
        }
    }

    private int evictTenantFromResolver(String tenantId) {
        if (namespaceResolverProvider == null || tenantId == null) {
            return 0;
        }
        try {
            NamespaceResolver resolver = namespaceResolverProvider.getIfAvailable();
            if (resolver != null) {
                return resolver.evictTenant(tenantId);
            }
        } catch (Exception e) {
            log.debug("[TenantKeyService] Failed to evict tenant '{}' from NamespaceResolver: {}", tenantId, e.getMessage());
        }
        return 0;
    }

    private Path resolveNamespaceDir(String tenantId, String namespaceId) {
        if (namespaceResolverProvider != null) {
            NamespaceResolver resolver = namespaceResolverProvider.getIfAvailable();
            if (resolver != null) {
                return resolver.resolveNamespaceDirectory(tenantId, namespaceId);
            }
        }
        if (synapseProperties != null) {
            Path root = synapseProperties.remembererRoot();
            if (root != null) {
                boolean tenantRooted = synapseProperties.getNamespace() != null
                        && synapseProperties.getNamespace().isTenantRootedEnabled();
                Path dir = NamespacePathResolver.resolve(root, tenantRooted ? tenantId : null, namespaceId).dir();
                if (tenantRooted && !Files.exists(dir)) {
                    Path flatDir = NamespacePathResolver.resolve(root, null, namespaceId).dir();
                    if (Files.exists(flatDir)) {
                        return flatDir;
                    }
                }
                return dir;
            }
        }
        return null;
    }

    private static String incrementVersionLabel(String current) {
        if (current == null || current.isBlank()) {
            return "v2";
        }
        String trimmed = current.trim();
        if ((trimmed.startsWith("v") || trimmed.startsWith("V")) && trimmed.length() > 1) {
            try {
                int num = Integer.parseInt(trimmed.substring(1));
                return "v" + (num + 1);
            } catch (NumberFormatException ignored) {
            }
        }
        return trimmed + "-rotated";
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
            case "REVOKED", "KEY_REVOKED" -> KeyHealthStatus.REVOKED;
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
                wrappedB64 != null && !wrappedB64.isBlank() ? Base64.getDecoder().decode(wrappedB64) : new byte[0],
                ivB64 != null && !ivB64.isBlank() ? Base64.getDecoder().decode(ivB64) : new byte[0],
                aadB64 != null && !aadB64.isBlank() ? Base64.getDecoder().decode(aadB64) : new byte[0],
                rs.getString("status"),
                createdTs != null ? createdTs.toInstant() : null,
                rotatedTs != null ? rotatedTs.toInstant() : null);
    }
}
