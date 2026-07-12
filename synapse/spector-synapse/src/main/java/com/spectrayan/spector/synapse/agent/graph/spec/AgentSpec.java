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

import java.util.List;

/**
 * Agent configuration within a flow spec.
 *
 * @param id           unique agent identifier (referenced by node specs)
 * @param name         human-readable name
 * @param systemPrompt system prompt for the LLM
 * @param instructions additional instructions
 * @param llm          LLM configuration
 * @param tools        tools available to this agent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentSpec(
        String id,
        String name,
        @JsonProperty("system_prompt") String systemPrompt,
        String instructions,
        LlmSpec llm,
        List<ToolSpec> tools
) {
    public AgentSpec {
        if (tools == null) tools = List.of();
    }
}
