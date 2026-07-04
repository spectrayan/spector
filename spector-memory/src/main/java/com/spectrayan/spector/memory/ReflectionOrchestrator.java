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

import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.error.SpectorGraphDecayException;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.graph.HyperEntityGraph;
// RelationType enum replaced by open-schema strings via TypeRegistry
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hebbian.SynapticDecayModulator;
import com.spectrayan.spector.memory.hippocampus.ReflectDaemon;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.temporal.TemporalChain;

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
 */
final class ReflectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReflectionOrchestrator.class);

    /** Minimum Hebbian weight to qualify for cross-layer promotion to entity graph. */
    private static final float HEBBIAN_PROMOTION_MIN_WEIGHT = 3.0f;

    /** Hebbian decay factor per reflection cycle (10% decay = multiply by 0.9). */
    private static final float HEBBIAN_DECAY_FACTOR = 0.9f;

    /** Entity edge decay factor per cycle (5% decay). */
    private static final float ENTITY_DECAY_FACTOR = 0.95f;

    /** Entity edge pruning threshold (edges below this weight are removed). */
    private static final float ENTITY_PRUNE_THRESHOLD = 0.5f;

    /** Entity→memory adjacency decay factor per cycle (5% decay — LTD). */
    private static final float ENTITY_ADJ_DECAY_FACTOR = 0.95f;

    /** Entity→memory adjacency pruning threshold (links below this are removed). */
    private static final float ENTITY_ADJ_PRUNE_THRESHOLD = 0.2f;

    /** Levenshtein distance threshold for merging near-duplicate entities. */
    private static final int ENTITY_MERGE_DISTANCE = 2;

    // ── STC Cross-Capture Constants ──

    /**
     * Minimum Hebbian weight to qualify for cross-capture propagation.
     * Only moderately-strong edges propagate signals across layers.
     */
    private static final float CROSS_CAPTURE_MIN_WEIGHT = 2.0f;

    /**
     * Scale factor mapping Hebbian weight to entity edge boost.
     * Hebbian weight 4.0 × 0.05 = 0.20 boost to entity edge weight.
     */
    private static final float CROSS_CAPTURE_SCALE_FACTOR = 0.05f;

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
    private final TemporalChain temporalChain;
    private final EntityGraph entityGraph;
    private final HyperEntityGraph hyperEntityGraph;
    private final MemoryWal wal;
    private final int temporalRetentionDays;

    ReflectionOrchestrator(ReflectDaemon reflectDaemon,
                           HebbianGraphBase hebbianGraph,
                           TemporalChain temporalChain,
                           EntityGraph entityGraph,
                           HyperEntityGraph hyperEntityGraph,
                           MemoryWal wal,
                           int temporalRetentionDays) {
        this.reflectDaemon = reflectDaemon;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityGraph = entityGraph;
        this.hyperEntityGraph = hyperEntityGraph;
        this.wal = wal;
        this.temporalRetentionDays = temporalRetentionDays;
    }

    /**
     * Runs a full reflection cycle: REM consolidation, graph decay, temporal pruning,
     * cross-layer promotion, and entity maintenance.
     *
     * @param tierRouter the current tier router
     * @param index      the memory index (for text lookups during consolidation)
     * @return a {@link ReflectReport} summarizing what was consolidated, pruned, and promoted
     */
    ReflectReport reflect(TierRouter tierRouter, MemoryIndex index) {
        log.info("Manual reflection triggered");

        // Create metrics collector for this cycle
        var graphMetrics = new GraphHealthMetrics();

        // Phase 1: REM cycle — episodic → semantic consolidation
        var semanticTarget = tierRouter.semantic();
        ReflectReport daemonReport = reflectDaemon.runCycle(
                tierRouter.episodic(), semanticTarget,
                offset -> index.findTextByOffset(MemoryType.EPISODIC, offset));

        // Phase 2: Hebbian decay (synaptic homeostasis, arousal-modulated)
        decayHebbianEdges(tierRouter, graphMetrics);

        // Phase 3: Temporal chain pruning (age + importance)
        int temporalPruned = pruneTemporalChain(tierRouter);

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

    // ── Phase 2: Hebbian Decay ──

    private void decayHebbianEdges(TierRouter tierRouter, GraphHealthMetrics metrics) {
        try {
            // Wire arousal-modulated decay: read synaptic importance/arousal before decay
            hebbianGraph.setDecayModulator(
                    new SynapticDecayModulator(tierRouter, hebbianGraph.capacity()));

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

    private int pruneTemporalChain(TierRouter tierRouter) {
        if (temporalChain == null) return 0;
        try {
            long cutoffMs = System.currentTimeMillis()
                    - (long) temporalRetentionDays * 24 * 60 * 60 * 1000;

            // Phase 3a: Age-based pruning (original behavior)
            int agePruned = temporalChain.pruneOlderThan(cutoffMs);

            // Phase 3b: Importance-based pruning — protects high-importance temporal links
            int importancePruned = 0;
            if (tierRouter != null && tierRouter.episodic() != null) {
                var episodic = tierRouter.episodic();
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
        if (entityGraph == null || entityGraph.entityCount() == 0) return;
        try {
            // Build reverse index: memoryIdx → List<entityId>
            int ecnt = entityGraph.entityCount();
            Map<Integer, List<Integer>> memToEntities = new HashMap<>();
            for (int e = 0; e < ecnt; e++) {
                int refCount = entityGraph.memoryRefCount(e);
                for (int r = 0; r < refCount; r++) {
                    int memIdx = entityGraph.memoryRefAt(e, r);
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
                                // Boost both directions (entity edges are directional)
                                if (entityGraph.boostEdgeWeight(eA, eB, boost)) {
                                    captured++;
                                    if (metrics != null) metrics.recordCrossCapture();
                                }
                                if (entityGraph.boostEdgeWeight(eB, eA, boost)) {
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
        if (entityGraph == null || entityGraph.entityCount() == 0) return 0;

        // Build reverse index: memoryIdx → List<entityId>
        int ecnt = entityGraph.entityCount();
        Map<Integer, List<Integer>> memToEntities = new HashMap<>();
        for (int e = 0; e < ecnt; e++) {
            int refCount = entityGraph.memoryRefCount(e);
            for (int r = 0; r < refCount; r++) {
                int memIdx = entityGraph.memoryRefAt(e, r);
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
                            entityGraph.addRelation(eA, eB, "RELATED_TO");
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
        if (entityGraph == null || entityGraph.entityCount() == 0) return;
        try {
            int entityDecayed = entityGraph.decayEdges(
                    ENTITY_DECAY_FACTOR, ENTITY_PRUNE_THRESHOLD, metrics);
            if (entityDecayed > 0) {
                log.info("Reflect: entity graph decayed {} weak edges", entityDecayed);
            }
            int entityMerged = entityGraph.mergeSimilarEntities(ENTITY_MERGE_DISTANCE);
            if (entityMerged > 0) {
                log.info("Reflect: merged {} similar entities", entityMerged);
            }
        } catch (RuntimeException e) {
            log.warn("Entity graph maintenance failed: {}", e.getMessage());
        }
    }

    // ── Phase 5b: Entity→Memory Adjacency LTD Decay ──

    private void decayEntityAdjacency() {
        if (entityGraph == null || entityGraph.entityCount() == 0) return;
        try {
            int pruned = entityGraph.decayAdjacencyWeights(
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
        if (entityGraph == null || entityGraph.entityCount() == 0) return;
        try {
            long reclaimed = entityGraph.compactAdjacency();
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
