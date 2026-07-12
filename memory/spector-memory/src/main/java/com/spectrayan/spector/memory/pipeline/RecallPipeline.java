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
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.TextSearchMode;
import com.spectrayan.spector.memory.cortex.AbstractTierStore;
import com.spectrayan.spector.memory.cortex.EpisodicMemoryStore.EpisodicPartition;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index.BM25Candidate;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex.SpladeCandidate;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.cortex.TierStore;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.prospective.Reminder;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.ReplaySnapshot;
import com.spectrayan.spector.memory.sync.WalReplayer;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.CognitiveScorer;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import com.spectrayan.spector.memory.hebbian.CoActivationTracker;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.temporal.TemporalChain;
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

import com.spectrayan.spector.commons.concurrent.NativeOsMemory;

import com.spectrayan.spector.embed.SparseEncodingProvider;
import com.spectrayan.spector.embed.SparseEncodingResult;
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
 * off-heap {@link MemorySegment} — zero contention. With 4 tiers + N episodic
 * partitions, recall latency = max(tier_latency) instead of sum(tier_latencies).</p>
 *
 * <h3>Performance: Async Post-Recall Hooks</h3>
 * <p>Steps 7–8 (LTP reconsolidation, Hebbian co-activation) fire on Virtual Threads
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
    private final TierRouter tierRouter;
    private final MemoryIndex index;
    private final SuppressionSet suppressionSet;
    private final HabituationPenalty habituationPenalty;
    private final ProspectiveScheduler prospectiveScheduler;
    private final MemoryWal wal;
    private final float[] calibrationMins;
    private final float[] calibrationScales;
    private final SemanticRecallStrategy semanticRecallStrategy; // nullable
    private final CoActivationTracker coActivationTracker; // nullable — for STDP causal boost
    private final GraphScoringPolicy graphScoringPolicy;
    private final GraphExpansionStage graphExpansionStage;

    private final List<RecallListener> listeners = new ArrayList<>();

    // ── 3-Layer Cognitive Graph (all nullable) ──
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChain temporalChain;
    private final EntityGraph entityGraph;
    private final EntityExtractor entityExtractor;

    // ── BM25 Text Search (nullable — graceful degradation) ──
    private final MemoryBM25Index bm25Index;

    // ── SPLADE Sparse Search (nullable — graceful degradation) ──
    private final MemorySpladeIndex spladeIndex;
    private final SparseEncodingProvider spladeProvider;
    private volatile boolean spladeWarnLogged = false;

    // ── ColBERT v2 Reranker (nullable — graceful degradation) ──
    private final ColBERTReranker colbertReranker;
    private volatile boolean colbertWarnLogged = false;

    // ── Neurodivergent: Lateral feedback tracking ──
    // Maps memoryId → RetrievalMode for the most recent recall.
    // Used by SpectorMemory.reinforce()/suppress() to feed LateralEvaluator.
    // Entries expire implicitly via size cap (oldest evicted at 2000).
    private final ConcurrentHashMap<String, RetrievalMode> recentRetrievalModes
            = new ConcurrentHashMap<>();
    private static final int RETRIEVAL_MODE_CACHE_MAX = 2000;
    private RecallOptions lastRecallOptions; // for detecting hyperfocus mode

    // ── Semantic Satiation: Anti-looping cache ──
    // Bounded cache of last N result IDs. Any result that appears in this
    // hot cache gets a 0.5× penalty, breaking exact-query loops.
    // Uses ConcurrentHashMap to avoid virtual thread pinning (ADR-005).
    // Size-bounded via eviction on put — acceptable for a 10-entry cache.
    private static final int SATIATION_CACHE_SIZE = 10;
    private static final float SATIATION_PENALTY = 0.5f;
    private final ConcurrentHashMap<String, Long> satiationCache = new ConcurrentHashMap<>(16);

    /**
     * Creates a recall pipeline with all required subsystems.
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           TierRouter tierRouter,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales) {
        this(embeddingProvider, tierRouter, index, suppressionSet, habituationPenalty,
                prospectiveScheduler, wal, calibrationMins, calibrationScales, null, null,
                null, null, null, null, GraphScoringPolicy.DEFAULT, null,
                null, null, null);
    }

    /**
     * Creates a recall pipeline with optional fused semantic recall.
     *
     * @param semanticRecallStrategy nullable — when provided, semantic recall uses
     *                                HNSW vector search fused with cognitive scoring
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           TierRouter tierRouter,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales,
                           SemanticRecallStrategy semanticRecallStrategy) {
        this(embeddingProvider, tierRouter, index, suppressionSet, habituationPenalty,
                prospectiveScheduler, wal, calibrationMins, calibrationScales,
                semanticRecallStrategy, null,
                null, null, null, null, GraphScoringPolicy.DEFAULT, null,
                null, null, null);
    }

    /**
     * Creates a recall pipeline with optional fused semantic recall and STDP.
     *
     * @param semanticRecallStrategy nullable — when provided, semantic recall uses
     *                                HNSW vector search fused with cognitive scoring
     * @param coActivationTracker    nullable — when provided, STDP causal boost is applied
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           TierRouter tierRouter,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales,
                           SemanticRecallStrategy semanticRecallStrategy,
                           CoActivationTracker coActivationTracker) {
        this(embeddingProvider, tierRouter, index, suppressionSet, habituationPenalty,
                prospectiveScheduler, wal, calibrationMins, calibrationScales,
                semanticRecallStrategy, coActivationTracker,
                null, null, null, null, GraphScoringPolicy.DEFAULT, null,
                null, null, null);
    }

    /**
     * Creates a recall pipeline with optional fused semantic recall, STDP, and 3-Layer Cognitive Graph.
     */
    public RecallPipeline(EmbeddingProvider embeddingProvider,
                           TierRouter tierRouter,
                           MemoryIndex index,
                           SuppressionSet suppressionSet,
                           HabituationPenalty habituationPenalty,
                           ProspectiveScheduler prospectiveScheduler,
                           MemoryWal wal,
                           float[] calibrationMins,
                           float[] calibrationScales,
                           SemanticRecallStrategy semanticRecallStrategy,
                           CoActivationTracker coActivationTracker,
                           HebbianGraphBase hebbianGraph,
                           TemporalChain temporalChain,
                           EntityGraph entityGraph,
                           EntityExtractor entityExtractor,
                           GraphScoringPolicy graphScoringPolicy,
                           MemoryBM25Index bm25Index,
                           MemorySpladeIndex spladeIndex,
                           SparseEncodingProvider spladeProvider,
                           ColBERTReranker colbertReranker) {
        this.embeddingProvider = embeddingProvider;
        this.tierRouter = tierRouter;
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
        this.entityGraph = entityGraph;
        this.entityExtractor = entityExtractor;
        this.graphScoringPolicy = graphScoringPolicy != null ? graphScoringPolicy : GraphScoringPolicy.DEFAULT;
        this.bm25Index = bm25Index;
        this.spladeIndex = spladeIndex;
        this.spladeProvider = spladeProvider;
        this.colbertReranker = colbertReranker;

        // ── Delegate graph expansion to focused stage class ──
        this.graphExpansionStage = new GraphExpansionStage(
                hebbianGraph, temporalChain, entityGraph, entityExtractor,
                this.graphScoringPolicy, index, tierRouter,
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

    /**
     * Executes the full recall pipeline with parallel tier scanning.
     *
     * @param queryText the query text (will be embedded)
     * @param options   recall configuration
     * @return ranked list of cognitive results
     */
    public List<CognitiveResult> recall(String queryText, RecallOptions options) {
        if (queryText == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "queryText"); }
        if (options == null) options = RecallOptions.DEFAULT;

        if (options.recallMode() == RecallMode.REPLAY) {
            return replayRecall(queryText, options);
        }

        log.debug("Recall query: '{}', topK={}, mode={}", queryText, options.topK(), options.recallMode());
        this.lastRecallOptions = options; // for RetrievalMode detection in headerToResult

        // Step 1: Embed query
        float[] queryVector = embeddingProvider.embed(queryText).vector();

        long nowMs = System.currentTimeMillis();
        List<CognitiveResult> allResults = new ArrayList<>();

        // Step 2: Collect due prospective reminders
        List<Reminder> dueReminders = prospectiveScheduler.collectDue();
        for (Reminder r : dueReminders) {
            allResults.add(new CognitiveResult(
                    r.id(), r.text(), 10.0f, 10.0f, 0f,
                    (short) 0, (byte) 0, MemoryType.WORKING, MemorySource.PROCEDURAL,
                    new String[]{"prospective"}, 1.0f, 1.0f));
        }

        // Step 3: Parallel tier scanning via ConcurrentTasks.forkJoinAll
        MemoryType[] targetTypes = options.memoryTypes();
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
                return allResults;
            }
        }

        // Step 3b: BM25 text search — parallel to tier scans
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

        // Step 3c: SPLADE learned sparse search
        if (options.enableTextSearch() && options.textSearchMode().usesSPLADE()) {
            if (spladeIndex != null && spladeProvider != null) {
                try {
                    SparseEncodingResult querySparse = spladeProvider.encode(queryText);
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
                log.warn("SPLADE search requested (mode={}) but SparseEncodingProvider/SpladeIndex " +
                         "not configured — degrading to BM25", options.textSearchMode());
                spladeWarnLogged = true;
            }
        }

        // Step 4: Filter suppressed memories (inhibition) — always active
        allResults.removeIf(r -> suppressionSet.isSuppressed(r.id()));

        // ── Steps 5-5e: Cognitive post-processing ──
        // In SIMILARITY mode, skip ALL cognitive scoring modifications:
        // habituation, causal boost, Hebbian, temporal chains, entity graph.
        // This ensures benchmarks measure pure retrieval quality.
        boolean cognitiveScoring = options.scoringMode() != ScoringMode.SIMILARITY;

        if (cognitiveScoring) {
        // Step 5: Apply habituation penalty + inhibition of return + semantic satiation
        for (int i = 0; i < allResults.size(); i++) {
            CognitiveResult r = allResults.get(i);
            float habPenalty = (options.recallMode() == RecallMode.LEARN)
                    ? habituationPenalty.recordAndComputePenalty(r.id())
                    : habituationPenalty.currentPenalty(r.id());
            float iorPenalty = habituationPenalty.computeInhibitionOfReturn(r.id(), nowMs);
            float combinedPenalty = Math.min(habPenalty, iorPenalty); // stronger suppression wins

            // Semantic Satiation: 0.5× penalty for results in the hot LRU cache
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

        // Step 5b: STDP causal boost — cross-boost results whose tags are causally linked
        // For each result, check if earlier results' tags predict its tags (via STDP edges).
        // This promotes memories that form causal chains.
        if (coActivationTracker != null && allResults.size() >= 2) {
            // Use tags from the first few results as "context tags" to boost subsequent results
            // (imperative loop — avoids Stream API allocation overhead in hot path)
            Set<String> contextTagSet = new HashSet<>();
            int contextLimit = Math.min(3, allResults.size());
            for (int cl = 0; cl < contextLimit; cl++) {
                String[] ctxTags = allResults.get(cl).synapticTags();
                if (ctxTags != null) {
                    for (String t : ctxTags) contextTagSet.add(t);
                }
            }

            if (!contextTagSet.isEmpty()) {
                // Convert to list once for getPredictiveStrength API
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
        } // end cognitiveScoring

        // Steps 5c-5e: Graph expansion (delegated to GraphExpansionStage)
        graphExpansionStage.expand(allResults, queryVector, options);

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

                        log.debug("ColBERT reranked {} candidates → {} results",
                                rerankerDepth, allResults.size());
                    }
                } catch (RuntimeException e) {
                    log.warn("ColBERT reranking failed, keeping first-stage order", e);
                }
            } else if (!colbertWarnLogged) {
                log.warn("ColBERT reranking requested (mode={}) but ColBERTReranker " +
                         "not configured — skipping rerank step", options.textSearchMode());
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
                // ConcurrentHashMap iteration order is arbitrary, which is fine —
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

            // Step 8b: Update semantic satiation cache (bounded via eviction — ADR-005)
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

        // ── Pipeline Tracing (opt-in) ──
        // When enableTrace is true, attach a RecallTrace to each result showing
        // how its score evolved through the cognitive pipeline phases.
        if (options.enableTrace() && !allResults.isEmpty()) {
            int totalCandidates = allResults.size();
            for (int i = 0; i < allResults.size(); i++) {
                CognitiveResult r = allResults.get(i);
                RecallTrace.Builder traceBuilder = new RecallTrace.Builder(r.id());

                // Phase 1: Cognitive Score (fused α×similarity + β×importance×decay)
                if (r.hasBreakdown()) {
                    ScoreBreakdown bd = r.breakdown();
                    traceBuilder.addStep("COGNITIVE_SCORE", 0f, bd.finalScore(),
                            0, totalCandidates,
                            String.format("α=%.2f, sim=%.3f, β=%.2f, impDecay=%.3f, tagBoost=%.2f",
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
                    // No breakdown — just record final score
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

    // ══════════════════════════════════════════════════════════════
    // BM25 FUSION — merges keyword results with vector results
    // ══════════════════════════════════════════════════════════════

    /**
     * Fuses BM25 text search candidates with existing vector recall results.
     *
     * <p>Three cases:</p>
     * <ol>
     *   <li><b>Both paths</b>: vector result gets a γ·bm25Score additive boost</li>
     *   <li><b>BM25-only</b>: creates a new CognitiveResult with score = γ·bm25Score</li>
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
        // ── Reciprocal Rank Fusion (RRF) ──
        // Industry-standard fusion: RRF_score(d) = Σ 1/(k + rank(d))
        // where k=60 prevents top-1 from dominating. Used by Elasticsearch,
        // Weaviate, Qdrant. Much better than additive score fusion because
        // it normalizes heterogeneous score distributions.
        final int RRF_K = 60;

        // Build rank maps: id → rank (1-based)
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
                // BM25-only result — create from index metadata
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

        log.debug("RRF fused {} vector + {} BM25 candidates → {} unique results",
                vectorRanks.size(), bm25Ranks.size(), vectorResults.size());
    }

    // ══════════════════════════════════════════════════════════════
    // PARALLEL SCANNING — builds Callable tasks for each tier/partition
    // ══════════════════════════════════════════════════════════════

    private List<Callable<List<CognitiveResult>>> buildScanTasks(
            float[] queryVector, RecallOptions options, long nowMs, MemoryType[] targetTypes) {
        List<Callable<List<CognitiveResult>>> tasks = new ArrayList<>();

        // Working Memory scan — use visibleCount() for SWMR safety
        if (TierRouter.shouldScan(MemoryType.WORKING, targetTypes)
                && tierRouter.working().visibleCount() > 0) {
            tasks.add(() -> {
                MemorySegment seg = tierRouter.working().segment();
                NativeOsMemory.advise(seg, NativeOsMemory.MADV_SEQUENTIAL);
                try {
                    return scoreStoreToList(
                            seg, tierRouter.working().visibleCount(),
                            tierRouter.working().layout(), queryVector, options, nowMs,
                            MemoryType.WORKING, 0L);
                } finally {
                    NativeOsMemory.advise(seg, NativeOsMemory.MADV_NORMAL);
                }
            });
        }

        // Episodic Memory — one task per partition (disjoint segments → zero contention)
        if (TierRouter.shouldScan(MemoryType.EPISODIC, targetTypes)) {
            for (EpisodicPartition partition : tierRouter.episodic().partitions()) {
                if (partition.visibleCount() > 0) {
                    tasks.add(() -> {
                        MemorySegment seg = partition.segment();
                        NativeOsMemory.advise(seg, NativeOsMemory.MADV_SEQUENTIAL);
                        try {
                            return scoreStoreToList(
                                    seg, partition.visibleCount(),
                                    partition.layout(), queryVector, options, nowMs,
                                    MemoryType.EPISODIC, partition.dataOffset());
                        } finally {
                            NativeOsMemory.advise(seg, NativeOsMemory.MADV_NORMAL);
                        }
                    });
                }
            }
        }

        // Semantic Memory — fused HNSW+cognitive if strategy available, else header slab
        if (TierRouter.shouldScan(MemoryType.SEMANTIC, targetTypes)) {
            if (tierRouter.semantic() != null && tierRouter.semantic().visibleCount() > 0) {
                if (semanticRecallStrategy != null && semanticRecallStrategy.isAvailable()) {
                    // Fused pipeline: HNSW search → cognitive re-ranking
                    tasks.add(() -> semanticRecallStrategy.recall(queryVector, options, nowMs));
                } else {
                    // Fallback: full-record slab scan (with tag/valence filters)
                    tasks.add(() -> {
                        MemorySegment seg = tierRouter.semantic().headerSlab();
                        NativeOsMemory.advise(seg, NativeOsMemory.MADV_SEQUENTIAL);
                        try {
                            return scoreHeaderSlabToList(
                                    seg, tierRouter.semantic().visibleCount(),
                                    tierRouter.semantic().layout(), queryVector, options, nowMs,
                                    tierRouter.semantic().isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0);
                        } finally {
                            NativeOsMemory.advise(seg, NativeOsMemory.MADV_NORMAL);
                        }
                    });
                }
            }
        }

        // Procedural Memory scan
        if (TierRouter.shouldScan(MemoryType.PROCEDURAL, targetTypes)
                && tierRouter.procedural().visibleCount() > 0) {
            tasks.add(() -> {
                MemorySegment seg = tierRouter.procedural().segment();
                NativeOsMemory.advise(seg, NativeOsMemory.MADV_SEQUENTIAL);
                try {
                    return scoreStoreToList(
                            seg, tierRouter.procedural().visibleCount(),
                            tierRouter.procedural().layout(), queryVector, options, nowMs,
                            MemoryType.PROCEDURAL, 0L);
                } finally {
                    NativeOsMemory.advise(seg, NativeOsMemory.MADV_NORMAL);
                }
            });
        }

        return tasks;
    }

    /**
     * Fallback sequential scan (used if parallel scan fails).
     */
    private List<CognitiveResult> sequentialScan(float[] queryVector, RecallOptions options,
                                                   long nowMs, MemoryType[] targetTypes) {
        List<CognitiveResult> results = new ArrayList<>();
        if (TierRouter.shouldScan(MemoryType.WORKING, targetTypes)
                && tierRouter.working().visibleCount() > 0) {
            results.addAll(scoreStoreToList(tierRouter.working().segment(),
                    tierRouter.working().visibleCount(), tierRouter.working().layout(),
                    queryVector, options, nowMs, MemoryType.WORKING, 0L));
        }
        if (TierRouter.shouldScan(MemoryType.EPISODIC, targetTypes)) {
            for (EpisodicPartition p : tierRouter.episodic().partitions()) {
                if (p.visibleCount() > 0) {
                    results.addAll(scoreStoreToList(p.segment(), p.visibleCount(), p.layout(),
                            queryVector, options, nowMs, MemoryType.EPISODIC, p.dataOffset()));
                }
            }
        }
        if (TierRouter.shouldScan(MemoryType.SEMANTIC, targetTypes)) {
            if (tierRouter.semantic() != null && tierRouter.semantic().visibleCount() > 0) {
                if (semanticRecallStrategy != null && semanticRecallStrategy.isAvailable()) {
                    results.addAll(semanticRecallStrategy.recall(queryVector, options, nowMs));
                } else {
                    results.addAll(scoreHeaderSlabToList(tierRouter.semantic().headerSlab(),
                            tierRouter.semantic().visibleCount(), tierRouter.semantic().layout(),
                            queryVector, options, nowMs,
                            tierRouter.semantic().isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0));
                }
            }
        }
        if (TierRouter.shouldScan(MemoryType.PROCEDURAL, targetTypes)
                && tierRouter.procedural().visibleCount() > 0) {
            results.addAll(scoreStoreToList(tierRouter.procedural().segment(),
                    tierRouter.procedural().visibleCount(), tierRouter.procedural().layout(),
                    queryVector, options, nowMs, MemoryType.PROCEDURAL, 0L));
        }
        return results;
    }

    // ══════════════════════════════════════════════════════════════
    // SCORING HELPERS — return lists (for parallel composition)
    // ══════════════════════════════════════════════════════════════

    private List<CognitiveResult> scoreStoreToList(MemorySegment segment, int recordCount,
                                                     CognitiveRecordLayout layout, float[] queryVector,
                                                     RecallOptions options, long nowMs, MemoryType type,
                                                     long baseOffset) {
        List<ScoredRecord> scored = CognitiveScorer.score(
                segment, recordCount, layout, queryVector, options, nowMs, baseOffset,
                calibrationMins, calibrationScales);

        List<CognitiveResult> results = new ArrayList<>(scored.size());
        for (ScoredRecord sr : scored) {
            // P8: Header already captured during scoring — no off-heap re-read
            results.add(headerToResult(sr, sr.header(), type));
        }
        return results;
    }

    private List<CognitiveResult> scoreHeaderSlabToList(MemorySegment headerSlab, int recordCount,
                                                          CognitiveRecordLayout layout, float[] queryVector,
                                                          RecallOptions options, long nowMs,
                                                          long dataOffset) {
        long queryTagMask = options.synapticTagMask();
        byte minValence = options.minValence();
        byte maxValence = options.maxValence();
        float tagRelevanceBoost = options.tagRelevanceBoost();

        List<CognitiveResult> results = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            long offset = dataOffset + (long) i * layout.stride();
            CognitiveHeader header = layout.readHeader(headerSlab, offset);

            byte flags = header.flags();
            if (SynapticHeaderConstants.isTombstoned(flags)) continue;

            // Phase 2: Synaptic tag gating (was missing for semantic tier)
            if (queryTagMask != 0) {
                if ((header.synapticTags() & queryTagMask) == 0) continue; // zero overlap → skip
            }

            // Phase 3: Valence filter (was missing for semantic tier)
            byte valence = header.valence();
            if (valence < minValence || valence > maxValence) continue;

            float importance = header.importance();
            if (importance < options.minImportance()) continue;

            long timestamp = header.timestampMs();
            int agentRecallCount = header.agentRecallCount();
            int rawBucket = DecayStrategy.ageToBucket(timestamp, nowMs);
            int adjusted = DecayStrategy.adjustForReconsolidation(rawBucket, agentRecallCount);
            float decay = DecayStrategy.decay(adjusted);

            // Score with weighted tag relevance boost (consistent with CognitiveScorer)
            float baseScore = options.beta() * importance * decay;
            float tagOverlap = SynapticTagEncoder.overlapRatio(header.synapticTags(), queryTagMask);
            float score = baseScore * (1.0f + tagOverlap * tagRelevanceBoost);

            results.add(headerToResult(new ScoredRecord(offset, score, i, header), header, MemoryType.SEMANTIC));
        }
        return results;
    }

    private CognitiveResult headerToResult(ScoredRecord sr, CognitiveHeader header, MemoryType type) {
        String id = index.findIdByOffset(type, sr.offset());  // O(1) via reverse index
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

        // ── ScoreBreakdown: re-derive components from header ──
        // Uses the same formula as CognitiveScorer Phase 6.
        // Note: these are approximations — the scorer's strictness/arousal/storageBoost
        // values are folded into the fused score. We capture what we can from the header.
        float importanceDecay = header.importance() * ltpDecay;
        // Breakdown: individual multipliers default to 1.0 (no effect)
        // habituationPenalty and graphBoost are applied post-scorer in the pipeline
        // and updated in-place on CognitiveResult — we record 1.0 here and
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

        return new CognitiveResult(
                id != null ? id : "unknown-" + sr.index(),
                text, sr.score(), header.importance(), ageDays,
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



    // ══════════════════════════════════════════════════════════════
    // WAL REPLAY — Point-in-Time Recall
    // ══════════════════════════════════════════════════════════════

    /**
     * Performs recall against a reconstructed point-in-time memory state.
     *
     * <p>Replays WAL events up to the target timestamp, builds an ephemeral
     * off-heap segment, runs a simplified linear scan, and disposes all
     * ephemeral state after returning results.</p>
     *
     * <p>Always operates in OBSERVE mode — no mutations to the live state.</p>
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
}

