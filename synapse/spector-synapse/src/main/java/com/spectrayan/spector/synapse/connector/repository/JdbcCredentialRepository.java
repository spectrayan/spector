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
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
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

/**
 * JDBC implementation of {@link CredentialRepository} backed by the {@code credentials} table.
 */
@Repository
public class JdbcCredentialRepository implements CredentialRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcCredentialRepository.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcCredentialRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcClient must not be null");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper must not be null");
    }

    @Override
    public CredentialRecord save(CredentialRecord record) {
        Objects.requireNonNull(record, "record must not be null");

        String propertiesJson = null;
        if (record.properties() != null && !record.properties().isEmpty()) {
            try {
                propertiesJson = mapper.writeValueAsString(record.properties());
            } catch (Exception e) {
                log.warn("[JdbcCredRepo] Failed to serialize properties for '{}': {}", record.name(), e.getMessage());
            }
        }

        jdbc.sql("""
                MERGE INTO credentials (
                    credential_id, tenant_id, user_id, name, category, provider, credential_type,
                    ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                    description, version, created_at, updated_at, expires_at
                ) KEY (tenant_id, name) VALUES (
                    :credentialId, :tenantId, :userId, :name, :category, :provider, :credentialType,
                    :ciphertext, :iv, :authTag, :maskedPreview, :propertiesJson, :isDefault,
                    :description, :version, :createdAt, :updatedAt, :expiresAt
                )
                """)
                .param("credentialId", record.credentialId())
                .param("tenantId", record.tenantId())
                .param("userId", record.userId())
                .param("name", record.name())
                .param("category", record.category().name())
                .param("provider", record.provider())
                .param("credentialType", record.credentialType().name())
                .param("ciphertext", record.ciphertext())
                .param("iv", record.iv())
                .param("authTag", record.authTag())
                .param("maskedPreview", record.maskedPreview())
                .param("propertiesJson", propertiesJson)
                .param("isDefault", record.isDefault())
                .param("description", record.description())
                .param("version", record.version())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .param("expiresAt", record.expiresAt() != null ? Timestamp.from(record.expiresAt()) : null)
                .update();

        return findByName(record.tenantId(), record.name()).orElse(record);
    }

    @Override
    public Optional<CredentialRecord> findByName(String tenantId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String effectiveTenant = tenantId != null ? tenantId : "default";

        try {
            return jdbc.sql("""
                    SELECT credential_id, tenant_id, user_id, name, category, provider, credential_type,
                           ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                           description, version, created_at, updated_at, expires_at, last_used_at
                    FROM credentials
                    WHERE tenant_id = :tenantId AND name = :name
                    """)
                    .param("tenantId", effectiveTenant)
                    .param("name", name.trim().toLowerCase())
                    .query(this::mapRow)
                    .optional();
        } catch (Exception e) {
            log.warn("[JdbcCredRepo] Failed to find credential '{}' in tenant '{}': {}", name, effectiveTenant, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<CredentialRecord> findDefaultByProvider(String tenantId, String provider) {
        if (provider == null || provider.isBlank()) return Optional.empty();
        String effectiveTenant = tenantId != null ? tenantId : "default";

        try {
            return jdbc.sql("""
                    SELECT credential_id, tenant_id, user_id, name, category, provider, credential_type,
                           ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
                           description, version, created_at, updated_at, expires_at, last_used_at
                    FROM credentials
                    WHERE tenant_id = :tenantId AND provider = :provider AND is_default = TRUE
                    """)
                    .param("tenantId", effectiveTenant)
                    .param("provider", provider.trim().toLowerCase())
                    .query(this::mapRow)
                    .optional();
        } catch (Exception e) {
            log.warn("[JdbcCredRepo] Failed to find default credential for provider '{}': {}", provider, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
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
            log.error("[JdbcCredRepo] Failed to list credentials for tenant '{}'", effectiveTenant, e);
            return List.of();
        }
    }

    @Override
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
            log.error("[JdbcCredRepo] Failed to list credentials for user '{}'", userId, e);
            return List.of();
        }
    }

    @Override
    public void clearDefault(String tenantId, String provider) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        jdbc.sql("UPDATE credentials SET is_default = FALSE WHERE tenant_id = :tenantId AND provider = :provider")
                .param("tenantId", effectiveTenant)
                .param("provider", provider.trim().toLowerCase())
                .update();
    }

    @Override
    public boolean deleteByName(String tenantId, String name) {
        if (name == null || name.isBlank()) return false;
        String effectiveTenant = tenantId != null ? tenantId : "default";

        int rows = jdbc.sql("DELETE FROM credentials WHERE tenant_id = :tenantId AND name = :name")
                .param("tenantId", effectiveTenant)
                .param("name", name.trim().toLowerCase())
                .update();

        return rows > 0;
    }

    @Override
    public void updateLastUsedAt(String credentialId, Instant timestamp) {
        try {
            jdbc.sql("UPDATE credentials SET last_used_at = :now WHERE credential_id = :id")
                    .param("now", Timestamp.from(timestamp != null ? timestamp : Instant.now()))
                    .param("id", credentialId)
                    .update();
        } catch (Exception ignored) {}
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
