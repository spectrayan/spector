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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST API for inspecting feature flags.
 *
 * <p>Exposes the current state of all feature flags and an enriched manifest
 * with metadata (description, category, default value, LLM dependency).
 * No mutation endpoints — flags are internal-only and controlled via
 * {@code application.yml} or environment variables.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/v1/features} — current flag states as a flat JSON map</li>
 *   <li>{@code GET /api/v1/features/manifest} — enriched manifest with metadata</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/features")
public class FeatureFlagController {

    private final FeatureFlags featureFlags;

    /**
     * Constructs the controller with the injected feature flags.
     *
     * @param featureFlags the bound feature flag configuration
     */
    public FeatureFlagController(FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
    }

    /**
     * Returns all feature flags as a flat JSON map.
     *
     * @return map of flag name → enabled state
     */
    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getFeatures() {
        var flags = new LinkedHashMap<String, Boolean>();
        flags.put("chatEnabled", featureFlags.chatEnabled());
        flags.put("agentChatEnabled", featureFlags.agentChatEnabled());
        flags.put("agentWorkspacesEnabled", featureFlags.agentWorkspacesEnabled());
        flags.put("tagExtractionEnabled", featureFlags.tagExtractionEnabled());
        flags.put("entityExtractionEnabled", featureFlags.entityExtractionEnabled());
        flags.put("reflectionEnabled", featureFlags.reflectionEnabled());
        flags.put("gpuAccelerationEnabled", featureFlags.gpuAccelerationEnabled());
        flags.put("clusterModeEnabled", featureFlags.clusterModeEnabled());
        flags.put("connectorsEnabled", featureFlags.connectorsEnabled());
        flags.put("benchmarksEnabled", featureFlags.benchmarksEnabled());
        flags.put("advancedVisualizationsEnabled", featureFlags.advancedVisualizationsEnabled());
        flags.put("governanceEnabled", featureFlags.governanceEnabled());
        flags.put("mcpServerEnabled", featureFlags.mcpServerEnabled());
        return ResponseEntity.ok(flags);
    }

    /**
     * Returns an enriched manifest with metadata for each feature flag.
     *
     * <p>Each entry includes the flag name, human-readable description,
     * category, default value, current value, and whether it requires
     * an LLM provider to function.</p>
     *
     * @return list of feature manifest entries
     */
    @GetMapping("/manifest")
    public ResponseEntity<List<Map<String, Object>>> getManifest() {
        var manifest = List.of(
                manifestEntry("chatEnabled", "Chat interface for LLM conversations",
                        "llm", false, featureFlags.chatEnabled(), true),
                manifestEntry("agentChatEnabled", "Autonomous agent chat with tool use",
                        "llm", false, featureFlags.agentChatEnabled(), true),
                manifestEntry("agentWorkspacesEnabled", "Per-agent workspace isolation",
                        "agent", false, featureFlags.agentWorkspacesEnabled(), false),
                manifestEntry("tagExtractionEnabled", "Automatic tag extraction from memories",
                        "cognitive", true, featureFlags.tagExtractionEnabled(), true),
                manifestEntry("entityExtractionEnabled", "Named entity extraction from memories",
                        "cognitive", true, featureFlags.entityExtractionEnabled(), true),
                manifestEntry("reflectionEnabled", "Agent self-reflection and meta-cognition",
                        "cognitive", true, featureFlags.reflectionEnabled(), true),
                manifestEntry("gpuAccelerationEnabled", "GPU-accelerated vector operations via Panama",
                        "infrastructure", false, featureFlags.gpuAccelerationEnabled(), false),
                manifestEntry("clusterModeEnabled", "Multi-node cluster mode",
                        "infrastructure", false, featureFlags.clusterModeEnabled(), false),
                manifestEntry("connectorsEnabled", "Data connector routing (Camel-based)",
                        "integration", false, featureFlags.connectorsEnabled(), false),
                manifestEntry("benchmarksEnabled", "Performance benchmark endpoints",
                        "infrastructure", false, featureFlags.benchmarksEnabled(), false),
                manifestEntry("advancedVisualizationsEnabled", "Cortex UI advanced visualization features",
                        "ui", true, featureFlags.advancedVisualizationsEnabled(), false),
                manifestEntry("governanceEnabled", "Data governance and audit trail",
                        "platform", true, featureFlags.governanceEnabled(), false),
                manifestEntry("mcpServerEnabled", "Model Context Protocol server",
                        "platform", true, featureFlags.mcpServerEnabled(), false)
        );
        return ResponseEntity.ok(manifest);
    }

    /**
     * Builds a single manifest entry map.
     */
    private static Map<String, Object> manifestEntry(String name, String description,
                                                      String category, boolean defaultValue,
                                                      boolean currentValue, boolean requiresLlm) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("name", name);
        entry.put("description", description);
        entry.put("category", category);
        entry.put("defaultValue", defaultValue);
        entry.put("currentValue", currentValue);
        entry.put("requiresLlm", requiresLlm);
        return entry;
    }
}
