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
 * Tool specification within an agent.
 *
 * @param id         unique tool identifier
 * @param type       tool type: JAVA, MCP
 * @param name       tool display name
 * @param className  Java class name for JAVA tools (looked up via ToolRegistry)
 * @param params     additional parameters
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolSpec(
        String id,
        ToolType type,
        String name,
        @JsonProperty("class_name") String className,
        Map<String, Object> params
) {
    public ToolSpec {
        if (type == null) type = ToolType.JAVA;
        if (params == null) params = Map.of();
    }

    public enum ToolType { JAVA, MCP }
}
