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
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.cortex.AbstractTierStore;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.cortex.TierStore;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.temporal.TemporalChain;
import com.spectrayan.spector.core.similarity.SimilarityFunction;

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
    private final TemporalChain temporalChain;
    private final EntityGraph entityGraph;
    private final EntityExtractor entityExtractor;
    private final GraphScoringPolicy graphScoringPolicy;
    private final MemoryIndex index;
    private final TierRouter tierRouter;
    private final float[] calibrationMins;
    private final float[] calibrationScales;

    GraphExpansionStage(HebbianGraphBase hebbianGraph,
                        TemporalChain temporalChain,
                        EntityGraph entityGraph,
                        EntityExtractor entityExtractor,
                        GraphScoringPolicy graphScoringPolicy,
                        MemoryIndex index,
                        TierRouter tierRouter,
                        float[] calibrationMins,
                        float[] calibrationScales) {
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityGraph = entityGraph;
        this.entityExtractor = entityExtractor;
        this.graphScoringPolicy = graphScoringPolicy != null ? graphScoringPolicy : GraphScoringPolicy.DEFAULT;
        this.index = index;
        this.tierRouter = tierRouter;
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
    }

    /**
     * Returns true if any graph subsystem is available for expansion.
     */
    boolean hasGraphSubsystems() {
        return hebbianGraph != null || temporalChain != null || entityGraph != null;
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
                || (entityGraph != null && (entityExtractor != null && entityExtractor.isAvailable()
                        || !options.entityHints().isEmpty()));

        if (!cognitiveScoring || !hasSubsystems || allResults.isEmpty()) {
            return;
        }

        // ── Similarity-gated expansion ──
        float maxDirectSimilarity = 0f;
        for (CognitiveResult r : allResults) {
            if (r.hasBreakdown()) {
                maxDirectSimilarity = Math.max(maxDirectSimilarity, r.breakdown().similarity());
            }
        }
        float expansionThreshold = options.graphExpansionThreshold();
        if (maxDirectSimilarity >= expansionThreshold) {
            log.debug("Graph expansion skipped: maxDirectSimilarity={} >= threshold={}",
                    maxDirectSimilarity, expansionThreshold);
            return;
        }

        // Build existingIds ONCE for all three layers
        Set<String> existingIds = new HashSet<>(allResults.size());
        for (CognitiveResult r : allResults) {
            if (r.id() != null) existingIds.add(r.id());
        }

        // Cross-layer dedup: track best score per graph-expanded candidate
        Map<String, CognitiveResult> graphCandidates = new HashMap<>();


        // Step 5c: Hebbian spreading activation
        if (hebbianGraph != null) {
            expandHebbian(allResults, existingIds, graphCandidates, queryVector);
        }

        // Step 5d: Temporal chain extension
        if (temporalChain != null) {
            expandTemporal(allResults, existingIds, graphCandidates, queryVector);
        }

        // Step 5e: Entity graph traversal
        if (entityGraph != null) {
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
                    (hebbianGraph != null ? 1 : 0) + (temporalChain != null ? 1 : 0) + (entityGraph != null ? 1 : 0));
        }

    }

    // ─────────────── Private helpers ───────────────

    private void expandHebbian(List<CognitiveResult> allResults,
                                 Set<String> existingIds,
                                 Map<String, CognitiveResult> graphCandidates,
                                 float[] queryVector) {
        try {
            int seeds = Math.min(3, allResults.size());
            for (int s = 0; s < seeds; s++) {
                CognitiveResult seed = allResults.get(s);
                MemoryIndex.MemoryLocation loc = index.locate(seed.id());
                if (loc == null) continue;

                int memIdx = offsetToRecordIndex(loc);
                var activated = hebbianGraph.activateNeighbors(memIdx, graphScoringPolicy.hebbianMaxDepth());
                for (var edge : activated) {
                    String neighborId = findMemoryByApproximateIndex(edge.neighborIndex());
                    if (neighborId != null && !existingIds.contains(neighborId)) {
                        float neighborSim = computeNeighborSimilarity(neighborId, queryVector);
                        float saturatedWeight = Math.min(edge.weight() / 5.0f, 1.0f);
                        float graphScore = neighborSim
                                + seed.score() * saturatedWeight * graphScoringPolicy.hebbianBoostFactor();

                        CognitiveResult candidate = buildGraphCandidate(
                                neighborId, graphScore, seed, MemoryType.SEMANTIC);
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
                                 float[] queryVector) {
        try {
            int seeds = Math.min(3, allResults.size());
            for (int s = 0; s < seeds; s++) {
                CognitiveResult seed = allResults.get(s);
                MemoryIndex.MemoryLocation loc = index.locate(seed.id());
                if (loc == null) continue;

                int memIdx = offsetToRecordIndex(loc);
                for (int chainIdx : temporalChain.followForward(memIdx, graphScoringPolicy.temporalMaxHops())) {
                    addChainResultCoFusion(chainIdx, seed, existingIds, graphCandidates,
                            queryVector, graphScoringPolicy.temporalForwardFactor());
                }
                for (int chainIdx : temporalChain.followBackward(memIdx, graphScoringPolicy.temporalMaxHops())) {
                    addChainResultCoFusion(chainIdx, seed, existingIds, graphCandidates,
                            queryVector, graphScoringPolicy.temporalBackwardFactor());
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
            for (var entity : queryEntities) {
                int entityId = entityGraph.findEntity(entity.name());
                if (entityId < 0) continue;

                Set<Integer> reachableMemories = entityGraph.collectMemories(
                        entityId, null, graphScoringPolicy.entityMaxHops());
                for (int memIdx : reachableMemories) {
                    String memId = findMemoryByApproximateIndex(memIdx);
                    if (memId != null && !existingIds.contains(memId)) {
                        float neighborSim = computeNeighborSimilarity(memId, queryVector);
                        float fanAttenuation = entityGraph.fanFactor(entityId);
                        float entityScore = neighborSim
                                + allResults.getFirst().score()
                                  * graphScoringPolicy.entityHopAttenuation()
                                  * fanAttenuation;

                        CognitiveResult candidate = buildGraphCandidate(
                                memId, entityScore, null, MemoryType.SEMANTIC);
                        graphCandidates.merge(memId, candidate,
                                (a, b) -> a.score() >= b.score() ? a : b);
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
                                         float[] queryVector, float attenuation) {
        String chainId = findMemoryByApproximateIndex(chainIdx);
        if (chainId != null && !existingIds.contains(chainId)) {
            float neighborSim = computeNeighborSimilarity(chainId, queryVector);
            float chainScore = neighborSim + seed.score() * attenuation * 0.2f;

            CognitiveResult candidate = buildGraphCandidate(chainId, chainScore, seed, seed.memoryType());
            graphCandidates.merge(chainId, candidate,
                    (a, b) -> a.score() >= b.score() ? a : b);
        }
    }

    /**
     * Builds a CognitiveResult for a graph-expanded candidate using index metadata.
     */
    private CognitiveResult buildGraphCandidate(String memId, float score,
                                                 CognitiveResult seed, MemoryType type) {
        String text = index.text(memId);
        MemorySource source = index.source(memId);
        String[] tags = index.tags(memId);
        java.util.Map<String, String> meta = index.metadata(memId);
        SourceModality modality = meta != null
                ? SourceModality.fromName(meta.get(SourceModality.METADATA_KEY))
                : SourceModality.TEXT;

        float importance = seed != null ? seed.importance() : 0.5f;
        return new CognitiveResult(
                memId, text, score, importance, 0f,
                (short) 0, (byte) 0, type, source,
                tags, 1.0f, 1.0f, RetrievalMode.STANDARD, null, null,
                modality, meta);
    }

    /**
     * Converts a MemoryLocation's byte offset to a record index.
     */
    int offsetToRecordIndex(MemoryIndex.MemoryLocation loc) {
        int stride = tierRouter.layoutFor(loc.type()).stride();
        TierStore store = tierRouter.get(loc.type());
        long dataOffset = (store instanceof AbstractTierStore ats && ats.isPersistent())
                ? AbstractTierStore.METADATA_HEADER_BYTES : 0;
        return (int) ((loc.offset() - dataOffset) / stride);
    }

    /**
     * Finds a memory ID by approximate index across all tiers.
     */
    String findMemoryByApproximateIndex(int approxIdx) {
        for (MemoryType type : MemoryType.values()) {
            var layout = tierRouter.layoutFor(type);
            if (layout == null) continue;
            TierStore store = tierRouter.get(type);
            long dataOffset = (store instanceof AbstractTierStore ats && ats.isPersistent())
                    ? AbstractTierStore.METADATA_HEADER_BYTES : 0;
            long offset = dataOffset + (long) approxIdx * layout.stride();
            String id = index.findIdByOffset(type, offset);
            if (id != null) return id;
        }
        return null;
    }

    /**
     * Computes actual cosine-derived similarity for a graph-expanded neighbor.
     */
    float computeNeighborSimilarity(String memoryId, float[] queryVector) {
        try {
            MemoryIndex.MemoryLocation loc = index.locate(memoryId);
            if (loc == null) return 0f;

            MemorySegment seg = tierRouter.segmentFor(loc.type());
            if (seg == null) return 0f;

            CognitiveRecordLayout layout = tierRouter.layoutFor(loc.type());
            float l2dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                    queryVector, seg, layout.vectorOffset(loc.offset()),
                    calibrationMins, calibrationScales, layout.quantizedVecBytes());
            return 1.0f / (1.0f + l2dist);
        } catch (RuntimeException e) {
            log.trace("Failed to compute neighbor similarity for '{}': {}", memoryId, e.getMessage());
            return 0f;
        }
    }
}
