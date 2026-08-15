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
package com.spectrayan.spector.connector.core;

import com.spectrayan.spector.connector.model.ConnectionTestResult;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.spi.CredentialProvider;
import com.spectrayan.spector.connector.template.TemplateDescriptorValidator;
import com.spectrayan.spector.connector.template.TemplateRegistry;

import com.spectrayan.spector.connector.spi.ConnectionProber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-level service for route lifecycle operations.
 *
 * <p>Orchestrates: validate → resolve credentials → deploy route.</p>
 *
 * <p>Validation is driven entirely by the {@link TemplateDescriptor}
 * metadata — no per-connector adapter classes needed.</p>
 */
public class RouteLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(RouteLifecycleService.class);

    private final CamelConnectorEngine engine;
    private final TemplateRegistry templateRegistry;
    private final CredentialProvider credentialProvider;

    public RouteLifecycleService(CamelConnectorEngine engine,
                                  TemplateRegistry templateRegistry,
                                  CredentialProvider credentialProvider) {
        this.engine = Objects.requireNonNull(engine);
        this.templateRegistry = Objects.requireNonNull(templateRegistry);
        this.credentialProvider = Objects.requireNonNull(credentialProvider);
    }

    /**
     * Activate a route: validate → resolve credentials → deploy.
     *
     * @param config the route configuration
     * @return the deployed RouteConfig
     * @throws IllegalArgumentException if validation fails
     */
    public RouteConfig activateRoute(RouteConfig config) throws Exception {
        Objects.requireNonNull(config, "RouteConfig must not be null");

        // 1. Validate against template descriptor
        List<String> errors = validateRoute(config);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Route validation failed: " + String.join("; ", errors));
        }

        // 2. Resolve credentials into parameters
        Map<String, String> resolvedParams = resolveParameters(config);

        // 3. If credentials were resolved, rebuild config with merged properties
        RouteConfig deployConfig = config;
        if (!resolvedParams.equals(config.properties()) || config.status() != com.spectrayan.spector.connector.model.RouteStatus.ACTIVE) {
            deployConfig = RouteConfig.builder(config.id(), config.name(), config.templateId())
                    .connectorType(config.connectorType())
                    .tenantId(config.tenantId())
                    .source(config.source())
                    .schedule(config.schedule())
                    .properties(resolvedParams)
                    .credentialRef(config.credentialRef())
                    .routeYaml(config.routeYaml())
                    .status(com.spectrayan.spector.connector.model.RouteStatus.ACTIVE)
                    .enabled(config.enabled())
                    .build();
        }

        // 4. Deploy via engine
        engine.deployRoute(deployConfig);

        log.info("[Lifecycle] Activated route '{}' (template={}, tenant={})",
                config.id(), config.templateId(), config.tenantId());
        return deployConfig;
    }

    /**
     * Deactivate a route: stop and remove from CamelContext.
     */
    public boolean deactivateRoute(String routeId) throws Exception {
        boolean removed = engine.removeRoute(routeId);
        if (removed) {
            log.info("[Lifecycle] Deactivated route '{}'", routeId);
        }
        return removed;
    }

    /**
     * Reload a route: deactivate → activate with fresh config.
     */
    public void reloadRoute(RouteConfig config) throws Exception {
        deactivateRoute(config.id());
        activateRoute(config);
    }

    /**
     * Validate a route configuration against its template descriptor.
     *
     * <p>Uses {@link TemplateDescriptorValidator} which reads the YAML
     * metadata — no per-connector adapter classes needed.</p>
     *
     * @return list of validation errors (empty = valid)
     */
    public List<String> validateRoute(RouteConfig config) {
        return templateRegistry.findTemplate(config.templateId())
                .map(descriptor -> TemplateDescriptorValidator.validate(config, descriptor))
                .orElse(List.of()); // Unknown template → skip validation, Camel will fail later
    }

    /**
     * Test connectivity for a route config (v1: validation only).
     */
    public ConnectionTestResult testConnection(RouteConfig config) {
        List<String> errors = validateRoute(config);
        if (!errors.isEmpty()) {
            return ConnectionTestResult.failure("Validation failed: " + String.join("; ", errors));
        }

        long start = System.currentTimeMillis();
        try {
            // Resolve credentials first so prober gets full properties
            Map<String, String> resolvedParams = resolveParameters(config);

            // Get the prober for this connectorType
            String connectorType = config.connectorType();
            if (connectorType == null || connectorType.isBlank()) {
                // Try to find default template
                connectorType = templateRegistry.findTemplate(config.templateId())
                        .map(TemplateDescriptor::connectorType)
                        .orElse("DEFAULT");
            }

            ConnectionProber prober = ConnectionProbers.getProber(connectorType);
            prober.probe(resolvedParams);

            long latency = System.currentTimeMillis() - start;
            return ConnectionTestResult.success("Connection successful", latency);
        } catch (Exception e) {
            log.warn("[Lifecycle] Connection probe failed for route '{}' (template={}): {}",
                    config.id(), config.templateId(), e.getMessage());
            return ConnectionTestResult.failure("Connection test failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════

    private Map<String, String> resolveParameters(RouteConfig config) {
        Map<String, String> params = new HashMap<>(config.properties());

        if (config.credentialRef() != null && !config.credentialRef().isBlank()) {
            String secret = credentialProvider.resolve(config.credentialRef())
                    .orElseThrow(() -> new IllegalStateException(
                            "Could not resolve credential: " + config.credentialRef()));
            params.put("_resolvedCredential", secret);
        }

        return params;
    }
}
