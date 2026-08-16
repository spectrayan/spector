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

import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.commons.cache.TtlConcurrentMapCacheManager;
import com.spectrayan.spector.memory.cache.MemoryCacheNames;
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
import com.spectrayan.spector.memory.graph.causal.CausalChain;
import com.spectrayan.spector.memory.graph.causal.CausalQueryEngine;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final TemporalChainMemory temporalChain;

    /** Identity companion (ADR-0003 #455). When present, all identity reads route here. */
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;
    private final OntologyConfig ontologyConfig;
    private final MemoryIndex index;

    private final SpectorCache overviewCache;
    private final SpectorCache topologyCache;

    public void invalidateCache() {
        overviewCache.clear();
        topologyCache.clear();
    }

    /**
     * Legacy constructor (no {@link EntityDirectory}) — identity reads fall back to the binary
     * {@code entityGraph}. Retained for callers/tests predating the hypergraph graduation.
     */
    public CognitiveGraphFacade(HebbianGraphBase hebbianGraph,
                                TemporalChainMemory temporalChain,
                                HyperEntityGraphMemory hyperEntityGraph,
                                MemoryIndex index) {
        this(hebbianGraph, temporalChain, null, hyperEntityGraph, null, null, index, null);
    }

    public CognitiveGraphFacade(HebbianGraphBase hebbianGraph,
                                TemporalChainMemory temporalChain,
                                EntityDirectory entityDirectory,
                                HyperEntityGraphMemory hyperEntityGraph,
                                MemoryIndex index) {
        this(hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, null, null, index, null);
    }

    public CognitiveGraphFacade(HebbianGraphBase hebbianGraph,
                                TemporalChainMemory temporalChain,
                                EntityDirectory entityDirectory,
                                HyperEntityGraphMemory hyperEntityGraph,
                                TemporalKnowledgeGraph temporalKnowledgeGraph,
                                MemoryIndex index) {
        this(hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, temporalKnowledgeGraph, null, index, null);
    }

    public CognitiveGraphFacade(HebbianGraphBase hebbianGraph,
                                TemporalChainMemory temporalChain,
                                EntityDirectory entityDirectory,
                                HyperEntityGraphMemory hyperEntityGraph,
                                TemporalKnowledgeGraph temporalKnowledgeGraph,
                                OntologyConfig ontologyConfig,
                                MemoryIndex index) {
        this(hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, temporalKnowledgeGraph, ontologyConfig, index, null);
    }

    public CognitiveGraphFacade(HebbianGraphBase hebbianGraph,
                                TemporalChainMemory temporalChain,
                                EntityDirectory entityDirectory,
                                HyperEntityGraphMemory hyperEntityGraph,
                                TemporalKnowledgeGraph temporalKnowledgeGraph,
                                OntologyConfig ontologyConfig,
                                MemoryIndex index,
                                SpectorCacheManager cacheManager) {
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.ontologyConfig = ontologyConfig != null ? ontologyConfig : OntologyConfig.defaultInstance();
        this.index = index;

        SpectorCacheManager effectiveManager = cacheManager != null
                ? cacheManager
                : TtlConcurrentMapCacheManager.defaultManager();
        this.overviewCache = effectiveManager.getCache(MemoryCacheNames.GRAPH_OVERVIEW);
        this.topologyCache = effectiveManager.getCache(MemoryCacheNames.TOPOLOGY_STATS);
    }

    // ── Identity read helpers: route to the directory when present (ADR-0003 #455). ──

    /** True when entity identity is available (directory or legacy graph). */
    private boolean hasIdentity() {
        return entityDirectory != null;
    }

    private java.util.Map<String, Integer> identityNameIndex() {
        return entityDirectory != null ? entityDirectory.nameIndex() : java.util.Map.of();
    }

    private int[] identityMemoriesForEntity(int entityId) {
        return entityDirectory != null ? entityDirectory.memoriesForEntity(entityId) : new int[0];
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL & BENCHMARK ACCESSORS
    // ══════════════════════════════════════════════════════════════

    public HebbianGraphBase rawHebbianGraph() { return hebbianGraph; }
    public TemporalChainMemory rawTemporalChain() { return temporalChain; }
    public HyperEntityGraphMemory rawHyperEntityGraph() { return hyperEntityGraph; }

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
        int entityNodes = entityDirectory != null ? entityDirectory.entityCount() : 0;
        int entityEdges = hyperEntityGraph != null ? hyperEntityGraph.totalHyperedges() : 0;
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
        String key = "overview:" + maxNodes;
        return overviewCache.get(key, GraphNeighborhood.class, () -> computeOverview(maxNodes, inspector));
    }

    private GraphNeighborhood computeOverview(int maxNodes, Function<String, CognitiveRecord> inspector) {
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
            if (hasIdentity()) {
                for (int entityId : identityNameIndex().values()) {
                    int[] mems = identityMemoriesForEntity(entityId);
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
        return topologyCache.get("current", TopologyStats.class, this::computeTopologyStats);
    }

    private TopologyStats computeTopologyStats() {
        if (hyperEntityGraph == null || !hasIdentity()) return TopologyStats.empty();
        try {
            var nameIndex = identityNameIndex();

            Map<String, int[]> entityTypeAgg = new LinkedHashMap<>();
            Map<String, int[]> relationTypeAgg = new LinkedHashMap<>();

            // 1. Aggregate entity types from EntityDirectory
            for (var entry : nameIndex.entrySet()) {
                int entityId = entry.getValue();
                String entityType = safeEntityType(entityId);

                var eStats = entityTypeAgg.computeIfAbsent(entityType, _ -> new int[3]);
                eStats[0]++; // node count
                var hEdges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                eStats[2] += hEdges.size(); // memory refs
                eStats[1] += hEdges.size();
            }

            // 2. Aggregate relation types from Temporal Knowledge Graph predicates
            if (temporalKnowledgeGraph != null && temporalKnowledgeGraph.predicateRegistry() != null) {
                var predRegistry = temporalKnowledgeGraph.predicateRegistry();
                for (var predEntry : predRegistry.entries().entrySet()) {
                    String predName = predEntry.getKey();
                    int predId = predEntry.getValue();
                    int factCount = 0;
                    Set<Integer> subjectNodes = new HashSet<>();
                    Set<Integer> objectNodes = new HashSet<>();
                    for (int entityId : nameIndex.values()) {
                        var facts = temporalKnowledgeGraph.readFactsForEntity(entityId);
                        if (facts == null) continue;
                        for (var f : facts) {
                            if (f.predicateId() == predId && !f.isRetraction()) {
                                factCount++;
                                subjectNodes.add(f.subjectEntityId());
                                objectNodes.add(f.objectEntityId());
                            }
                        }
                    }
                    if (factCount > 0) {
                        var rStats = relationTypeAgg.computeIfAbsent(predName, _ -> new int[3]);
                        rStats[0] += factCount; // edge count
                        rStats[1] += subjectNodes.size() + objectNodes.size(); // distinct nodes
                        rStats[2] += factCount; // memory refs
                    }
                }
            }

            // 3. If no TKG relations exist, aggregate shared entity categories
            if (relationTypeAgg.isEmpty()) {
                for (var entry : nameIndex.entrySet()) {
                    int entityId = entry.getValue();
                    String entityType = safeEntityType(entityId);
                    var hEdges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                    if (!hEdges.isEmpty()) {
                        String relType = entityType != null && !entityType.equals("UNKNOWN") ? entityType : "SHARED_ENTITY";
                        var rStats = relationTypeAgg.computeIfAbsent(relType, _ -> new int[3]);
                        rStats[0] += hEdges.size();
                        rStats[1] += 1;
                        rStats[2] += hEdges.size();
                    }
                }
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
    // CAUSAL REASONING QUERIES (ADR-0010, #273)
    // ══════════════════════════════════════════════════════════════

    /**
     * Answers "Why did X happen?" or "What caused X?" by traversing causal antecedents
     * backward from the target entity up to {@code maxHops}.
     *
     * @param entityName the focal entity or incident name
     * @param maxHops    maximum traversal hops
     * @param inspector  optional record inspector for memory text snippets
     * @return structured causal chain
     */
    public CausalChain traceWhy(String entityName, int maxHops, Function<String, CognitiveRecord> inspector) {
        CausalQueryEngine engine = new CausalQueryEngine(
                entityDirectory, hyperEntityGraph, temporalKnowledgeGraph,
                temporalKnowledgeGraph != null ? temporalKnowledgeGraph.predicateRegistry() : null,
                ontologyConfig, index, inspector);
        return engine.traceWhy(entityName, maxHops);
    }

    public CausalChain traceWhy(String entityName, int maxHops) {
        return traceWhy(entityName, maxHops, null);
    }

    public CausalChain traceWhy(String entityName) {
        return traceWhy(entityName, 5, null);
    }

    /**
     * Answers "What resulted from X?" or "What did X cause?" by traversing causal consequences
     * forward from the source entity up to {@code maxHops}.
     *
     * @param entityName the focal event or entity name
     * @param maxHops    maximum traversal hops
     * @param inspector  optional record inspector for memory text snippets
     * @return structured causal chain with downstream consequences
     */
    public CausalChain traceEffects(String entityName, int maxHops, Function<String, CognitiveRecord> inspector) {
        CausalQueryEngine engine = new CausalQueryEngine(
                entityDirectory, hyperEntityGraph, temporalKnowledgeGraph,
                temporalKnowledgeGraph != null ? temporalKnowledgeGraph.predicateRegistry() : null,
                ontologyConfig, index, inspector);
        return engine.traceEffects(entityName, maxHops);
    }

    public CausalChain traceEffects(String entityName, int maxHops) {
        return traceEffects(entityName, maxHops, null);
    }

    public CausalChain traceEffects(String entityName) {
        return traceEffects(entityName, 5, null);
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
        } catch (Exception e) {
            log.warn("Operation failed: Failed to collect Hebbian edges", e);
        }
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
        } catch (Exception e) {
            log.warn("Operation failed: Failed to collect temporal edges", e);
        }
    }

    private void collectEntityEdges(Map<Integer, String> slotToId,
                                    HashSet<String> validIds, List<GraphEdge> edges) {
        if (hyperEntityGraph == null || !hasIdentity()) return;
        try {
            Set<Integer> validEntityIds = new HashSet<>();
            for (Map.Entry<Integer, String> entry : slotToId.entrySet()) {
                if (validIds.contains(entry.getValue())) {
                    validEntityIds.addAll(entityDirectory.entitiesForMemory(entry.getKey()).keySet());
                }
            }

            Map<Integer, String> idToName = new java.util.HashMap<>();
            for (int entityId : validEntityIds) {
                idToName.put(entityId, entityDirectory.entityName(entityId));
            }

            // 1. Structured relational edges from Temporal Knowledge Graph (facts)
            if (temporalKnowledgeGraph != null && temporalKnowledgeGraph.predicateRegistry() != null) {
                var predRegistry = temporalKnowledgeGraph.predicateRegistry();
                for (int subjectId : validEntityIds) {
                    String subjectType = safeEntityType(subjectId);
                    var facts = temporalKnowledgeGraph.readFactsForEntity(subjectId);
                    if (facts == null || facts.isEmpty()) continue;

                    int[] subjectMems = identityMemoriesForEntity(subjectId);
                    for (var fact : facts) {
                        if (fact.isRetraction()) continue;
                        int objectId = fact.objectEntityId();
                        if (!validEntityIds.contains(objectId)) continue;

                        String objectType = safeEntityType(objectId);
                        String predicateName = predRegistry.nameOf((int) fact.predicateId());
                        if (predicateName == null || predicateName.isBlank()) {
                            predicateName = "RELATED_TO";
                        }

                        int[] objectMems = identityMemoriesForEntity(objectId);
                        for (int sm : subjectMems) {
                            String fromMemId = slotToId.get(sm);
                            if (fromMemId == null || !validIds.contains(fromMemId)) continue;
                            for (int om : objectMems) {
                                if (sm == om) continue; // skip self-loops
                                String toMemId = slotToId.get(om);
                                if (toMemId == null || !validIds.contains(toMemId)) continue;

                                edges.add(new GraphEdge(
                                        fromMemId, toMemId, "ENTITY", predicateName,
                                        fact.confidence() > 0 ? (double) fact.confidence() : 0.8,
                                        subjectType, objectType));
                            }
                        }
                    }
                }
            }

            // 2. Entity co-occurrence / shared entity edges
            for (Map.Entry<Integer, String> entry : idToName.entrySet()) {
                int entityId = entry.getKey();
                String entityName = entry.getValue();
                String entityType = safeEntityType(entityId);
                var hEdges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                for (int i = 0; i < hEdges.size(); i++) {
                    for (int j = i + 1; j < hEdges.size(); j++) {
                        int fm = hEdges.get(i).memoryIdx();
                        int tm = hEdges.get(j).memoryIdx();
                        if (fm == tm) continue;
                        String fromMemId = slotToId.get(fm);
                        String toMemId = slotToId.get(tm);
                        if (fromMemId == null || !validIds.contains(fromMemId)) continue;
                        if (toMemId == null || !validIds.contains(toMemId)) continue;

                        String relationLabel = (entityType != null && !entityType.equals("UNKNOWN") && !entityType.equals("ENTITY"))
                                ? entityType + ": " + entityName
                                : entityName;

                        edges.add(new GraphEdge(
                                fromMemId, toMemId, "ENTITY", relationLabel,
                                0.5, entityType, entityType));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Operation failed: Failed to collect entity edges", e);
        }
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
        } catch (Exception e) {
            log.warn("Operation failed: Failed in BFS Hebbian neighbors", e);
        }
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
        } catch (Exception e) {
            log.warn("Operation failed: Failed in BFS temporal neighbors", e);
        }
    }

    private void bfsEntityNeighbors(String currentId, int slot, Map<Integer, String> slotToId,
                                    Map<Integer, List<Integer>> slotToEntities,
                                    List<String> visitedIds, HashSet<String> visitedIdsSet,
                                    List<Integer> nextLevel, List<GraphEdge> edges) {
        if (hyperEntityGraph == null) return;
        List<Integer> entities = slotToEntities.get(slot);
        if (entities == null) return;
        try {
            Map<Integer, String> idToName = new java.util.HashMap<>();
            var nameIndex = identityNameIndex();
            for (var entry : nameIndex.entrySet()) {
                idToName.put(entry.getValue(), entry.getKey());
            }

            for (int entityId : entities) {
                String entityName = idToName.getOrDefault(entityId, "Entity");
                String entityType = safeEntityType(entityId);
                String relationLabel = (entityType != null && !entityType.equals("UNKNOWN") && !entityType.equals("ENTITY"))
                        ? entityType + ": " + entityName
                        : entityName;

                var hEdges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                for (var he : hEdges) {
                    int targetSlot = he.memoryIdx();
                    if (targetSlot == slot || targetSlot < 0) continue;
                    String targetId = slotToId.get(targetSlot);
                    if (targetId != null) {
                        edges.add(new GraphEdge(
                                currentId, targetId, "ENTITY", relationLabel,
                                0.5, entityType, entityType));
                        if (!visitedIdsSet.contains(targetId)) {
                            visitedIds.add(targetId);
                            visitedIdsSet.add(targetId);
                            nextLevel.add(targetSlot);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Operation failed: Failed in BFS entity neighbors", e);
        }
    }

    private List<String> entityNamesForMemory(int slot) {
        if (!hasIdentity() || slot < 0) return List.of();
        try {
            return new ArrayList<>(entityDirectory.entitiesForMemory(slot).values());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String safeEntityType(int entityId) {
        try {
            return entityDirectory != null ? entityDirectory.entityType(entityId) : "ENTITY";
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
