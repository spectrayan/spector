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
package com.spectrayan.spector.mcp.tools.memory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.mcp.util.McpTemplateEngine;
import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_associate} — explicit association creation in cognitive graph (#969).
 *
 * <p>Supports three association planes:
 * <ul>
 *   <li>{@code hebbian} (default): Bidirectional co-activation edge between two memories for spreading activation.</li>
 *   <li>{@code temporal}: Session-local sequential predecessor to successor causal link.</li>
 *   <li>{@code hyperedge}: N-ary multi-entity co-occurrence relation, optionally anchored to a memory.</li>
 * </ul>
 * </p>
 */
public final class MemoryAssociateTool extends MemoryToolHandler {

    public static final String NAME = "memory_associate";

    public MemoryAssociateTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryAssociateTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                     Map<String, Object> args) throws Exception {
        String type = optionalString(args, "type", "hebbian").strip().toLowerCase(Locale.ROOT);

        return switch (type) {
            case "hebbian" -> executeHebbian(memory, args);
            case "temporal" -> executeTemporal(memory, args);
            case "hyperedge" -> executeHyperedge(memory, args);
            default -> errorResult("Unsupported association type: '" + type
                    + "'. Expected one of: 'hebbian', 'temporal', 'hyperedge'.");
        };
    }

    private McpSchema.CallToolResult executeHebbian(SpectorMemory memory, Map<String, Object> args) {
        String sourceId = requireString(args, "source_id");
        String targetId = requireString(args, "target_id");
        float weight = optionalFloat(args, "weight", 1.0f);

        boolean ok = memory.associateHebbian(sourceId, targetId, weight);
        if (!ok) {
            return errorResult("Failed to create Hebbian association: one or both memories not found in index ('"
                    + sourceId + "', '" + targetId + "').");
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("isHebbian", true);
        model.put("sourceId", sourceId);
        model.put("targetId", targetId);
        model.put("weight", weight);

        return textResult(McpTemplateEngine.render("memory-associate", model));
    }

    private McpSchema.CallToolResult executeTemporal(SpectorMemory memory, Map<String, Object> args) {
        String sourceId = requireString(args, "source_id");
        String targetId = requireString(args, "target_id");
        int sessionId = optionalInt(args, "session_id", 0);

        boolean ok = memory.associateTemporal(sourceId, targetId, sessionId);
        if (!ok) {
            return errorResult("Failed to create temporal association: one or both memories not found in index ('"
                    + sourceId + "', '" + targetId + "').");
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("isTemporal", true);
        model.put("sourceId", sourceId);
        model.put("targetId", targetId);
        model.put("sessionId", sessionId);

        return textResult(McpTemplateEngine.render("memory-associate", model));
    }

    private McpSchema.CallToolResult executeHyperedge(SpectorMemory memory, Map<String, Object> args) {
        List<String> entityNames = parseEntities(args.get("entities"));
        if (entityNames.size() < 2) {
            return errorResult("Hyperedge association requires at least 2 entities, got " + entityNames.size() + ".");
        }

        String memoryId = optionalString(args, "memory_id", "").strip();
        float weight = optionalFloat(args, "weight", 1.0f);

        int edgeId = memory.associateHyperedge(entityNames, memoryId.isBlank() ? null : memoryId, weight);
        if (edgeId < 0) {
            return errorResult("Failed to create hyperedge: graph capacity exceeded or entity directory unavailable.");
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("isHyperedge", true);
        model.put("edgeId", edgeId);
        model.put("entitiesStr", String.join(", ", entityNames));
        if (!memoryId.isBlank()) {
            model.put("memoryId", memoryId);
        }
        model.put("weight", weight);

        return textResult(McpTemplateEngine.render("memory-associate", model));
    }

    private static List<String> parseEntities(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(String::strip)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        if (raw instanceof String str) {
            if (str.isBlank()) return List.of();
            return Arrays.stream(str.split("\\s*,\\s*"))
                    .map(String::strip)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }
}
