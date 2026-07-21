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
package com.spectrayan.spector.synapse.agent.tools;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import io.modelcontextprotocol.spec.McpSchema;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Queries JSON data using a simple dot-notation path (e.g., "data.items[0].name").
 *
 * <p>Supports array indexing with [N] and wildcard with [*].</p>
 */
@Component
public class JsonQueryTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonQueryTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override public String name() { return "json_query"; }

    @Override public String description() {
        return "Query JSON data using a dot-notation path. Supports array indexing [N] and wildcard [*].";
    }

    @Override public McpToolCategory category() { return McpToolCategory.DATA; }
    @Override public boolean isWriteTool() { return false; }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "json", Map.of("type", "string", "description", "JSON string to query"),
                        "path", Map.of("type", "string", "description", "Dot-notation path (e.g., 'data.items[0].name', 'users[*].email')")
                ),
                "required", List.of("json", "path")
        );
    }

    @Override
    public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(com.spectrayan.spector.runtime.SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> args) throws Exception {
        var jsonStr = (String) args.get("json");
        var path = (String) args.get("path");
        if (jsonStr == null || jsonStr.isBlank()) return "Error: Missing required argument: json";
        if (path == null || path.isBlank()) return "Error: Missing required argument: path";

        try {
            var root = MAPPER.readTree(jsonStr);
            var result = navigate(root, path);
            log.debug("[JsonQueryTool] path={} → found {} node(s)", path, result != null ? 1 : 0);
            return result != null ? result.toPrettyString() : "null (path not found: " + path + ")";
        } catch (Exception e) {
            return "Error parsing JSON: " + e.getMessage();
        }
    }

    private static JsonNode navigate(JsonNode node, String path) {
        String[] segments = path.split("\\.");
        JsonNode current = node;

        for (String segment : segments) {
            if (current == null || current.isMissingNode()) return null;

            var arrayMatch = Pattern.compile("(.+)\\[(\\d+|\\*)]").matcher(segment);
            if (arrayMatch.matches()) {
                String field = arrayMatch.group(1);
                String index = arrayMatch.group(2);
                current = current.get(field);
                if (current == null || !current.isArray()) return null;

                if ("*".equals(index)) {
                    var result = MAPPER.createArrayNode();
                    for (JsonNode element : current) result.add(element);
                    current = result;
                } else {
                    current = current.get(Integer.parseInt(index));
                }
            } else {
                current = current.get(segment);
            }
        }
        return current;
    }
}
