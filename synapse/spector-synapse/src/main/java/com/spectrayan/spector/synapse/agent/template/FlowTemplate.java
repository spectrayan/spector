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
package com.spectrayan.spector.synapse.agent.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A pre-built FlowSpec template for common agent workflows.
 *
 * <p>Templates are loaded from JSON resources on the classpath and can be
 * customized at runtime by overriding specific fields before compilation
 * into a LangGraph4j StateGraph via {@link com.spectrayan.spector.synapse.agent.graph.DynamicGraphBuilder}.</p>
 *
 * @param id          unique template identifier (e.g., "research-agent")
 * @param name        human-readable display name
 * @param description what this agent workflow does
 * @param category    template category (research, code, data, content, support)
 * @param tags        searchable tags
 * @param flowSpec    the raw FlowSpec JSON that can be compiled by DynamicGraphBuilder
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowTemplate(
        String id,
        String name,
        String description,
        String category,
        List<String> tags,
        Map<String, Object> flowSpec
) {
    /** Compact constructor — defaults for null collections. */
    public FlowTemplate {
        if (tags == null) tags = List.of();
        if (flowSpec == null) flowSpec = Map.of();
    }

    /**
     * Load a template from a classpath JSON resource.
     *
     * @param resourcePath path relative to classpath root (e.g., "templates/research-agent.json")
     * @return the parsed FlowTemplate
     */
    public static FlowTemplate fromClasspath(String resourcePath) throws IOException {
        try (var is = FlowTemplate.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Template resource not found: " + resourcePath);
            return new ObjectMapper().readValue(is, FlowTemplate.class);
        }
    }
}
