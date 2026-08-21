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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.GraphRecallOptions;
import com.spectrayan.spector.memory.model.GraphTraversalResult;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_graph_recall} — multi-hop entity relationship traversal (GraphRAG).
 *
 * <p>Traverses Spector's knowledge graph across explicit temporal facts and hypergraph
 * co-occurrences up to {@code max_hops} deep, surfacing relational paths and grounding
 * cognitive memory context for complex reasoning questions.</p>
 */
public final class MemoryGraphRecallTool extends MemoryToolHandler {

    private static final int DEFAULT_MAX_HOPS = 3;
    private static final int DEFAULT_TOP_PATHS = 10;

    public MemoryGraphRecallTool(SpectorMemory memory) {
        super(memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryGraphRecallTool(Supplier<SpectorMemory> memoryResolver) {
        super(memoryResolver);
    }

    @Override public String name() { return "memory_graph_recall"; }

    @Override public Set<String> requiredScopes() { return Set.of(SpectorScopes.MEMORY_READ); }

    @Override
    public String description() {
        return "Traverse Spector's knowledge graph across multi-hop entity relationships (GraphRAG). "
                + "Discovers connections between entities through explicit facts (TemporalKnowledgeGraph) "
                + "and co-occurrence hyperedges (HyperEntityGraph). Supports start entity targeting, "
                + "pathfinding to a target entity, max hops bounding (1-5), entity type filtering "
                + "(e.g., 'PERSON,ORGANIZATION,PROJECT'), and relation type filtering (e.g., 'works_at,leads'). "
                + "Returns structured relational paths grounded in supporting cognitive memory text.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemaBuilder.object()
                .optionalString("start_entity",
                        "Focal entity name to begin traversal from (e.g. 'Alice', 'Sarah Chen'). "
                        + "If omitted, entities are inferred from 'query'.", "")
                .optionalString("query",
                        "Natural language query or keywords used to discover starting entities when "
                        + "'start_entity' is not provided.", "")
                .optionalString("target_entity",
                        "Optional destination entity to find relational path(s) to (point-to-point traversal).", "")
                .optionalInt("max_hops",
                        "Maximum traversal depth in hops (1-5, default: 3).", DEFAULT_MAX_HOPS)
                .optionalString("entity_types",
                        "Comma-separated list of entity types to filter by (e.g. 'PERSON,PROJECT,ORGANIZATION').", "")
                .optionalString("relation_types",
                        "Comma-separated list of relation / predicate types to filter by (e.g. 'works_at,leads,located_in').", "")
                .optionalBoolean("include_memories",
                        "Whether to include grounding text excerpts from linked cognitive memories (default: true).", true)
                .optionalInt("top_paths",
                        "Maximum number of discovered relational paths to return (1-50, default: 10).", DEFAULT_TOP_PATHS)
                .optionalString("as_of", "ISO-8601 timestamp for point-in-time temporal queries (e.g., '2025-06-15T00:00:00Z'). When provided, only facts valid at this timestamp are traversed.", "")
                .optionalBoolean("include_superseded", "Whether to include superseded (retracted) facts in traversal (default: false).", false)
                .build();
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        // 1. Parse MCP arguments into GraphRecallOptions
        var builder = GraphRecallOptions.builder();
        
        String startEntity = optionalString(args, "start_entity", "").strip();
        if (!startEntity.isBlank()) builder.startEntity(startEntity);
        
        String query = optionalString(args, "query", "").strip();
        if (!query.isBlank()) builder.query(query);
        
        String targetEntity = optionalString(args, "target_entity", "").strip();
        if (!targetEntity.isBlank()) builder.targetEntity(targetEntity);
        
        builder.maxHops(optionalInt(args, "max_hops", 3));
        builder.topPaths(optionalInt(args, "top_paths", 10));
        builder.includeMemories(optionalBoolean(args, "include_memories", true));
        
        Set<String> entityTypes = parseFilters(optionalString(args, "entity_types", ""));
        if (!entityTypes.isEmpty()) builder.entityTypeFilters(entityTypes);
        
        Set<String> relationTypes = parseFilters(optionalString(args, "relation_types", ""));
        if (!relationTypes.isEmpty()) builder.relationTypeFilters(relationTypes);
        
        // Parse temporal parameters
        String asOfStr = optionalString(args, "as_of", "").strip();
        if (!asOfStr.isBlank()) {
            builder.asOf(java.time.Instant.parse(asOfStr));
        }
        builder.includeSuperseded(optionalBoolean(args, "include_superseded", false));
        
        // 2. Execute graph recall via public API
        GraphTraversalResult result = memory.graphRecall(builder.build());
        
        // 3. Format result for MCP output
        return textResult(formatResult(result));
    }

    private String formatResult(GraphTraversalResult result) {
        if (result.isError()) {
            return "❌ Graph traversal failed: " + result.error();
        }
        
        var sb = new StringBuilder();
        sb.append("🕸️ **Knowledge Graph Traversal Results** (").append(result.elapsedMs()).append("ms)\n\n");
        sb.append("**Start Entity:** `").append(result.startEntityName()).append("` [").append(result.startEntityType()).append("]\n");
        if (result.targetEntityName() != null) {
            sb.append("**Target Entity:** `").append(result.targetEntityName()).append("` [").append(result.targetEntityType()).append("]\n");
        }
        sb.append("**Max Traversal Depth:** ").append(result.maxHops()).append(" hops\n");
        sb.append("**Discovered Entities:** ").append(result.entityCount()).append("\n");
        sb.append("**Discovered Paths:** ").append(result.pathCount()).append("\n\n");
        
        // Entities
        sb.append("### 🏷️ Discovered Entities\n");
        for (var entity : result.discoveredEntities()) {
            sb.append("- **").append(entity.name()).append("** [").append(entity.type()).append("]")
                    .append(" (").append(entity.memoryRefCount()).append(" memory reference")
                    .append(entity.memoryRefCount() == 1 ? "" : "s").append(")\n");
        }
        sb.append("\n");
        
        // Paths
        sb.append("### 🔗 Relational Paths\n");
        if (result.discoveredPaths().isEmpty()) {
            sb.append("_No relational paths found matching the query constraints._\n\n");
        } else {
            int pathIdx = 1;
            for (var path : result.discoveredPaths()) {
                sb.append(pathIdx++).append(". ");
                for (int i = 0; i < path.nodes().size(); i++) {
                    var node = path.nodes().get(i);
                    sb.append("`").append(node.entityName()).append("` (").append(node.entityType()).append(")");
                    if (i < path.nodes().size() - 1) {
                        var nextNode = path.nodes().get(i + 1);
                        if (nextNode.relation() != null && !nextNode.relation().isBlank()) {
                            sb.append(" ──[").append(nextNode.relation()).append("]──> ");
                        } else {
                            sb.append(" ──> ");
                        }
                    }
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // Grounding memories
        if (!result.groundingMemories().isEmpty()) {
            sb.append("### 🧠 Grounding Memory Context\n");
            for (var gm : result.groundingMemories()) {
                sb.append("- **[").append(gm.id()).append("]** (").append(gm.memoryType()).append("): ")
                        .append("\"").append(gm.textExcerpt()).append("\"\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    private Set<String> parseFilters(String filterString) {
        if (filterString == null || filterString.isBlank()) return Set.of();
        return Arrays.stream(filterString.split("\\s*,\\s*"))
                .map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "…";
    }
}
