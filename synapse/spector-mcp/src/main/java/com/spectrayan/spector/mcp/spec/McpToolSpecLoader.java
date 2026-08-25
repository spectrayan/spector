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
package com.spectrayan.spector.mcp.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, thread-safe loader that discovers, parses, and caches MCP tool JSON specifications
 * from the classpath resource path {@code /mcp/tools/*.json}.
 */
public final class McpToolSpecLoader {

    private static final Logger log = LoggerFactory.getLogger(McpToolSpecLoader.class);
    private static final String SPEC_DIR = "mcp/tools/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, McpToolSpec> CACHE = new ConcurrentHashMap<>();

    private McpToolSpecLoader() {}

    /**
     * Loads the specification for the given tool name from {@code mcp/tools/{name}.json}.
     *
     * @param toolName the name of the tool (e.g. {@code "memory_remember"})
     * @return the parsed {@link McpToolSpec}
     * @throws IllegalArgumentException if the tool specification cannot be found or parsed
     */
    public static McpToolSpec load(String toolName) {
        return find(toolName).orElseThrow(() -> new IllegalArgumentException(
                "MCP tool specification not found for '" + toolName + "' at classpath resource '" + SPEC_DIR + toolName + ".json'"));
    }

    /**
     * Finds and parses the specification for the given tool name, returning {@link Optional#empty()} if not found.
     *
     * @param toolName the name of the tool
     * @return optional containing the tool spec if found
     */
    public static Optional<McpToolSpec> find(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        McpToolSpec cached = CACHE.get(toolName);
        if (cached != null) {
            return Optional.of(cached);
        }

        String resourcePath = SPEC_DIR + toolName + ".json";
        try (InputStream in = McpToolSpecLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.debug("[McpToolSpecLoader] No specification found for tool '{}' at '{}'", toolName, resourcePath);
                return Optional.empty();
            }

            JsonNode root = MAPPER.readTree(in);
            String name = root.hasNonNull("name") ? root.get("name").asText() : toolName;
            String description = root.hasNonNull("description") ? root.get("description").asText() : "";
            String category = root.hasNonNull("category") ? root.get("category").asText() : "MEMORY";

            Set<String> scopes = new HashSet<>();
            if (root.has("scopes") && root.get("scopes").isArray()) {
                for (JsonNode scopeNode : root.get("scopes")) {
                    scopes.add(scopeNode.asText());
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = root.has("inputSchema")
                    ? MAPPER.convertValue(root.get("inputSchema"), Map.class)
                    : Map.of("type", "object");

            @SuppressWarnings("unchecked")
            Map<String, Object> outputSchema = root.has("outputSchema")
                    ? MAPPER.convertValue(root.get("outputSchema"), Map.class)
                    : null;

            McpToolSpec spec = new McpToolSpec(name, description, category, scopes, inputSchema, outputSchema);
            CACHE.put(toolName, spec);
            log.trace("[McpToolSpecLoader] Successfully loaded spec for tool '{}'", toolName);
            return Optional.of(spec);
        } catch (Exception e) {
            log.error("[McpToolSpecLoader] Failed to parse tool specification for '{}' at '{}': {}",
                    toolName, resourcePath, e.getMessage(), e);
            throw new IllegalStateException("Failed to parse MCP tool spec for '" + toolName + "': " + e.getMessage(), e);
        }
    }
}
