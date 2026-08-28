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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.error.SpectorGraphDecayException;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.TypeNormalizer;
// RelationType enum replaced by open-schema strings via TypeRegistry
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hebbian.SynapticDecayModulator;
import com.spectrayan.spector.memory.hippocampus.ReflectDaemon;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.RememberPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates sleep consolidation (reflection) cycles.
 *
 * <p>Coordinates the following phases during a single {@link #reflect} call:</p>
 * <ol>
 *   <li><b>REM cycle</b> — delegates to {@link ReflectDaemon} for episodic→semantic consolidation</li>
 *   <li><b>Hebbian decay</b> — decays weak co-activation edges (synaptic homeostasis)</li>
 *   <li><b>Temporal pruning</b> — removes causal links older than the retention window</li>
 *   <li><b>Cross-layer promotion</b> — promotes strong Hebbian edges into entity RELATED_TO relations</li>
 *   <li><b>Entity maintenance</b> — decays entity edges and merges near-duplicate entities</li>
 * </ol>
 *
 * <p>Thread-safe: individual subsystem operations are thread-safe; the orchestrator
 * itself does not maintain mutable state.</p>
 *
 * @deprecated As of 1.3.0, replaced by {@link ReflectPathway} and its composable relays.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
final class ReflectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReflectionOrchestrator.class);

    /** Minimum Hebbian weight to qualify for cross-layer promotion to entity graph. */
    private static final float HEBBIAN_PROMOTION_MIN_WEIGHT = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_PROMOTION_MIN_WEIGHT;

    /** Hebbian decay factor per reflection cycle (10% decay = multiply by 0.9). */
    private static final float HEBBIAN_DECAY_FACTOR = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_DECAY_FACTOR;

    /** Entity edge decay factor per cycle (5% decay). */
    private static final float ENTITY_DECAY_FACTOR = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_DECAY_FACTOR;

    /** Entity edge pruning threshold (edges below this weight are removed). */
    private static final float ENTITY_PRUNE_THRESHOLD = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_PRUNE_THRESHOLD;

    /** Entity→memory adjacency decay factor per cycle (5% decay — LTD). */
    private static final float ENTITY_ADJ_DECAY_FACTOR = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_ADJ_DECAY_FACTOR;

    /** Entity→memory adjacency pruning threshold (links below this are removed). */
    private static final float ENTITY_ADJ_PRUNE_THRESHOLD = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_ADJ_PRUNE_THRESHOLD;

    /** Levenshtein distance threshold for merging near-duplicate entities. */
    private static final int ENTITY_MERGE_DISTANCE = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_MERGE_DISTANCE;

    // ── STC Cross-Capture Constants ──

    /**
     * Minimum Hebbian weight to qualify for cross-capture propagation.
     * Only moderately-strong edges propagate signals across layers.
     */
    private static final float CROSS_CAPTURE_MIN_WEIGHT = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_CROSS_CAPTURE_MIN_WEIGHT;

    /**
     * Scale factor mapping Hebbian weight to entity edge boost.
     * Hebbian weight 4.0 × 0.05 = 0.20 boost to entity edge weight.
     */
    private static final float CROSS_CAPTURE_SCALE_FACTOR = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_CROSS_CAPTURE_SCALE_FACTOR;

    /**
     * Maximum per-cycle boost to an entity edge from cross-capture.
     * Prevents runaway amplification even with very strong Hebbian edges.
     */
    private static final float CROSS_CAPTURE_MAX_BOOST = 0.3f;

    /**
     * Importance threshold for temporal chain pruning — sessions with all
     * constituent memories below this importance are prunable when old.
     */
    private static final float TEMPORAL_IMPORTANCE_THRESHOLD = 1.0f;

    private final ReflectDaemon reflectDaemon;
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final MemoryWal wal;
    private final int temporalRetentionDays;
    private final EmbeddingProvider embeddingProvider;
    private final LlmProvider llmProvider;
    private final boolean entityResolutionEnabled;
    private final boolean entityShadowMode;
    private final float entityCosineThreshold;
    private final TypeNormalizer typeNormalizer;

    ReflectionOrchestrator(ReflectDaemon reflectDaemon,
                           HebbianGraphBase hebbianGraph,
                           TemporalChainMemory temporalChain,
                           EntityDirectory entityDirectory,
                           HyperEntityGraphMemory hyperEntityGraph,
                           MemoryWal wal,
                           int temporalRetentionDays,
                           EmbeddingProvider embeddingProvider,
                           LlmProvider llmProvider,
                           boolean entityResolutionEnabled,
                           boolean entityShadowMode,
                           float entityCosineThreshold,
                           TypeNormalizer typeNormalizer) {
        this.reflectDaemon = reflectDaemon;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.wal = wal;
        this.temporalRetentionDays = temporalRetentionDays;
        this.embeddingProvider = embeddingProvider;
        this.llmProvider = llmProvider;
        this.entityResolutionEnabled = entityResolutionEnabled;
        this.entityShadowMode = entityShadowMode;
        this.entityCosineThreshold = entityCosineThreshold;
        this.typeNormalizer = typeNormalizer;
    }

    /**
     * Runs a full reflection cycle across all frozen and active partitions (#446):
     * REM consolidation, graph decay, temporal pruning, cross-layer promotion, and entity maintenance.
     *
     * @param partitionManager the partition manager providing all open partition handles
     * @param index            the memory index (for text lookups and graph slot resolution)
     * @param ingestionTarget  the ingestion target for promoted semantic memories (active partition)
     * @return a {@link ReflectReport} summarizing what was consolidated, pruned, and promoted
     */
    ReflectReport reflect(PartitionManager partitionManager, MemoryIndex index, RememberPathway ingestionTarget) {
        log.info("Manual reflection triggered across partitions");

        // Create metrics collector for this cycle
        var graphMetrics = new GraphHealthMetrics();

        // Phase 1: REM cycle — episodic → semantic consolidation across all partition handles
        ReflectReport daemonReport = reflectDaemon.runCycle(partitionManager, ingestionTarget, index);

        // Phase 2: Hebbian decay (synaptic homeostasis, arousal-modulated across all partitions)
        decayHebbianEdges(partitionManager, index, graphMetrics);

        // Phase 3: Temporal chain pruning (age + importance across all partitions)
        int temporalPruned = pruneTemporalChain(partitionManager, index);

        // Phase 4: Cross-layer promotion (Hebbian → Entity)
        promoteCrossLayer();

        // Phase 4b: STC cross-capture (Hebbian strength → Entity edge boost)
        crossCaptureHebbianToEntity(graphMetrics);

        // Phase 5: Entity graph maintenance (edge decay + entity merge)
        maintainEntityGraph(graphMetrics);

        // Phase 5b: Entity→memory adjacency LTD decay
        decayEntityAdjacency();

        // Phase 5c: Adjacency compaction (defragmentation)
        compactEntityAdjacency();

        // Phase 5d: HyperEntityGraph decay (hyperedge weight homeostasis)
        decayHyperEntityGraph();

        // Log graph health summary
        if (graphMetrics.totalEdgesDecayed() > 0 || graphMetrics.totalEdgesSurviving() > 0) {
            log.info("Reflect: graph health — {}", graphMetrics);
        }

        // Append WAL event
        wal.append(WalEvent.EventType.REFLECT, "system", null);

        // Overlay temporal pruning count onto the daemon's report
        return new ReflectReport(
                daemonReport.consolidatedCount(), daemonReport.tombstonedCount(),
                daemonReport.compactedPartitions(), temporalPruned,
                daemonReport.duration(), graphMetrics);
    }

    /**
     * Backward-compatible reflect overload using a single cognitive router.
     */
    ReflectReport reflect(CognitiveMemoryRouter cognitiveRouter, MemoryIndex index, RememberPathway ingestionTarget) {
        log.info("Manual reflection triggered (single-router fallback)");

        var graphMetrics = new GraphHealthMetrics();

        ReflectReport daemonReport = reflectDaemon.runCycle(
                cognitiveRouter != null ? cognitiveRouter.episodic() : null, ingestionTarget,
                offset -> index != null ? index.findTextByOffset(MemoryType.EPISODIC, offset) : null);

        decayHebbianEdges(cognitiveRouter, graphMetrics);
        int temporalPruned = pruneTemporalChain(cognitiveRouter);

        promoteCrossLayer();
        crossCaptureHebbianToEntity(graphMetrics);
        maintainEntityGraph(graphMetrics);
        decayEntityAdjacency();
        compactEntityAdjacency();
        decayHyperEntityGraph();

        if (graphMetrics.totalEdgesDecayed() > 0 || graphMetrics.totalEdgesSurviving() > 0) {
            log.info("Reflect: graph health — {}", graphMetrics);
        }

        wal.append(WalEvent.EventType.REFLECT, "system", null);

        return new ReflectReport(
                daemonReport.consolidatedCount(), daemonReport.tombstonedCount(),
                daemonReport.compactedPartitions(), temporalPruned,
                daemonReport.duration(), graphMetrics);
    }

    // ── Phase 2: Hebbian Decay ──

    private void decayHebbianEdges(PartitionManager partitionManager, MemoryIndex index, GraphHealthMetrics metrics) {
        try {
            hebbianGraph.setDecayModulator(
                    new SynapticDecayModulator(partitionManager, index, hebbianGraph.capacity()));

            int decayed = hebbianGraph.decayEdges(HEBBIAN_DECAY_FACTOR, metrics);
            hebbianGraph.setDecayModulator(null);

            if (decayed > 0) {
                log.info("Reflect: Hebbian graph decayed {} weak edges (arousal-modulated across partitions)", decayed);
            }
        } catch (RuntimeException e) {
            hebbianGraph.setDecayModulator(null);
            SpectorGraphDecayException ex = new SpectorGraphDecayException("Hebbian edge decay", e);
            log.warn(ex.getMessage());
        }
    }

    private void decayHebbianEdges(CognitiveMemoryRouter cognitiveRouter, GraphHealthMetrics metrics) {
        try {
            // Wire arousal-modulated decay: read synaptic importance/arousal before decay
            hebbianGraph.setDecayModulator(
                    new SynapticDecayModulator(cognitiveRouter, hebbianGraph.capacity()));

            int decayed = hebbianGraph.decayEdges(HEBBIAN_DECAY_FACTOR, metrics);

            // Clear modulator — snapshot is no longer valid after decay
            hebbianGraph.setDecayModulator(null);

            if (decayed > 0) {
                log.info("Reflect: Hebbian graph decayed {} weak edges (arousal-modulated)", decayed);
            }
        } catch (RuntimeException e) {
            hebbianGraph.setDecayModulator(null); // clean up on failure
            SpectorGraphDecayException ex = new SpectorGraphDecayException("Hebbian edge decay", e);
            log.warn(ex.getMessage());
        }
    }

    // ── Phase 3: Temporal Pruning ──

    private int pruneTemporalChain(PartitionManager partitionManager, MemoryIndex index) {
        if (temporalChain == null) return 0;
        try {
            long cutoffMs = System.currentTimeMillis()
                    - (long) temporalRetentionDays * 24 * 60 * 60 * 1000;

            int agePruned = temporalChain.pruneOlderThan(cutoffMs);

            int importancePruned = 0;
            if (partitionManager != null && index != null) {
                importancePruned = temporalChain.pruneByImportance(
                        cutoffMs, TEMPORAL_IMPORTANCE_THRESHOLD,
                        memIdx -> {
                            try {
                                String id = index.idAt(memIdx);
                                if (id == null) return 0f;
                                var loc = index.locate(id);
                                if (loc == null) return 0f;
                                var router = partitionManager.routerFor(loc.colocatedPartition());
                                if (router == null) return 0f;
                                var body = router.readRecordBody(loc, false);
                                return body != null && body.header() != null ? body.header().importance() : 0f;
                            } catch (RuntimeException e) {
                                return 0f;
                            }
                        });
            }

            return agePruned + importancePruned;
        } catch (RuntimeException e) {
            log.warn("Temporal chain pruning failed: {}", e.getMessage());
            return 0;
        }
    }

    private int pruneTemporalChain(CognitiveMemoryRouter cognitiveRouter) {
        if (temporalChain == null) return 0;
        try {
            long cutoffMs = System.currentTimeMillis()
                    - (long) temporalRetentionDays * 24 * 60 * 60 * 1000;

            // Phase 3a: Age-based pruning (original behavior)
            int agePruned = temporalChain.pruneOlderThan(cutoffMs);

            // Phase 3b: Importance-based pruning — protects high-importance temporal links
            int importancePruned = 0;
            if (cognitiveRouter != null && cognitiveRouter.episodic() != null) {
                var episodic = cognitiveRouter.episodic();
                var layout = episodic.layout();
                var segment = episodic.segment();
                int totalRecs = episodic.totalRecords();

                importancePruned = temporalChain.pruneByImportance(
                        cutoffMs, TEMPORAL_IMPORTANCE_THRESHOLD,
                        memIdx -> {
                            if (memIdx < 0 || memIdx >= totalRecs) return 0f;
                            try {
                                long offset = episodic.recordOffset(memIdx);
                                return layout.readImportance(segment, offset);
                            } catch (RuntimeException e) {
                                return 0f;
                            }
                        });
            }

            return agePruned + importancePruned;
        } catch (RuntimeException e) {
            log.warn("Temporal chain pruning failed: {}", e.getMessage());
            return 0;
        }
    }

    // ── Phase 4: Cross-Layer Promotion (Hebbian → Entity) ──

    private void promoteCrossLayer() {
        try {
            int crossPromoted = promoteHebbianToEntity(HEBBIAN_PROMOTION_MIN_WEIGHT);
            if (crossPromoted > 0) {
                log.info("Reflect: cross-layer promoted {} Hebbian edges to entity relations",
                        crossPromoted);
            }
        } catch (RuntimeException e) {
            log.warn("Cross-layer promotion failed: {}", e.getMessage());
        }
    }

    // ── Phase 4b: STC Cross-Capture (Hebbian → Entity Boost) ──

    /**
     * Propagates Hebbian co-activation strength to entity edges (Synaptic Tagging
     * and Capture).
     *
     * <p>For each strong Hebbian edge (memA ↔ memB), boosts existing entity edges
     * between memA's entities and memB's entities. This mirrors the biological STC
     * mechanism where strong synapses protect nearby weak ones through shared
     * plasticity-related proteins (Frey & Morris, 1997).</p>
     *
     * <p>Cross-capture only boosts <em>existing</em> entity edges — it never creates
     * new relations. The boost is capped at {@link #CROSS_CAPTURE_MAX_BOOST} per
     * cycle to prevent runaway amplification.</p>
     *
     * @param metrics collector for cross-capture telemetry
     */
    private void crossCaptureHebbianToEntity(GraphHealthMetrics metrics) {
        if (entityDirectory == null || entityDirectory.entityCount() == 0) return;
        if (hyperEntityGraph == null) return;
        try {
            // Build reverse index: memoryIdx → List<entityId>
            int ecnt = entityDirectory.entityCount();
            Map<Integer, List<Integer>> memToEntities = new HashMap<>();
            for (int e = 0; e < ecnt; e++) {
                int refCount = entityDirectory.memoryRefCount(e);
                for (int r = 0; r < refCount; r++) {
                    int memIdx = entityDirectory.memoryRefAt(e, r);
                    if (memIdx >= 0) {
                        memToEntities.computeIfAbsent(memIdx, k -> new ArrayList<>(2)).add(e);
                    }
                }
            }

            int captured = 0;
            int capacity = hebbianGraph.capacity();

            for (int nodeA = 0; nodeA < capacity; nodeA++) {
                var edges = hebbianGraph.neighbors(nodeA);
                for (var edge : edges) {
                    if (edge.weight() < CROSS_CAPTURE_MIN_WEIGHT) break; // sorted descending
                    int nodeB = edge.neighborIndex();
                    if (nodeB <= nodeA) continue; // avoid double-processing A↔B

                    var entitiesA = memToEntities.get(nodeA);
                    var entitiesB = memToEntities.get(nodeB);
                    if (entitiesA == null || entitiesB == null) continue;

                    // Compute boost: scale Hebbian weight, cap at maximum
                    float boost = Math.min(
                            edge.weight() * CROSS_CAPTURE_SCALE_FACTOR,
                            CROSS_CAPTURE_MAX_BOOST);

                    for (int eA : entitiesA) {
                        for (int eB : entitiesB) {
                            if (eA != eB) {
                                // Boost 2-vertex hyperedges (ADR-0003 #459: replaces directional binary edges)
                                if (hyperEntityGraph.boostHyperedgeWeight(eA, eB, boost)) {
                                    captured++;
                                    if (metrics != null) metrics.recordCrossCapture();
                                }
                                if (hyperEntityGraph.boostHyperedgeWeight(eB, eA, boost)) {
                                    captured++;
                                    if (metrics != null) metrics.recordCrossCapture();
                                }
                            }
                        }
                    }
                }
            }

            if (captured > 0) {
                log.info("Reflect: STC cross-capture boosted {} entity edges from strong Hebbian links",
                        captured);
            }
        } catch (RuntimeException e) {
            log.warn("STC cross-capture failed: {}", e.getMessage());
        }
    }

    /**
     * Promotes strong Hebbian co-activation edges into entity-level RELATED_TO edges.
     *
     * <p>For each Hebbian edge with weight ≥ {@code minWeight}, scans both endpoint
     * memories' entity associations and creates RELATED_TO edges between all entity
     * pairs. This bridges the statistical co-occurrence layer (Hebbian) with the
     * structured knowledge layer (Entity graph).</p>
     *
     * @param minWeight minimum Hebbian weight to qualify for promotion
     * @return number of entity relations created or strengthened
     */
    private int promoteHebbianToEntity(float minWeight) {
        if (entityDirectory == null || entityDirectory.entityCount() == 0) return 0;
        if (hyperEntityGraph == null) return 0;

        // Build reverse index: memoryIdx → List<entityId>
        int ecnt = entityDirectory.entityCount();
        Map<Integer, List<Integer>> memToEntities = new HashMap<>();
        for (int e = 0; e < ecnt; e++) {
            int refCount = entityDirectory.memoryRefCount(e);
            for (int r = 0; r < refCount; r++) {
                int memIdx = entityDirectory.memoryRefAt(e, r);
                if (memIdx >= 0) {
                    memToEntities.computeIfAbsent(memIdx, k -> new ArrayList<>(2)).add(e);
                }
            }
        }

        int promoted = 0;
        int capacity = hebbianGraph.capacity();

        for (int nodeA = 0; nodeA < capacity; nodeA++) {
            var edges = hebbianGraph.neighbors(nodeA);
            for (var edge : edges) {
                if (edge.weight() < minWeight) break; // sorted descending
                int nodeB = edge.neighborIndex();
                if (nodeB <= nodeA) continue; // avoid double-processing A↔B

                var entitiesA = memToEntities.get(nodeA);
                var entitiesB = memToEntities.get(nodeB);
                if (entitiesA == null || entitiesB == null) continue;

                for (int eA : entitiesA) {
                    for (int eB : entitiesB) {
                        if (eA != eB) {
                            // ADR-0003 #459: RELATED_TO becomes a 2-vertex typed hyperedge
                            hyperEntityGraph.addHyperedge(
                                    new int[]{eA, eB},
                                    new int[]{HyperEntityGraphMemory.ROLE_SUBJECT, HyperEntityGraphMemory.ROLE_OBJECT},
                                    0, // type id 0 = RELATED_TO (default/untyped)
                                    1.0f, -1, System.currentTimeMillis());
                            promoted++;
                        }
                    }
                }
            }
        }
        return promoted;
    }

    // ── Phase 5: Entity Graph Maintenance ──

    private void maintainEntityGraph(GraphHealthMetrics metrics) {
        // ADR-0003 #459: identity maintenance (merge) uses EntityDirectory;
        // hyperedge decay is handled separately in decayHyperEntityGraph().
        if (entityDirectory == null || entityDirectory.entityCount() == 0) return;
        try {
            int entityMerged;
            if (entityResolutionEnabled && embeddingProvider != null && llmProvider != null) {
                entityMerged = entityDirectory.mergeSimilarEntities(embeddingProvider, llmProvider, entityCosineThreshold, entityShadowMode, typeNormalizer);
            } else {
                entityMerged = entityDirectory.mergeSimilarEntities(ENTITY_MERGE_DISTANCE, typeNormalizer);
            }
            if (entityMerged > 0) {
                log.info("Reflect: merged {} similar entities", entityMerged);
            }
        } catch (RuntimeException e) {
            log.warn("Entity graph maintenance failed: {}", e.getMessage());
        }
    }

    // ── Phase 5b: Entity→Memory Adjacency LTD Decay ──

    private void decayEntityAdjacency() {
        if (entityDirectory == null || entityDirectory.entityCount() == 0) return;
        try {
            int pruned = entityDirectory.decayAdjacencyWeights(
                    ENTITY_ADJ_DECAY_FACTOR, ENTITY_ADJ_PRUNE_THRESHOLD);
            if (pruned > 0) {
                log.info("Reflect: LTD decayed entity→memory adjacency, pruned {} weak links", pruned);
            }
        } catch (RuntimeException e) {
            log.warn("Entity adjacency decay failed: {}", e.getMessage());
        }
    }

    // ── Phase 5c: Adjacency Compaction (Defragmentation) ──

    private void compactEntityAdjacency() {
        if (entityDirectory == null || entityDirectory.entityCount() == 0) return;
        try {
            long reclaimed = entityDirectory.compactAdjacency();
            if (reclaimed > 0) {
                log.info("Reflect: adjacency compaction reclaimed {}KB", reclaimed / 1024);
            }
        } catch (RuntimeException e) {
            log.warn("Entity adjacency compaction failed: {}", e.getMessage());
        }
    }

    // ── Phase 5d: HyperEntityGraph Decay ──

    private void decayHyperEntityGraph() {
        if (hyperEntityGraph == null) return;
        try {
            int evicted = hyperEntityGraph.decayHyperedges(ENTITY_DECAY_FACTOR, ENTITY_PRUNE_THRESHOLD);
            if (evicted > 0) {
                log.info("Reflect: HyperEntityGraph decayed {} weak hyperedges", evicted);
            }
        } catch (RuntimeException e) {
            log.warn("HyperEntityGraph decay failed: {}", e.getMessage());
        }
    }
}
