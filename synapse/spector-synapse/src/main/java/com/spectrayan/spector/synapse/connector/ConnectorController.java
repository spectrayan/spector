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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConnectorException;
import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.core.RouteLifecycleService;
import com.spectrayan.spector.connector.model.ConnectionTestResult;
import com.spectrayan.spector.connector.model.ExecutionRecord;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.synapse.config.FeatureGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                .orElseThrow(() -> new SpectorConnectorException(
                        ErrorCode.CONNECTOR_TEMPLATE_NOT_FOUND, id));
    }

    /** Create and activate a connector route from a template. */
    @PostMapping("/routes")
    public ResponseEntity<RouteConfig> createRoute(@RequestBody RouteConfig config) throws Exception {
        MDC.put("routeId", config.id());
        MDC.put("tenantId", config.tenantId());
        try {
            RouteConfig activated = lifecycleService.activateRoute(config);
            log.info("[Connector] Activated route: {} (template={}, tenant={})",
                    activated.name(), activated.templateId(), activated.tenantId());
            return ResponseEntity.status(HttpStatus.CREATED).body(activated);
        } finally {
            MDC.remove("routeId");
            MDC.remove("tenantId");
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
                .orElseThrow(() -> new SpectorConnectorException(
                        ErrorCode.CONNECTOR_ROUTE_NOT_FOUND, id));
    }

    /** Get route runtime status. */
    @GetMapping("/routes/{id}/status")
    public ResponseEntity<Map<String, Object>> getRouteStatus(@PathVariable String id) {
        org.apache.camel.ServiceStatus status = connectorEngine.getRouteStatus(id);
        if (status == null && connectorEngine.getRoute(id).isEmpty()) {
            throw new SpectorConnectorException(ErrorCode.CONNECTOR_ROUTE_NOT_FOUND, id);
        }
        return ResponseEntity.ok(Map.of("routeId", id, "status", status != null ? status.name() : "STOPPED"));
    }

    /** Start a connector route. */
    @PostMapping("/routes/{id}/start")
    public ResponseEntity<RouteConfig> startRoute(@PathVariable String id) throws Exception {
        if (connectorEngine.getRoute(id).isEmpty()) {
            throw new SpectorConnectorException(ErrorCode.CONNECTOR_ROUTE_NOT_FOUND, id);
        }
        try {
            connectorEngine.startRoute(id);
            log.info("[Connector] Started route: {}", id);
            return connectorEngine.getRoute(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.ok().build());
        } catch (Exception e) {
            throw new SpectorConnectorException(
                    ErrorCode.CONNECTOR_ROUTE_START_FAILED, e, id, e.getMessage());
        }
    }

    /** Stop a connector route. */
    @PostMapping("/routes/{id}/stop")
    public ResponseEntity<RouteConfig> stopRoute(@PathVariable String id) throws Exception {
        if (connectorEngine.getRoute(id).isEmpty()) {
            throw new SpectorConnectorException(ErrorCode.CONNECTOR_ROUTE_NOT_FOUND, id);
        }
        try {
            connectorEngine.stopRoute(id);
            log.info("[Connector] Stopped route: {}", id);
            return connectorEngine.getRoute(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.ok().build());
        } catch (Exception e) {
            throw new SpectorConnectorException(
                    ErrorCode.CONNECTOR_ROUTE_STOP_FAILED, e, id, e.getMessage());
        }
    }

    /** Delete (deactivate and undeploy) a connector route. */
    @DeleteMapping("/routes/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable String id) throws Exception {
        boolean deactivated = lifecycleService.deactivateRoute(id);
        if (!deactivated) {
            throw new SpectorConnectorException(ErrorCode.CONNECTOR_ROUTE_NOT_FOUND, id);
        }
        return ResponseEntity.noContent().build();
    }

    /** Test connection for an existing route ID. */
    @PostMapping("/routes/{id}/test")
    public ResponseEntity<ConnectionTestResult> testConnectionForRoute(@PathVariable String id) {
        Optional<RouteConfig> route = connectorEngine.getRoute(id);
        if (route.isEmpty()) {
            throw new SpectorConnectorException(ErrorCode.CONNECTOR_ROUTE_NOT_FOUND, id);
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
