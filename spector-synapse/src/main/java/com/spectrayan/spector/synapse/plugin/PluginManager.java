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
package com.spectrayan.spector.synapse.plugin;

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.connector.TemplateRegistry;
import com.spectrayan.spector.synapse.plugin.SynapsePlugin.PluginInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin manager — discovers, initializes, and manages Synapse plugins.
 *
 * <p>Plugins are discovered via Spring component scanning. Each plugin's
 * tools are registered with the {@link ToolRegistry} and connector templates
 * with the {@link TemplateRegistry}.</p>
 */
@Service
@RestController
@RequestMapping("/api/v1/plugins")
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final ConcurrentHashMap<String, SynapsePlugin> plugins = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private final TemplateRegistry templateRegistry;

    public PluginManager(List<SynapsePlugin> discoveredPlugins,
                         ToolRegistry toolRegistry,
                         TemplateRegistry templateRegistry) {
        this.toolRegistry = toolRegistry;
        this.templateRegistry = templateRegistry;
        for (SynapsePlugin plugin : discoveredPlugins) {
            plugins.put(plugin.id(), plugin);
        }
    }

    @PostConstruct
    void initPlugins() {
        log.info("[PluginManager] Initializing {} plugins...", plugins.size());
        for (SynapsePlugin plugin : plugins.values()) {
            try {
                plugin.init(Map.of());

                // Register tools from plugin
                plugin.tools().forEach(toolRegistry::register);

                // Register connector templates from plugin
                plugin.connectorTemplates().forEach(templateRegistry::register);

                log.info("[PluginManager] ✓ {} v{} — {} tools, {} connectors",
                        plugin.name(), plugin.version(),
                        plugin.tools().size(), plugin.connectorTemplates().size());
            } catch (Exception e) {
                log.error("[PluginManager] ✗ {} failed to initialize: {}",
                        plugin.id(), e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    void shutdownPlugins() {
        log.info("[PluginManager] Shutting down {} plugins...", plugins.size());
        for (SynapsePlugin plugin : plugins.values()) {
            try {
                plugin.shutdown();
            } catch (Exception e) {
                log.warn("[PluginManager] Error shutting down {}: {}", plugin.id(), e.getMessage());
            }
        }
    }

    /** List all plugins. */
    @GetMapping
    public List<PluginInfo> listPlugins() {
        return plugins.values().stream()
                .map(p -> new PluginInfo(
                        p.id(), p.name(), p.description(), p.version(),
                        p.healthCheck(), p.tools().size(), p.connectorTemplates().size()))
                .toList();
    }

    /** Get a plugin by ID. */
    public SynapsePlugin get(String id) {
        return plugins.get(id);
    }

    /** Get all plugins. */
    public Map<String, SynapsePlugin> all() {
        return Collections.unmodifiableMap(plugins);
    }
}
