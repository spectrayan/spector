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
package com.spectrayan.spector.memory.pipeline;


import com.spectrayan.spector.memory.error.SpectorEntityGraphException;
import com.spectrayan.spector.memory.error.SpectorHebbianException;
import com.spectrayan.spector.memory.error.SpectorTemporalChainException;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.cortex.AbstractCognitiveRecordMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import static com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline stage: Graph expansion (Steps 5c-5e).
 *
 * <p>Expands recall results by following three cognitive graph layers:</p>
 * <ul>
 *   <li><b>Hebbian</b> (5c): Spreading activation across memory-to-memory associations</li>
 *   <li><b>Temporal</b> (5d): Follow session-linked sequences forward/backward</li>
 *   <li><b>Entity</b> (5e): Multi-hop knowledge graph traversal via extracted entities</li>
 * </ul>
 *
 * <p>All graph-expanded candidates are similarity-grounded using co-fusion:
 * actual L2 distance to the query vector is computed for each neighbor,
 * preventing fabricated scores.</p>
 *
 * <p>Cross-layer deduplication ensures each memory appears at most once,
 * keeping the highest score across all three layers.</p>
 *
 * @see RecallPipeline
 * @see GraphScoringPolicy
 */
final class GraphExpansionStage {

    private static final Logger log = LoggerFactory.getLogger(GraphExpansionStage.class);

    // ── Dependencies (all nullable — graceful degradation) ──
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    /** Identity companion (ADR-0003 #455). When present, identity reads (findEntity/fanFactor) route here. */
    private final EntityDirectory entityDirectory;
    private final com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph;
    private final EntityExtractor entityExtractor;
    private final GraphScoringPolicy graphScoringPolicy;
    private final MemoryIndex index;
    private final PartitionRegistry partitionRegistry;   // #443: live registry (nullable in tests)
    private final float[] calibrationMins;
    private final float[] calibrationScales;

    GraphExpansionStage(HebbianGraphBase hebbianGraph,
                        TemporalChainMemory temporalChain,
                        EntityDirectory entityDirectory,
                        com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph,
                        EntityExtractor entityExtractor,
                        GraphScoringPolicy graphScoringPolicy,
                        MemoryIndex index,
                        PartitionRegistry partitionRegistry,
                        float[] calibrationMins,
                        float[] calibrationScales) {
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.entityExtractor = entityExtractor;
        this.graphScoringPolicy = graphScoringPolicy != null ? graphScoringPolicy : GraphScoringPolicy.DEFAULT;
        this.index = index;
        this.partitionRegistry = partitionRegistry;
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
    }

    /**
     * Active router for graph-slot resolution. Graph expansion operates on the active
     * partition for #443 (spanning frozen partitions for graphs is a deferred follow-up).
     */
    private CognitiveMemoryRouter activeRouter() {
        return partitionRegistry != null ? partitionRegistry.activeRouter() : null;
    }

    /**
     * Returns true if any graph subsystem is available for expansion.
     */
    boolean hasGraphSubsystems() {
        return hebbianGraph != null || temporalChain != null || hyperEntityGraph != null;
    }

    /**
     * Expands results by following Hebbian, temporal, and entity graph edges.
     *
     * <p>Modifies {@code allResults} in-place by appending deduplicated
     * graph-expanded candidates. Skipped entirely when cognitive scoring
     * is disabled or direct similarity exceeds the expansion threshold.</p>
     *
     * @param allResults   mutable result list (modified in-place)
     * @param queryVector  the embedded query vector
     * @param options      recall options (for expansion threshold, entity hints)
     */
    void expand(List<CognitiveResult> allResults, float[] queryVector, RecallOptions options) {
        boolean cognitiveScoring = options.scoringMode() != ScoringMode.SIMILARITY;
        boolean hasSubsystems = hebbianGraph != null || temporalChain != null
                || (entityDirectory != null && (entityExtractor != null && entityExtractor.isAvailable()
                        || !options.entityHints().isEmpty()));

        if (!cognitiveScoring || !hasSubsystems || allResults.isEmpty()) {
            return;
        }

        // ── Similarity-gated expansion ──
        GraphExpansionMode mode = graphScoringPolicy.graphExpansionMode();
        if (mode == GraphExpansionMode.ENTITY_ONLY && options.entityHints().isEmpty()) {
            log.debug("Graph expansion skipped: ENTITY_ONLY mode and no entity hints");
            return;
        }

        if (mode == GraphExpansionMode.GATED) {
            float maxDirectSimilarity = 0f;
            for (CognitiveResult r : allResults) {
                if (r.hasBreakdown()) {
                    maxDirectSimilarity = Math.max(maxDirectSimilarity, r.breakdown().similarity());
                }
            }
            // Resolve threshold: prefer options value, but fall back to policy if options
            // has the default (0.40) — this ensures the policy-level override is respected
            // even when callers don't explicitly set the threshold on RecallOptions.
            float optionsThreshold = options.graphExpansionThreshold();
            float policyThreshold = graphScoringPolicy.graphExpansionThreshold();
            float expansionThreshold = (optionsThreshold == 0.40f && policyThreshold != 0.40f)
                    ? policyThreshold : optionsThreshold;

            // Diagnostic: emit maxDirectSimilarity for histogram analysis
            log.info("GATED diagnostic: maxDirectSimilarity={}, threshold={} (options={}, policy={})",
                    String.format("%.6f", maxDirectSimilarity),
                    String.format("%.4f", expansionThreshold),
                    String.format("%.4f", optionsThreshold),
                    String.format("%.4f", policyThreshold));

            if (maxDirectSimilarity >= expansionThreshold) {
                log.debug("Graph expansion skipped: maxDirectSimilarity={} >= threshold={}",
                        maxDirectSimilarity, expansionThreshold);
                return;
            }
        }
        // ALWAYS mode: fall through unconditionally

        // Build existingIds ONCE for all three layers
        Set<String> existingIds = new HashSet<>(allResults.size());
        for (CognitiveResult r : allResults) {
            if (r.id() != null) existingIds.add(r.id());
        }

        // Cross-layer dedup: track best score per graph-expanded candidate
        Map<String, CognitiveResult> graphCandidates = new HashMap<>();


        // Step 5c: Hebbian spreading activation
        if (hebbianGraph != null) {
            expandHebbian(allResults, existingIds, graphCandidates, queryVector, options);
        }

        // Step 5d: Temporal chain extension
        if (temporalChain != null) {
            expandTemporal(allResults, existingIds, graphCandidates, queryVector, options);
        }


        // Step 5e: Entity graph traversal (hyper-only — identity from the directory,
        // topology from the hypergraph; ADR-0003 #456)
        if (entityDirectory != null) {
            expandEntity(allResults, existingIds, graphCandidates, queryVector, options);
        }

        // Add deduplicated graph candidates to results
        if (!graphCandidates.isEmpty()) {
            allResults.addAll(graphCandidates.values());
            for (String id : graphCandidates.keySet()) {
                existingIds.add(id);
            }
            log.debug("Graph expansion added {} candidates (from {} layers)",
                    graphCandidates.size(),
                    (hebbianGraph != null ? 1 : 0) + (temporalChain != null ? 1 : 0) + (entityDirectory != null ? 1 : 0));
        }

    }

    // ─────────────── Private helpers ───────────────

    private void expandHebbian(List<CognitiveResult> allResults,
                                 Set<String> existingIds,
                                 Map<String, CognitiveResult> graphCandidates,
                                 float[] queryVector,
                                 RecallOptions options) {
        try {
            int seeds = Math.min(3, allResults.size());
            for (int s = 0; s < seeds; s++) {
                CognitiveResult seed = allResults.get(s);
                MemoryIndex.MemoryLocation loc = index.locate(seed.id());
                if (loc == null) continue;

                int memIdx = loc.graphSlot();
                var activated = hebbianGraph.activateNeighbors(memIdx, graphScoringPolicy.hebbianMaxDepth());
                for (var edge : activated) {
                    String neighborId = ((com.spectrayan.spector.memory.index.IndexRecordMemory) index).idAt(edge.neighborIndex());
                    if (neighborId == null) continue;
                    if (!existingIds.contains(neighborId) && matchesFilters(neighborId, options)) {
                        float neighborSim = computeNeighborSimilarity(neighborId, queryVector);
                        float saturatedWeight = Math.min(edge.weight() / 5.0f, 1.0f);
                        float graphScore = neighborSim
                                + seed.score() * saturatedWeight * graphScoringPolicy.hebbianBoostFactor();

                        CognitiveResult candidate = buildGraphCandidate(
                                neighborId, graphScore, seed, MemoryType.SEMANTIC, "HEBBIAN", neighborSim);
                        graphCandidates.merge(neighborId, candidate,
                                (a, b) -> a.score() >= b.score() ? a : b);
                    }
                }
            }
        } catch (RuntimeException e) {
            SpectorHebbianException ex = new SpectorHebbianException("spreading activation", e);
            log.debug(ex.getMessage());
        }
    }

    /**
     * Temporal chain extension — follow session-linked sequences.
     */
    private void expandTemporal(List<CognitiveResult> allResults,
                                 Set<String> existingIds,
                                 Map<String, CognitiveResult> graphCandidates,
                                 float[] queryVector,
                                 RecallOptions options) {
        try {
            int seeds = Math.min(3, allResults.size());
            for (int s = 0; s < seeds; s++) {
                CognitiveResult seed = allResults.get(s);
                MemoryIndex.MemoryLocation loc = index.locate(seed.id());
                if (loc == null) continue;

                int memIdx = loc.graphSlot();
                for (int chainIdx : temporalChain.followForward(memIdx, graphScoringPolicy.temporalMaxHops())) {
                    addChainResultCoFusion(chainIdx, seed, existingIds, graphCandidates,
                            queryVector, graphScoringPolicy.temporalForwardFactor(), options);
                }
                for (int chainIdx : temporalChain.followBackward(memIdx, graphScoringPolicy.temporalMaxHops())) {
                    addChainResultCoFusion(chainIdx, seed, existingIds, graphCandidates,
                            queryVector, graphScoringPolicy.temporalBackwardFactor(), options);
                }
            }
        } catch (RuntimeException e) {
            SpectorTemporalChainException ex = new SpectorTemporalChainException("chain extension", e);
            log.debug(ex.getMessage());
        }
    }


    /**
     * Entity graph traversal — multi-hop knowledge discovery.
     */
    private void expandEntity(List<CognitiveResult> allResults,
                               Set<String> existingIds,
                               Map<String, CognitiveResult> graphCandidates,
                               float[] queryVector, RecallOptions options) {
        List<ExtractedEntity> queryEntities = null;

        // Priority 1: Pre-extracted entity hints from RecallOptions
        if (!options.entityHints().isEmpty()) {
            queryEntities = options.entityHints();
        }
        // Priority 2: Live EntityExtractor SPI
        else if (entityExtractor != null && entityExtractor.isAvailable()) {
            try {
                // We don't have the query text here — extract from first result
                // This is a compromise vs. passing queryText through the stage
                queryEntities = entityExtractor.extract("query", allResults.getFirst().text());
            } catch (RuntimeException e) {
                SpectorEntityGraphException ex = new SpectorEntityGraphException("entity extraction", e);
                log.debug(ex.getMessage());
            }
        }

        if (queryEntities == null || queryEntities.isEmpty()) return;

        try {
            // Identity (name→id, fan factor) from the directory; topology (reachable memories)
            // from the hypergraph — unconditionally, no binary-graph fallback (ADR-0003 #456).
            if (entityDirectory == null || hyperEntityGraph == null) return;
            for (var entity : queryEntities) {
                int entityId = entityDirectory.findEntity(entity.name());
                if (entityId < 0) continue;

                Set<Integer> reachableMemories =
                        hyperEntityGraph.collectMemories(entityId, graphScoringPolicy.entityMaxHops());
                for (int memIdx : reachableMemories) {
                    String memId = ((com.spectrayan.spector.memory.index.IndexRecordMemory) index).idAt(memIdx);
                    if (memId == null) continue;
                    if (!existingIds.contains(memId) && matchesFilters(memId, options)) {
                        float neighborSim = computeNeighborSimilarity(memId, queryVector);
                        float fanAttenuation = entityDirectory.fanFactor(entityId);
                        float entityScore = neighborSim
                                + allResults.getFirst().score()
                                  * graphScoringPolicy.entityHopAttenuation()
                                  * fanAttenuation;

                        CognitiveResult candidate = buildGraphCandidate(
                                memId, entityScore, null, MemoryType.SEMANTIC, "ENTITY", neighborSim);
                        graphCandidates.merge(memId, candidate,
                                (a, b) -> a.score() >= b.score() ? a : b);
                    }
                }

                // Traversal of CONTRADICTS hyperedges (#528)
                // If this entity is part of a corrected relation, traverse to the corrector entity's memories
                List<HyperEntityGraphMemory.HyperEdge> hyperedges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                if (hyperedges != null) {
                    for (var edge : hyperedges) {
                        if (edge.type() == HyperEntityGraphMemory.TYPE_CONTRADICTS && edge.vertices() != null) {
                            boolean isCorrected = false;
                            int correctorEntityId = -1;
                            for (var v : edge.vertices()) {
                                if (v.entityId() == entityId && v.roleId() == HyperEntityGraphMemory.ROLE_CORRECTED) {
                                    isCorrected = true;
                                }
                                if (v.roleId() == HyperEntityGraphMemory.ROLE_CORRECTOR) {
                                    correctorEntityId = v.entityId();
                                }
                            }
                            if (isCorrected && correctorEntityId >= 0) {
                                int refCount = entityDirectory.memoryRefCount(correctorEntityId);
                                for (int r = 0; r < refCount; r++) {
                                    int memIdx = entityDirectory.memoryRefAt(correctorEntityId, r);
                                    if (memIdx >= 0) {
                                        String memId = ((com.spectrayan.spector.memory.index.IndexRecordMemory) index).idAt(memIdx);
                                        if (memId != null && !existingIds.contains(memId) && matchesFilters(memId, options)) {
                                            float neighborSim = computeNeighborSimilarity(memId, queryVector);
                                            float entityScore = neighborSim
                                                    + allResults.getFirst().score()
                                                      * graphScoringPolicy.entityHopAttenuation()
                                                      * 1.2f;

                                            CognitiveResult candidate = buildGraphCandidate(
                                                    memId, entityScore, null, MemoryType.SEMANTIC, "CONTRADICTION_CORRECTOR", neighborSim);
                                            graphCandidates.merge(memId, candidate,
                                                    (a, b) -> a.score() >= b.score() ? a : b);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            SpectorEntityGraphException ex = new SpectorEntityGraphException("graph traversal", e);
            log.debug(ex.getMessage());
        }
    }

    /**
     * Adds a temporal chain result using co-fusion scoring.
     */
    private void addChainResultCoFusion(int chainIdx, CognitiveResult seed,
                                         Set<String> existingIds,
                                         Map<String, CognitiveResult> graphCandidates,
                                         float[] queryVector, float attenuation, RecallOptions options) {
        String chainId = ((com.spectrayan.spector.memory.index.IndexRecordMemory) index).idAt(chainIdx);
        if (chainId == null) return;
        if (!existingIds.contains(chainId) && matchesFilters(chainId, options)) {
            float neighborSim = computeNeighborSimilarity(chainId, queryVector);
            float chainScore = neighborSim + seed.score() * attenuation * 0.2f;

            CognitiveResult candidate = buildGraphCandidate(chainId, chainScore, seed, seed.memoryType(), "TEMPORAL", neighborSim);
            graphCandidates.merge(chainId, candidate,
                    (a, b) -> a.score() >= b.score() ? a : b);
        }
    }


    /**
     * Builds a CognitiveResult for a graph-expanded candidate using index metadata.
     */
    private CognitiveResult buildGraphCandidate(String memId, float score,
                                                 CognitiveResult seed, MemoryType type, String graphSource, float neighborSim) {
        String text = index.text(memId);
        MemorySource source = index.source(memId);
        String[] tags = index.tags(memId);
        java.util.Map<String, String> meta = index.metadata(memId);
        SourceModality modality = meta != null
                ? SourceModality.fromName(meta.get(SourceModality.METADATA_KEY))
                : SourceModality.TEXT;

        float importance = seed != null ? seed.importance() : 0.5f;

        // Populate ScoreBreakdown so benchmark telemetry and scoring trace work properly
        float graphBoost = Math.max(0.002f, score - neighborSim);
        ScoreBreakdown breakdown = new ScoreBreakdown(
                neighborSim,
                0f,
                1.0f,
                1.0f,
                graphBoost,
                1.0f,
                score
        );

        // Populate metadata with graph telemetry
        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        if (meta != null) {
            metadata.putAll(meta);
        }
        metadata.put("graph_source", graphSource);
        if (seed != null) {
            metadata.put("graph_seed_id", seed.id());
            metadata.put("graph_seed_score", String.valueOf(seed.score()));
        }

        return new CognitiveResult(
                memId, text, score, importance, 0f,
                (short) 0, (byte) 0, type, source,
                tags, 1.0f, 1.0f, RetrievalMode.STANDARD, breakdown, null,
                modality, metadata);
    }



    /**
     * Computes actual cosine-derived similarity for a graph-expanded neighbor.
     */
    float computeNeighborSimilarity(String memoryId, float[] queryVector) {
        try {
            MemoryIndex.MemoryLocation loc = index.locate(memoryId);
            if (loc == null) return 0f;

            // #443: resolve the neighbor's segment by the partition it actually lives in.
            CognitiveMemoryRouter router = partitionRegistry != null
                    ? partitionRegistry.routerFor(loc.colocatedPartition()) : null;
            if (router == null) return 0f;
            MemorySegment seg = router.segmentFor(loc.type());
            if (seg == null) return 0f;

            CognitiveRecordLayout layout = router.layoutFor(loc.type());
            float l2dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                    queryVector, seg, layout.vectorOffset(loc.offset()),
                    calibrationMins, calibrationScales, layout.quantizedVecBytes());
            return 1.0f / (1.0f + l2dist);
        } catch (RuntimeException e) {
            log.trace("Failed to compute neighbor similarity for '{}': {}", memoryId, e.getMessage());
            return 0f;
        }
    }

    private boolean matchesFilters(String neighborId, RecallOptions options) {
        MemoryIndex.MemoryLocation loc = index.locate(neighborId);
        if (loc == null) return false;

        // 1. Memory Type Gating
        if (options.memoryTypes() != null) {
            boolean typeAllowed = false;
            for (MemoryType t : options.memoryTypes()) {
                if (t == loc.type()) {
                    typeAllowed = true;
                    break;
                }
            }
            if (!typeAllowed) return false;
        }

        // 2. Synaptic Tag Gating
        String[] tags = index.tags(neighborId);
        long recordTags = SynapticTagEncoder.encode(tags);
        if (options.hyperfocusMask() != 0L) {
            if ((recordTags & options.hyperfocusMask()) != options.hyperfocusMask()) {
                return false;
            }
        } else if (options.synapticTagMask() != 0L) {
            if ((recordTags & options.synapticTagMask()) == 0L) {
                return false;
            }
        }


        // 3. Valence Gating & Tombstone & Contradiction
        CognitiveMemoryRouter router = partitionRegistry != null
                ? partitionRegistry.routerFor(loc.colocatedPartition()) : null;
        if (router == null) return false;
        MemorySegment seg = router.segmentFor(loc.type());
        if (seg == null) return false;

        byte flags = seg.get(LAYOUT_FLAGS, loc.offset() + OFFSET_FLAGS);
        if ((flags & FLAG_TOMBSTONE) != 0) {
            return false;
        }

        if (!options.includeContradictions()) {
            byte cFlags = seg.get(LAYOUT_CONSOLIDATION_FLAGS, loc.offset() + OFFSET_CONSOLIDATION_FLAGS);
            if ((cFlags & FLAG_CONTRADICTED) != 0) {
                return false;
            }
        }

        byte valence = seg.get(LAYOUT_VALENCE, loc.offset() + OFFSET_VALENCE);
        if (valence < options.minValence() || valence > options.maxValence()) {
            return false;
        }

        float importance = seg.get(LAYOUT_IMPORTANCE, loc.offset() + OFFSET_IMPORTANCE);
        if (importance < options.minImportance()) {
            return false;
        }

        return true;
    }
}

