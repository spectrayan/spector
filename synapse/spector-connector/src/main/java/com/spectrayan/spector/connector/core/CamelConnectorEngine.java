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

import com.spectrayan.spector.connector.model.ExecutionRecord;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.ExecutionLogger;
import com.spectrayan.spector.connector.spi.RouteConfigProvider;
import com.spectrayan.spector.connector.template.TemplateRegistry;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.DefaultCamelContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core Camel engine manager — creates, starts, and stops the CamelContext
 * and manages dynamic route lifecycle via templates.
 *
 * <p>This is the standalone (non-Spring) engine that uses
 * {@link DefaultCamelContext} directly. Routes are created by instantiating
 * Camel route template definitions loaded from YAML via
 * via {@link TemplateRegistry}.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #start()} — creates CamelContext, registers templates and sink, loads enabled routes</li>
 *   <li>Routes are added/removed dynamically via {@link #deployRoute}/{@link #removeRoute}</li>
 *   <li>{@link #close()} — gracefully shuts down all routes and the context</li>
 * </ol>
 */
public class CamelConnectorEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CamelConnectorEngine.class);

    private final ExecutionLogger executionLogger;
    private final SpectorIngestionSink ingestionSink;
    private final RouteConfigProvider configProvider;
    private final TemplateRegistry templateRegistry;
    private final CamelContext camelContext;
    private final Map<String, RouteConfig> activeRoutes = new ConcurrentHashMap<>();
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private volatile boolean started = false;

    /**
     * Creates a fully generic connector engine with engine-level execution logger.
     *
     * @param executionLogger  execution history and audit logger (defaults to in-memory if null)
     * @param ingestionSink    optional ingestion sink for Inbound -> Spector ingestion routes
     * @param configProvider   route config persistence
     * @param templateRegistry template registry for route creation
     * @param camelContext     CamelContext instance
     */
    public CamelConnectorEngine(ExecutionLogger executionLogger,
                                SpectorIngestionSink ingestionSink,
                                RouteConfigProvider configProvider,
                                TemplateRegistry templateRegistry,
                                CamelContext camelContext) {
        this.executionLogger = executionLogger != null
                ? executionLogger
                : (ingestionSink != null && ingestionSink.executionLogger() != null
                        ? ingestionSink.executionLogger()
                        : new com.spectrayan.spector.connector.spi.InMemoryExecutionLogger());
        this.ingestionSink = ingestionSink;
        this.configProvider = Objects.requireNonNull(configProvider, "RouteConfigProvider must not be null");
        this.templateRegistry = Objects.requireNonNull(templateRegistry, "TemplateRegistry must not be null");
        this.camelContext = Objects.requireNonNull(camelContext, "CamelContext must not be null");
    }

    /**
     * Creates a connector engine with execution logging and optional ingestion sink.
     */
    public CamelConnectorEngine(ExecutionLogger executionLogger,
                                SpectorIngestionSink ingestionSink,
                                RouteConfigProvider configProvider,
                                TemplateRegistry templateRegistry) {
        this(executionLogger, ingestionSink, configProvider, templateRegistry, new DefaultCamelContext());
    }

    /**
     * Creates a generic connector engine without a dedicated ingestion sink
     * (e.g. for notifications, chat communications, exports, and custom integrations).
     */
    public CamelConnectorEngine(ExecutionLogger executionLogger,
                                RouteConfigProvider configProvider,
                                TemplateRegistry templateRegistry) {
        this(executionLogger, null, configProvider, templateRegistry, new DefaultCamelContext());
    }

    /**
     * Creates a connector engine with an ingestion sink (backward-compatible).
     *
     * @param ingestionSink    the sink that bridges Camel → Spector
     * @param configProvider   route config persistence
     * @param templateRegistry template registry for route creation
     */
    public CamelConnectorEngine(SpectorIngestionSink ingestionSink,
                                RouteConfigProvider configProvider,
                                TemplateRegistry templateRegistry) {
        this(ingestionSink != null ? ingestionSink.executionLogger() : null,
                ingestionSink, configProvider, templateRegistry, new DefaultCamelContext());
    }

    /** Package-private constructor for testing with a custom CamelContext. */
    CamelConnectorEngine(SpectorIngestionSink ingestionSink,
                         RouteConfigProvider configProvider,
                         TemplateRegistry templateRegistry,
                         CamelContext camelContext) {
        this(ingestionSink != null ? ingestionSink.executionLogger() : null,
                ingestionSink, configProvider, templateRegistry, camelContext);
    }

    /**
     * Starts the Camel engine — registers audit event notifier, execution logger,
     * templates, ingestion sink (if configured), and loads all enabled routes.
     */
    public void start() throws Exception {
        lifecycleLock.lock();
        try {
            if (started) {
                log.warn("Connector engine already started");
                return;
            }

            // Register audit event notifier for generic route execution history tracking
            camelContext.getManagementStrategy().addEventNotifier(
                    new ConnectorExecutionAuditNotifier(executionLogger));

            // Register execution logger as a named bean in the Camel registry
            camelContext.getRegistry().bind("spectorExecutionLogger", executionLogger);

            // Register the ingestion sink as a named bean in the registry if configured
            if (ingestionSink != null) {
                camelContext.getRegistry().bind("spectorIngestionSink", ingestionSink);
            }

            // Load route templates from YAML (classpath + filesystem)
            templateRegistry.loadRouteTemplatesInto(camelContext);

            camelContext.start();
            started = true;
            log.info("[ConnectorEngine] Started CamelContext with route templates and generic audit logging");

            // Load and deploy all enabled routes
            var enabledRoutes = configProvider.findAllEnabled();
            for (var routeConfig : enabledRoutes) {
                try {
                    deployRoute(routeConfig);
                } catch (Exception e) {
                    log.error("[ConnectorEngine] Failed to deploy route '{}': {}",
                            routeConfig.id(), e.getMessage(), e);
                }
            }
            log.info("[ConnectorEngine] Deployed {} routes on startup", activeRoutes.size());
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Deploys a route by instantiating a Camel routeTemplate with
     * the user's parameters.
     *
     * @param config the route configuration
     * @throws Exception if the route cannot be added
     */
    public void deployRoute(RouteConfig config) throws Exception {
        Objects.requireNonNull(config, "RouteConfig must not be null");

        if (activeRoutes.containsKey(config.id())) {
            log.info("[ConnectorEngine] Removing existing route '{}' before redeploy", config.id());
            removeRoute(config.id());
        }

        // Build parameters map from config properties
        Map<String, String> params = new HashMap<>(config.properties());

        // Register MongoClient bean dynamically if deploying a MongoDB route template
        if ("mongodb-poll".equals(config.templateId())) {
            String connectionUri = params.get("connectionUri");
            if (connectionUri != null && !connectionUri.isBlank()) {
                var existingClients = camelContext.getRegistry().findByType(com.mongodb.client.MongoClient.class);
                if (existingClients.isEmpty()) {
                    try {
                        var mongoClient = com.mongodb.client.MongoClients.create(connectionUri);
                        camelContext.getRegistry().bind("mongoClient", mongoClient);
                        log.info("[ConnectorEngine] Bound MongoClient dynamically");
                    } catch (Exception e) {
                        log.error("[ConnectorEngine] Failed to register MongoClient dynamically", e);
                    }
                } else {
                    log.info("[ConnectorEngine] Reusing existing MongoClient found in registry: {}", existingClients.iterator().next());
                }
            }
        }

        // Instantiate via TemplateRegistry
        templateRegistry.instantiate(
                camelContext,
                config.templateId(),
                config.id(),
                config.tenantId(),
                params);

        activeRoutes.put(config.id(), config);
        log.info("[ConnectorEngine] Deployed route '{}' (template={}, tenant={})",
                config.id(), config.templateId(), config.tenantId());
    }

    /**
     * Removes a route by ID and stops it gracefully.
     */
    public boolean removeRoute(String routeId) throws Exception {
        if (!activeRoutes.containsKey(routeId)) {
            return false;
        }

        camelContext.getRouteController().stopRoute(routeId);
        camelContext.removeRoute(routeId);
        activeRoutes.remove(routeId);
        log.info("[ConnectorEngine] Removed route '{}'", routeId);
        return true;
    }

    /** Starts a previously stopped route. */
    public void startRoute(String routeId) throws Exception {
        camelContext.getRouteController().startRoute(routeId);
    }

    /** Stops a running route (without removing it). */
    public void stopRoute(String routeId) throws Exception {
        camelContext.getRouteController().stopRoute(routeId);
    }

    /** Returns the status of a route. */
    public ServiceStatus getRouteStatus(String routeId) {
        return camelContext.getRouteController().getRouteStatus(routeId);
    }

    /** Returns IDs of all active (deployed) routes. */
    public Set<String> activeRouteIds() {
        return Set.copyOf(activeRoutes.keySet());
    }

    /** Returns true if the engine is started. */
    public boolean isStarted() {
        return started;
    }

    /** Returns the underlying CamelContext. */
    public CamelContext camelContext() {
        return camelContext;
    }

    /** Returns a deployed route config by ID, if present. */
    public Optional<RouteConfig> getRoute(String routeId) {
        return Optional.ofNullable(activeRoutes.get(routeId));
    }

    /** Returns all deployed route configs. */
    public List<RouteConfig> listRoutes() {
        return List.copyOf(activeRoutes.values());
    }

    /** Returns all deployed route configs for a specific tenant. */
    public List<RouteConfig> listRoutes(String tenantId) {
        return activeRoutes.values().stream()
                .filter(r -> tenantId.equals(r.tenantId()))
                .toList();
    }

    /** Returns execution history for a route across all connector types. */
    public List<ExecutionRecord> getExecutionHistory(String routeId, int limit) {
        return executionLogger != null
                ? executionLogger.getHistory(routeId, limit)
                : List.of();
    }

    /** Returns the latest execution record for a route, if any. */
    public Optional<ExecutionRecord> getLatestExecution(String routeId) {
        return executionLogger != null
                ? executionLogger.getLatest(routeId)
                : Optional.empty();
    }

    /** Returns the engine-level execution logger. */
    public ExecutionLogger executionLogger() {
        return executionLogger;
    }

    /** Returns the configured ingestion sink, if any. */
    public Optional<SpectorIngestionSink> ingestionSink() {
        return Optional.ofNullable(ingestionSink);
    }

    /** Returns the template registry. */
    public TemplateRegistry templateRegistry() {
        return templateRegistry;
    }

    @Override
    public void close() throws Exception {
        lifecycleLock.lock();
        try {
            if (!started) return;

            log.info("[ConnectorEngine] Shutting down {} routes...", activeRoutes.size());
            camelContext.stop();
            activeRoutes.clear();
            started = false;
            log.info("[ConnectorEngine] Shutdown complete");
        } finally {
            lifecycleLock.unlock();
        }
    }
}
