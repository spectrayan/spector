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
package com.spectrayan.spector.synapse.connector;

import com.spectrayan.spector.synapse.connector.ConnectorDto.ConnectionTestResult;
import com.spectrayan.spector.synapse.connector.ConnectorDto.ExecutionRecord;
import com.spectrayan.spector.synapse.connector.ConnectorDto.RouteConfig;
import com.spectrayan.spector.synapse.connector.ConnectorDto.RouteStatus;
import com.spectrayan.spector.synapse.connector.ConnectorDto.TemplateDescriptor;
import com.spectrayan.spector.synapse.config.FeatureGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connector management REST API.
 *
 * <p>Manages connector templates, route instances, and connection testing.</p>
 *
 * <p>Gated by the {@code connectorsEnabled} feature flag — returns HTTP 404
 * when connectors are disabled.</p>
 */
@RestController
@RequestMapping("/api/v1/connectors")
@FeatureGate("connectorsEnabled")
public class ConnectorController {

    private static final Logger log = LoggerFactory.getLogger(ConnectorController.class);

    private final TemplateRegistry templateRegistry;
    private final ConcurrentHashMap<String, RouteConfig> routes = new ConcurrentHashMap<>();

    public ConnectorController(TemplateRegistry templateRegistry) {
        this.templateRegistry = templateRegistry;
    }

    /** List all connector templates. */
    @GetMapping("/templates")
    public ResponseEntity<List<TemplateDescriptor>> listTemplates() {
        return ResponseEntity.ok(templateRegistry.all());
    }

    /** Get a specific template. */
    @GetMapping("/templates/{id}")
    public ResponseEntity<TemplateDescriptor> getTemplate(@PathVariable String id) {
        return templateRegistry.get(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a connector route from a template. */
    @PostMapping("/routes")
    public ResponseEntity<RouteConfig> createRoute(@RequestBody RouteConfig config) {
        String id = config.id() != null ? config.id() : UUID.randomUUID().toString();
        RouteConfig route = new RouteConfig(id, config.templateId(), config.name(),
                RouteStatus.CREATED, config.config(), config.cronExpression(),
                Instant.now(), null);
        routes.put(id, route);
        log.info("[Connector] Created route: {} (template={})", route.name(), route.templateId());
        return ResponseEntity.status(HttpStatus.CREATED).body(route);
    }

    /** List all connector routes. */
    @GetMapping("/routes")
    public ResponseEntity<List<RouteConfig>> listRoutes() {
        return ResponseEntity.ok(List.copyOf(routes.values()));
    }

    /** Get a specific route. */
    @GetMapping("/routes/{id}")
    public ResponseEntity<RouteConfig> getRoute(@PathVariable String id) {
        RouteConfig route = routes.get(id);
        return route != null ? ResponseEntity.ok(route) : ResponseEntity.notFound().build();
    }

    /** Start a connector route. */
    @PostMapping("/routes/{id}/start")
    public ResponseEntity<RouteConfig> startRoute(@PathVariable String id) {
        RouteConfig route = routes.get(id);
        if (route == null) return ResponseEntity.notFound().build();

        RouteConfig updated = new RouteConfig(route.id(), route.templateId(), route.name(),
                RouteStatus.RUNNING, route.config(), route.cronExpression(),
                route.createdAt(), Instant.now());
        routes.put(id, updated);
        log.info("[Connector] Started route: {}", route.name());
        return ResponseEntity.ok(updated);
    }

    /** Stop a connector route. */
    @PostMapping("/routes/{id}/stop")
    public ResponseEntity<RouteConfig> stopRoute(@PathVariable String id) {
        RouteConfig route = routes.get(id);
        if (route == null) return ResponseEntity.notFound().build();

        RouteConfig updated = new RouteConfig(route.id(), route.templateId(), route.name(),
                RouteStatus.STOPPED, route.config(), route.cronExpression(),
                route.createdAt(), route.lastRunAt());
        routes.put(id, updated);
        log.info("[Connector] Stopped route: {}", route.name());
        return ResponseEntity.ok(updated);
    }

    /** Delete a connector route. */
    @DeleteMapping("/routes/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable String id) {
        RouteConfig removed = routes.remove(id);
        return removed != null ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Test connection for a route. */
    @PostMapping("/routes/{id}/test")
    public ResponseEntity<ConnectionTestResult> testConnection(@PathVariable String id) {
        RouteConfig route = routes.get(id);
        if (route == null) return ResponseEntity.notFound().build();

        // TODO: Implement actual connection testing per connector type
        ConnectionTestResult result = ConnectionTestResult.success(Duration.ofMillis(42));
        return ResponseEntity.ok(result);
    }
}
