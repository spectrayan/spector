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
package com.spectrayan.spector.synapse.agent.graph.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level graph specification — the JSON contract for defining agent workflows.
 *
 * <p>This is the Java equivalent of the {@code FlowSpec.json} schema.
 * An LLM can generate this JSON dynamically, and the {@code DynamicGraphBuilder}
 * compiles it into a LangGraph4j {@code StateGraph}.</p>
 *
 * <h3>Example JSON</h3>
 * <pre>{@code
 * {
 *   "version": "1.0",
 *   "id": "research-agent",
 *   "name": "Research Assistant",
 *   "entry_point": "search",
 *   "nodes": { "search": { "type": "TOOL", "tool_name": "web_search" } },
 *   "edges": [{ "from": "search", "to": "summarize" }],
 *   "agents": [{ "id": "summarizer", "system_prompt": "..." }]
 * }
 * }</pre>
 *
 * @param version          schema version (default "1.0")
 * @param id               unique flow identifier
 * @param name             human-readable name
 * @param entryPoint       name of the first node to execute
 * @param nodes            named node definitions
 * @param edges            simple edges connecting nodes
 * @param conditionalEdges edges with routing logic
 * @param agents           agent configurations (LLM + tools)
 * @param policy           execution policy (timeouts, max iterations)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowSpec(
        String version,
        String id,
        String name,
        @JsonProperty("entry_point") String entryPoint,
        Map<String, NodeSpec> nodes,
        List<EdgeSpec> edges,
        @JsonProperty("conditional_edges") List<ConditionalEdgeSpec> conditionalEdges,
        List<AgentSpec> agents,
        ExecutionPolicy policy
) {

    /** Current schema version. */
    public static final String CURRENT_VERSION = "1.0";

    /** Set of schema versions this engine can compile. */
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("1.0");

    private static final Logger log = LoggerFactory.getLogger(FlowSpec.class);

    /** Compact constructor — defaults for null collections and version validation. */
    public FlowSpec {
        if (version == null || version.isBlank()) version = CURRENT_VERSION;
        if (!SUPPORTED_VERSIONS.contains(version)) {
            log.warn("[FlowSpec] Unknown schema version '{}' (supported: {}). " +
                    "Proceeding with best-effort compilation.", version, SUPPORTED_VERSIONS);
        }
        if (nodes == null) nodes = Map.of();
        if (edges == null) edges = List.of();
        if (conditionalEdges == null) conditionalEdges = List.of();
        if (agents == null) agents = List.of();
        if (policy == null) policy = ExecutionPolicy.defaults();
    }

    /** Lookup an agent spec by ID. */
    public AgentSpec agentById(String agentId) {
        return agents.stream()
                .filter(a -> a.id().equals(agentId))
                .findFirst()
                .orElse(null);
    }

    /** Returns true if this spec uses a supported schema version. */
    public boolean isSupportedVersion() {
        return SUPPORTED_VERSIONS.contains(version);
    }
}
