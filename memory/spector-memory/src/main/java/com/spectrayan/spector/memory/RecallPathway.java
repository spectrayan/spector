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

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ConsolidationRelay;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.sync.ReplaySnapshot;
import com.spectrayan.spector.memory.sync.WalReplayer;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.GraphExpansionStage;
import com.spectrayan.spector.memory.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.pipeline.RecallHistory;
import com.spectrayan.spector.memory.pipeline.RecallListener;
import com.spectrayan.spector.memory.pipeline.gatherer.RecallCandidateGatherer;
import com.spectrayan.spector.memory.pipeline.graph.TemporalFactWeavingStage;
import com.spectrayan.spector.memory.pipeline.pruning.PartitionPruner;
import com.spectrayan.spector.memory.pipeline.reranker.MmrReranker;
import com.spectrayan.spector.memory.pipeline.scorer.SalienceAndHabituationScorer;
import com.spectrayan.spector.memory.recall.relay.AssociativeGraphRelay;
import com.spectrayan.spector.memory.recall.relay.CognitiveRerankRelay;
import com.spectrayan.spector.memory.recall.relay.CorticalTierScanRelay;
import com.spectrayan.spector.memory.recall.relay.LexicalFusionRelay;
import com.spectrayan.spector.memory.recall.relay.MmrDiversityRelay;
import com.spectrayan.spector.memory.recall.relay.NeuromodulatoryScoringRelay;
import com.spectrayan.spector.memory.recall.relay.ProspectiveReminderRelay;
import com.spectrayan.spector.memory.recall.relay.QueryTransductionRelay;
import com.spectrayan.spector.memory.recall.relay.RecallGates;
import com.spectrayan.spector.memory.recall.relay.RecallPathwayFactory;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;
import com.spectrayan.spector.memory.recall.relay.RrfRescoreRelay;
import com.spectrayan.spector.memory.recall.relay.SortAndTruncateRelay;
import com.spectrayan.spector.memory.recall.relay.TemperatureSoftmaxRelay;
import com.spectrayan.spector.memory.synapse.CognitiveScorer;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestrates memory recall using the pathway/relay architecture.
 */
public final class RecallPathway {

    private static final Logger log = LoggerFactory.getLogger(RecallPathway.class);
    private static final int RETRIEVAL_MODE_CACHE_MAX = 1024;
    private static final int SATIATION_CACHE_SIZE = 5000;

    private final CognitivePathway<RecallSignal> pathway;
    private final QueryTransductionRelay transductionRelay;

    private final EmbeddingProvider embeddingProvider;
    private final MemoryWal wal;
    private final CoActivationRecordMemory coActivationTracker;
    private final HabituationPenalty habituationPenalty;
    private final RecallHistory recallHistory;
    private final MemoryIndex index;
    private final PartitionRegistry partitionRegistry;
    private final float[] calibrationMins;
    private final float[] calibrationScales;
    private final SalienceAndHabituationScorer salienceScorer;

    private final List<RecallListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, RetrievalMode> recentRetrievalModes = new ConcurrentHashMap<>();

    private volatile RecallOptions lastRecallOptions;

    private RecallPathway(final Builder builder, final RecallHistory recallHistory, final MmrReranker mmrReranker) {
        this.embeddingProvider = builder.embeddingProvider;
        this.wal = builder.wal;
        this.coActivationTracker = builder.bio != null ? builder.bio.coActivationTracker() : null;
        this.habituationPenalty = builder.bio.habituationPenalty();
        this.recallHistory = recallHistory;
        this.index = builder.index;
        this.partitionRegistry = builder.partitionManager;
        this.calibrationMins = builder.cortex.quantizer().mins();
        this.calibrationScales = builder.cortex.quantizer().scales();

        final MemoryObservationHook hook = builder.hook != null ? builder.hook : MemoryObservationHook.NOOP;
        final GraphScoringPolicy gsp =
                builder.graphScoringPolicy != null ? builder.graphScoringPolicy
                        : GraphScoringPolicy.DEFAULT;

        this.transductionRelay = new QueryTransductionRelay(builder.embeddingProvider);

        this.salienceScorer = new SalienceAndHabituationScorer(
                builder.bio.suppressionSet(), builder.bio.habituationPenalty(), hook);

        final SemanticRecallStrategy semanticStrategy = builder.semanticIndex != null
                ? new SemanticRecallStrategy(builder.semanticIndex, builder.partitionManager, builder.index) : null;

        final ProspectiveReminderRelay prospectiveRelay = new ProspectiveReminderRelay(
                salienceScorer, builder.bio.prospectiveScheduler());

        final CorticalTierScanRelay vectorSearchRelay = new CorticalTierScanRelay(
                builder.partitionManager, PartitionPruner.defaultPruner(),
                semanticStrategy, this::scoreStoreToList);

        final NeuromodulatoryScoringRelay scoringRelay = new NeuromodulatoryScoringRelay(
                salienceScorer, builder.bio.coActivationTracker(), gsp);

        final GraphExpansionStage graphExpansionStage = new GraphExpansionStage(
                builder.graphs.hebbianGraph(), builder.graphs.temporalChain(),
                builder.graphs.entityDirectory(), builder.graphs.hyperEntityGraph(), builder.graphs.entityExtractor(),
                gsp, builder.index, builder.partitionManager,
                this.calibrationMins, this.calibrationScales);

        final TemporalFactWeavingStage temporalFactWeavingStage = new TemporalFactWeavingStage(
                builder.graphs.temporalKnowledgeGraph(), builder.graphs.entityDirectory(),
                builder.graphs.entityExtractor(), builder.index);

        final AssociativeGraphRelay graphExpansionRelay = new AssociativeGraphRelay(
                graphExpansionStage, temporalFactWeavingStage);

        final RecallCandidateGatherer candidateGatherer = new RecallCandidateGatherer(
                builder.index, builder.retrieval.bm25Index());

        final LexicalFusionRelay bm25SearchRelay = new LexicalFusionRelay(
                builder.retrieval.bm25Index(), builder.retrieval.memorySpladeIndex(),
                builder.sparseEmbeddingProvider, candidateGatherer, builder.partitionManager);

        final RrfRescoreRelay rrfRescoreRelay = new RrfRescoreRelay(
                salienceScorer, builder.bio.coActivationTracker(), gsp);

        final SortAndTruncateRelay sortAndTruncateRelay = new SortAndTruncateRelay(
                builder.bio.suppressionSet());

        final CognitiveRerankRelay cognitiveRerankRelay = new CognitiveRerankRelay(
                builder.retrieval.colbertReranker());

        final MmrDiversityRelay mmrDiversityRelay = new MmrDiversityRelay(mmrReranker);

        final TemperatureSoftmaxRelay temperatureSoftmaxRelay = new TemperatureSoftmaxRelay(
                builder.bio.surpriseDetector(), builder.partitionManager,
                this.calibrationMins, this.calibrationScales);

        final ConsolidationRelay<RecallSignal> consolidationRelay = new ConsolidationRelay<>(
                RelayNames.CONSOLIDATION, s -> {});

        this.pathway = RecallPathwayFactory.create(
                builder.interceptor,
                transductionRelay, prospectiveRelay, vectorSearchRelay, scoringRelay,
                graphExpansionRelay, bm25SearchRelay, rrfRescoreRelay, sortAndTruncateRelay,
                cognitiveRerankRelay, mmrDiversityRelay, temperatureSoftmaxRelay, consolidationRelay);
    }

    /**
     * Executes recall for a text query.
     *
     * @param queryText the query text
     * @param options   the options
     * @return the list of results
     */
    public List<CognitiveResult> recall(final String queryText, final RecallOptions options) {
        if (queryText == null) {
            throw new IllegalArgumentException("queryText cannot be null");
        }
        final RecallOptions opts = options == null ? RecallOptions.DEFAULT : options;
        
        if (opts.recallMode() == RecallMode.REPLAY) {
            return replayRecall(queryText, opts);
        }
        if (opts.scoringMode() == ScoringMode.ASSOCIATIVE && recallHistory != null) {
            return recallAssociative(queryText, opts);
        }

        this.lastRecallOptions = opts;

        final RecallSignal signal = RecallSignal.forTextQuery(queryText, opts);
        
        // Execute pathway
        pathway.conduct(signal);
        
        final List<CognitiveResult> allResults = new ArrayList<>(signal.candidates());
        
        // Post-recall listeners
        if (opts.recallMode() == RecallMode.LEARN && !listeners.isEmpty()) {
            final List<CognitiveResult> finalResults = List.copyOf(allResults);
            for (final RecallListener listener : listeners) {
                ConcurrentTasks.fireAndForget(() -> listener.onRecallComplete(finalResults));
            }
        }
        
        // Session bookkeeping
        applySessionBookkeeping(allResults, opts);
        
        // Write ordinal
        writeProfileOrdinalToResults(allResults, opts);
        
        // Record history
        if (recallHistory != null && opts.recallMode() == RecallMode.LEARN) {
            for (final CognitiveResult r : allResults) {
                if (r.synapticTags() != null && r.synapticTags().length > 0) {
                    recallHistory.record(r.synapticTags());
                }
            }
        }
        
        return allResults;
    }

    /**
     * Executes recall for a vector query.
     *
     * @param queryVector the vector query
     * @param options     the options
     * @return the list of results
     */
    public List<CognitiveResult> recall(final float[] queryVector, final RecallOptions options) {
        if (queryVector == null) {
            throw new IllegalArgumentException("queryVector cannot be null");
        }
        final RecallOptions opts = options == null ? RecallOptions.DEFAULT : options;

        this.lastRecallOptions = opts;

        final RecallSignal signal = RecallSignal.forVectorQuery(queryVector, opts);
        
        // 6. Execute pathway
        pathway.conduct(signal);
        
        final List<CognitiveResult> allResults = new ArrayList<>(signal.candidates());
        
        // Post-recall listeners
        if (opts.recallMode() == RecallMode.LEARN && !listeners.isEmpty()) {
            final List<CognitiveResult> finalResults = List.copyOf(allResults);
            for (final RecallListener listener : listeners) {
                ConcurrentTasks.fireAndForget(() -> listener.onRecallComplete(finalResults));
            }
        }
        
        // Session bookkeeping
        applySessionBookkeeping(allResults, opts);
        
        // Write ordinal
        writeProfileOrdinalToResults(allResults, opts);
        
        // Record history
        if (recallHistory != null && opts.recallMode() == RecallMode.LEARN) {
            for (final CognitiveResult r : allResults) {
                if (r.synapticTags() != null && r.synapticTags().length > 0) {
                    recallHistory.record(r.synapticTags());
                }
            }
        }
        
        return allResults;
    }

    private List<CognitiveResult> replayRecall(final String queryText, final RecallOptions options) {
        if (options.replayTimestamp() == null) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_NULL,
                    "replayTimestamp is required for RecallMode.REPLAY");
        }

        log.info("REPLAY recall: query='{}', target={}, maxEvents={}",
                queryText, options.replayTimestamp(), options.maxReplayEvents());

        final float[] queryVector = embeddingProvider.embed(queryText).vector();
        final int quantizedVecBytes = queryVector.length; // INT8 = 1 byte per dimension
        final long nowMs = options.replayTimestamp().toEpochMilli();

        try (final ReplaySnapshot snapshot = WalReplayer.replay(
                wal, options.replayTimestamp(), options.maxReplayEvents(), quantizedVecBytes)) {

            if (snapshot.memoryCount() == 0) {
                log.info("REPLAY recall: no memories at target timestamp {}", options.replayTimestamp());
                return List.of();
            }

            final List<CognitiveResult> results = new ArrayList<>();
            final CognitiveRecordLayout layout = new CognitiveRecordLayout(quantizedVecBytes);

            for (final String memId : snapshot.index().allIds()) {
                final var loc = snapshot.index().locate(memId);
                if (loc == null) continue;

                final long offset = loc.offset();
                final MemorySegment seg = snapshot.arena().allocate(0);

                try {
                    final String text = snapshot.index().text(memId);
                    final MemorySource source = snapshot.index().source(memId);
                    final String[] memTags = snapshot.index().tags(memId);

                    final float importance = 0.5f;
                    final byte valence = 0;
                    final float ageDays = (float) ((nowMs - layout.readTimestamp(seg, offset))
                            / (double) (24 * 60 * 60 * 1000));

                    final java.util.Map<String, String> rMeta = snapshot.index().metadata(memId);
                    final SourceModality rModality = rMeta != null
                            ? SourceModality.fromName(rMeta.get(SourceModality.METADATA_KEY))
                            : SourceModality.TEXT;
                    results.add(new CognitiveResult(
                            memId, text, importance, importance,
                            Math.max(0, ageDays),
                            (short) 0, valence, MemoryType.SEMANTIC, source,
                            memTags, 1.0f, 1.0f, CognitiveResult.RetrievalMode.STANDARD, null, null,
                            rModality, rMeta));

                } catch (final RuntimeException e) {
                    log.debug("REPLAY: skipping memory '{}': {}", memId, e.getMessage());
                }
            }

            results.sort(Comparator.comparing(CognitiveResult::score).reversed());
            if (results.size() > options.topK()) {
                return new ArrayList<>(results.subList(0, options.topK()));
            }

            log.info("REPLAY recall: returned {} results from {} reconstructed memories at {}",
                    results.size(), snapshot.memoryCount(), options.replayTimestamp());

            return results;
        }
    }

    private List<CognitiveResult> recallAssociative(final String queryText, final RecallOptions options) {
        final Map<String, Float> contextTags = recallHistory.weightedRecentTags(20, 0.85f);

        if (contextTags.isEmpty()) {
            log.debug("Associative recall: cold start (no history), falling back to COGNITIVE");
            final RecallOptions fallback = RecallOptions.builder()
                    .topK(options.topK())
                    .profile(options.profile())
                    .scoringMode(ScoringMode.COGNITIVE)
                    .recallMode(options.recallMode())
                    .build();
            return recall(queryText, fallback);
        }

        log.debug("Associative recall: {} context tags from history", contextTags.size());

        final Map<String, Float> predictedTags = new LinkedHashMap<>();
        if (coActivationTracker != null) {
            for (final Map.Entry<String, Float> ctxEntry : contextTags.entrySet()) {
                final String ctxTag = ctxEntry.getKey();
                final float recencyWeight = ctxEntry.getValue();
                final List<String> associated = coActivationTracker.getAssociatedTags(ctxTag, 5);
                for (final String predTag : associated) {
                    predictedTags.merge(predTag, recencyWeight, Float::sum);
                }
            }
        }

        final List<String> topPredicted = predictedTags.entrySet().stream()
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        final RecallOptions.Builder cogBuilder = RecallOptions.builder()
                .topK(options.topK() * 2)
                .profile(options.profile())
                .scoringMode(ScoringMode.COGNITIVE)
                .recallMode(options.recallMode());

        if (!topPredicted.isEmpty()) {
            cogBuilder.synapticFilter(topPredicted.toArray(new String[0]));
        }

        final List<CognitiveResult> associativeResults = new ArrayList<>(recall(queryText, cogBuilder.build()));

        if (coActivationTracker != null && !contextTags.isEmpty()) {
            final List<String> contextTagList = new ArrayList<>(contextTags.keySet());
            for (int i = 0; i < associativeResults.size(); i++) {
                final CognitiveResult r = associativeResults.get(i);
                if (r.synapticTags() == null || r.synapticTags().length == 0) continue;

                final float predictive = coActivationTracker.getPredictiveStrength(
                        contextTagList, r.synapticTags());
                if (predictive > 0) {
                    final float boosted = r.score() * (1.0f + predictive * 0.5f);
                    associativeResults.set(i, new CognitiveResult(
                            r.id(), r.text(), boosted, r.importance(), r.ageDays(),
                            r.agentRecallCount(), r.valence(), r.memoryType(), r.source(),
                            r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay(),
                            r.retrievalMode(), r.breakdown(), r.trace(), r.sourceModality(), r.metadata()));
                }
            }
        }

        associativeResults.sort(Comparator.comparing(CognitiveResult::score).reversed());
        List<CognitiveResult> finalResults = associativeResults;
        if (finalResults.size() > options.topK()) {
            finalResults = new ArrayList<>(finalResults.subList(0, options.topK()));
        }

        for (final CognitiveResult r : finalResults) {
            if (r.synapticTags() != null && r.synapticTags().length > 0) {
                recallHistory.record(r.synapticTags());
            }
        }
        return finalResults;
    }

    private void applySessionBookkeeping(final List<CognitiveResult> allResults, final RecallOptions opts) {
        if (opts.recallMode() == RecallMode.LEARN) {
            final long nowMs = System.currentTimeMillis();
            for (final CognitiveResult r : allResults) {
                habituationPenalty.recordRecall(r.id(), nowMs);
            }

            if (recentRetrievalModes.size() > RETRIEVAL_MODE_CACHE_MAX) {
                final int toRemove = RETRIEVAL_MODE_CACHE_MAX / 4;
                final var iter = recentRetrievalModes.keySet().iterator();
                for (int i = 0; i < toRemove && iter.hasNext(); i++) {
                    iter.next();
                    iter.remove();
                }
            }
            for (final CognitiveResult r : allResults) {
                if (r.id() != null) {
                    recentRetrievalModes.put(r.id(), r.retrievalMode());
                }
            }

            for (final CognitiveResult r : allResults) {
                if (r.id() != null) {
                    salienceScorer.satiationCache().put(r.id(), nowMs);
                }
            }
            while (salienceScorer.satiationCache().size() > SATIATION_CACHE_SIZE) {
                String oldest = null;
                long oldestTs = Long.MAX_VALUE;
                for (final var e : salienceScorer.satiationCache().entrySet()) {
                    if (e.getValue() < oldestTs) {
                        oldestTs = e.getValue();
                        oldest = e.getKey();
                    }
                }
                if (oldest != null) {
                    salienceScorer.satiationCache().remove(oldest, oldestTs);
                } else {
                    break;
                }
            }
        }
    }

    private void writeProfileOrdinalToResults(final List<CognitiveResult> results, final RecallOptions options) {
        final CognitiveProfile profile = options.profile();
        if (profile == null || results.isEmpty()) return;

        final byte profileOrdinal = (byte) profile.ordinal();
        for (final CognitiveResult result : results) {
            if (result.id() == null) continue;
            try {
                final var loc = index.locate(result.id());
                if (loc == null) continue;
                final MemorySegment segment = partitionRegistry.routerFor(loc.colocatedPartition())
                        .segmentFor(loc.type());
                if (segment != null) {
                    segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE,
                            loc.offset() + SynapticHeaderConstants.OFFSET_LAST_RECALL_PROFILE,
                            profileOrdinal);
                }
            } catch (final RuntimeException e) {
                log.trace("Failed to write profile ordinal for '{}': {}", result.id(), e.getMessage());
            }
        }
    }

    /**
     * Adds a post-recall listener.
     *
     * @param listener the listener
     */
    public void addListener(final RecallListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private List<CognitiveResult> scoreStoreToList(final MemorySegment segment, final int recordCount,
                                                   final CognitiveRecordLayout layout, final float[] queryVector,
                                                   final RecallOptions options, final long nowMs, final MemoryType type,
                                                   final long baseOffset, final int partitionSeq) {
        final List<ScoredRecord> scored = CognitiveScorer.score(
                segment, recordCount, layout, queryVector, options, nowMs, baseOffset,
                calibrationMins, calibrationScales);

        final List<CognitiveResult> results = new ArrayList<>(scored.size());
        for (final ScoredRecord sr : scored) {
            results.add(headerToResult(sr, sr.header(), type, partitionSeq));
        }
        return results;
    }

    private CognitiveResult headerToResult(final ScoredRecord sr, final CognitiveHeader header, final MemoryType type,
                                           final int partitionSeq) {
        final String id = index.findIdByOffset(partitionSeq, type, sr.offset());
        final String text = id != null ? index.text(id) : "";
        final String[] tags = id != null ? index.tags(id) : new String[0];

        final long nowMs = System.currentTimeMillis();
        final float ageDays = (nowMs - header.timestampMs()) / (1000f * 60f * 60f * 24f);

        final int rawBucket = DecayStrategy.ageToBucket(header.timestampMs(), nowMs);
        final int adjusted = DecayStrategy.adjustForReconsolidation(rawBucket, header.agentRecallCount());
        final float rawDecay = DecayStrategy.decay(rawBucket);
        final float ltpDecay = DecayStrategy.decay(adjusted);

        RetrievalMode mode;
        if (sr.lateral()) {
            mode = RetrievalMode.LATERAL;
        } else if (lastRecallOptions != null && lastRecallOptions.hyperfocusMask() != 0) {
            mode = RetrievalMode.HYPERFOCUS;
        } else {
            mode = RetrievalMode.STANDARD;
        }

        final float importanceDecay = header.importance() * ltpDecay;
        final ScoreBreakdown breakdown = new ScoreBreakdown(
                Math.max(0, sr.score() > 0 ? sr.score() : 0),
                importanceDecay,
                1.0f,
                1.0f,
                1.0f,
                1.0f,
                sr.score()
        );

        final SourceModality modality = SourceModality.fromOrdinal(
                SynapticHeaderConstants.sourceModalityOrdinal(header.flags()));
        Map<String, String> metadata = id != null ? index.metadata(id) : Map.of();

        String resultText = text;
        if (metadata != null && metadata.containsKey("parent_chunk_id")) {
            final String parentId = metadata.get("parent_chunk_id");
            final String parentText = index.text(parentId);
            if (parentText != null && !parentText.isBlank()) {
                resultText = parentText;
                final var mutableMeta = new HashMap<>(metadata);
                mutableMeta.put("child_text", text);
                metadata = Map.copyOf(mutableMeta);
            }
        }

        final MemorySource source = id != null ? index.source(id) : MemorySource.OBSERVED;

        return new CognitiveResult(
                id != null ? id : "unknown-" + sr.index(),
                resultText, sr.score(), header.importance(), ageDays,
                header.agentRecallCount(), header.valence(), type, source, tags,
                rawDecay, ltpDecay, mode, breakdown, null, modality, metadata);
    }

    /**
     * Builder for {@link RecallPathway}.
     */
    public static final class Builder {
        private EmbeddingProvider embeddingProvider;
        private CognitiveCortexBuilder.CortexFoundation cortex;
        private BiologicalSubsystemsBuilder.BiologicalSubsystems bio;
        private CognitiveGraphBuilder.CognitiveGraphs graphs;
        private RetrievalIndexBuilder.RetrievalIndices retrieval;
        private MemoryIndex index;
        private PartitionManager partitionManager;
        private MemoryWal wal;
        private GraphScoringPolicy graphScoringPolicy;
        private SparseEmbeddingProvider sparseEmbeddingProvider;
        private MemoryObservationHook hook;
        private com.spectrayan.spector.index.VectorIndex semanticIndex;
        private java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<RecallSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<RecallSignal>> interceptor;

        public Builder() {}

        public Builder interceptor(
                final java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<RecallSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<RecallSignal>> interceptor) {
            this.interceptor = interceptor;
            return this;
        }

        public Builder embeddingProvider(final EmbeddingProvider embeddingProvider) {
            this.embeddingProvider = embeddingProvider;
            return this;
        }

        public Builder cortex(final CognitiveCortexBuilder.CortexFoundation cortex) {
            this.cortex = cortex;
            return this;
        }

        public Builder bio(final BiologicalSubsystemsBuilder.BiologicalSubsystems bio) {
            this.bio = bio;
            return this;
        }

        public Builder graphs(final CognitiveGraphBuilder.CognitiveGraphs graphs) {
            this.graphs = graphs;
            return this;
        }

        public Builder retrieval(final RetrievalIndexBuilder.RetrievalIndices retrieval) {
            this.retrieval = retrieval;
            return this;
        }

        public Builder index(final MemoryIndex index) {
            this.index = index;
            return this;
        }

        public Builder partitionManager(final PartitionManager partitionManager) {
            this.partitionManager = partitionManager;
            return this;
        }

        public Builder wal(final MemoryWal wal) {
            this.wal = wal;
            return this;
        }

        public Builder graphScoringPolicy(
                final GraphScoringPolicy graphScoringPolicy) {
            this.graphScoringPolicy = graphScoringPolicy;
            return this;
        }

        public Builder sparseEmbeddingProvider(
                final SparseEmbeddingProvider sparseEmbeddingProvider) {
            this.sparseEmbeddingProvider = sparseEmbeddingProvider;
            return this;
        }

        public Builder hook(final MemoryObservationHook hook) {
            this.hook = hook;
            return this;
        }

        public Builder semanticIndex(final com.spectrayan.spector.index.VectorIndex semanticIndex) {
            this.semanticIndex = semanticIndex;
            return this;
        }

        /**
         * Builds the {@link RecallPathway}.
         *
         * @return the recall pathway
         */
        public RecallPathway build() {
            final RecallHistory recallHistory = new RecallHistory();
            final MmrReranker mmrReranker = new MmrReranker(
                    index, partitionManager, cortex.quantizer().mins(), cortex.quantizer().scales());
            return new RecallPathway(this, recallHistory, mmrReranker);
        }
    }
}
