/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.connector;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.core.RouteLifecycleService;
import com.spectrayan.spector.connector.model.ConnectionTestResult;
import com.spectrayan.spector.connector.model.ExecutionRecord;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.model.RouteStatus;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.template.TemplateRegistry;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Connector management REST API.
 *
 * <p>Manages connector templates, route instances, lifecycle, and connection testing.</p>
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
    private final CamelConnectorEngine connectorEngine;
    private final RouteLifecycleService lifecycleService;

    public ConnectorController(TemplateRegistry templateRegistry,
                               CamelConnectorEngine connectorEngine,
                               RouteLifecycleService lifecycleService) {
        this.templateRegistry = templateRegistry;
        this.connectorEngine = connectorEngine;
        this.lifecycleService = lifecycleService;
    }

    /** List all connector templates. */
    @GetMapping("/templates")
    public ResponseEntity<List<TemplateDescriptor>> listTemplates() {
        return ResponseEntity.ok(templateRegistry.listTemplates());
    }

    /** Get a specific template by ID. */
    @GetMapping("/templates/{id}")
    public ResponseEntity<TemplateDescriptor> getTemplate(@PathVariable String id) {
        return templateRegistry.findTemplate(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create and activate a connector route from a template. */
    @PostMapping("/routes")
    public ResponseEntity<?> createRoute(@RequestBody RouteConfig config) {
        try {
            RouteConfig activated = lifecycleService.activateRoute(config);
            log.info("[Connector] Activated route: {} (template={}, tenant={})",
                    activated.name(), activated.templateId(), activated.tenantId());
            return ResponseEntity.status(HttpStatus.CREATED).body(activated);
        } catch (IllegalArgumentException e) {
            log.warn("[Connector] Validation failed creating route: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Connector] Error activating route: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** List all connector routes. */
    @GetMapping("/routes")
    public ResponseEntity<List<RouteConfig>> listRoutes(
            @RequestParam(name = "tenantId", required = false) String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            return ResponseEntity.ok(connectorEngine.listRoutes(tenantId));
        }
        return ResponseEntity.ok(connectorEngine.listRoutes());
    }

    /** Get a specific route by ID. */
    @GetMapping("/routes/{id}")
    public ResponseEntity<RouteConfig> getRoute(@PathVariable String id) {
        return connectorEngine.getRoute(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Get route runtime status. */
    @GetMapping("/routes/{id}/status")
    public ResponseEntity<Map<String, Object>> getRouteStatus(@PathVariable String id) {
        org.apache.camel.ServiceStatus status = connectorEngine.getRouteStatus(id);
        return ResponseEntity.ok(Map.of("routeId", id, "status", status != null ? status.name() : "UNKNOWN"));
    }

    /** Start a connector route. */
    @PostMapping("/routes/{id}/start")
    public ResponseEntity<?> startRoute(@PathVariable String id) {
        try {
            connectorEngine.startRoute(id);
            log.info("[Connector] Started route: {}", id);
            return connectorEngine.getRoute(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.ok().build());
        } catch (Exception e) {
            log.error("[Connector] Failed to start route {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Stop a connector route. */
    @PostMapping("/routes/{id}/stop")
    public ResponseEntity<?> stopRoute(@PathVariable String id) {
        try {
            connectorEngine.stopRoute(id);
            log.info("[Connector] Stopped route: {}", id);
            return connectorEngine.getRoute(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.ok().build());
        } catch (Exception e) {
            log.error("[Connector] Failed to stop route {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Delete (deactivate and undeploy) a connector route. */
    @DeleteMapping("/routes/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable String id) {
        try {
            lifecycleService.deactivateRoute(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.warn("[Connector] Error deactivating route {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /** Test connection for an existing route ID or route configuration. */
    @PostMapping("/routes/{id}/test")
    public ResponseEntity<ConnectionTestResult> testConnectionForRoute(@PathVariable String id) {
        Optional<RouteConfig> route = connectorEngine.getRoute(id);
        if (route.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ConnectionTestResult result = lifecycleService.testConnection(route.get());
        return ResponseEntity.ok(result);
    }

    /** Test connection for a given route configuration payload without deploying it. */
    @PostMapping("/test")
    public ResponseEntity<ConnectionTestResult> testConnectionConfig(@RequestBody RouteConfig config) {
        ConnectionTestResult result = lifecycleService.testConnection(config);
        return ResponseEntity.ok(result);
    }

    /** Get execution records for a specific route. */
    @GetMapping("/routes/{id}/history")
    public ResponseEntity<List<ExecutionRecord>> getExecutionHistory(
            @PathVariable String id,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(connectorEngine.getExecutionHistory(id, limit));
    }
}
