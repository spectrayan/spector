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

import java.util.Map;
import java.util.Set;

/**
 * Immutable declaration of an MCP tool specification loaded from declarative JSON resources.
 *
 * @param name         unique tool name (e.g., {@code "memory_remember"})
 * @param description  detailed LLM prompt description explaining behavior, parameters, and return values
 * @param category     tool classification category (e.g. {@code "MEMORY"}, {@code "SEARCH"}, {@code "COGNITIVE"})
 * @param scopes       required authorization scopes (e.g. {@code ["memory:write"]})
 * @param inputSchema  JSON Schema object describing the tool parameters
 * @param outputSchema optional JSON Schema describing return output structure
 */
public record McpToolSpec(
        String name,
        String description,
        String category,
        Set<String> scopes,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema
) {
    public McpToolSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool spec name must not be blank");
        }
        if (description == null) {
            description = "";
        }
        if (category == null || category.isBlank()) {
            category = "MEMORY";
        }
        if (scopes == null) {
            scopes = Set.of();
        } else {
            scopes = Set.copyOf(scopes);
        }
        if (inputSchema == null) {
            inputSchema = Map.of("type", "object");
        } else {
            inputSchema = Map.copyOf(inputSchema);
        }
        if (outputSchema != null) {
            outputSchema = Map.copyOf(outputSchema);
        }
    }
}
