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
package com.spectrayan.spector.synapse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature flags for Spector Synapse v0.1.0-alpha.
 *
 * <p>Internal-only feature gates that control which capabilities are active.
 * Flags are bound from {@code spector.features.*} in {@code application.yml}
 * and can be overridden via environment variables.</p>
 *
 * <h3>Default Policy</h3>
 * <ul>
 *   <li>LLM-dependent features (chat, agent chat) default to {@code false}
 *       and are auto-enabled when Ollama is detected at startup.</li>
 *   <li>Cognitive features (tag extraction, entity extraction, reflection)
 *       default to {@code true} — always ON when an LLM provider is available.</li>
 *   <li>Infrastructure features (GPU, cluster, connectors, benchmarks)
 *       default to {@code false} — opt-in for alpha.</li>
 *   <li>UI and platform features (visualizations, governance, MCP server)
 *       default to {@code true}.</li>
 * </ul>
 *
 * @param chatEnabled                  chat interface (auto-enabled by Ollama detection)
 * @param agentChatEnabled             autonomous agent chat (auto-enabled by Ollama detection)
 * @param agentWorkspacesEnabled       per-agent workspace isolation
 * @param tagExtractionEnabled         automatic tag extraction from memories
 * @param entityExtractionEnabled      named entity extraction from memories
 * @param reflectionEnabled            agent self-reflection and meta-cognition
 * @param gpuAccelerationEnabled       GPU-accelerated vector operations via Panama
 * @param clusterModeEnabled           multi-node cluster mode
 * @param connectorsEnabled            data connector routing (Camel-based)
 * @param benchmarksEnabled            performance benchmark endpoints
 * @param advancedVisualizationsEnabled cortex UI advanced visualization features
 * @param governanceEnabled            data governance and audit trail
 * @param mcpServerEnabled             Model Context Protocol server
 */
@ConfigurationProperties(prefix = "spector.features")
public record FeatureFlags(
        Boolean chatEnabled,
        Boolean agentChatEnabled,
        Boolean agentWorkspacesEnabled,
        Boolean tagExtractionEnabled,
        Boolean entityExtractionEnabled,
        Boolean reflectionEnabled,
        Boolean gpuAccelerationEnabled,
        Boolean clusterModeEnabled,
        Boolean connectorsEnabled,
        Boolean benchmarksEnabled,
        Boolean advancedVisualizationsEnabled,
        Boolean governanceEnabled,
        Boolean mcpServerEnabled
) {

    /**
     * Compact constructor that applies sensible defaults when Spring
     * does not bind a value (i.e., the parameter is {@code null}).
     */
    public FeatureFlags {
        // LLM-dependent: off by default (auto-enabled by OllamaDetector)
        if (chatEnabled == null) chatEnabled = false;
        if (agentChatEnabled == null) agentChatEnabled = false;

        // Infrastructure: opt-in for alpha
        if (agentWorkspacesEnabled == null) agentWorkspacesEnabled = false;
        if (gpuAccelerationEnabled == null) gpuAccelerationEnabled = false;
        if (clusterModeEnabled == null) clusterModeEnabled = false;
        if (connectorsEnabled == null) connectorsEnabled = false;
        if (benchmarksEnabled == null) benchmarksEnabled = false;

        // Cognitive: ON by default (auto-enabled when LLM provider is present)
        if (tagExtractionEnabled == null) tagExtractionEnabled = true;
        if (entityExtractionEnabled == null) entityExtractionEnabled = true;
        if (reflectionEnabled == null) reflectionEnabled = true;

        // UI and platform: ON by default
        if (advancedVisualizationsEnabled == null) advancedVisualizationsEnabled = true;
        if (governanceEnabled == null) governanceEnabled = true;
        if (mcpServerEnabled == null) mcpServerEnabled = true;
    }

    /**
     * Checks whether the named feature flag is enabled.
     *
     * @param featureName the camelCase flag name (e.g., "chatEnabled")
     * @return {@code true} if the flag is enabled, {@code false} otherwise
     * @throws IllegalArgumentException if the feature name is unknown
     */
    public boolean isEnabled(String featureName) {
        return switch (featureName) {
            case "chatEnabled" -> chatEnabled;
            case "agentChatEnabled" -> agentChatEnabled;
            case "agentWorkspacesEnabled" -> agentWorkspacesEnabled;
            case "tagExtractionEnabled" -> tagExtractionEnabled;
            case "entityExtractionEnabled" -> entityExtractionEnabled;
            case "reflectionEnabled" -> reflectionEnabled;
            case "gpuAccelerationEnabled" -> gpuAccelerationEnabled;
            case "clusterModeEnabled" -> clusterModeEnabled;
            case "connectorsEnabled" -> connectorsEnabled;
            case "benchmarksEnabled" -> benchmarksEnabled;
            case "advancedVisualizationsEnabled" -> advancedVisualizationsEnabled;
            case "governanceEnabled" -> governanceEnabled;
            case "mcpServerEnabled" -> mcpServerEnabled;
            default -> throw new IllegalArgumentException("Unknown feature flag: " + featureName);
        };
    }
}
