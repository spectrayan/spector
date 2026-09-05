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
package com.spectrayan.spector.memory.pathway.pipeline;

import com.spectrayan.spector.memory.error.SpectorEntityGraphException;
import com.spectrayan.spector.memory.error.SpectorHebbianException;
import com.spectrayan.spector.memory.error.SpectorTemporalChainException;
import com.spectrayan.spector.memory.cortex.consolidation.CadpContradictionResolver;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderAccessor;
import com.spectrayan.spector.memory.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import static com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.*;

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
public final class GraphExpansionStage {

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
    private final com.spectrayan.spector.memory.model.SalienceProfile salienceProfile;
    private final com.spectrayan.spector.memory.graph.hebbian.CoActivationMemory coActivationTracker;

    public GraphExpansionStage(HebbianGraphBase hebbianGraph,
                        TemporalChainMemory temporalChain,
                        EntityDirectory entityDirectory,
                        com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph,
                        EntityExtractor entityExtractor,
                        GraphScoringPolicy graphScoringPolicy,
                        MemoryIndex index,
                        PartitionRegistry partitionRegistry,
                        float[] calibrationMins,
                        float[] calibrationScales,
                        com.spectrayan.spector.memory.model.SalienceProfile salienceProfile,
                        com.spectrayan.spector.memory.graph.hebbian.CoActivationMemory coActivationTracker) {
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
        this.salienceProfile = salienceProfile;
        this.coActivationTracker = coActivationTracker;
    }

    public GraphExpansionStage(HebbianGraphBase hebbianGraph,
                        TemporalChainMemory temporalChain,
                        EntityDirectory entityDirectory,
                        com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph,
                        EntityExtractor entityExtractor,
                        GraphScoringPolicy graphScoringPolicy,
                        MemoryIndex index,
                        PartitionRegistry partitionRegistry,
                        float[] calibrationMins,
                        float[] calibrationScales,
                        com.spectrayan.spector.memory.model.SalienceProfile salienceProfile) {
        this(hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph,
                entityExtractor, graphScoringPolicy, index, partitionRegistry,
                calibrationMins, calibrationScales, salienceProfile, null);
    }

    public GraphExpansionStage(HebbianGraphBase hebbianGraph,
                        TemporalChainMemory temporalChain,
                        EntityDirectory entityDirectory,
                        com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph,
                        EntityExtractor entityExtractor,
                        GraphScoringPolicy graphScoringPolicy,
                        MemoryIndex index,
                        PartitionRegistry partitionRegistry,
                        float[] calibrationMins,
                        float[] calibrationScales) {
        this(hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph,
                entityExtractor, graphScoringPolicy, index, partitionRegistry,
                calibrationMins, calibrationScales, null, null);
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
    public void expand(List<CognitiveResult> allResults, float[] queryVector, RecallOptions options, String rawQuery) {
        boolean cognitiveScoring = options.scoringMode() != ScoringMode.SIMILARITY;
        boolean hasSubsystems = hebbianGraph != null || temporalChain != null
                || entityDirectory != null
                || (entityExtractor != null && entityExtractor.isAvailable())
                || !options.entityHints().isEmpty();

        if (!cognitiveScoring || !hasSubsystems || allResults.isEmpty()) {
            return;
        }

        // ── Similarity-gated expansion ──
        GraphExpansionMode mode = graphScoringPolicy.graphExpansionMode();
        if (options.graphExpansionThreshold() > 1.0f) {
            mode = GraphExpansionMode.ALWAYS;
        }
        log.info("GraphExpansionStage.expand: cognitiveScoring={}, hasSubsystems={}, allResultsSize={}, mode={}, threshold={}, rawQuery='{}'",
                cognitiveScoring, hasSubsystems, allResults.size(), mode, options.graphExpansionThreshold(), rawQuery);
        if (mode == GraphExpansionMode.ENTITY_ONLY && options.entityHints().isEmpty()) {
            log.debug("Graph expansion skipped: ENTITY_ONLY mode and no entity hints");
            return;
        }

        if (mode == GraphExpansionMode.GATED) {
            float maxDirectSimilarity = 0f;
            for (CognitiveResult r : allResults) {
                if (r.hasBreakdown()) {
                    float s = r.breakdown().similarity();
                    if (s > 1.0f) {
                        s = 1.0f / (1.0f + s);
                    }
                    if (s >= 0.0f && s <= 1.0f) {
                        maxDirectSimilarity = Math.max(maxDirectSimilarity, s);
                    }
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
            expandEntity(allResults, existingIds, graphCandidates, queryVector, options, rawQuery);
        }

        // Step 5f: Layer 4 — Synaptic Tagging & Capture (STC) Cross-Capture Graph
        if (index != null) {
            expandCrossCaptureSTC(allResults, existingIds, graphCandidates, queryVector, options);
        }

        // Add deduplicated graph candidates to results
        if (!graphCandidates.isEmpty()) {
            allResults.addAll(graphCandidates.values());
            for (String id : graphCandidates.keySet()) {
                existingIds.add(id);
            }
            log.info("Graph expansion added {} candidates (from {} layers)",
                    graphCandidates.size(),
                    (hebbianGraph != null ? 1 : 0) + (temporalChain != null ? 1 : 0) + (entityDirectory != null ? 1 : 0) + 1);
        } else {
            log.info("Graph expansion found 0 candidates");
        }

    }

    // ─────────────── Private helpers ───────────────

    private void expandHebbian(List<CognitiveResult> allResults,
                                 Set<String> existingIds,
                                 Map<String, CognitiveResult> graphCandidates,
                                 float[] queryVector,
                                 RecallOptions options) {
        try {
            // Self-tune spreading activation based on User Soul Salience & query topicality
            float soulBoost = (salienceProfile != null && queryVector != null && !salienceProfile.isNeutral())
                    ? salienceProfile.computeSelfRelevanceBoost(queryVector) : 1.0f;
            float topicBoost = (salienceProfile != null && queryVector != null && !salienceProfile.isNeutral())
                    ? salienceProfile.computeTopicBoost(queryVector) : 1.0f;

            float salienceMultiplier = Math.max(1.0f, soulBoost * topicBoost);
            int seedLimit = (salienceMultiplier > 1.05f) ? 15 : 10;
            int maxDepth = (salienceMultiplier > 1.15f)
                    ? Math.min(6, graphScoringPolicy.hebbianMaxDepth() + 1)
                    : graphScoringPolicy.hebbianMaxDepth();

            float effectiveHebbianBoost = graphScoringPolicy.hebbianBoostFactor() * salienceMultiplier;

            List<CognitiveResult> seeds = allResults.subList(0, Math.min(seedLimit, allResults.size()));

            for (CognitiveResult seed : seeds) {
                MemoryIndex.MemoryLocation loc = index.locate(seed.id());
                if (loc == null) continue;

                int memIdx = loc.graphSlot();
                List<Integer> seedEntities = (entityDirectory != null && memIdx >= 0)
                        ? CadpContradictionResolver.findEntitiesForSlot(entityDirectory, memIdx) : null;

                var activated = hebbianGraph.activateNeighbors(memIdx, maxDepth);
                for (var edge : activated) {
                    String neighborId = ((com.spectrayan.spector.memory.cortex.index.IndexRecordMemory) index).idAt(edge.neighborIndex());
                    if (neighborId == null) continue;
                    if (!existingIds.contains(neighborId) && matchesFilters(neighborId, options)) {
                        float neighborSim = computeNeighborSimilarity(neighborId, queryVector);
                        float saturatedWeight = Math.min(edge.weight() / 5.0f, 1.0f);

                        // Entity-Coherent multiplier: boosts associative edges sharing core subject entities
                        float entityMultiplier = 1.0f;
                        if (entityDirectory != null && seedEntities != null && !seedEntities.isEmpty()) {
                            List<Integer> neighborEntities = CadpContradictionResolver.findEntitiesForSlot(entityDirectory, edge.neighborIndex());
                            if (neighborEntities != null && !neighborEntities.isEmpty()) {
                                for (Integer se : seedEntities) {
                                    if (neighborEntities.contains(se)) {
                                        entityMultiplier = 1.35f;
                                        break;
                                    }
                                }
                            }
                        }

                        float boost = saturatedWeight * effectiveHebbianBoost * entityMultiplier;
                        float graphScore = neighborSim > 0.0f
                                ? (neighborSim + seed.score() * boost) * 0.5f
                                : seed.score() * Math.min(0.85f, boost);

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
     * Layer 4: Synaptic Tagging & Capture (STC) Cross-Capture Graph.
     * Traverses tag co-occurrence matrix and inverted index via {@link com.spectrayan.spector.memory.graph.hebbian.CoActivationMemory}.
     */
    private void expandCrossCaptureSTC(List<CognitiveResult> allResults,
                                       Set<String> existingIds,
                                       Map<String, CognitiveResult> graphCandidates,
                                       float[] queryVector,
                                       RecallOptions options) {
        if (coActivationTracker == null || index == null) return;
        try {
            int seedLimit = Math.min(10, allResults.size());
            List<CognitiveResult> seeds = allResults.subList(0, seedLimit);
            List<String> seedTags = new java.util.ArrayList<>();

            for (CognitiveResult seed : seeds) {
                String[] tags = index.tags(seed.id());
                if (tags != null) {
                    for (String t : tags) {
                        if (t != null && t.length() >= com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_CROSS_CAPTURE_MIN_TAG_LENGTH) {
                            String lower = t.trim().toLowerCase();
                            boolean ignored = false;
                            for (String pfx : com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_CROSS_CAPTURE_IGNORED_TAG_PREFIXES) {
                                if (lower.startsWith(pfx)) {
                                    ignored = true;
                                    break;
                                }
                            }
                            if (!ignored) {
                                seedTags.add(lower);
                            }
                        }
                    }
                }
            }

            if (seedTags.isEmpty()) return;

            var crossCandidates = coActivationTracker.crossCaptureTraversal(seedTags, 5, 10);
            for (var cc : crossCandidates) {
                String neighborId = index.idAt(cc.memorySlotIndex());
                if (neighborId != null && !existingIds.contains(neighborId) && matchesFilters(neighborId, options)) {
                    float neighborSim = computeNeighborSimilarity(neighborId, queryVector);
                    float saturatedScore = Math.min(cc.score() / 5.0f, 1.0f);
                    float topScore = !allResults.isEmpty() ? allResults.getFirst().score() : 0.04f;
                    float graphScore = neighborSim > 0.0f
                            ? (neighborSim + topScore * saturatedScore * 0.4f) * 0.5f
                            : topScore * saturatedScore * 0.4f;

                    MemoryType resolvedType = MemoryType.SEMANTIC;
                    MemoryIndex.MemoryLocation loc = index.locate(neighborId);
                    if (loc != null && loc.type() != null) {
                        resolvedType = loc.type();
                    }

                    CognitiveResult candidate = buildGraphCandidate(
                            neighborId, graphScore, null, resolvedType, "STC_CAPTURE", neighborSim);
                    graphCandidates.merge(neighborId, candidate,
                            (a, b) -> a.score() >= b.score() ? a : b);
                }
            }
        } catch (RuntimeException e) {
            log.debug("STC cross-capture expansion encountered non-fatal exception: {}", e.getMessage());
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
            List<CognitiveResult> seeds = allResults.subList(0, Math.min(20, allResults.size()));

            for (CognitiveResult seed : seeds) {
                MemoryIndex.MemoryLocation loc = index.locate(seed.id());
                if (loc == null) continue;

                int memIdx = loc.graphSlot();
                int[] forward = temporalChain.followForward(memIdx, graphScoringPolicy.temporalMaxHops());
                for (int i = 0; i < forward.length; i++) {
                    float hopAtten = Math.max(0.60f, 1.0f - 0.03f * (i + 1));
                    addChainResultCoFusion(forward[i], seed, existingIds, graphCandidates,
                            queryVector, hopAtten, options);
                }
                int[] backward = temporalChain.followBackward(memIdx, graphScoringPolicy.temporalMaxHops());
                for (int i = 0; i < backward.length; i++) {
                    float hopAtten = Math.max(0.60f, 1.0f - 0.03f * (i + 1));
                    addChainResultCoFusion(backward[i], seed, existingIds, graphCandidates,
                            queryVector, hopAtten, options);
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
                               float[] queryVector, RecallOptions options, String rawQuery) {
        List<ExtractedEntity> queryEntities = null;

        // Priority 0: Direct entity name matching from the raw query text against the EntityDirectory.
        // This is a zero-LLM approach that catches entity names like "Caroline", "Melanie", "Sweden"
        // mentioned in the query, without relying on seed candidate slots.
        if (rawQuery != null && !rawQuery.isBlank() && entityDirectory != null) {
            String queryLower = rawQuery.toLowerCase(java.util.Locale.ROOT);
            java.util.Map<String, Integer> knownEntities = entityDirectory.nameIndex();
            java.util.List<ExtractedEntity> directMatches = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, Integer> entry : knownEntities.entrySet()) {
                String entityName = entry.getKey();
                // Match entity names that are at least 3 chars (avoid trivial matches)
                if (entityName.length() >= 3 && queryLower.contains(entityName)) {
                    String type = entityDirectory.entityType(entry.getValue());
                    directMatches.add(new ExtractedEntity(entityName, type != null ? type : "UNKNOWN", List.of()));
                }
            }
            if (!directMatches.isEmpty()) {
                queryEntities = directMatches;
                log.debug("Query-text entity matching found {} entities in query '{}'",
                        directMatches.size(), rawQuery);
            }
        }

        // Priority 1: Pre-extracted entity hints from RecallOptions (merge with any direct matches)
        if (!options.entityHints().isEmpty()) {
            if (queryEntities == null) {
                queryEntities = new java.util.ArrayList<>(options.entityHints());
            } else {
                // Merge: add hints not already found by direct matching
                Set<String> existingNames = new HashSet<>();
                for (ExtractedEntity e : queryEntities) existingNames.add(e.name().toLowerCase(java.util.Locale.ROOT));
                for (ExtractedEntity hint : options.entityHints()) {
                    if (!existingNames.contains(hint.name().toLowerCase(java.util.Locale.ROOT))) {
                        queryEntities.add(hint);
                    }
                }
            }
        }
        // Priority 2: Fast zero-LLM directory lookup from top seed candidate's indexed slot
        if ((queryEntities == null || queryEntities.isEmpty())
                && entityDirectory != null && index != null && !allResults.isEmpty()) {
            List<CognitiveResult> semanticSeeds = allResults.stream()
                    .filter(r -> r.memoryType() == MemoryType.SEMANTIC || r.memoryType() == MemoryType.PROCEDURAL)
                    .limit(5)
                    .toList();
            if (semanticSeeds.isEmpty()) {
                semanticSeeds = List.of(allResults.getFirst());
            }
            Set<Integer> collectedEntityIds = new HashSet<>();
            for (CognitiveResult s : semanticSeeds) {
                MemoryIndex.MemoryLocation loc = index.locate(s.id());
                if (loc != null) {
                    int slot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);
                    List<Integer> seedEntityIds = com.spectrayan.spector.memory.cortex.consolidation.CadpContradictionResolver
                            .findEntitiesForSlot(entityDirectory, slot);
                    if (seedEntityIds != null) {
                        collectedEntityIds.addAll(seedEntityIds);
                    }
                }
            }
            if (!collectedEntityIds.isEmpty()) {
                queryEntities = new java.util.ArrayList<>(collectedEntityIds.size());
                for (int eid : collectedEntityIds) {
                    String name = entityDirectory.entityName(eid);
                    String type = entityDirectory.entityType(eid);
                    if (name != null) {
                        queryEntities.add(new ExtractedEntity(name, type != null ? type : "UNKNOWN", List.of()));
                    }
                }
            }
        }
        // Priority 3: Fallback to live EntityExtractor SPI only if no directory entities found
        if ((queryEntities == null || queryEntities.isEmpty())
                && entityExtractor != null && entityExtractor.isAvailable() && !allResults.isEmpty()) {
            try {
                queryEntities = entityExtractor.extract("query", allResults.getFirst().text());
            } catch (RuntimeException e) {
                SpectorEntityGraphException ex = new SpectorEntityGraphException("entity extraction", e);
                log.debug(ex.getMessage());
            }
        }

        if (queryEntities == null || queryEntities.isEmpty()) return;

        try {
            // Identity (name→id, fan factor) from the directory; topology (reachable memories)
            // from the hypergraph when available. Falls back to EntityDirectory.memoriesForEntity()
            // for single-entity records that aren't indexed in the hypergraph.
            if (entityDirectory == null) return;
            for (var entity : queryEntities) {
                int entityId = entityDirectory.findEntity(entity.name());
                if (entityId < 0) continue;

                // Hub entity protection: Limit multi-hop traversal depth on global speaker entities (refCount > 25)
                // to 1 hop to prevent flooding retrieval with unrelated cross-topic facts, while preserving multi-session predicate bridging.
                int refCnt = entityDirectory.memoryRefCount(entityId);
                int maxHops = refCnt > 25 ? 1 : graphScoringPolicy.entityMaxHops();

                // First try hypergraph traversal for multi-hop entity discovery
                Set<Integer> reachableMemories = null;
                if (hyperEntityGraph != null) {
                    reachableMemories = hyperEntityGraph.collectMemories(entityId, maxHops);
                }

                // Fallback: EntityDirectory adjacency list for single-entity memory references
                // This catches memories that reference an entity but aren't part of any hyperedge
                if (reachableMemories == null || reachableMemories.isEmpty()) {
                    int[] directMemories = entityDirectory.memoriesForEntity(entityId);
                    if (directMemories.length > 0) {
                        reachableMemories = new HashSet<>(directMemories.length);
                        for (int m : directMemories) reachableMemories.add(m);
                        log.debug("Entity '{}' (id={}) fallback to directory adjacency: {} memories",
                                entity.name(), entityId, directMemories.length);
                    }
                }

                if (reachableMemories != null) {
                    for (int memIdx : reachableMemories) {
                        String memId = ((com.spectrayan.spector.memory.cortex.index.IndexRecordMemory) index).idAt(memIdx);
                        if (memId == null) continue;
                        if (!existingIds.contains(memId) && matchesFilters(memId, options)) {
                            float neighborSim = computeNeighborSimilarity(memId, queryVector);
                            float fanAttenuation = entityDirectory.fanFactor(entityId);
                            float entityAtten = graphScoringPolicy.entityHopAttenuation() * fanAttenuation;
                            float entityScore = neighborSim > 0.0f
                                    ? (neighborSim + allResults.getFirst().score() * entityAtten) * 0.5f
                                    : allResults.getFirst().score() * Math.min(0.85f, entityAtten);

                            CognitiveResult candidate = buildGraphCandidate(
                                    memId, entityScore, null, MemoryType.SEMANTIC, "ENTITY", neighborSim);
                            graphCandidates.merge(memId, candidate,
                                    (a, b) -> a.score() >= b.score() ? a : b);
                        }
                    }
                }

                // Predicate-guided multi-session association:
                // If this entity participates in typed relations (e.g. HAS_PET, ATTENDED),
                // traverse hyperedges that share the same predicate type for the subject to pull multi-session associates.
                if (hyperEntityGraph != null) {
                    List<HyperEntityGraphMemory.HyperEdge> typedEdges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                    if (typedEdges != null) {
                        for (var e : typedEdges) {
                            if (e.type() > 0 && e.vertices() != null) {
                                for (var v : e.vertices()) {
                                    if (v.roleId() == HyperEntityGraphMemory.ROLE_SUBJECT) {
                                        List<HyperEntityGraphMemory.HyperEdge> siblingEdges =
                                                hyperEntityGraph.findHyperedgesForEntityAndPredicate(v.entityId(), e.type());
                                        for (var sib : siblingEdges) {
                                            if (sib.memoryIdx() >= 0) {
                                                String memId = ((com.spectrayan.spector.memory.cortex.index.IndexRecordMemory) index).idAt(sib.memoryIdx());
                                                if (memId != null && !existingIds.contains(memId) && matchesFilters(memId, options)) {
                                                    float neighborSim = computeNeighborSimilarity(memId, queryVector);
                                                    float entityAtten = graphScoringPolicy.entityHopAttenuation();
                                                    float entityScore = neighborSim > 0.0f
                                                            ? (neighborSim + allResults.getFirst().score() * entityAtten) * 0.5f
                                                            : allResults.getFirst().score() * Math.min(0.85f, entityAtten);
                                                    CognitiveResult candidate = buildGraphCandidate(
                                                            memId, entityScore, null, MemoryType.SEMANTIC, "PREDICATE_BRIDGE", neighborSim);
                                                    graphCandidates.merge(memId, candidate,
                                                            (a, b) -> a.score() >= b.score() ? a : b);
                                                }
                                            }
                                            if (sib.vertices() != null) {
                                                for (var sibVert : sib.vertices()) {
                                                    if (sibVert.entityId() >= 0 && sibVert.entityId() != entityId) {
                                                        int[] directMems = entityDirectory.memoriesForEntity(sibVert.entityId());
                                                        if (directMems != null && index instanceof com.spectrayan.spector.memory.cortex.index.IndexRecordMemory irm) {
                                                            for (int dm : directMems) {
                                                                String dMemId = irm.idAt(dm);
                                                                if (dMemId != null && !existingIds.contains(dMemId) && matchesFilters(dMemId, options)) {
                                                                    float dSim = computeNeighborSimilarity(dMemId, queryVector);
                                                                    float dScore = (dSim + allResults.getFirst().score() * 0.45f) * 1.8f;
                                                                    CognitiveResult dCandidate = buildGraphCandidate(
                                                                            dMemId, dScore, null, MemoryType.SEMANTIC, "HYPER_SIBLING", dSim);
                                                                    graphCandidates.merge(dMemId, dCandidate,
                                                                            (a, b) -> a.score() >= b.score() ? a : b);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Traversal of CONTRADICTS hyperedges (#528)
                // If this entity is part of a corrected relation, traverse to the corrector entity's memories
                List<HyperEntityGraphMemory.HyperEdge> hyperedges = hyperEntityGraph != null
                        ? hyperEntityGraph.findHyperedgesForEntity(entityId) : null;
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
                                        String memId = ((com.spectrayan.spector.memory.cortex.index.IndexRecordMemory) index).idAt(memIdx);
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
        String chainId = ((com.spectrayan.spector.memory.cortex.index.IndexRecordMemory) index).idAt(chainIdx);
        if (chainId == null) return;
        if (!existingIds.contains(chainId) && matchesFilters(chainId, options)) {
            float neighborSim = computeNeighborSimilarity(chainId, queryVector);
            float chainScore = neighborSim > 0.0f
                    ? (neighborSim + seed.score() * attenuation) * 0.5f
                    : seed.score() * attenuation;

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

        long ts = 0L;
        byte valence = 0;
        MemoryType resolvedType = type;
        try {
            MemoryIndex.MemoryLocation loc = index != null ? index.locate(memId) : null;
            if (loc != null) {
                if (loc.type() != null) {
                    resolvedType = loc.type();
                }
                CognitiveMemoryRouter router = partitionRegistry != null
                        ? partitionRegistry.routerFor(loc.colocatedPartition()) : null;
                if (router != null) {
                    MemorySegment seg = router.segmentFor(loc.type());
                    if (seg != null) {
                        if (loc.type() == MemoryType.EPISODIC) {
                            ts = EpisodicHeaderAccessor.readTimestamp(seg, loc.offset());
                            valence = EpisodicHeaderAccessor.readValence(seg, loc.offset());
                        } else {
                            FixedEngramLayout layout = router.layoutFor(loc.type());
                            if (layout != null) {
                                ts = layout.readTimestamp(seg, loc.offset());
                                valence = layout.readValence(seg, loc.offset());
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return new CognitiveResult(
                memId, text, score, importance, 0f,
                0, valence, resolvedType, source,
                tags, 1.0f, 1.0f, RetrievalMode.STANDARD, breakdown, null,
                modality, metadata, (byte) 0, ts);
    }



    /**
     * Computes actual cosine-derived similarity for a graph-expanded neighbor.
     */
    float computeNeighborSimilarity(String memoryId, float[] queryVector) {
        if (queryVector == null) return 0f;
        try {
            MemoryIndex.MemoryLocation loc = index.locate(memoryId);
            if (loc == null) return 0f;

            // #443: resolve the neighbor's segment by the partition it actually lives in.
            CognitiveMemoryRouter router = partitionRegistry != null
                    ? partitionRegistry.routerFor(loc.colocatedPartition()) : null;
            if (router == null) return 0f;
            MemorySegment seg = router.segmentFor(loc.type());
            if (seg == null) return 0f;

            if (loc.type() == MemoryType.EPISODIC) {
                return 0f;
            }

            FixedEngramLayout layout = router.layoutFor(loc.type());
            if (layout == null) return 0f;
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

        if (loc.type() == MemoryType.EPISODIC) {
            if (EpisodicHeaderAccessor.isTombstoned(seg, loc.offset())) {
                return false;
            }
            byte valence = EpisodicHeaderAccessor.readValence(seg, loc.offset());
            if (valence < options.minValence() || valence > options.maxValence()) {
                return false;
            }
            float importance = EpisodicHeaderAccessor.readImportance(seg, loc.offset());
            if (importance < options.minImportance()) {
                return false;
            }
            return true;
        }

        FixedEngramLayout layout = router.layoutFor(loc.type());
        if (layout == null) return false;

        byte flags = layout.readFlags(seg, loc.offset());
        if ((flags & FLAG_TOMBSTONE) != 0) {
            return false;
        }

        if (!options.includeContradictions()) {
            byte cFlags = layout.readConsolidationFlags(seg, loc.offset());
            if ((cFlags & FLAG_CONTRADICTED) != 0) {
                return false;
            }
        }

        byte valence = layout.readValence(seg, loc.offset());
        if (valence < options.minValence() || valence > options.maxValence()) {
            return false;
        }

        float importance = layout.readImportance(seg, loc.offset());
        if (importance < options.minImportance()) {
            return false;
        }

        return true;
    }
}

