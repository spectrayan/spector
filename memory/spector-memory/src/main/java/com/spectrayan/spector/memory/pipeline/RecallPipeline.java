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

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;



import com.spectrayan.spector.memory.model.RecallTrace;

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.TextSearchMode;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory.EpisodicPartition;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index.BM25Candidate;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex.SpladeCandidate;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.prospective.Reminder;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.ReplaySnapshot;
import com.spectrayan.spector.memory.sync.WalReplayer;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.CognitiveScorer;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.core.similarity.SimilarityFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.spectrayan.spector.memory.model.CognitiveProfile;

import com.spectrayan.spector.commons.concurrent.NativeOsMemory;

import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingResult;
import com.spectrayan.spector.index.ColBERTReranker;
import com.spectrayan.spector.index.ColBERTReranker.RerankCandidate;
import com.spectrayan.spector.index.ColBERTReranker.RerankResult;



/**
 * 8-step recall pipeline for cognitive memory retrieval.
 *
 * <h3>Pipeline Steps</h3>
 * <pre>
 *   Step 1: Embed query text
 *   Step 2: Collect due prospective reminders
 *   Step 3: Score across each tier store (parallel via ConcurrentTasks)
 *   Step 4: Filter suppressed memories (inhibition)
 *   Step 5: Apply habituation penalty (anti-filter-bubble)
 *   Step 6: Sort by score descending, limit to topK
 *   Step 7: Fire async post-recall listeners (LTP + Hebbian)
 * </pre>
 *
 * <h3>Performance: Parallel Tier Scanning</h3>
 * <p>Step 3 fans out tier scans as parallel tasks via
 * {@link ConcurrentTasks#forkJoinAll}. Each scan operates on a disjoint
 * off-heap {@link MemorySegment}  --  zero contention. With 4 tiers + N episodic
 * partitions, recall latency = max(tier_latency) instead of sum(tier_latencies).</p>
 *
 * <h3>Performance: Async Post-Recall Hooks</h3>
 * <p>Steps 7 - 8 (LTP reconsolidation, Hebbian co-activation) fire on Virtual Threads
 * so the caller doesn't block on post-recall bookkeeping.</p>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Template Method</b>: Pipeline skeleton is fixed; scoring delegated to
 *       {@link CognitiveScorer}</li>
 *   <li><b>Observer</b>: Post-recall hooks via {@link RecallListener}</li>
 * </ul>
 */
public final class RecallPipeline {

    private static final Logger log = LoggerFactory.getLogger(RecallPipeline.class);

    private final EmbeddingProvider embeddingProvider;
    private final PartitionRegistry partitionRegistry;
    private final MemoryIndex index;
    private final SuppressionSet suppressionSet;
    private final HabituationPenalty habituationPenalty;
    private final ProspectiveScheduler prospectiveScheduler;
    private final MemoryWal wal;
    private final float[] calibrationMins;
    private final float[] calibrationScales;
    private final SemanticRecallStrategy semanticRecallStrategy; // nullable
    private final CoActivationRecordMemory coActivationTracker; // nullable  --  for STDP causal boost
    private final GraphScoringPolicy graphScoringPolicy;
    private final GraphExpansionStage graphExpansionStage;

    private final List<RecallListener> listeners = new ArrayList<>();

    //  3-Layer Cognitive Graph (all nullable) 
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final EntityDirectory entityDirectory;
    private final com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph;
    private final EntityExtractor entityExtractor;

    //  BM25 Text Search (nullable  --  graceful degradation) 
    private final MemoryBM25Index bm25Index;

    //  SPLADE Sparse Search (nullable  --  graceful degradation) 
    private final MemorySpladeIndex spladeIndex;
    private final SparseEmbeddingProvider spladeProvider;
    private volatile boolean spladeWarnLogged = false;

    //  ColBERT v2 Reranker (nullable  --  graceful degradation) 
    private final ColBERTReranker colbertReranker;
    private volatile boolean colbertWarnLogged = false;

    //  Neurodivergent: Lateral feedback tracking 
    // Maps memoryId  ->  RetrievalMode for the most recent recall.
    // Used by SpectorMemory.reinforce()/suppress() to feed LateralEvaluator.
    // Entries expire implicitly via size cap (oldest evicted at 2000).
    private final ConcurrentHashMap<String, RetrievalMode> recentRetrievalModes
            = new ConcurrentHashMap<>();
    private static final int RETRIEVAL_MODE_CACHE_MAX = 2000;
    private RecallOptions lastRecallOptions; // for detecting hyperfocus mode

    //  Executive Dysfunction: Associative recall context history 
    private final RecallHistory recallHistory;

    //  Semantic Satiation: Anti-looping cache 
    // Bounded cache of last N result IDs. Any result that appears in this
    // hot cache gets a 0.5x penalty, breaking exact-query loops.
    // Uses ConcurrentHashMap to avoid virtual thread pinning (ADR-005).
    // Size-bounded via eviction on put  --  acceptable for a 10-entry cache.
    private static final int SATIATION_CACHE_SIZE = 10;
    private static final float SATIATION_PENALTY = 0.5f;
    private final ConcurrentHashMap<String, Long> satiationCache = new ConcurrentHashMap<>(16);

    /**
     * Creates a recall pipeline with all required subsystems.
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           PartitionRegistry partitionRegistry,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales) {
        this(embeddingProvider, partitionRegistry, index, suppressionSet, habituationPenalty,
                prospectiveScheduler, wal, calibrationMins, calibrationScales, null, null,
                null, null, null, null, null, GraphScoringPolicy.DEFAULT, null,
                null, null, null);
    }

    /**
     * Creates a recall pipeline with optional fused semantic recall.
     *
     * @param semanticRecallStrategy nullable  --  when provided, semantic recall uses
     *                                HNSW vector search fused with cognitive scoring
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           PartitionRegistry partitionRegistry,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales,
                            SemanticRecallStrategy semanticRecallStrategy) {
        this(embeddingProvider, partitionRegistry, index, suppressionSet, habituationPenalty,
                prospectiveScheduler, wal, calibrationMins, calibrationScales,
                semanticRecallStrategy, null,
                null, null, null, null, null, GraphScoringPolicy.DEFAULT, null,
                null, null, null);
    }

    /**
     * Creates a recall pipeline with optional fused semantic recall and STDP.
     *
     * @param semanticRecallStrategy nullable  --  when provided, semantic recall uses
     *                                HNSW vector search fused with cognitive scoring
     * @param coActivationTracker    nullable  --  when provided, STDP causal boost is applied
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           PartitionRegistry partitionRegistry,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales,
                           SemanticRecallStrategy semanticRecallStrategy,
                           CoActivationRecordMemory coActivationTracker) {
        this(embeddingProvider, partitionRegistry, index, suppressionSet, habituationPenalty,
                prospectiveScheduler, wal, calibrationMins, calibrationScales,
                semanticRecallStrategy, coActivationTracker,
                null, null, null, null, null, GraphScoringPolicy.DEFAULT, null,
                null, null, null);
    }

    /**
     * Creates a recall pipeline with optional fused semantic recall, STDP, and 3-Layer Cognitive Graph.
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           PartitionRegistry partitionRegistry,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales,
                           SemanticRecallStrategy semanticRecallStrategy,
                           CoActivationRecordMemory coActivationTracker,
                           HebbianGraphBase hebbianGraph,
                           TemporalChainMemory temporalChain,
                           EntityDirectory entityDirectory,
                           com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph,
                           EntityExtractor entityExtractor,
                           GraphScoringPolicy graphScoringPolicy,
                           MemoryBM25Index bm25Index,
                           MemorySpladeIndex spladeIndex,
                           SparseEmbeddingProvider spladeProvider,
                           ColBERTReranker colbertReranker) {
        this.embeddingProvider = embeddingProvider;
        this.partitionRegistry = partitionRegistry;
        this.index = index;
        this.suppressionSet = suppressionSet;
        this.habituationPenalty = habituationPenalty;
        this.prospectiveScheduler = prospectiveScheduler;
        this.wal = wal;
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
        this.semanticRecallStrategy = semanticRecallStrategy;
        this.coActivationTracker = coActivationTracker;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.entityExtractor = entityExtractor;
        this.graphScoringPolicy = graphScoringPolicy != null ? graphScoringPolicy : GraphScoringPolicy.DEFAULT;
        this.bm25Index = bm25Index;
        this.spladeIndex = spladeIndex;
        this.spladeProvider = spladeProvider;
        this.colbertReranker = colbertReranker;
        this.recallHistory = null;

        //  Delegate graph expansion to focused stage class 
        this.graphExpansionStage = new GraphExpansionStage(
                hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, entityExtractor,
                this.graphScoringPolicy, index, partitionRegistry,
                calibrationMins, calibrationScales);
    }

    /**
     * Creates a recall pipeline with all subsystems plus RecallHistory for associative recall.
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           PartitionRegistry partitionRegistry,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales,
                           SemanticRecallStrategy semanticRecallStrategy,
                           CoActivationRecordMemory coActivationTracker,
                           HebbianGraphBase hebbianGraph,
                           TemporalChainMemory temporalChain,
                           EntityDirectory entityDirectory,
                           com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph,
                           EntityExtractor entityExtractor,
                           GraphScoringPolicy graphScoringPolicy,
                           MemoryBM25Index bm25Index,
                           MemorySpladeIndex spladeIndex,
                           SparseEmbeddingProvider spladeProvider,
                           ColBERTReranker colbertReranker,
                           RecallHistory recallHistory) {
        this.embeddingProvider = embeddingProvider;
        this.partitionRegistry = partitionRegistry;
        this.index = index;
        this.suppressionSet = suppressionSet;
        this.habituationPenalty = habituationPenalty;
        this.prospectiveScheduler = prospectiveScheduler;
        this.wal = wal;
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
        this.semanticRecallStrategy = semanticRecallStrategy;
        this.coActivationTracker = coActivationTracker;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.entityExtractor = entityExtractor;
        this.graphScoringPolicy = graphScoringPolicy != null ? graphScoringPolicy : GraphScoringPolicy.DEFAULT;
        this.bm25Index = bm25Index;
        this.spladeIndex = spladeIndex;
        this.spladeProvider = spladeProvider;
        this.colbertReranker = colbertReranker;
        this.recallHistory = recallHistory;

        //  Delegate graph expansion to focused stage class 
        this.graphExpansionStage = new GraphExpansionStage(
                hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, entityExtractor,
                this.graphScoringPolicy, index, partitionRegistry,
                calibrationMins, calibrationScales);
    }

    /**
     * Registers a post-recall listener (Observer pattern).
     *
     * @param listener called after each successful recall with the final results
     */
    public void addListener(RecallListener listener) {
        if (listener == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "listener"); } listeners.add(listener);
    }

    // ==============================================================
    // SHARED RECALL FLOW HELPERS (issue #437)
    //
    // The vector-driven recall(float[],…) and text-driven recall(String,…)
    // entry points share three verbatim-identical phases: prospective-reminder
    // seeding, the parallel tier scan (+ sequential fallback), and cognitive
    // post-scoring (habituation + STDP). These helpers hold that shared logic so
    // the two entry flows differ only where they genuinely must (text search,
    // reranking, profile-ordinal write, tracing).
    // ==============================================================

    /** Seeds due prospective reminders as top-priority working results. */
    private void seedProspectiveReminders(List<CognitiveResult> allResults) {
        List<Reminder> dueReminders = prospectiveScheduler.collectDue();
        for (Reminder r : dueReminders) {
            allResults.add(new CognitiveResult(
                    r.id(), r.text(), 10.0f, 10.0f, 0f,
                    (short) 0, (byte) 0, MemoryType.WORKING, MemorySource.PROCEDURAL,
                    new String[]{"prospective"}, 1.0f, 1.0f));
        }
    }

    /**
     * Runs the parallel tier scan (with sequential fallback), appending tier results
     * to {@code allResults}. Returns {@code false} if the scan was interrupted and the
     * caller should return the partial results immediately.
     */
    private boolean runTierScan(List<CognitiveResult> allResults, float[] queryVector,
                                RecallOptions options, long nowMs, MemoryType[] targetTypes) {
        List<Callable<List<CognitiveResult>>> scanTasks = buildScanTasks(
                queryVector, options, nowMs, targetTypes);
        if (!scanTasks.isEmpty()) {
            try {
                List<List<CognitiveResult>> tierResults = ConcurrentTasks.forkJoinAll(scanTasks);
                for (List<CognitiveResult> tier : tierResults) {
                    allResults.addAll(tier);
                }
            } catch (ConcurrentExecutionException e) {
                log.error("Parallel tier scan failed: {}", e.getMessage(), e);
                // Fallback: sequential scan
                allResults.addAll(sequentialScan(queryVector, options, nowMs, targetTypes));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Recall interrupted during parallel scan");
                return false;
            }
        }
        return true;
    }

    /**
     * Applies cognitive post-scoring in place: habituation + inhibition-of-return +
     * semantic satiation penalties, then STDP causal boost. No-op in SIMILARITY mode
     * (benchmarks measure pure retrieval quality — no cognitive modifications).
     */
    private void applyCognitiveScoring(List<CognitiveResult> allResults,
                                       RecallOptions options, long nowMs) {
        if (options.scoringMode() == ScoringMode.SIMILARITY) return;

        // Habituation penalty + inhibition of return + semantic satiation
        for (int i = 0; i < allResults.size(); i++) {
            CognitiveResult r = allResults.get(i);
            float habPenalty = (options.recallMode() == RecallMode.LEARN)
                    ? habituationPenalty.recordAndComputePenalty(r.id())
                    : habituationPenalty.currentPenalty(r.id());
            float iorPenalty = habituationPenalty.computeInhibitionOfReturn(r.id(), nowMs);
            float combinedPenalty = Math.min(habPenalty, iorPenalty); // stronger suppression wins

            // Semantic Satiation: 0.5x penalty for results in the hot LRU cache
            if (satiationCache.containsKey(r.id())) {
                combinedPenalty *= SATIATION_PENALTY;
            }

            if (combinedPenalty < 1.0f) {
                float newScore = r.score() * combinedPenalty;
                // Carry breakdown with actual habituation penalty recorded
                ScoreBreakdown bd = r.breakdown() != null
                        ? new ScoreBreakdown(
                                r.breakdown().similarity(),
                                r.breakdown().importanceDecay(),
                                r.breakdown().tagBoostFactor(),
                                combinedPenalty,
                                r.breakdown().graphBoost(),
                                r.breakdown().valenceAlignment(),
                                newScore)
                        : null;
                allResults.set(i, new CognitiveResult(
                        r.id(), r.text(), newScore, r.importance(), r.ageDays(),
                        r.agentRecallCount(), r.valence(), r.memoryType(), r.source(),
                        r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay(),
                        r.retrievalMode(), bd, r.trace(), r.sourceModality(), r.metadata()));
            }
        }

        // STDP causal boost — cross-boost results whose tags are causally linked.
        // For each result, check if earlier results' tags predict its tags (via STDP
        // edges). This promotes memories that form causal chains.
        if (coActivationTracker != null && allResults.size() >= 2) {
            // Use tags from the first few results as "context tags" to boost subsequent
            // results (imperative loop — avoids Stream API allocation overhead in hot path)
            Set<String> contextTagSet = new HashSet<>();
            int contextLimit = Math.min(3, allResults.size());
            for (int cl = 0; cl < contextLimit; cl++) {
                String[] ctxTags = allResults.get(cl).synapticTags();
                if (ctxTags != null) {
                    for (String t : ctxTags) contextTagSet.add(t);
                }
            }

            if (!contextTagSet.isEmpty()) {
                List<String> contextTags = new ArrayList<>(contextTagSet);
                for (int i = 0; i < allResults.size(); i++) {
                    CognitiveResult r = allResults.get(i);
                    if (r.synapticTags() == null || r.synapticTags().length == 0) continue;

                    float predictive = coActivationTracker.getPredictiveStrength(
                            contextTags, r.synapticTags());
                    if (predictive > 0) {
                        float boostedScore = r.score() * (1.0f + predictive * graphScoringPolicy.causalBoostWeight());
                        allResults.set(i, new CognitiveResult(
                                r.id(), r.text(), boostedScore, r.importance(), r.ageDays(),
                                r.agentRecallCount(), r.valence(), r.memoryType(), r.source(),
                                r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay(),
                                r.retrievalMode(), r.breakdown(), r.trace(), r.sourceModality(), r.metadata()));
                    }
                }
            }
        }
    }

    /**
     * Executes the full recall pipeline with parallel tier scanning.
     *
     * @param queryText the query text (will be embedded)
     * @param options   recall configuration
     * @return ranked list of cognitive results
     */
    /**
     * Executes cognitive recall directly using a pre-computed query vector.
     *
     * @param queryVector the embedded query vector
     * @param options     recall configuration
     * @return ranked list of cognitive results
     */
    public List<CognitiveResult> recall(float[] queryVector, RecallOptions options) {
        if (queryVector == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "queryVector"); }
        if (options == null) options = RecallOptions.DEFAULT;

        log.debug("Recall query vector: topK={}, mode={}", options.topK(), options.recallMode());
        this.lastRecallOptions = options;

        long nowMs = System.currentTimeMillis();
        List<CognitiveResult> allResults = new ArrayList<>();

        // Collect due prospective reminders
        seedProspectiveReminders(allResults);

        // Parallel tier scanning (shared flow)
        MemoryType[] targetTypes = options.memoryTypes();
        if (!runTierScan(allResults, queryVector, options, nowMs, targetTypes)) {
            return allResults;
        }

        // Filter suppressed memories
        allResults.removeIf(r -> suppressionSet.isSuppressed(r.id()));

        // Cognitive post-scoring: habituation + STDP (shared flow)
        applyCognitiveScoring(allResults, options, nowMs);

        // Graph expansion
        graphExpansionStage.expand(allResults, queryVector, options);

        // Sort and limit
        allResults.sort(Comparator.comparing(CognitiveResult::score).reversed());
        if (allResults.size() > options.topK()) {
            allResults = new ArrayList<>(allResults.subList(0, options.topK()));
        }

        // Fire post-recall listeners
        if (options.recallMode() == RecallMode.LEARN && !listeners.isEmpty()) {
            final List<CognitiveResult> finalResults = allResults;
            for (RecallListener listener : listeners) {
                ConcurrentTasks.fireAndForget(() -> listener.onRecallComplete(finalResults));
            }
        }

        // Ephemeral session state
        if (options.recallMode() == RecallMode.LEARN) {
            long recallTs = System.currentTimeMillis();
            for (CognitiveResult r : allResults) {
                habituationPenalty.recordRecall(r.id(), recallTs);
            }

            if (recentRetrievalModes.size() > RETRIEVAL_MODE_CACHE_MAX) {
                int toRemove = RETRIEVAL_MODE_CACHE_MAX / 4;
                var iter = recentRetrievalModes.keySet().iterator();
                for (int i = 0; i < toRemove && iter.hasNext(); i++) {
                    iter.next();
                    iter.remove();
                }
            }
            for (CognitiveResult r : allResults) {
                if (r.id() != null) {
                    recentRetrievalModes.put(r.id(), r.retrievalMode());
                }
            }

            for (CognitiveResult r : allResults) {
                satiationCache.put(r.id(), nowMs);
            }
        }

        return allResults;
    }

    public List<CognitiveResult> recall(String queryText, RecallOptions options) {
        if (queryText == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "queryText"); }
        if (options == null) options = RecallOptions.DEFAULT;

        if (options.recallMode() == RecallMode.REPLAY) {
            return replayRecall(queryText, options);
        }

        log.debug("Recall query: '{}', topK={}, mode={}", queryText, options.topK(), options.recallMode());
        this.lastRecallOptions = options; // for RetrievalMode detection in headerToResult

        // Route ASSOCIATIVE scoring to dedicated method
        if (options.scoringMode() == ScoringMode.ASSOCIATIVE && recallHistory != null) {
            return recallAssociative(queryText, options);
        }

        // Step 1: Embed query
        float[] queryVector = embeddingProvider.embed(queryText).vector();

        long nowMs = System.currentTimeMillis();
        List<CognitiveResult> allResults = new ArrayList<>();

        // Step 2: Collect due prospective reminders (shared flow)
        seedProspectiveReminders(allResults);

        // Step 3: Parallel tier scanning via ConcurrentTasks.forkJoinAll (shared flow)
        MemoryType[] targetTypes = options.memoryTypes();
        if (!runTierScan(allResults, queryVector, options, nowMs, targetTypes)) {
            return allResults;
        }

        // Step 4: Filter suppressed memories (inhibition)  --  always active
        allResults.removeIf(r -> suppressionSet.isSuppressed(r.id()));

        //  Steps 5-5b: Cognitive post-processing (shared flow) 
        // In SIMILARITY mode, applyCognitiveScoring skips ALL cognitive scoring
        // modifications (habituation, causal boost) so benchmarks measure pure
        // retrieval quality.
        applyCognitiveScoring(allResults, options, nowMs);

        // Steps 5c-5e: Graph expansion (delegated to GraphExpansionStage)
        graphExpansionStage.expand(allResults, queryVector, options);

        // Sort vector candidates by cognitive score descending before RRF rank assignment
        allResults.sort(Comparator.comparing(CognitiveResult::score).reversed());

        // Step 5f: BM25 text search & fusion (if enabled)
        if (bm25Index != null && options.enableTextSearch()
                && options.textSearchMode() != TextSearchMode.VECTOR_ONLY) {
            try {
                List<BM25Candidate> bm25Hits = bm25Index.search(queryText, options.topK() * 2);
                if (!bm25Hits.isEmpty()) {
                    fuseBM25Candidates(allResults, bm25Hits, options, nowMs);
                }
            } catch (RuntimeException e) {
                log.warn("BM25 search failed, continuing with vector-only results", e);
            }
        }

        // Step 5g: SPLADE learned sparse search & fusion (if enabled)
        if (options.enableTextSearch() && options.textSearchMode().usesSPLADE()) {
            if (spladeIndex != null && spladeProvider != null) {
                try {
                    SparseEmbeddingResult querySparse = spladeProvider.encode(queryText);
                    List<SpladeCandidate> spladeHits =
                            spladeIndex.search(querySparse.weights(), options.topK() * 2);
                    if (!spladeHits.isEmpty()) {
                        // Convert SPLADE candidates to BM25Candidate format for RRF fusion
                        List<BM25Candidate> asBm25 = spladeHits.stream()
                                .map(sc -> new BM25Candidate(
                                        sc.id(), sc.spladeScore(), sc.partitionIndex()))
                                .toList();
                        fuseBM25Candidates(allResults, asBm25, options, nowMs);
                    }
                } catch (RuntimeException e) {
                    log.warn("SPLADE search failed, continuing without", e);
                }
            } else if (!spladeWarnLogged) {
                log.warn("SPLADE search requested (mode={}) but SparseEmbeddingProvider/SpladeIndex " +
                         "not configured  --  degrading to BM25", options.textSearchMode());
                spladeWarnLogged = true;
            }
        }

        // Step 6: Sort by score descending, limit to topK
        allResults.sort(Comparator.comparing(CognitiveResult::score).reversed());
        if (allResults.size() > options.topK()) {
            allResults = new ArrayList<>(allResults.subList(0, options.topK()));
        }

        // Step 6b: ColBERT v2 reranker (if enabled and provider available)
        if (options.enableReranker() && options.textSearchMode().usesColBERT()) {
            if (colbertReranker != null) {
                try {
                    int rerankerDepth = Math.min(options.rerankerDepth(), allResults.size());
                    if (rerankerDepth > 0) {
                        List<CognitiveResult> toRerank = allResults.subList(0, rerankerDepth);

                        List<RerankCandidate> candidates = toRerank.stream()
                                .map(r -> new RerankCandidate(
                                        r.id(), r.text() != null ? r.text() : "", r.score()))
                                .toList();

                        List<RerankResult> reranked =
                                colbertReranker.rerank(queryText, candidates, options.topK());

                        // Build reranked result list: replace first-stage scores with combined scores
                        Map<String, Float> rerankScores = new HashMap<>();
                        for (RerankResult rr : reranked) {
                            rerankScores.put(rr.id(), rr.combinedScore());
                        }

                        // Update scores for reranked candidates
                        for (int i = 0; i < toRerank.size(); i++) {
                            CognitiveResult r = toRerank.get(i);
                            Float newScore = rerankScores.get(r.id());
                            if (newScore != null) {
                                allResults.set(i, new CognitiveResult(
                                        r.id(), r.text(), newScore, r.importance(),
                                        r.ageDays(), r.agentRecallCount(), r.valence(),
                                        r.memoryType(), r.source(), r.synapticTags(),
                                        r.decayFactor(), r.ltpAdjustedDecay(),
                                        r.retrievalMode(), r.breakdown(), r.trace(),
                                        r.sourceModality(), r.metadata()));
                            }
                        }

                        // Re-sort after reranking
                        allResults.sort(Comparator.comparing(CognitiveResult::score).reversed());
                        if (allResults.size() > options.topK()) {
                            allResults = new ArrayList<>(allResults.subList(0, options.topK()));
                        }

                        log.debug("ColBERT reranked {} candidates  ->  {} results",
                                rerankerDepth, allResults.size());
                    }
                } catch (RuntimeException e) {
                    log.warn("ColBERT reranking failed, keeping first-stage order", e);
                }
            } else if (!colbertWarnLogged) {
                log.warn("ColBERT reranking requested (mode={}) but ColBERTReranker " +
                         "not configured  --  skipping rerank step", options.textSearchMode());
                colbertWarnLogged = true;
            }
        }

        // Step 7: Fire async post-recall listeners (LTP reconsolidation + Hebbian)
        // In OBSERVE mode, listeners are skipped to prevent persistent mutations.
        if (options.recallMode() == RecallMode.LEARN && !listeners.isEmpty()) {
            final List<CognitiveResult> finalResults = allResults;
            for (RecallListener listener : listeners) {
                ConcurrentTasks.fireAndForget(() -> listener.onRecallComplete(finalResults));
            }
        }

        // Steps 8-8c: Record ephemeral session state (LEARN mode only)
        if (options.recallMode() == RecallMode.LEARN) {
            // Step 8: Record recall timestamps for Inhibition of Return
            long recallTs = System.currentTimeMillis();
            for (CognitiveResult r : allResults) {
                habituationPenalty.recordRecall(r.id(), recallTs);
            }

            log.debug("Recall returned {} results for '{}'", allResults.size(), queryText);

            // Step 8c: Cache retrieval modes for lateral feedback (reinforce/suppress)
            if (recentRetrievalModes.size() > RETRIEVAL_MODE_CACHE_MAX) {
                // Evict ~25% of entries instead of clearing everything.
                // ConcurrentHashMap iteration order is arbitrary, which is fine  -- 
                // retrieval modes are ephemeral session state.
                int toRemove = RETRIEVAL_MODE_CACHE_MAX / 4;
                var iter = recentRetrievalModes.keySet().iterator();
                for (int i = 0; i < toRemove && iter.hasNext(); i++) {
                    iter.next();
                    iter.remove();
                }
            }
            for (CognitiveResult r : allResults) {
                if (r.id() != null) {
                    recentRetrievalModes.put(r.id(), r.retrievalMode());
                }
            }

            // Step 8b: Update semantic satiation cache (bounded via eviction  --  ADR-005)
            long nowForSatiation = System.currentTimeMillis();
            for (CognitiveResult r : allResults) {
                if (r.id() != null) {
                    satiationCache.put(r.id(), nowForSatiation);
                }
            }
            // Evict oldest entries when cache exceeds bound
            while (satiationCache.size() > SATIATION_CACHE_SIZE) {
                String oldest = null;
                long oldestTs = Long.MAX_VALUE;
                for (var e : satiationCache.entrySet()) {
                    if (e.getValue() < oldestTs) {
                        oldestTs = e.getValue();
                        oldest = e.getKey();
                    }
                }
                if (oldest != null) {
                    satiationCache.remove(oldest, oldestTs);
                } else {
                    break;
                }
            }
        } else {
            log.debug("Recall [OBSERVE] returned {} results for '{}'", allResults.size(), queryText);
        }

        // Step 9: Write last-used profile ordinal to synaptic header byte 60
        // This enables ProfileAdaptor to read which profile produced each result
        // during reinforce() calls.
        writeProfileOrdinalToResults(allResults, options);

        // Step 9b: Record tags in RecallHistory for associative recall context
        if (recallHistory != null && options.recallMode() == RecallMode.LEARN) {
            for (CognitiveResult r : allResults) {
                if (r.synapticTags() != null && r.synapticTags().length > 0) {
                    recallHistory.record(r.synapticTags());
                }
            }
        }

        //  Pipeline Tracing (opt-in) 
        // When enableTrace is true, attach a RecallTrace to each result showing
        // how its score evolved through the cognitive pipeline phases.
        if (options.enableTrace() && !allResults.isEmpty()) {
            int totalCandidates = allResults.size();
            for (int i = 0; i < allResults.size(); i++) {
                CognitiveResult r = allResults.get(i);
                RecallTrace.Builder traceBuilder = new RecallTrace.Builder(r.id());

                // Phase 1: Cognitive Score (fused alphaxsimilarity + betaximportancexdecay)
                if (r.hasBreakdown()) {
                    ScoreBreakdown bd = r.breakdown();
                    traceBuilder.addStep("COGNITIVE_SCORE", 0f, bd.finalScore(),
                            0, totalCandidates,
                            String.format("alpha=%.2f, sim=%.3f, beta=%.2f, impDecay=%.3f, tagBoost=%.2f",
                                    options.alpha(), bd.similarity(),
                                    options.beta(), bd.importanceDecay(), bd.tagBoostFactor()));

                    // Phase 2: Habituation
                    if (bd.habituationPenalty() < 1.0f) {
                        float preHab = bd.finalScore() / bd.habituationPenalty();
                        traceBuilder.addStep("HABITUATION", preHab, bd.finalScore(),
                                totalCandidates, totalCandidates,
                                String.format("penalty=%.3f", bd.habituationPenalty()));
                    } else {
                        traceBuilder.addStep("HABITUATION", bd.finalScore(), bd.finalScore(),
                                totalCandidates, totalCandidates, "no penalty");
                    }

                    // Phase 3: Graph boost
                    if (bd.graphBoost() != 0f) {
                        float preGraph = r.score() - bd.graphBoost();
                        traceBuilder.addStep("GRAPH_BOOST", preGraph, r.score(),
                                totalCandidates, totalCandidates,
                                String.format("boost=%.4f", bd.graphBoost()));
                    }

                    // Phase 4: Valence alignment
                    if (bd.valenceAlignment() != 0f) {
                        traceBuilder.addStep("VALENCE_ALIGN", r.score(), r.score() + bd.valenceAlignment(),
                                totalCandidates, totalCandidates,
                                String.format("alignment=%.4f", bd.valenceAlignment()));
                    }
                } else {
                    // No breakdown  --  just record final score
                    traceBuilder.addStep("COGNITIVE_SCORE", 0f, r.score(),
                            0, totalCandidates, "no breakdown available");
                }

                // Phase 5: Top-K cutoff
                traceBuilder.addStep("TOPK_CUTOFF", r.score(), r.score(),
                        totalCandidates, options.topK(),
                        String.format("rank=%d/%d, included=true", i + 1, options.topK()));

                allResults.set(i, r.withTrace(traceBuilder.build()));
            }
            log.debug("Pipeline tracing: attached traces to {} results", allResults.size());
        }

        return allResults;
    }

    // ==============================================================
    // BM25 FUSION  --  merges keyword results with vector results
    // ==============================================================

    /**
     * Fuses BM25 text search candidates with existing vector recall results.
     *
     * <p>Three cases:</p>
     * <ol>
     *   <li><b>Both paths</b>: vector result gets a Î³ ·bm25Score additive boost</li>
     *   <li><b>BM25-only</b>: creates a new CognitiveResult with score = Î³ ·bm25Score</li>
     *   <li><b>Vector-only</b>: unmodified (no BM25 boost)</li>
     * </ol>
     *
     * @param vectorResults mutable list of existing vector recall results (modified in-place)
     * @param bm25Hits      BM25 search candidates from all partitions
     * @param options       recall options (for gamma weight)
     * @param nowMs         current time for age calculation
     */
    private void fuseBM25Candidates(List<CognitiveResult> vectorResults,
                                     List<BM25Candidate> bm25Hits,
                                     RecallOptions options, long nowMs) {
        //  Reciprocal Rank Fusion (RRF) 
        // Industry-standard fusion: RRF_score(d) = sum 1/(k + rank(d))
        // where k=60 prevents top-1 from dominating. Used by Elasticsearch,
        // Weaviate, Qdrant. Much better than additive score fusion because
        // it normalizes heterogeneous score distributions.
        final int RRF_K = 60;

        // Build rank maps: id  ->  rank (1-based)
        Map<String, Integer> vectorRanks = new java.util.LinkedHashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            String id = vectorResults.get(i).id();
            if (id != null && !vectorRanks.containsKey(id)) {
                vectorRanks.put(id, i + 1); // 1-based rank
            }
        }

        Map<String, Integer> bm25Ranks = new java.util.LinkedHashMap<>();
        for (int i = 0; i < bm25Hits.size(); i++) {
            String id = bm25Hits.get(i).id();
            if (id != null && !bm25Ranks.containsKey(id)) {
                bm25Ranks.put(id, i + 1);
            }
        }

        // Collect all unique IDs
        java.util.Set<String> allIds = new java.util.LinkedHashSet<>();
        allIds.addAll(vectorRanks.keySet());
        allIds.addAll(bm25Ranks.keySet());

        // Compute RRF score for each ID
        Map<String, Float> rrfScores = new java.util.HashMap<>();
        for (String id : allIds) {
            float score = 0f;
            Integer vr = vectorRanks.get(id);
            Integer br = bm25Ranks.get(id);
            if (vr != null) score += 1.0f / (RRF_K + vr);
            if (br != null) score += 1.0f / (RRF_K + br);
            rrfScores.put(id, score);
        }

        // Index existing vector results by ID for metadata lookup
        Map<String, CognitiveResult> existingById = new java.util.LinkedHashMap<>();
        for (CognitiveResult r : vectorResults) {
            if (r.id() != null && !existingById.containsKey(r.id())) {
                existingById.put(r.id(), r);
            }
        }

        // Rebuild result list with RRF scores
        vectorResults.clear();
        for (String id : allIds) {
            float rrfScore = rrfScores.get(id);
            CognitiveResult existing = existingById.get(id);

            if (existing != null) {
                // Re-score existing result with RRF
                vectorResults.add(new CognitiveResult(
                        existing.id(), existing.text(), rrfScore, existing.importance(),
                        existing.ageDays(), existing.agentRecallCount(), existing.valence(),
                        existing.memoryType(), existing.source(), existing.synapticTags(),
                        existing.decayFactor(), existing.ltpAdjustedDecay(),
                        existing.retrievalMode(), existing.breakdown(), existing.trace(),
                        existing.sourceModality(), existing.metadata()));
            } else {
                // BM25-only result  --  create from index metadata
                if (!options.includeContradictions()) {
                    MemoryIndex.MemoryLocation loc = index.locate(id);
                    if (loc != null) {
                        CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
                        MemorySegment segment = router.segmentFor(loc.type());
                        if (segment != null) {
                            CognitiveRecordLayout layout = router.layoutFor(loc.type());
                            byte cFlags = layout.readConsolidationFlags(segment, loc.offset());
                            if (SynapticHeaderConstants.isContradicted(cFlags)) continue;
                        }
                    }
                }

                String text = index.text(id);
                if (text == null || text.isEmpty()) continue;


                MemorySource source = index.source(id);
                String[] tags = index.tags(id);
                MemoryIndex.MemoryLocation loc = index.locate(id);
                MemoryType type = loc != null ? loc.type() : MemoryType.SEMANTIC;

                java.util.Map<String, String> bm25Meta = index.metadata(id);
                SourceModality bm25Modality = bm25Meta != null
                        ? SourceModality.fromName(bm25Meta.get(SourceModality.METADATA_KEY))
                        : SourceModality.TEXT;
                vectorResults.add(new CognitiveResult(
                        id, text, rrfScore, 0f, 0f,
                        (short) 0, (byte) 0, type, source,
                        tags, 1.0f, 1.0f, CognitiveResult.RetrievalMode.STANDARD, null, null,
                        bm25Modality, bm25Meta));
            }
        }

        // Sort by RRF score descending
        vectorResults.sort(java.util.Comparator.comparing(CognitiveResult::score).reversed());

        log.debug("RRF fused {} vector + {} BM25 candidates  ->  {} unique results",
                vectorRanks.size(), bm25Ranks.size(), vectorResults.size());
    }

    // ==============================================================
    // PARALLEL SCANNING  --  builds Callable tasks for each tier/partition
    // ==============================================================

    private List<Callable<List<CognitiveResult>>> buildScanTasks(
            float[] queryVector, RecallOptions options, long nowMs, MemoryType[] targetTypes) {
        List<Callable<List<CognitiveResult>>> tasks = new ArrayList<>();
        scan(new ParallelScanEmitter(tasks, queryVector, options, nowMs), targetTypes);
        return tasks;
    }

    /**
     * Shared scan traversal driving both {@link #buildScanTasks} (parallel) and
     * {@link #sequentialScan} (sequential fallback). The parallel-vs-sequential
     * difference is isolated to the {@link ScanEmitter}; the per-tier decision logic
     * lives in the {@link TierScanStrategy} registry (OCP).
     *
     * <p><b>#443 (D4b):</b> one volatile read of the immutable partition snapshot at
     * recall start. Working memory is GLOBAL — scanned once (emitted first, to preserve
     * result ordering); the record tiers fan out per partition (D2) in snapshot order.</p>
     */
    private void scan(ScanEmitter emitter, MemoryType[] targetTypes) {
        List<PartitionHandle> snapshot = partitionRegistry.snapshot();
        CognitiveMemoryRouter active = partitionRegistry.activeRouter();
        boolean singlePartition = snapshot.size() == 1;
        int activeSeq = snapshot.get(snapshot.size() - 1).seq();
        boolean semanticHnswAvailable =
                semanticRecallStrategy != null && semanticRecallStrategy.isAvailable();
        ScanContext ctx = new ScanContext(targetTypes, active, singlePartition,
                activeSeq, semanticHnswAvailable);

        PartitionHandle activeHandle = snapshot.get(snapshot.size() - 1);

        // Working memory — GLOBAL, scanned once (baseOffset 0) via the active router.
        WORKING_SCAN.contribute(ctx, activeHandle, emitter);

        // #443 (D2): record tiers fan out — one scan per partition segment per tier.
        // Disjoint segments → zero contention. Snapshot order preserved.
        for (PartitionHandle handle : snapshot) {
            for (TierScanStrategy strategy : PER_PARTITION_SCANS) {
                strategy.contribute(ctx, handle, emitter);
            }
        }
    }

    /** Immutable per-recall context shared by every {@link TierScanStrategy}. */
    private record ScanContext(MemoryType[] targetTypes, CognitiveMemoryRouter active,
                               boolean singlePartition, int activeSeq,
                               boolean semanticHnswAvailable) {
    }

    /**
     * Turns a strategy's per-tier scan decision into actual work: either a deferred
     * parallel {@link Callable} (build-tasks mode) or an immediate synchronous scan
     * (sequential-fallback mode). Segment/visibleCount are supplied lazily so the
     * parallel path reads them at task-execution time (matching the pre-refactor lambdas).
     */
    private interface ScanEmitter {
        /** Emits a full-record slab scan of the given store slice. */
        void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                          CognitiveRecordLayout layout, MemoryType type,
                          long baseOffset, int partitionSeq);

        /** Emits the semantic HNSW fast-path recall (active single partition only). */
        void emitSemanticHnsw();
    }

    /** Parallel emitter — each scan becomes an {@code madvise}-wrapped {@link Callable}. */
    private final class ParallelScanEmitter implements ScanEmitter {
        private final List<Callable<List<CognitiveResult>>> tasks;
        private final float[] queryVector;
        private final RecallOptions options;
        private final long nowMs;

        ParallelScanEmitter(List<Callable<List<CognitiveResult>>> tasks,
                            float[] queryVector, RecallOptions options, long nowMs) {
            this.tasks = tasks;
            this.queryVector = queryVector;
            this.options = options;
            this.nowMs = nowMs;
        }

        @Override
        public void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                                 CognitiveRecordLayout layout, MemoryType type,
                                 long baseOffset, int partitionSeq) {
            tasks.add(() -> {
                MemorySegment seg = segment.get();
                NativeOsMemory.advise(seg, NativeOsMemory.MADV_SEQUENTIAL);
                try {
                    return scoreStoreToList(seg, visibleCount.getAsInt(), layout,
                            queryVector, options, nowMs, type, baseOffset, partitionSeq);
                } finally {
                    NativeOsMemory.advise(seg, NativeOsMemory.MADV_NORMAL);
                }
            });
        }

        @Override
        public void emitSemanticHnsw() {
            tasks.add(() -> semanticRecallStrategy.recall(queryVector, options, nowMs));
        }
    }

    /** Sequential emitter — each scan runs immediately (no {@code madvise}), matching the fallback path. */
    private final class SequentialScanEmitter implements ScanEmitter {
        private final List<CognitiveResult> results;
        private final float[] queryVector;
        private final RecallOptions options;
        private final long nowMs;

        SequentialScanEmitter(List<CognitiveResult> results,
                              float[] queryVector, RecallOptions options, long nowMs) {
            this.results = results;
            this.queryVector = queryVector;
            this.options = options;
            this.nowMs = nowMs;
        }

        @Override
        public void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                                 CognitiveRecordLayout layout, MemoryType type,
                                 long baseOffset, int partitionSeq) {
            results.addAll(scoreStoreToList(segment.get(), visibleCount.getAsInt(), layout,
                    queryVector, options, nowMs, type, baseOffset, partitionSeq));
        }

        @Override
        public void emitSemanticHnsw() {
            results.addAll(semanticRecallStrategy.recall(queryVector, options, nowMs));
        }
    }

    /**
     * Produces the scan work for a single memory tier given a {@link PartitionHandle}.
     * One implementation per {@link MemoryType} removes the per-tier if/else that used
     * to live inline in the scan builders (OCP), while preserving the #443 per-partition
     * fan-out and the single-partition HNSW-vs-slab decision for SEMANTIC exactly.
     */
    private interface TierScanStrategy {
        MemoryType tier();
        void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter);
    }

    /** Working memory is GLOBAL — scanned once via the active router (baseOffset 0). */
    private static final class WorkingTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.WORKING; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.WORKING, ctx.targetTypes())) return;
            CognitiveRecordMemory working = ctx.active().working();
            if (working.visibleCount() <= 0) return;
            emitter.emitSlabScan(working::segment, working::visibleCount,
                    working.cognitiveLayout(), MemoryType.WORKING, 0L, ctx.activeSeq());
        }
    }

    /** Episodic — one scan per episodic partition of the handle. */
    private static final class EpisodicTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.EPISODIC; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.EPISODIC, ctx.targetTypes())) return;
            for (EpisodicPartition partition : handle.router().episodic().partitions()) {
                if (partition.visibleCount() > 0) {
                    emitter.emitSlabScan(partition::segment, partition::visibleCount,
                            partition.layout(), MemoryType.EPISODIC,
                            partition.dataOffset(), handle.seq());
                }
            }
        }
    }

    /**
     * Semantic — the active partition uses the HNSW fast path ONLY while there is a
     * single partition (the global HNSW's per-store slot indices collide across
     * partitions after a roll). Once rolled, every semantic partition (including the
     * active one) is scored on its full-record slab via CognitiveScorer, which computes
     * similarity (SemanticRecordMemory stores header + vector).
     */
    private static final class SemanticTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.SEMANTIC; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.SEMANTIC, ctx.targetTypes())) return;
            CognitiveRecordMemory semantic = handle.router().semantic();
            if (semantic == null || semantic.visibleCount() <= 0) return;
            boolean useHnsw = handle.writable() && ctx.singlePartition() && ctx.semanticHnswAvailable();
            if (useHnsw) {
                emitter.emitSemanticHnsw();
            } else {
                emitter.emitSlabScan(semantic::segment, semantic::visibleCount,
                        semantic.cognitiveLayout(), MemoryType.SEMANTIC,
                        semantic.dataOffset(), handle.seq());
            }
        }
    }

    /** Procedural — a single flat slab scan per handle. */
    private static final class ProceduralTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.PROCEDURAL; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.PROCEDURAL, ctx.targetTypes())) return;
            CognitiveRecordMemory procedural = handle.router().procedural();
            if (procedural.visibleCount() <= 0) return;
            emitter.emitSlabScan(procedural::segment, procedural::visibleCount,
                    procedural.cognitiveLayout(), MemoryType.PROCEDURAL,
                    procedural.dataOffset(), handle.seq());
        }
    }

    /** Global (working) tier strategy — invoked once per scan. */
    private static final TierScanStrategy WORKING_SCAN = new WorkingTierScanStrategy();

    /** Per-partition tier strategies, in the fixed emit order EPISODIC → SEMANTIC → PROCEDURAL. */
    private static final List<TierScanStrategy> PER_PARTITION_SCANS = List.of(
            new EpisodicTierScanStrategy(),
            new SemanticTierScanStrategy(),
            new ProceduralTierScanStrategy());

    /**
     * Fallback sequential scan (used if parallel scan fails).
     */
    private List<CognitiveResult> sequentialScan(float[] queryVector, RecallOptions options,
                                                   long nowMs, MemoryType[] targetTypes) {
        List<CognitiveResult> results = new ArrayList<>();
        scan(new SequentialScanEmitter(results, queryVector, options, nowMs), targetTypes);
        return results;
    }

    // ==============================================================
    // SCORING HELPERS  --  return lists (for parallel composition)
    // ==============================================================

    private List<CognitiveResult> scoreStoreToList(MemorySegment segment, int recordCount,
                                                     CognitiveRecordLayout layout, float[] queryVector,
                                                     RecallOptions options, long nowMs, MemoryType type,
                                                     long baseOffset, int partitionSeq) {
        List<ScoredRecord> scored = CognitiveScorer.score(
                segment, recordCount, layout, queryVector, options, nowMs, baseOffset,
                calibrationMins, calibrationScales);

        List<CognitiveResult> results = new ArrayList<>(scored.size());
        for (ScoredRecord sr : scored) {
            // P8: Header already captured during scoring  --  no off-heap re-read
            results.add(headerToResult(sr, sr.header(), type, partitionSeq));
        }
        return results;
    }

    private CognitiveResult headerToResult(ScoredRecord sr, CognitiveHeader header, MemoryType type,
                                            int partitionSeq) {
        // #443: resolve id via the partition being scanned (partition-aware reverse key).
        String id = index.findIdByOffset(partitionSeq, type, sr.offset());  // O(1) via reverse index
        String text = id != null ? index.text(id) : "";
        MemorySource source = id != null ? index.source(id) : MemorySource.OBSERVED;
        String[] tags = id != null ? index.tags(id) : new String[0];

        long nowMs = System.currentTimeMillis();
        float ageDays = (nowMs - header.timestampMs()) / (1000f * 60f * 60f * 24f);

        int rawBucket = DecayStrategy.ageToBucket(header.timestampMs(), nowMs);
        int adjusted = DecayStrategy.adjustForReconsolidation(rawBucket, header.agentRecallCount());
        float rawDecay = DecayStrategy.decay(rawBucket);
        float ltpDecay = DecayStrategy.decay(adjusted);

        // Determine retrieval mode from scorer metadata
        RetrievalMode mode;
        if (sr.lateral()) {
            mode = RetrievalMode.LATERAL;
        } else if (lastRecallOptions != null && lastRecallOptions.hyperfocusMask() != 0) {
            mode = RetrievalMode.HYPERFOCUS;
        } else {
            mode = RetrievalMode.STANDARD;
        }

        //  ScoreBreakdown: re-derive components from header 
        // Uses the same formula as CognitiveScorer Phase 6.
        // Note: these are approximations  --  the scorer's strictness/arousal/storageBoost
        // values are folded into the fused score. We capture what we can from the header.
        float importanceDecay = header.importance() * ltpDecay;
        // Breakdown: individual multipliers default to 1.0 (no effect)
        // habituationPenalty and graphBoost are applied post-scorer in the pipeline
        // and updated in-place on CognitiveResult  --  we record 1.0 here and
        // the pipeline adjusts them when it applies those factors.
        ScoreBreakdown breakdown = new ScoreBreakdown(
                /* similarity */       Math.max(0, sr.score() > 0 ? sr.score() : 0),
                /* importanceDecay */  importanceDecay,
                /* tagBoostFactor */   1.0f,
                /* habituationPenalty */ 1.0f,
                /* graphBoost */       1.0f,
                /* valenceAlignment */ 1.0f,
                /* finalScore */       sr.score()
        );

        // Read source modality from flags byte (bits 6-7)
        SourceModality modality = SourceModality.fromOrdinal(
                SynapticHeaderConstants.sourceModalityOrdinal(header.flags()));
        java.util.Map<String, String> metadata = id != null ? index.metadata(id) : java.util.Map.of();
        
        String resultText = text;
        if (metadata != null && metadata.containsKey("parent_chunk_id")) {
            String parentId = metadata.get("parent_chunk_id");
            String parentText = index.text(parentId);
            if (parentText != null && !parentText.isBlank()) {
                resultText = parentText;
                var mutableMeta = new java.util.HashMap<>(metadata);
                mutableMeta.put("child_text", text);
                metadata = java.util.Map.copyOf(mutableMeta);
            }
        }

        return new CognitiveResult(
                id != null ? id : "unknown-" + sr.index(),
                resultText, sr.score(), header.importance(), ageDays,
                header.agentRecallCount(), header.valence(), type, source,
                tags, rawDecay, ltpDecay, mode, breakdown, null,
                modality, metadata
        );
    }

    /**
     * Returns whether the given memory was returned as a lateral result
     * in a recent recall.
     *
     * @param memoryId the memory ID to check
     * @return true if the memory was a lateral result, false otherwise
     */
    public boolean wasLateral(String memoryId) {
        RetrievalMode mode = recentRetrievalModes.get(memoryId);
        return mode == RetrievalMode.LATERAL;
    }

    /**
     * Returns the retrieval mode for a recently recalled memory.
     *
     * @param memoryId the memory ID to check
     * @return the retrieval mode, or null if not in cache
     */
    public RetrievalMode retrievalModeOf(String memoryId) {
        return recentRetrievalModes.get(memoryId);
    }



    // ==============================================================
    // WAL REPLAY  --  Point-in-Time Recall
    // ==============================================================

    /**
     * Performs recall against a reconstructed point-in-time memory state.
     *
     * <p>Replays WAL events up to the target timestamp, builds an ephemeral
     * off-heap segment, runs a simplified linear scan, and disposes all
     * ephemeral state after returning results.</p>
     *
     * <p>Always operates in OBSERVE mode  --  no mutations to the live state.</p>
     *
     * @param queryText the query text
     * @param options   recall options (must have recallMode=REPLAY and replayTimestamp set)
     * @return ranked list of cognitive results from the historical state
     */
    private List<CognitiveResult> replayRecall(String queryText, RecallOptions options) {
        if (options.replayTimestamp() == null) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_NULL,
                    "replayTimestamp is required for RecallMode.REPLAY");
        }

        log.info("REPLAY recall: query='{}', target={}, maxEvents={}",
                queryText, options.replayTimestamp(), options.maxReplayEvents());

        // Step 1: Embed the query
        float[] queryVector = embeddingProvider.embed(queryText).vector();
        int quantizedVecBytes = queryVector.length; // INT8 = 1 byte per dimension
        long nowMs = options.replayTimestamp().toEpochMilli();

        // Step 2: Reconstruct historical state from WAL
        try (ReplaySnapshot snapshot = WalReplayer.replay(
                wal, options.replayTimestamp(), options.maxReplayEvents(), quantizedVecBytes)) {

            if (snapshot.memoryCount() == 0) {
                log.info("REPLAY recall: no memories at target timestamp {}", options.replayTimestamp());
                return List.of();
            }

            // Step 3: Linear scan of the reconstructed segment
            // Use the ephemeral index to find all live memory IDs and their locations
            List<CognitiveResult> results = new ArrayList<>();
            CognitiveRecordLayout layout = new CognitiveRecordLayout(quantizedVecBytes);

            for (String memId : snapshot.index().allIds()) {
                var loc = snapshot.index().locate(memId);
                if (loc == null) continue;

                long offset = loc.offset();
                MemorySegment seg = snapshot.arena().allocate(0); // We need the actual segment

                // Read header from the replay segment
                // Note: the replay segment is the Arena's first allocation
                // We need to access it through the snapshot
                try {
                    String text = snapshot.index().text(memId);
                    MemorySource source = snapshot.index().source(memId);
                    String[] memTags = snapshot.index().tags(memId);

                    // Read cognitive header fields from the replay segment
                    float importance = 0.5f; // Default for replay
                    byte valence = 0;
                    float ageDays = (float) ((nowMs - layout.readTimestamp(seg, offset))
                            / (double) (24 * 60 * 60 * 1000));

                    java.util.Map<String, String> rMeta = snapshot.index().metadata(memId);
                    SourceModality rModality = rMeta != null
                            ? SourceModality.fromName(rMeta.get(SourceModality.METADATA_KEY))
                            : SourceModality.TEXT;
                    results.add(new CognitiveResult(
                            memId, text, importance, importance,
                            Math.max(0, ageDays),
                            (short) 0, valence, MemoryType.SEMANTIC, source,
                            memTags, 1.0f, 1.0f, CognitiveResult.RetrievalMode.STANDARD, null, null,
                            rModality, rMeta));

                } catch (RuntimeException e) {
                    log.debug("REPLAY: skipping memory '{}': {}", memId, e.getMessage());
                }
            }

            // Step 4: Sort by importance (score) and limit to topK
            results.sort(java.util.Comparator.comparing(CognitiveResult::score).reversed());
            if (results.size() > options.topK()) {
                results = new ArrayList<>(results.subList(0, options.topK()));
            }

            log.info("REPLAY recall: returned {} results from {} reconstructed memories at {}",
                    results.size(), snapshot.memoryCount(), options.replayTimestamp());

            return results;
        }
    }

    // ==============================================================
    // PROFILE HEADER WRITE  --  stamps profile ordinal at byte 60
    // ==============================================================

    /**
     * Writes the CognitiveProfile ordinal to byte 60 of each result's synaptic header.
     *
     * <p>This enables the ReinforcementHandler to read which profile produced
     * each result during reinforce() calls, allowing the ProfileAdaptor to
     * learn context -> profile mappings.</p>
     */
    private void writeProfileOrdinalToResults(List<CognitiveResult> results, RecallOptions options) {
        CognitiveProfile profile = options.profile();
        if (profile == null || results.isEmpty()) return;

        byte profileOrdinal = (byte) profile.ordinal();
        for (CognitiveResult result : results) {
            if (result.id() == null) continue;
            try {
                var loc = index.locate(result.id());
                if (loc == null) continue;
                MemorySegment segment = partitionRegistry.routerFor(loc.colocatedPartition())
                        .segmentFor(loc.type());
                if (segment != null) {
                    segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE,
                            loc.offset() + SynapticHeaderConstants.OFFSET_LAST_RECALL_PROFILE,
                            profileOrdinal);
                }
            } catch (RuntimeException e) {
                // Non-critical  --  don't fail recall for header writes
                log.trace("Failed to write profile ordinal for '{}': {}", result.id(), e.getMessage());
            }
        }
    }

    // ==============================================================
    // ASSOCIATIVE RECALL  --  bottom-up context-driven retrieval
    // ==============================================================

    /**
     * Associative recall for Executive Dysfunction profile.
     *
     * <p>Instead of relying on explicit query intent, this method uses recent
     * activity context (from RecallHistory) and STDP causal predictions (from
     * CoActivationTracker) to surface contextually relevant memories bottom-up.
     * This models how the default mode network retrieves memories through
     * associative spreading activation rather than directed search.</p>
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li>Get recent context tags from RecallHistory (weighted by recency)</li>
     *   <li>Query STDP edges for causal predictions from those tags</li>
     *   <li>Run standard vector+cognitive recall with predicted tag boost</li>
     *   <li>Blend 2/3 associative + 1/3 standard vector results</li>
     * </ol>
     *
     * @param queryText the query text
     * @param options   recall options (with ASSOCIATIVE scoring mode)
     * @return ranked results combining context-driven and query-driven signals
     */
    private List<CognitiveResult> recallAssociative(String queryText, RecallOptions options) {
        // Step 1: Get recent context tags (weighted by recency)
        Map<String, Float> contextTags = recallHistory.weightedRecentTags(20, 0.85f);

        if (contextTags.isEmpty()) {
            // Cold start  --  fall back to standard cognitive recall with low alpha (more importance-driven)
            log.debug("Associative recall: cold start (no history), falling back to COGNITIVE");
            RecallOptions fallback = RecallOptions.builder()
                    .topK(options.topK())
                    .profile(options.profile())
                    .scoringMode(ScoringMode.COGNITIVE)
                    .recallMode(options.recallMode())
                    .build();
            return recall(queryText, fallback);
        }

        log.debug("Associative recall: {} context tags from history", contextTags.size());

        // Step 2: Query STDP edges for causal predictions
        Map<String, Float> predictedTags = new LinkedHashMap<>();
        if (coActivationTracker != null) {
            for (Map.Entry<String, Float> ctxEntry : contextTags.entrySet()) {
                String ctxTag = ctxEntry.getKey();
                float recencyWeight = ctxEntry.getValue();
                // Get tags causally associated with this context tag
                List<String> associated = coActivationTracker.getAssociatedTags(ctxTag, 5);
                for (String predTag : associated) {
                    // Each associated tag gets 1.0 x recency weight
                    predictedTags.merge(predTag, recencyWeight, Float::sum);
                }
            }
        }

        // Step 3: Build predicted tag set (top 10 by combined weight)
        List<String> topPredicted = predictedTags.entrySet().stream()
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        // Step 4: Run standard cognitive recall with predicted tags as synaptic filter
        RecallOptions.Builder cogBuilder = RecallOptions.builder()
                .topK(options.topK() * 2) // over-fetch for blending
                .profile(options.profile())
                .scoringMode(ScoringMode.COGNITIVE)
                .recallMode(options.recallMode());

        if (!topPredicted.isEmpty()) {
            cogBuilder.synapticFilter(topPredicted.toArray(new String[0]));
        }

        List<CognitiveResult> associativeResults = new ArrayList<>(recall(queryText, cogBuilder.build()));

        // Step 5: Re-score with STDP predictive boost for results matching predicted tags
        if (coActivationTracker != null && !contextTags.isEmpty()) {
            List<String> contextTagList = new ArrayList<>(contextTags.keySet());
            for (int i = 0; i < associativeResults.size(); i++) {
                CognitiveResult r = associativeResults.get(i);
                if (r.synapticTags() == null || r.synapticTags().length == 0) continue;

                float predictive = coActivationTracker.getPredictiveStrength(
                        contextTagList, r.synapticTags());
                if (predictive > 0) {
                    float boosted = r.score() * (1.0f + predictive * 0.5f);
                    associativeResults.set(i, new CognitiveResult(
                            r.id(), r.text(), boosted, r.importance(), r.ageDays(),
                            r.agentRecallCount(), r.valence(), r.memoryType(), r.source(),
                            r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay(),
                            r.retrievalMode(), r.breakdown(), r.trace(), r.sourceModality(), r.metadata()));
                }
            }
        }

        // Step 6: Sort, topK, record in recallHistory
        associativeResults.sort(Comparator.comparing(CognitiveResult::score).reversed());
        if (associativeResults.size() > options.topK()) {
            associativeResults = new ArrayList<>(associativeResults.subList(0, options.topK()));
        }

        // Record result tags in history for future associative context
        for (CognitiveResult r : associativeResults) {
            if (r.synapticTags() != null && r.synapticTags().length > 0) {
                recallHistory.record(r.synapticTags());
            }
        }

        // Write profile ordinal for reinforcement tracking
        writeProfileOrdinalToResults(associativeResults, options);

        log.debug("Associative recall: {} results (from {} context tags, {} predicted tags)",
                associativeResults.size(), contextTags.size(), predictedTags.size());

        return associativeResults;
    }
}

