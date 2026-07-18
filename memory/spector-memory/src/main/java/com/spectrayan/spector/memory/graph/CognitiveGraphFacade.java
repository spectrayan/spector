/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.GraphNeighborhood;
import com.spectrayan.spector.memory.model.GraphNeighborhood.GraphEdge;
import com.spectrayan.spector.memory.model.GraphNeighborhood.GraphNode;
import com.spectrayan.spector.memory.model.GraphStats;
import com.spectrayan.spector.memory.model.TopologyStats;
import com.spectrayan.spector.memory.model.TopologyStats.EntityTypeStats;
import com.spectrayan.spector.memory.model.TopologyStats.RelationTypeStats;
import com.spectrayan.spector.memory.temporal.TemporalChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Facade over the 3-layer cognitive graph subsystem (Hebbian + Temporal + Entity).
 *
 * <p>Encapsulates graph traversal, statistics, and neighborhood queries so that
 * consumers (MAO, admin dashboards) never touch raw graph internals directly.</p>
 *
 * <p>All 4 graph subsystems are nullable — when a graph is not configured,
 * the corresponding query methods gracefully return empty results.</p>
 *
 * @since 1.1.0
 */
public final class CognitiveGraphFacade {

    private static final Logger log = LoggerFactory.getLogger(CognitiveGraphFacade.class);

    private final HebbianGraphBase hebbianGraph;
    private final TemporalChain temporalChain;
    private final EntityGraph entityGraph;
    private final HyperEntityGraph hyperEntityGraph;
    private final MemoryIndex index;

    public CognitiveGraphFacade(HebbianGraphBase hebbianGraph,
                                TemporalChain temporalChain,
                                EntityGraph entityGraph,
                                HyperEntityGraph hyperEntityGraph,
                                MemoryIndex index) {
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityGraph = entityGraph;
        this.hyperEntityGraph = hyperEntityGraph;
        this.index = index;
    }

    // ══════════════════════════════════════════════════════════════
    // DEPRECATED RAW ACCESSORS (will be removed in a future release)
    // ══════════════════════════════════════════════════════════════

    /** @deprecated Use {@link #graphStats()} or {@link #neighborhood(String, int, Function)} instead. */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public HebbianGraphBase hebbianGraph() { return hebbianGraph; }

    /** @deprecated Use {@link #graphStats()} or {@link #neighborhood(String, int, Function)} instead. */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public TemporalChain temporalChain() { return temporalChain; }

    /** @deprecated Use {@link #graphStats()} or {@link #topologyStats()} instead. */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public EntityGraph entityGraph() { return entityGraph; }

    /** @deprecated Use {@link #topologyStats()} instead. */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public HyperEntityGraph hyperEntityGraph() { return hyperEntityGraph; }

    // ══════════════════════════════════════════════════════════════
    // HIGH-LEVEL GRAPH QUERIES
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns aggregate statistics for all graph subsystems.
     *
     * @return graph stats with edge/node counts
     */
    public GraphStats graphStats() {
        int hebbian = hebbianGraph != null ? hebbianGraph.totalEdges() : 0;
        int entityNodes = entityGraph != null ? entityGraph.entityCount() : 0;
        int entityEdges = entityGraph != null ? entityGraph.edgeCount() : 0;
        int temporalLinks = 0;
        if (temporalChain != null) {
            int cap = temporalChain.capacity();
            for (int i = 0; i < cap; i++) {
                if (temporalChain.isLinked(i)) temporalLinks++;
            }
        }
        return new GraphStats(hebbian, temporalLinks, entityNodes, entityEdges);
    }

    /**
     * Returns a sampled overview of the full graph, limited to {@code maxNodes}.
     *
     * <p>Includes Hebbian, temporal, and entity edges between the sampled nodes.</p>
     *
     * @param maxNodes  maximum number of nodes to include
     * @param inspector function to resolve a memory ID to its {@link CognitiveRecord}
     * @return the graph neighborhood, or empty if no memories exist
     */
    public GraphNeighborhood overview(int maxNodes, Function<String, CognitiveRecord> inspector) {
        try {
            List<String> allIds = new java.util.ArrayList<>(index.orderedIds());
            java.util.Collections.reverse(allIds);
            allIds = allIds.stream().limit(maxNodes).toList();
            if (allIds.isEmpty()) return GraphNeighborhood.empty(null);
            var allIdsSet = new HashSet<>(allIds);

            Map<Integer, String> slotToId = new LinkedHashMap<>();
            Map<String, Integer> idToSlot = new LinkedHashMap<>();
            index.buildGraphSlotMappings(slotToId, idToSlot);

            List<GraphNode> nodes = buildNodes(allIds, idToSlot, inspector);
            List<GraphEdge> edges = new ArrayList<>();

            // Hebbian + Temporal edges
            for (String id : allIds) {
                int slot = idToSlot.getOrDefault(id, -1);
                if (slot < 0) continue;
                collectHebbianEdges(id, slot, slotToId, allIdsSet, edges);
                collectTemporalEdgesForward(id, slot, slotToId, allIdsSet, edges);
            }

            // Entity edges
            collectEntityEdges(slotToId, allIdsSet, edges);

            return new GraphNeighborhood(null, nodes, edges, null);
        } catch (Exception e) {
            log.error("[CognitiveGraphFacade] Overview failed: {}", e.getMessage(), e);
            return GraphNeighborhood.empty("Overview failed: " + e.getMessage());
        }
    }

    /**
     * Returns the Hebbian/Temporal/Entity neighborhood for a specific memory,
     * using BFS traversal up to {@code depth} levels.
     *
     * @param memoryId  the center memory ID
     * @param depth     BFS depth (1 = immediate neighbors, 2 = neighbors of neighbors, etc.)
     * @param inspector function to resolve a memory ID to its {@link CognitiveRecord}
     * @return the graph neighborhood, or empty if the memory is not found
     */
    public GraphNeighborhood neighborhood(String memoryId, int depth,
                                          Function<String, CognitiveRecord> inspector) {
        try {
            Map<Integer, String> slotToId = new LinkedHashMap<>();
            Map<String, Integer> idToSlot = new LinkedHashMap<>();
            index.buildGraphSlotMappings(slotToId, idToSlot);

            int startSlot = idToSlot.getOrDefault(memoryId, -1);
            if (startSlot < 0) {
                return GraphNeighborhood.empty("Memory ID not found in index slot map");
            }

            List<String> visitedIds = new ArrayList<>();
            var visitedIdsSet = new HashSet<String>();
            List<GraphEdge> edges = new ArrayList<>();

            // Build slot-to-entity ID mapping for fast O(1) traversal lookups
            Map<Integer, List<Integer>> slotToEntities = new java.util.HashMap<>();
            if (entityGraph != null) {
                for (int entityId : entityGraph.nameIndex().values()) {
                    int[] mems = entityGraph.memoriesForEntity(entityId);
                    for (int m : mems) {
                        slotToEntities.computeIfAbsent(m, _ -> new ArrayList<>()).add(entityId);
                    }
                }
            }

            // BFS traversal
            List<Integer> currentLevel = new ArrayList<>();
            currentLevel.add(startSlot);
            visitedIds.add(memoryId);
            visitedIdsSet.add(memoryId);

            for (int d = 0; d < depth; d++) {
                List<Integer> nextLevel = new ArrayList<>();
                for (int slot : currentLevel) {
                    String currentId = slotToId.get(slot);
                    if (currentId == null) continue;

                    // Hebbian neighbors
                    bfsHebbianNeighbors(currentId, slot, slotToId, visitedIds, visitedIdsSet,
                            nextLevel, edges);

                    // Temporal neighbors (forward + backward)
                    bfsTemporalNeighbors(currentId, slot, slotToId, visitedIds, visitedIdsSet,
                            nextLevel, edges);

                    // Entity neighbors (shared entities & relationships)
                    bfsEntityNeighbors(currentId, slot, slotToId, slotToEntities, visitedIds, visitedIdsSet,
                            nextLevel, edges);
                }
                if (nextLevel.isEmpty()) break;
                currentLevel = nextLevel;
            }

            // Collect any remaining entity edges between any visited nodes
            collectEntityEdges(slotToId, visitedIdsSet, edges);

            // Deduplicate edges to avoid rendering redundant lines in UI
            List<GraphEdge> uniqueEdges = edges.stream().distinct().toList();

            // Inspect and build nodes
            List<GraphNode> nodes = buildNodes(visitedIds, idToSlot, inspector);

            return new GraphNeighborhood(memoryId, nodes, uniqueEdges, null);
        } catch (Exception e) {
            log.error("[CognitiveGraphFacade] Neighborhood query failed for id={}: {}",
                    memoryId, e.getMessage(), e);
            return GraphNeighborhood.empty(memoryId);
        }
    }

    /**
     * Returns entity/relation type aggregations for topology visualization.
     *
     * @return topology stats, or empty if entity graph is not configured
     */
    public TopologyStats topologyStats() {
        if (entityGraph == null) return TopologyStats.empty();
        try {
            var nameIndex = entityGraph.nameIndex();

            Map<String, int[]> entityTypeAgg = new LinkedHashMap<>();
            Map<String, int[]> relationTypeAgg = new LinkedHashMap<>();

            for (var entry : nameIndex.entrySet()) {
                int entityId = entry.getValue();
                String entityType = safeEntityType(entityId);

                var eStats = entityTypeAgg.computeIfAbsent(entityType, _ -> new int[3]);
                eStats[0]++; // node count
                int memCount = 0;
                try { memCount = entityGraph.memoryRefCount(entityId); } catch (Exception ignored) {}
                eStats[2] += memCount; // memory refs

                try {
                    var edgeList = entityGraph.edges(entityId);
                    eStats[1] += edgeList.size();
                    for (var ee : edgeList) {
                        String relType = ee.relationType() != null ? ee.relationType() : "UNKNOWN";
                        var rStats = relationTypeAgg.computeIfAbsent(relType, _ -> new int[3]);
                        rStats[0]++; // edge count
                        rStats[1] += 2; // from + to node
                        try {
                            rStats[2] += entityGraph.memoryRefCount(ee.targetEntityId());
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            List<EntityTypeStats> entityTypes = entityTypeAgg.entrySet().stream()
                    .map(e -> new EntityTypeStats(
                            e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                    .toList();

            List<RelationTypeStats> relationTypes = relationTypeAgg.entrySet().stream()
                    .map(e -> new RelationTypeStats(
                            e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                    .toList();

            return new TopologyStats(entityTypes, relationTypes);
        } catch (Exception e) {
            log.error("[CognitiveGraphFacade] Topology stats failed: {}", e.getMessage(), e);
            return TopologyStats.empty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ══════════════════════════════════════════════════════════════

    private List<GraphNode> buildNodes(List<String> ids, Map<String, Integer> idToSlot,
                                       Function<String, CognitiveRecord> inspector) {
        List<GraphNode> nodes = new ArrayList<>();
        for (String id : ids) {
            var record = inspector.apply(id);
            if (record == null) continue;
            var entityNames = entityNamesForMemory(idToSlot.getOrDefault(id, -1));
            nodes.add(new GraphNode(
                    id,
                    record.memoryType() != null ? record.memoryType().name() : "SEMANTIC",
                    truncate(record.text(), 120),
                    record.importance(),
                    record.valence(),
                    record.timestampMs(),
                    entityNames
            ));
        }
        return nodes;
    }

    private void collectHebbianEdges(String id, int slot, Map<Integer, String> slotToId,
                                     HashSet<String> validIds, List<GraphEdge> edges) {
        if (hebbianGraph == null) return;
        try {
            var neighbors = hebbianGraph.neighbors(slot);
            for (var edge : neighbors) {
                String neighborId = slotToId.get(edge.neighborIndex());
                if (neighborId != null && validIds.contains(neighborId)) {
                    edges.add(new GraphEdge(
                            id, neighborId, "HEBBIAN", null,
                            Math.min(1.0, edge.weight()), null, null));
                }
            }
        } catch (Exception ignored) {}
    }

    private void collectTemporalEdgesForward(String id, int slot, Map<Integer, String> slotToId,
                                             HashSet<String> validIds, List<GraphEdge> edges) {
        if (temporalChain == null) return;
        try {
            int[] forward = temporalChain.followForward(slot, 1);
            for (int neighborSlot : forward) {
                String neighborId = slotToId.get(neighborSlot);
                if (neighborId != null && validIds.contains(neighborId)) {
                    edges.add(new GraphEdge(
                            id, neighborId, "TEMPORAL", null, 0.8, null, null));
                }
            }
        } catch (Exception ignored) {}
    }

    private void collectEntityEdges(Map<Integer, String> slotToId,
                                    HashSet<String> validIds, List<GraphEdge> edges) {
        if (entityGraph == null) return;
        try {
            var nameIndex = entityGraph.nameIndex();
            for (var entry : nameIndex.entrySet()) {
                int entityId = entry.getValue();
                String entityType = safeEntityType(entityId);
                var entityEdgeList = entityGraph.edges(entityId);
                for (var ee : entityEdgeList) {
                    int[] fromMems = entityGraph.memoriesForEntity(entityId);
                    int[] toMems = entityGraph.memoriesForEntity(ee.targetEntityId());
                    String toEntityType = safeEntityType(ee.targetEntityId());
                    for (int fm : fromMems) {
                        String fromMemId = slotToId.get(fm);
                        if (fromMemId == null || !validIds.contains(fromMemId)) continue;
                        for (int tm : toMems) {
                            String toMemId = slotToId.get(tm);
                            if (toMemId == null || !validIds.contains(toMemId)) continue;
                            edges.add(new GraphEdge(
                                    fromMemId, toMemId, "ENTITY", ee.relationType(),
                                    (double) ee.weight(), entityType, toEntityType));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void bfsHebbianNeighbors(String currentId, int slot, Map<Integer, String> slotToId,
                                     List<String> visitedIds, HashSet<String> visitedIdsSet,
                                     List<Integer> nextLevel, List<GraphEdge> edges) {
        if (hebbianGraph == null) return;
        try {
            var neighbors = hebbianGraph.neighbors(slot);
            for (var edge : neighbors) {
                int nSlot = edge.neighborIndex();
                String nId = slotToId.get(nSlot);
                if (nId != null) {
                    edges.add(new GraphEdge(
                            currentId, nId, "HEBBIAN", null,
                            Math.min(1.0, edge.weight()), null, null));
                    if (!visitedIdsSet.contains(nId)) {
                        visitedIds.add(nId);
                        visitedIdsSet.add(nId);
                        nextLevel.add(nSlot);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void bfsTemporalNeighbors(String currentId, int slot, Map<Integer, String> slotToId,
                                      List<String> visitedIds, HashSet<String> visitedIdsSet,
                                      List<Integer> nextLevel, List<GraphEdge> edges) {
        if (temporalChain == null) return;
        try {
            int[] forward = temporalChain.followForward(slot, 1);
            for (int nSlot : forward) {
                String nId = slotToId.get(nSlot);
                if (nId != null) {
                    edges.add(new GraphEdge(currentId, nId, "TEMPORAL", null, 0.8, null, null));
                    if (!visitedIdsSet.contains(nId)) {
                        visitedIds.add(nId);
                        visitedIdsSet.add(nId);
                        nextLevel.add(nSlot);
                    }
                }
            }
            int[] backward = temporalChain.followBackward(slot, 1);
            for (int nSlot : backward) {
                String nId = slotToId.get(nSlot);
                if (nId != null) {
                    edges.add(new GraphEdge(nId, currentId, "TEMPORAL", null, 0.8, null, null));
                    if (!visitedIdsSet.contains(nId)) {
                        visitedIds.add(nId);
                        visitedIdsSet.add(nId);
                        nextLevel.add(nSlot);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void bfsEntityNeighbors(String currentId, int slot, Map<Integer, String> slotToId,
                                    Map<Integer, List<Integer>> slotToEntities,
                                    List<String> visitedIds, HashSet<String> visitedIdsSet,
                                    List<Integer> nextLevel, List<GraphEdge> edges) {
        if (entityGraph == null) return;
        List<Integer> entities = slotToEntities.get(slot);
        if (entities == null) return;
        try {
            for (int entityId : entities) {
                String entityType = safeEntityType(entityId);
                int[] mems = entityGraph.memoriesForEntity(entityId);

                // 1. Shared entity links
                for (int targetSlot : mems) {
                    if (targetSlot == slot) continue;
                    String targetId = slotToId.get(targetSlot);
                    if (targetId != null) {
                        edges.add(new GraphEdge(
                                currentId, targetId, "ENTITY", "SHARED_ENTITY",
                                0.5, entityType, entityType));
                        if (!visitedIdsSet.contains(targetId)) {
                            visitedIds.add(targetId);
                            visitedIdsSet.add(targetId);
                            nextLevel.add(targetSlot);
                        }
                    }
                }

                // 2. Connected entities
                var entityEdgeList = entityGraph.edges(entityId);
                for (var ee : entityEdgeList) {
                    int targetEntityId = ee.targetEntityId();
                    String targetEntityType = safeEntityType(targetEntityId);
                    int[] targetMems = entityGraph.memoriesForEntity(targetEntityId);
                    for (int targetSlot : targetMems) {
                        String targetId = slotToId.get(targetSlot);
                        if (targetId != null) {
                            edges.add(new GraphEdge(
                                    currentId, targetId, "ENTITY", ee.relationType(),
                                    (double) ee.weight(), entityType, targetEntityType));
                            if (!visitedIdsSet.contains(targetId)) {
                                visitedIds.add(targetId);
                                visitedIdsSet.add(targetId);
                                nextLevel.add(targetSlot);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private List<String> entityNamesForMemory(int slot) {
        if (entityGraph == null || slot < 0) return List.of();
        try {
            List<String> names = new ArrayList<>();
            var nameIndex = entityGraph.nameIndex();
            for (var entry : nameIndex.entrySet()) {
                int[] mems = entityGraph.memoriesForEntity(entry.getValue());
                for (int m : mems) {
                    if (m == slot) {
                        names.add(entry.getKey());
                        break;
                    }
                }
            }
            return names;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String safeEntityType(int entityId) {
        try {
            return entityGraph.entityType(entityId);
        } catch (Exception e) {
            return "ENTITY";
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "…";
    }
}
