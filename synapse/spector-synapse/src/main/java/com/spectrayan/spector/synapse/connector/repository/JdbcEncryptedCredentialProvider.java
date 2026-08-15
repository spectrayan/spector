/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.connector.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.connector.spi.CredentialProvider;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed encrypted implementation of {@link CredentialProvider} and credential storage.
 *
 * <p>Persists AES-256-GCM envelope-encrypted credentials into the {@code credentials} table,
 * providing per-tenant cryptographic isolation, BYOK support, and dynamic secret resolution.</p>
 */
@Repository
public class JdbcEncryptedCredentialProvider implements CredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(JdbcEncryptedCredentialProvider.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final AesGcmCipher cipher;

    // Short-lived in-memory cache for decrypted secrets (cache key: tenantId + ":" + credentialName)
    private final Map<String, CacheEntry> secretCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000; // 1 minute

    private record CacheEntry(String secret, long timestamp) {
        boolean isValid() { return System.currentTimeMillis() - timestamp < CACHE_TTL_MS; }
    }

    public JdbcEncryptedCredentialProvider(JdbcClient jdbc, ObjectMapper mapper, AesGcmCipher cipher) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcClient must not be null");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper must not be null");
        this.cipher = Objects.requireNonNull(cipher, "AesGcmCipher must not be null");
    }

    public CredentialRecord save(String tenantId,
                                String userId,
                                String name,
                                CredentialCategory category,
                                String provider,
                                CredentialType credentialType,
                                String rawSecret,
                                Map<String, Object> properties,
                                boolean isDefault,
                                String description,
                                Instant expiresAt) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(rawSecret, "rawSecret must not be null");

        String effectiveTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        String normalizedName = name.trim().toLowerCase();
        String normalizedProvider = provider.trim().toLowerCase();
        CredentialType effectiveType = credentialType != null ? credentialType : CredentialType.API_KEY;

        // Invalidate cache entry
        secretCache.remove(cacheKey(effectiveTenant, normalizedName));

        // If setting as default, clear existing defaults for (tenant, provider)
        if (isDefault) {
            jdbc.sql("UPDATE credentials SET is_default = FALSE WHERE tenant_id = :tenantId AND provider = :provider")
                    .param("tenantId", effectiveTenant)
                    .param("provider", normalizedProvider)
                    .update();
        }

        AesGcmCipher.EncryptedPayload encrypted = cipher.encrypt(rawSecret, effectiveTenant);
        String propertiesJson = null;
        if (properties != null && !properties.isEmpty()) {
            try {
                propertiesJson = mapper.writeValueAsString(properties);
            } catch (Exception e) {
                log.warn("[EncryptedCreds] Failed to serialize properties for '{}': {}", name, e.getMessage());
            }
        }

        String credentialId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        jdbc.sql("""
                MERGE INTO credentials (
                    credential_id, tenant_id, user_id, name, category, provider, credential_type,
                    ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                    description, version, created_at, updated_at, expires_at
                ) KEY (tenant_id, name) VALUES (
                    :credentialId, :tenantId, :userId, :name, :category, :provider, :credentialType,
                    :ciphertext, :iv, :authTag, :maskedPreview, :propertiesJson, :isDefault,
                    :description, 1, :createdAt, :updatedAt, :expiresAt
                )
                """)
                .param("credentialId", credentialId)
                .param("tenantId", effectiveTenant)
                .param("userId", userId)
                .param("name", normalizedName)
                .param("category", category.name())
                .param("provider", normalizedProvider)
                .param("credentialType", effectiveType.name())
                .param("ciphertext", encrypted.ciphertext())
                .param("iv", encrypted.iv())
                .param("authTag", encrypted.authTag())
                .param("maskedPreview", encrypted.maskedPreview())
                .param("propertiesJson", propertiesJson)
                .param("isDefault", isDefault)
                .param("description", description)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .param("expiresAt", expiresAt != null ? Timestamp.from(expiresAt) : null)
                .update();

        log.info("[EncryptedCreds] Saved credential '{}' (tenant={}, provider={}, category={}, default={})",
                normalizedName, effectiveTenant, normalizedProvider, category, isDefault);

        return findByName(effectiveTenant, normalizedName).orElseThrow();
    }

    public Optional<CredentialRecord> findByName(String tenantId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String effectiveTenant = tenantId != null ? tenantId : "default";
        String normalizedName = name.trim().toLowerCase();

        try {
            return jdbc.sql("""
                    SELECT credential_id, tenant_id, user_id, name, category, provider, credential_type,
                           ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                           description, version, created_at, updated_at, expires_at, last_used_at
                    FROM credentials
                    WHERE tenant_id = :tenantId AND name = :name
                    """)
                    .param("tenantId", effectiveTenant)
                    .param("name", normalizedName)
                    .query(this::mapRow)
                    .optional();
        } catch (Exception e) {
            log.warn("[EncryptedCreds] Failed to find credential '{}' for tenant '{}': {}",
                    normalizedName, effectiveTenant, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<CredentialRecord> findDefaultByProvider(String tenantId, String provider) {
        if (provider == null || provider.isBlank()) return Optional.empty();
        String effectiveTenant = tenantId != null ? tenantId : "default";
        String normalizedProvider = provider.trim().toLowerCase();

        try {
            return jdbc.sql("""
                    SELECT credential_id, tenant_id, user_id, name, category, provider, credential_type,
                           ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                           description, version, created_at, updated_at, expires_at, last_used_at
                    FROM credentials
                    WHERE tenant_id = :tenantId AND provider = :provider AND is_default = TRUE
                    """)
                    .param("tenantId", effectiveTenant)
                    .param("provider", normalizedProvider)
                    .query(this::mapRow)
                    .optional();
        } catch (Exception e) {
            log.warn("[EncryptedCreds] Failed to find default credential for provider '{}': {}",
                    normalizedProvider, e.getMessage());
            return Optional.empty();
        }
    }

    public List<CredentialRecord> findByTenantId(String tenantId) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        try {
            return jdbc.sql("""
                    SELECT credential_id, tenant_id, user_id, name, category, provider, credential_type,
                           ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                           description, version, created_at, updated_at, expires_at, last_used_at
                    FROM credentials
                    WHERE tenant_id = :tenantId
                    ORDER BY provider, name
                    """)
                    .param("tenantId", effectiveTenant)
                    .query(this::mapRow)
                    .list();
        } catch (Exception e) {
            log.error("[EncryptedCreds] Failed to list credentials for tenant '{}'", effectiveTenant, e);
            return List.of();
        }
    }

    public List<CredentialRecord> findByUserId(String tenantId, String userId) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        if (userId == null || userId.isBlank()) return List.of();

        try {
            return jdbc.sql("""
                    SELECT credential_id, tenant_id, user_id, name, category, provider, credential_type,
                           ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                           description, version, created_at, updated_at, expires_at, last_used_at
                    FROM credentials
                    WHERE tenant_id = :tenantId AND user_id = :userId
                    ORDER BY provider, name
                    """)
                    .param("tenantId", effectiveTenant)
                    .param("userId", userId)
                    .query(this::mapRow)
                    .list();
        } catch (Exception e) {
            log.error("[EncryptedCreds] Failed to list credentials for user '{}'", userId, e);
            return List.of();
        }
    }

    public boolean deleteByName(String tenantId, String name) {
        if (name == null || name.isBlank()) return false;
        String effectiveTenant = tenantId != null ? tenantId : "default";
        String normalizedName = name.trim().toLowerCase();

        secretCache.remove(cacheKey(effectiveTenant, normalizedName));

        int rows = jdbc.sql("DELETE FROM credentials WHERE tenant_id = :tenantId AND name = :name")
                .param("tenantId", effectiveTenant)
                .param("name", normalizedName)
                .update();

        log.info("[EncryptedCreds] Deleted credential '{}' for tenant '{}' (deleted: {})",
                normalizedName, effectiveTenant, rows > 0);
        return rows > 0;
    }

    @Override
    public Optional<String> resolve(String credentialRef, String tenantId) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }

        String effectiveTenant = tenantId != null ? tenantId : "default";
        String parsedName = credentialRef.trim();

        // Strip known prefixes
        if (parsedName.startsWith("tenant:")) {
            parsedName = parsedName.substring(7);
            if (parsedName.startsWith("current:")) {
                parsedName = parsedName.substring(8);
            } else if (parsedName.contains(":")) {
                int colonIdx = parsedName.indexOf(':');
                effectiveTenant = parsedName.substring(0, colonIdx);
                parsedName = parsedName.substring(colonIdx + 1);
            }
        } else if (parsedName.startsWith("user:")) {
            parsedName = parsedName.substring(5);
            if (parsedName.contains(":")) {
                int colonIdx = parsedName.indexOf(':');
                parsedName = parsedName.substring(colonIdx + 1);
            }
        }

        String normalizedName = parsedName.toLowerCase();
        String cKey = cacheKey(effectiveTenant, normalizedName);

        // Check in-memory LRU cache
        CacheEntry cached = secretCache.get(cKey);
        if (cached != null && cached.isValid()) {
            return Optional.of(cached.secret());
        }

        // Query by name first
        Optional<CredentialRecord> recordOpt = findByName(effectiveTenant, normalizedName);

        // If not found by name, check if reference is a provider name with a default credential
        if (recordOpt.isEmpty()) {
            recordOpt = findDefaultByProvider(effectiveTenant, normalizedName);
        }

        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }

        CredentialRecord record = recordOpt.get();
        try {
            String decrypted = cipher.decrypt(record.ciphertext(), record.iv(), effectiveTenant);
            secretCache.put(cKey, new CacheEntry(decrypted, System.currentTimeMillis()));

            // Update last_used_at timestamp asynchronously/safely
            try {
                jdbc.sql("UPDATE credentials SET last_used_at = :now WHERE credential_id = :id")
                        .param("now", Timestamp.from(Instant.now()))
                        .param("id", record.credentialId())
                        .update();
            } catch (Exception ignored) {}

            return Optional.of(decrypted);
        } catch (Exception e) {
            log.error("[EncryptedCreds] Failed decrypting credential '{}' for tenant '{}': {}",
                    normalizedName, effectiveTenant, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> resolve(String credentialRef) {
        return resolve(credentialRef, "default");
    }

    private String cacheKey(String tenantId, String name) {
        return tenantId + ":" + name;
    }

    private CredentialRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        String credentialId = rs.getString("credential_id");
        String tenantId = rs.getString("tenant_id");
        String userId = rs.getString("user_id");
        String name = rs.getString("name");
        String categoryStr = rs.getString("category");
        String provider = rs.getString("provider");
        String typeStr = rs.getString("credential_type");
        String ciphertext = rs.getString("ciphertext");
        String iv = rs.getString("iv");
        String authTag = rs.getString("auth_tag");
        String maskedPreview = rs.getString("masked_preview");
        String propertiesJson = rs.getString("properties_json");
        boolean isDefault = rs.getBoolean("is_default");
        String description = rs.getString("description");
        int version = rs.getInt("version");
        Instant createdAt = rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant() : Instant.now();
        Instant updatedAt = rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toInstant() : Instant.now();
        Instant expiresAt = rs.getTimestamp("expires_at") != null
                ? rs.getTimestamp("expires_at").toInstant() : null;
        Instant lastUsedAt = rs.getTimestamp("last_used_at") != null
                ? rs.getTimestamp("last_used_at").toInstant() : null;

        CredentialCategory category = CredentialCategory.LLM;
        if (categoryStr != null) {
            try { category = CredentialCategory.valueOf(categoryStr.toUpperCase()); } catch (Exception ignored) {}
        }

        CredentialType credentialType = CredentialType.API_KEY;
        if (typeStr != null) {
            try { credentialType = CredentialType.valueOf(typeStr.toUpperCase()); } catch (Exception ignored) {}
        }

        Map<String, Object> props = Collections.emptyMap();
        if (propertiesJson != null && !propertiesJson.isBlank()) {
            try {
                props = mapper.readValue(propertiesJson, MAP_TYPE);
            } catch (Exception ignored) {}
        }

        return new CredentialRecord(
                credentialId, tenantId, userId, name, category, provider, credentialType,
                ciphertext, iv, authTag, maskedPreview, props, isDefault, description,
                version, createdAt, updatedAt, expiresAt, lastUsedAt
        );
    }
}
