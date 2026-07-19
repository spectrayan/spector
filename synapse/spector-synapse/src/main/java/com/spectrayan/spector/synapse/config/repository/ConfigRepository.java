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
package com.spectrayan.spector.synapse.config.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ScopedConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC-backed repository for hierarchical {@link ScopedConfig} entries.
 */
@Repository
public class ConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfigRepository.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public ConfigRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Gets a scoped config by scope and category.
     */
    public Optional<ScopedConfig> get(String scope, ConfigCategory category) {
        try {
            return jdbc.sql("""
                            SELECT config_json, updated_at, updated_by
                            FROM scoped_config
                            WHERE scope = :scope AND category = :category
                            """)
                    .param("scope", scope)
                    .param("category", category.key())
                    .query((rs, rowNum) -> {
                        try {
                            Map<String, Object> values = mapper.readValue(
                                    rs.getString("config_json"), MAP_TYPE);
                            Instant updatedAt = rs.getTimestamp("updated_at") != null
                                    ? rs.getTimestamp("updated_at").toInstant() : Instant.now();
                            String updatedBy = rs.getString("updated_by");
                            return new ScopedConfig(scope, category, values, updatedAt, updatedBy);
                        } catch (Exception e) {
                            log.warn("Failed to parse config JSON for scope={}, category={}: {}",
                                    scope, category.key(), e.getMessage());
                            return null;
                        }
                    })
                    .optional();
        } catch (Exception e) {
            log.debug("Config lookup failed for scope={}, category={}: {}",
                    scope, category.key(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Saves (upserts) a scoped config.
     */
    public void save(ScopedConfig config) {
        try {
            String json = mapper.writeValueAsString(config.values());

            jdbc.sql("""
                            MERGE INTO scoped_config (scope, category, config_json, updated_at, updated_by)
                            KEY (scope, category)
                            VALUES (:scope, :category, :json, :updatedAt, :updatedBy)
                            """)
                    .param("scope", config.scope())
                    .param("category", config.category().key())
                    .param("json", json)
                    .param("updatedAt", java.sql.Timestamp.from(Instant.now()))
                    .param("updatedBy", config.updatedBy())
                    .update();
        } catch (Exception e) {
            log.error("Failed to save config for scope={}, category={}: {}",
                    config.scope(), config.category().key(), e.getMessage());
            throw new RuntimeException("Config save failed", e);
        }
    }

    /**
     * Deletes a scoped config override.
     */
    public boolean delete(String scope, ConfigCategory category) {
        int rows = jdbc.sql("DELETE FROM scoped_config WHERE scope = :scope AND category = :category")
                .param("scope", scope)
                .param("category", category.key())
                .update();
        return rows > 0;
    }

    /**
     * Lists all configs for a given category across all scopes.
     */
    public List<ScopedConfig> findByCategory(ConfigCategory category) {
        return jdbc.sql("""
                        SELECT scope, config_json, updated_at, updated_by
                        FROM scoped_config
                        WHERE category = :category
                        ORDER BY scope
                        """)
                .param("category", category.key())
                .query((rs, rowNum) -> {
                    try {
                        Map<String, Object> values = mapper.readValue(
                                rs.getString("config_json"), MAP_TYPE);
                        return new ScopedConfig(
                                rs.getString("scope"), category, values,
                                rs.getTimestamp("updated_at") != null
                                        ? rs.getTimestamp("updated_at").toInstant() : Instant.now(),
                                rs.getString("updated_by"));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .list();
    }
}
