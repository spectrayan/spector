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
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.spi.RouteConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed persistent implementation of {@link RouteConfigProvider}.
 *
 * <p>Persists connector route definitions to the {@code connector_routes} relational table,
 * enabling tenant-scoped configuration, dynamic route reloading, and persistence across restarts.</p>
 */
@Repository
public class JdbcRouteConfigProvider implements RouteConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(JdbcRouteConfigProvider.class);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcRouteConfigProvider(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcClient must not be null");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper must not be null");
    }

    @Override
    public void save(RouteConfig config) {
        Objects.requireNonNull(config, "RouteConfig must not be null");
        try {
            String json = mapper.writeValueAsString(config.properties());
            Instant now = Instant.now();

            jdbc.sql("""
                    MERGE INTO connector_routes (
                        route_id, tenant_id, name, template_id, connector_type,
                        source, schedule, enabled, parameters_json, created_at, updated_at
                    ) KEY (route_id) VALUES (
                        :routeId, :tenantId, :name, :templateId, :connectorType,
                        :source, :schedule, :enabled, :json, :createdAt, :updatedAt
                    )
                    """)
                    .param("routeId", config.id())
                    .param("tenantId", config.tenantId() != null ? config.tenantId() : "default")
                    .param("name", config.name() != null ? config.name() : config.id())
                    .param("templateId", config.templateId())
                    .param("connectorType", config.connectorType() != null ? config.connectorType() : "INGESTION")
                    .param("source", config.source())
                    .param("schedule", config.schedule())
                    .param("enabled", config.enabled())
                    .param("json", json)
                    .param("createdAt", java.sql.Timestamp.from(config.createdAt() != null ? config.createdAt() : now))
                    .param("updatedAt", java.sql.Timestamp.from(now))
                    .update();

            log.info("[JdbcRouteConfigProvider] Saved route '{}' (template={}, enabled={}, tenant={})",
                    config.id(), config.templateId(), config.enabled(), config.tenantId());
        } catch (Exception e) {
            log.error("[JdbcRouteConfigProvider] Failed to save route '{}'", config.id(), e);
            throw new RuntimeException("Failed to save RouteConfig for route: " + config.id(), e);
        }
    }

    @Override
    public void delete(String routeId) {
        if (routeId == null || routeId.isBlank()) return;
        try {
            int rows = jdbc.sql("DELETE FROM connector_routes WHERE route_id = :routeId")
                    .param("routeId", routeId)
                    .update();
            log.info("[JdbcRouteConfigProvider] Deleted route '{}' (rows affected: {})", routeId, rows);
        } catch (Exception e) {
            log.error("[JdbcRouteConfigProvider] Failed to delete route '{}'", routeId, e);
            throw new RuntimeException("Failed to delete RouteConfig: " + routeId, e);
        }
    }

    @Override
    public Optional<RouteConfig> findById(String routeId) {
        if (routeId == null || routeId.isBlank()) return Optional.empty();
        try {
            return jdbc.sql("""
                    SELECT route_id, tenant_id, name, template_id, connector_type,
                           source, schedule, enabled, parameters_json, created_at, updated_at, last_executed_at
                    FROM connector_routes
                    WHERE route_id = :routeId
                    """)
                    .param("routeId", routeId)
                    .query(this::mapRow)
                    .optional();
        } catch (Exception e) {
            log.debug("[JdbcRouteConfigProvider] Error looking up route '{}': {}", routeId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<RouteConfig> findByTenantId(String tenantId) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        try {
            return jdbc.sql("""
                    SELECT route_id, tenant_id, name, template_id, connector_type,
                           source, schedule, enabled, parameters_json, created_at, updated_at, last_executed_at
                    FROM connector_routes
                    WHERE tenant_id = :tenantId
                    ORDER BY route_id
                    """)
                    .param("tenantId", effectiveTenant)
                    .query(this::mapRow)
                    .list();
        } catch (Exception e) {
            log.error("[JdbcRouteConfigProvider] Failed to list routes for tenant '{}'", effectiveTenant, e);
            return List.of();
        }
    }

    @Override
    public List<RouteConfig> findAllEnabled() {
        try {
            return jdbc.sql("""
                    SELECT route_id, tenant_id, name, template_id, connector_type,
                           source, schedule, enabled, parameters_json, created_at, updated_at, last_executed_at
                    FROM connector_routes
                    WHERE enabled = TRUE
                    ORDER BY route_id
                    """)
                    .query(this::mapRow)
                    .list();
        } catch (Exception e) {
            log.error("[JdbcRouteConfigProvider] Failed to list enabled routes", e);
            return List.of();
        }
    }

    @Override
    public List<RouteConfig> findAll() {
        try {
            return jdbc.sql("""
                    SELECT route_id, tenant_id, name, template_id, connector_type,
                           source, schedule, enabled, parameters_json, created_at, updated_at, last_executed_at
                    FROM connector_routes
                    ORDER BY route_id
                    """)
                    .query(this::mapRow)
                    .list();
        } catch (Exception e) {
            log.error("[JdbcRouteConfigProvider] Failed to list all routes", e);
            return List.of();
        }
    }

    private RouteConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
        String routeId = rs.getString("route_id");
        String tenantId = rs.getString("tenant_id");
        String name = rs.getString("name");
        String templateId = rs.getString("template_id");
        String connectorType = rs.getString("connector_type");
        String source = rs.getString("source");
        String schedule = rs.getString("schedule");
        boolean enabled = rs.getBoolean("enabled");
        String paramsJson = rs.getString("parameters_json");
        Instant createdAt = rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant() : Instant.now();

        Map<String, String> properties = Collections.emptyMap();
        if (paramsJson != null && !paramsJson.isBlank()) {
            try {
                properties = mapper.readValue(paramsJson, MAP_TYPE);
            } catch (Exception e) {
                log.warn("[JdbcRouteConfigProvider] Failed to parse parameters JSON for route '{}': {}",
                        routeId, e.getMessage());
            }
        }

        return RouteConfig.builder(routeId, name, templateId)
                .tenantId(tenantId)
                .connectorType(connectorType)
                .source(source)
                .schedule(schedule)
                .properties(properties)
                .enabled(enabled)
                .createdAt(createdAt)
                .build();
    }
}
