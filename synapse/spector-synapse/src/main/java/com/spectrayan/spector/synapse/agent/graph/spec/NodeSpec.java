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

import java.util.Map;

/**
 * Specification for a single node in the graph.
 *
 * @param type          node type: AGENT, TOOL, FUNCTION, SUBGRAPH, END
 * @param description   human-readable description
 * @param agent         agent ID reference (for AGENT type)
 * @param toolName      tool name from registry (for TOOL type)
 * @param subgraph      inline or referenced FlowSpec ID (for SUBGRAPH type)
 * @param timeoutMs     per-node timeout in milliseconds
 * @param retryPolicy   retry configuration on failure
 * @param inputParams   static parameters passed to the node
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NodeSpec(
        NodeType type,
        String description,
        String agent,
        @JsonProperty("tool_name") String toolName,
        String subgraph,
        @JsonProperty("timeout_ms") int timeoutMs,
        @JsonProperty("retry_policy") RetryPolicy retryPolicy,
        @JsonProperty("input_params") Map<String, Object> inputParams
) {
    public NodeSpec {
        if (timeoutMs <= 0) timeoutMs = 60_000;
        if (inputParams == null) inputParams = Map.of();
    }

    public enum NodeType {
        AGENT, TOOL, FUNCTION, SUBGRAPH, END
    }

    /**
     * Retry configuration for node failures.
     *
     * @param maxRetries         maximum retry attempts
     * @param retryDelayMs       initial delay between retries
     * @param exponentialBackoff whether to use exponential backoff
     */
    public record RetryPolicy(
            @JsonProperty("max_retries") int maxRetries,
            @JsonProperty("retry_delay_ms") int retryDelayMs,
            @JsonProperty("exponential_backoff") boolean exponentialBackoff
    ) {
        public static RetryPolicy none() {
            return new RetryPolicy(0, 0, false);
        }
    }
}
