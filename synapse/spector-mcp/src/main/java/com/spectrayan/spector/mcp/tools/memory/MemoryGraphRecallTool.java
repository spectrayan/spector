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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdge;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdgeVertex;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.temporal.TemporalFact;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

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
    private static final int MIN_HOPS = 1;
    private static final int MAX_HOPS = 5;
    private static final int DEFAULT_TOP_PATHS = 10;
    private static final int MAX_EXPLORED_PATHS = 100;

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
                .build();
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        if (memory.admin() == null) {
            return errorResult("Spector memory administrative interface is not available.");
        }

        EntityDirectory entityDirectory = memory.admin().entityDirectory();
        if (entityDirectory == null) {
            return errorResult("Entity graph directory is not configured or entity extraction is disabled.");
        }

        HyperEntityGraphMemory hyperEntityGraph = memory.admin().hyperEntityGraph();
        TemporalKnowledgeGraph temporalKnowledgeGraph = memory.admin().temporalKnowledgeGraph();
        MemoryIndex memoryIndex = memory.admin().index();

        String startEntityName = optionalString(args, "start_entity", "").strip();
        String queryString = optionalString(args, "query", "").strip();
        String targetEntityName = optionalString(args, "target_entity", "").strip();
        int maxHops = Math.max(MIN_HOPS, Math.min(MAX_HOPS, optionalInt(args, "max_hops", DEFAULT_MAX_HOPS)));
        int topPaths = Math.max(1, Math.min(50, optionalInt(args, "top_paths", DEFAULT_TOP_PATHS)));
        boolean includeMemories = optionalBoolean(args, "include_memories", true);

        Set<String> entityTypeFilters = parseFilters(optionalString(args, "entity_types", ""));
        Set<String> relationTypeFilters = parseFilters(optionalString(args, "relation_types", ""));

        // 1. Resolve Start Entity
        Integer startEntityId = resolveEntityId(entityDirectory, startEntityName);
        if (startEntityId == null && !queryString.isBlank()) {
            startEntityId = inferEntityIdFromQuery(entityDirectory, queryString);
        }

        if (startEntityId == null) {
            return textResult(formatEntityNotFoundResult(entityDirectory, startEntityName, queryString));
        }

        // 2. Resolve Target Entity (if requested)
        Integer targetEntityId = null;
        if (!targetEntityName.isBlank()) {
            targetEntityId = resolveEntityId(entityDirectory, targetEntityName);
            if (targetEntityId == null) {
                return textResult("Target entity '" + targetEntityName + "' was not found in the entity directory.");
            }
        }

        // 3. Build Slot-to-ID mappings for memory index
        Map<Integer, String> slotToId = new LinkedHashMap<>();
        Map<String, Integer> idToSlot = new LinkedHashMap<>();
        if (memoryIndex != null) {
            memoryIndex.buildGraphSlotMappings(slotToId, idToSlot);
        }

        // 4. Perform Multi-Hop BFS Traversal
        long startTime = System.currentTimeMillis();
        TraversalResult traversal = performBfsTraversal(
                entityDirectory,
                hyperEntityGraph,
                temporalKnowledgeGraph,
                startEntityId,
                targetEntityId,
                maxHops,
                topPaths,
                entityTypeFilters,
                relationTypeFilters);
        long elapsedMs = System.currentTimeMillis() - startTime;

        // 5. Format Output
        String formattedOutput = formatTraversalResult(
                memory,
                entityDirectory,
                slotToId,
                traversal,
                startEntityId,
                targetEntityId,
                maxHops,
                includeMemories,
                elapsedMs);

        return textResult(formattedOutput);
    }

    // ═══════════════════════════════════════════════════════════════
    // TRAVERSAL ENGINE
    // ═══════════════════════════════════════════════════════════════

    record TraversalStep(int fromEntityId, int toEntityId, String relation, String source) {}

    record TraversalPath(List<Integer> entityIds, List<TraversalStep> steps, Set<Integer> memorySlots) {
        int length() { return steps.size(); }
        int lastEntityId() { return entityIds.get(entityIds.size() - 1); }
        boolean containsEntity(int entityId) { return entityIds.contains(entityId); }
    }

    record TraversalResult(Set<Integer> discoveredEntities, List<TraversalPath> discoveredPaths) {}

    private TraversalResult performBfsTraversal(
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            int startEntityId,
            Integer targetEntityId,
            int maxHops,
            int topPaths,
            Set<String> entityTypeFilters,
            Set<String> relationTypeFilters) {

        Set<Integer> discoveredEntities = new LinkedHashSet<>();
        discoveredEntities.add(startEntityId);

        List<TraversalPath> completedPaths = new ArrayList<>();
        Queue<TraversalPath> queue = new ArrayDeque<>();

        // Initial 0-hop path
        Set<Integer> initialMems = new HashSet<>();
        for (int m : entityDirectory.memoriesForEntity(startEntityId)) {
            if (m >= 0) initialMems.add(m);
        }
        queue.add(new TraversalPath(List.of(startEntityId), List.of(), initialMems));

        Set<Integer> retractedFactIds = (temporalKnowledgeGraph != null)
                ? temporalKnowledgeGraph.retractedFactIds()
                : Set.of();

        while (!queue.isEmpty() && completedPaths.size() < MAX_EXPLORED_PATHS) {
            TraversalPath currentPath = queue.poll();
            int currentEntityId = currentPath.lastEntityId();

            if (currentPath.length() >= maxHops) {
                continue;
            }

            // Layer 1: TemporalKnowledgeGraph Triples (Explicit Facts)
            if (temporalKnowledgeGraph != null) {
                List<TemporalFact> facts = temporalKnowledgeGraph.readFactsForEntity(currentEntityId);
                if (facts != null) {
                    var predRegistry = temporalKnowledgeGraph.predicateRegistry();
                    for (TemporalFact fact : facts) {
                        if (fact.isRetraction() || retractedFactIds.contains(fact.factId())) {
                            continue;
                        }

                        int subjectId = fact.subjectEntityId();
                        int objectId = fact.objectEntityId();

                        // Determine neighbor entity and direction
                        int neighborId;
                        String relName;
                        if (currentEntityId == subjectId) {
                            neighborId = objectId;
                            relName = predRegistry != null ? predRegistry.nameOf((int) fact.predicateId()) : "RELATED_TO";
                        } else if (currentEntityId == objectId) {
                            neighborId = subjectId;
                            String basePred = predRegistry != null ? predRegistry.nameOf((int) fact.predicateId()) : "RELATED_TO";
                            relName = "INVERSE_" + basePred;
                        } else {
                            continue;
                        }

                        if (relName == null || relName.isBlank()) relName = "RELATED_TO";

                        // Filter by relation type
                        if (relationTypeFilters != null && !relationTypeFilters.contains(relName.toLowerCase(Locale.ROOT))) {
                            continue;
                        }

                        // Avoid cycles in path
                        if (currentPath.containsEntity(neighborId)) {
                            continue;
                        }

                        // Filter by neighbor entity type
                        String neighborType = safeEntityType(entityDirectory, neighborId);
                        if (entityTypeFilters != null && !entityTypeFilters.contains(neighborType.toLowerCase(Locale.ROOT))) {
                            continue;
                        }

                        // Extend path
                        List<Integer> nextEntities = new ArrayList<>(currentPath.entityIds());
                        nextEntities.add(neighborId);

                        List<TraversalStep> nextSteps = new ArrayList<>(currentPath.steps());
                        nextSteps.add(new TraversalStep(currentEntityId, neighborId, relName, "FACT"));

                        Set<Integer> nextMems = new HashSet<>(currentPath.memorySlots());
                        for (int m : entityDirectory.memoriesForEntity(neighborId)) {
                            if (m >= 0) nextMems.add(m);
                        }

                        TraversalPath extended = new TraversalPath(nextEntities, nextSteps, nextMems);
                        discoveredEntities.add(neighborId);
                        completedPaths.add(extended);

                        // If target entity specified and reached, stop branching along this path
                        if (targetEntityId != null && neighborId == targetEntityId) {
                            continue;
                        }

                        queue.add(extended);
                    }
                }
            }

            // Layer 2: HyperEntityGraph Co-occurrences
            if (hyperEntityGraph != null) {
                List<HyperEdge> hyperedges = hyperEntityGraph.findHyperedgesForEntity(currentEntityId);
                if (hyperedges != null) {
                    for (HyperEdge he : hyperedges) {
                        int memIdx = he.memoryIdx();
                        List<HyperEdgeVertex> vertices = he.vertices();
                        if (vertices == null) continue;

                        for (HyperEdgeVertex v : vertices) {
                            int neighborId = v.entityId();
                            if (neighborId == currentEntityId || currentPath.containsEntity(neighborId)) {
                                continue;
                            }

                            String relName = "SHARED_MEMORY";
                            if (relationTypeFilters != null && !relationTypeFilters.contains(relName.toLowerCase(Locale.ROOT))) {
                                continue;
                            }

                            String neighborType = safeEntityType(entityDirectory, neighborId);
                            if (entityTypeFilters != null && !entityTypeFilters.contains(neighborType.toLowerCase(Locale.ROOT))) {
                                continue;
                            }

                            List<Integer> nextEntities = new ArrayList<>(currentPath.entityIds());
                            nextEntities.add(neighborId);

                            List<TraversalStep> nextSteps = new ArrayList<>(currentPath.steps());
                            nextSteps.add(new TraversalStep(currentEntityId, neighborId, relName, "HYPEREDGE"));

                            Set<Integer> nextMems = new HashSet<>(currentPath.memorySlots());
                            if (memIdx >= 0) nextMems.add(memIdx);
                            for (int m : entityDirectory.memoriesForEntity(neighborId)) {
                                if (m >= 0) nextMems.add(m);
                            }

                            TraversalPath extended = new TraversalPath(nextEntities, nextSteps, nextMems);
                            discoveredEntities.add(neighborId);
                            completedPaths.add(extended);

                            if (targetEntityId != null && neighborId == targetEntityId) {
                                continue;
                            }

                            queue.add(extended);
                        }
                    }
                }
            }
        }

        // Filter and rank paths
        List<TraversalPath> filteredPaths = completedPaths;
        if (targetEntityId != null) {
            filteredPaths = completedPaths.stream()
                    .filter(p -> p.lastEntityId() == targetEntityId)
                    .collect(Collectors.toList());
        }

        // Sort by shortest path first, then by memory evidence count descending
        filteredPaths.sort(Comparator.comparingInt(TraversalPath::length)
                .thenComparing((TraversalPath p) -> -p.memorySlots().size()));

        if (filteredPaths.size() > topPaths) {
            filteredPaths = filteredPaths.subList(0, topPaths);
        }

        return new TraversalResult(discoveredEntities, filteredPaths);
    }

    // ═══════════════════════════════════════════════════════════════
    // FORMATTING & RESOLUTION HELPERS
    // ═══════════════════════════════════════════════════════════════

    private String formatTraversalResult(
            SpectorMemory memory,
            EntityDirectory entityDirectory,
            Map<Integer, String> slotToId,
            TraversalResult traversal,
            int startEntityId,
            Integer targetEntityId,
            int maxHops,
            boolean includeMemories,
            long elapsedMs) {

        var sb = new StringBuilder();
        String startName = entityDirectory.entityName(startEntityId);
        String startType = safeEntityType(entityDirectory, startEntityId);

        sb.append("🕸️ **Knowledge Graph Traversal Results** (").append(elapsedMs).append("ms)\n\n");
        sb.append("**Start Entity:** `").append(startName).append("` [").append(startType).append("]\n");
        if (targetEntityId != null) {
            String targetName = entityDirectory.entityName(targetEntityId);
            String targetType = safeEntityType(entityDirectory, targetEntityId);
            sb.append("**Target Entity:** `").append(targetName).append("` [").append(targetType).append("]\n");
        }
        sb.append("**Max Traversal Depth:** ").append(maxHops).append(" hops\n");
        sb.append("**Discovered Entities:** ").append(traversal.discoveredEntities().size()).append("\n");
        sb.append("**Discovered Paths:** ").append(traversal.discoveredPaths().size()).append("\n\n");

        // 1. Entities Section
        sb.append("### 🏷️ Discovered Entities\n");
        for (int entityId : traversal.discoveredEntities()) {
            String name = entityDirectory.entityName(entityId);
            String type = safeEntityType(entityDirectory, entityId);
            int memCount = entityDirectory.memoriesForEntity(entityId).length;
            sb.append("- **").append(name).append("** [").append(type).append("]")
                    .append(" (").append(memCount).append(" memory reference").append(memCount == 1 ? "" : "s").append(")\n");
        }
        sb.append("\n");

        // 2. Relational Paths Section
        sb.append("### 🔗 Relational Paths\n");
        if (traversal.discoveredPaths().isEmpty()) {
            sb.append("_No relational paths found matching the query constraints._\n\n");
        } else {
            int pathIdx = 1;
            for (TraversalPath path : traversal.discoveredPaths()) {
                sb.append(pathIdx++).append(". ");
                for (int i = 0; i < path.entityIds().size(); i++) {
                    int eId = path.entityIds().get(i);
                    String eName = entityDirectory.entityName(eId);
                    String eType = safeEntityType(entityDirectory, eId);
                    sb.append("`").append(eName).append("` (").append(eType).append(")");
                    if (i < path.steps().size()) {
                        TraversalStep step = path.steps().get(i);
                        sb.append(" ──[").append(step.relation()).append("]──> ");
                    }
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 3. Grounding Memory Excerpts
        if (includeMemories) {
            Set<Integer> allMemorySlots = new HashSet<>();
            for (TraversalPath path : traversal.discoveredPaths()) {
                allMemorySlots.addAll(path.memorySlots());
            }

            if (!allMemorySlots.isEmpty()) {
                sb.append("### 🧠 Grounding Memory Context\n");
                int displayed = 0;
                for (int slot : allMemorySlots) {
                    if (displayed >= 8) break; // Cap memory excerpts to prevent token overflow
                    String memId = slotToId.get(slot);
                    if (memId != null) {
                        CognitiveRecord rec = memory.inspect(memId);
                        if (rec != null && rec.text() != null && !rec.text().isBlank()) {
                            sb.append("- **[").append(memId).append("]** (").append(rec.memoryType()).append("): ")
                                    .append("\"").append(truncate(rec.text(), 140)).append("\"\n");
                            displayed++;
                        }
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private Integer resolveEntityId(EntityDirectory entityDirectory, String entityName) {
        if (entityName == null || entityName.isBlank()) return null;
        var nameIndex = entityDirectory.nameIndex();
        if (nameIndex == null) return null;

        // Exact match
        Integer id = nameIndex.get(entityName);
        if (id != null) return id;

        // Case-insensitive match
        for (var entry : nameIndex.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(entityName)) {
                return entry.getValue();
            }
        }

        // Substring match
        String lower = entityName.toLowerCase(Locale.ROOT);
        for (var entry : nameIndex.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(lower)
                    || lower.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }

        return null;
    }

    private Integer inferEntityIdFromQuery(EntityDirectory entityDirectory, String query) {
        if (query == null || query.isBlank()) return null;
        var nameIndex = entityDirectory.nameIndex();
        if (nameIndex == null) return null;

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        Integer bestId = null;
        int maxLen = 0;

        for (var entry : nameIndex.entrySet()) {
            String nameLower = entry.getKey().toLowerCase(Locale.ROOT);
            if (lowerQuery.contains(nameLower) && nameLower.length() > maxLen) {
                bestId = entry.getValue();
                maxLen = nameLower.length();
            }
        }

        return bestId;
    }

    private String formatEntityNotFoundResult(EntityDirectory entityDirectory, String startEntity, String query) {
        var sb = new StringBuilder();
        sb.append("⚠️ Could not resolve starting entity for graph traversal.\n\n");
        if (!startEntity.isBlank()) {
            sb.append("- Specified 'start_entity': `").append(startEntity).append("` (not found)\n");
        }
        if (!query.isBlank()) {
            sb.append("- Inferred from 'query': `").append(query).append("` (no matching entities found)\n");
        }

        var nameIndex = entityDirectory.nameIndex();
        if (nameIndex != null && !nameIndex.isEmpty()) {
            sb.append("\n**Available Sample Entities:**\n");
            int count = 0;
            for (var entry : nameIndex.entrySet()) {
                if (count++ >= 10) break;
                String type = safeEntityType(entityDirectory, entry.getValue());
                sb.append("- `").append(entry.getKey()).append("` [").append(type).append("]\n");
            }
        }
        return sb.toString();
    }

    private String safeEntityType(EntityDirectory entityDirectory, int entityId) {
        try {
            String type = entityDirectory.entityType(entityId);
            return (type != null && !type.isBlank()) ? type : "ENTITY";
        } catch (Exception e) {
            return "ENTITY";
        }
    }

    private Set<String> parseFilters(String filterString) {
        if (filterString == null || filterString.isBlank()) return null;
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
