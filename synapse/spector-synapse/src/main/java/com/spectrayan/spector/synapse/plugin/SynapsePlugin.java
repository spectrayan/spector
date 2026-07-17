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
package com.spectrayan.spector.synapse.plugin;

import java.util.List;
import java.util.Map;

/**
 * Service Provider Interface for Synapse plugins.
 *
 * <p>Plugins extend the Synapse platform with custom tools, connectors,
 * or behaviors. Implementations are discovered via Spring component scanning
 * or Java SPI ({@code ServiceLoader}).</p>
 *
 * <h3>Plugin Lifecycle</h3>
 * <ol>
 *   <li>{@link #id()} — Unique plugin identifier</li>
 *   <li>{@link #init(Map)} — Called once during startup with configuration</li>
 *   <li>{@link #tools()} — Returns tools provided by this plugin</li>
 *   <li>{@link #shutdown()} — Called during graceful shutdown</li>
 * </ol>
 */
public interface SynapsePlugin {

    /** Unique plugin identifier (e.g., "jira-connector", "github-tools"). */
    String id();

    /** Human-readable plugin name. */
    String name();

    /** Plugin description. */
    String description();

    /** Plugin version string (SemVer). */
    String version();

    /**
     * Initialize the plugin with the given configuration.
     *
     * @param config plugin-specific configuration map
     */
    default void init(Map<String, Object> config) {}

    /**
     * Returns the list of tools this plugin provides.
     * These tools are registered with the global ToolRegistry.
     */
    default List<com.spectrayan.spector.synapse.agent.AgentTool> tools() {
        return List.of();
    }

    /**
     * Returns the list of connector types this plugin provides.
     */
    default List<com.spectrayan.spector.synapse.connector.ConnectorDto.TemplateDescriptor> connectorTemplates() {
        return List.of();
    }

    /**
     * Health check for this plugin.
     *
     * @return true if the plugin is operational
     */
    default boolean healthCheck() { return true; }

    /**
     * Shutdown hook — called during graceful application shutdown.
     */
    default void shutdown() {}

    /** Plugin metadata record. */
    record PluginInfo(String id, String name, String description, String version,
                      boolean healthy, int toolCount, int connectorCount) {}
}
