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

import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.cortex.TextBlobMemory;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;

import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.memory.model.SourceModality;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.WorkingMemory;
import com.spectrayan.spector.memory.neuromod.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.neuromod.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.error.SpectorMemoryTierFullException;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import static com.spectrayan.spector.commons.observation.MemoryObservationHook.*;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cognitive memory implementation of {@link IngestionTarget}.
 *
 * <p>Receives pre-embedded chunks from the unified {@link com.spectrayan.spector.ingestion.IngestionPipeline}
 * and performs the cognitive processing pipeline (steps 2 - 9):</p>
 *
 * <pre>
 *   Step  1b: Auto-extract synaptic tags (via pluggable {@link TagExtractor})
 *   Step  2: Encode synaptic tags  ->  64-bit Bloom filter
 *   Step  3: Compute surprise  ->  auto-set importance (Dopamine engine)
 *   Step 3b: ICNU fusion  --  blend LLM hints (I/C/U) with native novelty (N)
 *   Step  4: Flashbulb check  --  extreme surprise gets full fidelity
 *   Step  5: Quantize vector to INT8 via calibrated ScalarQuantizer
 *   Step  6: Build cognitive header
 *   Step  7: Route to tier store and write
 *   Step 7b: Add to HNSW index (SEMANTIC type only)
 *   Step  8: Register in ID index
 *   Step  9: WAL append
 *   Step 9b: Hebbian edge strengthening (co-ingestion within session)
 *   Step 9c: Temporal chain linking (session-local sequence)
 *   Step 9d: Entity extraction and graph population
 * </pre>
 *
 * <h3>Two Entry Points</h3>
 * <ul>
 *   <li>{@link #ingest(String, String, float[])}  --  from unified pipeline (bulk, auto-extracts tags)</li>
 *   <li>{@link #ingestCognitive}  --  from {@code SpectorMemory.remember()} (full cognitive params)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Stateless except for the subsystems it references (all thread-safe).
 * Multiple Virtual Threads can call {@link #ingest} concurrently.</p>
 *
 * @deprecated Superceded by {@link com.spectrayan.spector.memory.pathway.remember.RememberPathway} as part of the
 *             Cognitive Pathway Engine redesign (#561). Will be removed in a future major release.
 */
@Deprecated(since = "0.2.0-alpha", forRemoval = false)
public final class CognitiveIngestionTarget implements IngestionTarget {

    private static final Logger log = LoggerFactory.getLogger(CognitiveIngestionTarget.class);

    private final ScalarQuantizer quantizer;
    private final SurpriseDetector surpriseDetector;
    private final FlashbulbPolicy flashbulbPolicy;
    private volatile CognitiveMemoryRouter cognitiveRouter;  // volatile: swapped on partition roll
    private final MemoryIndex index;
    private final MemoryWal wal;
    private final WorkingMemory workingStore;  // nullable
    private final IcnuWeights icnuWeights;
    private final ImportanceProvider importanceProvider;
    private final VectorIndex semanticIndex;  // nullable  --  HNSW for semantic recall
    private final TagExtractor tagExtractor;
    private final boolean normalizeAtIngest;

    //  Graph components (all nullable  --  graceful degradation) 
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final EntityExtractor entityExtractor;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph temporalKnowledgeGraph;

    //  BM25 Text Search (nullable  --  graceful degradation) 
    private final MemoryBM25Index bm25Index;
    private final TextBlobMemory textDataStore;
    private final int activePartitionIndex;

    //  SPLADE Sparse Search (nullable  --  graceful degradation) 
    private final MemorySpladeIndex spladeIndex;
    private final SparseEmbeddingProvider spladeProvider;
    private final DataEncryptor encryptor;

    //  Salience Profile (NEUTRAL in OSS mode) 
    private volatile SalienceProfile salienceProfile;

    //  Soul version stamp (updated alongside salience profile) 
    private volatile short currentSoulVersion;

    //  Session tracking for Hebbian co-ingestion and temporal chains 
    private final AtomicInteger lastIngestedMemoryIdx = new AtomicInteger(-1);
    private final com.spectrayan.spector.memory.session.SessionRegistry sessionRegistry;

    //  Post-ingest index synchronization stage 
    private final PostIngestSync postIngestSync;

    //  Asynchronous entity extraction queue (non-blocking ingestion)
    private final AsyncEntityExtractionQueue asyncEntityExtractionQueue;

    //  Partition rolling callback (nullable) 
    private volatile Runnable partitionRollCallback;

    //  Observability hook (defaults to zero-overhead NOOP)
    private volatile MemoryObservationHook hook = MemoryObservationHook.NOOP;

    public CognitiveIngestionTarget(ScalarQuantizer quantizer,
                                     SurpriseDetector surpriseDetector,
                                     FlashbulbPolicy flashbulbPolicy,
                                     CognitiveMemoryRouter cognitiveRouter,
                                     MemoryIndex index,
                                     MemoryWal wal,
                                     WorkingMemory workingStore,
                                     IcnuWeights icnuWeights,
                                     VectorIndex semanticIndex,
                                     TagExtractor tagExtractor,
                                     boolean normalizeAtIngest,
                                     HebbianGraphBase hebbianGraph,
                                     TemporalChainMemory temporalChain,
                                     EntityExtractor entityExtractor,
                                     EntityDirectory entityDirectory,
                                     HyperEntityGraphMemory hyperEntityGraph,
                                     com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph temporalKnowledgeGraph,
                                     MemoryBM25Index bm25Index,
                                     TextBlobMemory textDataStore,
                                     int activePartitionIndex,
                                     MemorySpladeIndex spladeIndex,
                                     SparseEmbeddingProvider spladeProvider,
                                     ImportanceProvider importanceProvider,
                                     com.spectrayan.spector.memory.session.SessionRegistry sessionRegistry) {
        this(quantizer, surpriseDetector, flashbulbPolicy, cognitiveRouter,
                index, wal, workingStore, icnuWeights, semanticIndex,
                tagExtractor, normalizeAtIngest,
                hebbianGraph, temporalChain, entityExtractor,
                entityDirectory, hyperEntityGraph, temporalKnowledgeGraph,
                bm25Index, textDataStore, activePartitionIndex,
                spladeIndex, spladeProvider, DataEncryptor.NOOP, importanceProvider, sessionRegistry,
                1, 1000);
    }

    public CognitiveIngestionTarget(ScalarQuantizer quantizer,
                                     SurpriseDetector surpriseDetector,
                                     FlashbulbPolicy flashbulbPolicy,
                                     CognitiveMemoryRouter cognitiveRouter,
                                     MemoryIndex index,
                                     MemoryWal wal,
                                     WorkingMemory workingStore,
                                     IcnuWeights icnuWeights,
                                     VectorIndex semanticIndex,
                                     TagExtractor tagExtractor,
                                     boolean normalizeAtIngest,
                                     HebbianGraphBase hebbianGraph,
                                     TemporalChainMemory temporalChain,
                                     EntityExtractor entityExtractor,
                                     EntityDirectory entityDirectory,
                                     HyperEntityGraphMemory hyperEntityGraph,
                                     com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph temporalKnowledgeGraph,
                                     MemoryBM25Index bm25Index,
                                     TextBlobMemory textDataStore,
                                     int activePartitionIndex,
                                     MemorySpladeIndex spladeIndex,
                                     SparseEmbeddingProvider spladeProvider,
                                     DataEncryptor encryptor,
                                     ImportanceProvider importanceProvider,
                                     com.spectrayan.spector.memory.session.SessionRegistry sessionRegistry) {
        this(quantizer, surpriseDetector, flashbulbPolicy, cognitiveRouter,
                index, wal, workingStore, icnuWeights, semanticIndex,
                tagExtractor, normalizeAtIngest,
                hebbianGraph, temporalChain, entityExtractor,
                entityDirectory, hyperEntityGraph, temporalKnowledgeGraph,
                bm25Index, textDataStore, activePartitionIndex,
                spladeIndex, spladeProvider, encryptor, importanceProvider, sessionRegistry,
                1, 1000);
    }

    /**
     * Full constructor with data encryption and async entity extraction configuration support.
     */
    public CognitiveIngestionTarget(ScalarQuantizer quantizer,
                                     SurpriseDetector surpriseDetector,
                                     FlashbulbPolicy flashbulbPolicy,
                                     CognitiveMemoryRouter cognitiveRouter,
                                     MemoryIndex index,
                                     MemoryWal wal,
                                     WorkingMemory workingStore,
                                     IcnuWeights icnuWeights,
                                     VectorIndex semanticIndex,
                                     TagExtractor tagExtractor,
                                     boolean normalizeAtIngest,
                                     HebbianGraphBase hebbianGraph,
                                     TemporalChainMemory temporalChain,
                                     EntityExtractor entityExtractor,
                                     EntityDirectory entityDirectory,
                                     HyperEntityGraphMemory hyperEntityGraph,
                                     com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph temporalKnowledgeGraph,
                                     MemoryBM25Index bm25Index,
                                     TextBlobMemory textDataStore,
                                     int activePartitionIndex,
                                     MemorySpladeIndex spladeIndex,
                                     SparseEmbeddingProvider spladeProvider,
                                     DataEncryptor encryptor,
                                     ImportanceProvider importanceProvider,
                                     com.spectrayan.spector.memory.session.SessionRegistry sessionRegistry,
                                     int entityExtractionParallelism,
                                     int entityExtractionQueueCapacity) {
        this.quantizer = quantizer;
        this.surpriseDetector = surpriseDetector;
        this.flashbulbPolicy = flashbulbPolicy;
        this.cognitiveRouter = cognitiveRouter;
        this.index = index;
        this.wal = wal;
        this.workingStore = workingStore;
        this.icnuWeights = icnuWeights != null ? icnuWeights : IcnuWeights.DEFAULT;
        this.importanceProvider = importanceProvider != null ? importanceProvider : ImportanceProvider.baseline();
        this.semanticIndex = semanticIndex;
        this.tagExtractor = tagExtractor != null ? tagExtractor : new ContentTagExtractor();
        this.normalizeAtIngest = normalizeAtIngest;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityExtractor = entityExtractor;
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.bm25Index = bm25Index;
        this.textDataStore = textDataStore;
        this.activePartitionIndex = activePartitionIndex;
        this.spladeIndex = spladeIndex;
        this.spladeProvider = spladeProvider;
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        this.sessionRegistry = sessionRegistry;
        this.salienceProfile = SalienceProfile.NEUTRAL;
        this.postIngestSync = new PostIngestSync(
                cognitiveRouter, index, wal, semanticIndex,
                hebbianGraph, temporalChain, entityExtractor, entityDirectory,
                bm25Index, textDataStore, activePartitionIndex,
                spladeIndex, spladeProvider, this.encryptor, hyperEntityGraph, temporalKnowledgeGraph);
        this.asyncEntityExtractionQueue = new AsyncEntityExtractionQueue(
                entityExtractor, this.postIngestSync,
                entityExtractionParallelism, entityExtractionQueueCapacity);
    }

    /**
     * Updates the cognitive memory router after a partition roll.
     * Called by DefaultSpectorMemory.rollPartition() under lock.
     */
    public void updateCognitiveRouter(CognitiveMemoryRouter newRouter) {
        this.cognitiveRouter = newRouter;
        this.postIngestSync.updateCognitiveRouter(newRouter);
    }

    /**
     * Updates the partition-scoped {@code text.dat} store after a roll (#443, D3b).
     * Called by {@code PartitionManager.rollPartition()} under the roll lock.
     */
    public void updateTextDataStore(TextBlobMemory newText) {
        this.postIngestSync.updateTextDataStore(newText);
    }

    /**
     * Updates the active colocated-partition sequence after a roll (#443).
     * Called by {@code PartitionManager.rollPartition()} under the roll lock, and once
     * at wiring time to seed the initial partition seq.
     */
    public void updateActivePartitionSeq(int seq) {
        this.postIngestSync.updateActivePartitionSeq(seq);
    }



    /**
     * Sets the partition roll callback, invoked when a tier store is full.
     */
    public void setPartitionRollCallback(Runnable callback) {
        this.partitionRollCallback = callback;
    }

    /**
     * Injects the observation hook for unified observability.
     */
    public void setObservationHook(MemoryObservationHook hook) {
        this.hook = hook != null ? hook : MemoryObservationHook.NOOP;
        if (this.asyncEntityExtractionQueue != null) {
            this.asyncEntityExtractionQueue.setObservationHook(this.hook);
        }
    }

    public ScalarQuantizer quantizer() {
        return quantizer;
    }

    /**
     * Updates the salience profile for importance modulation.
     *
     * <p>Thread-safe: the field is volatile. Called by the enterprise layer
     * when a user/agent/tenant profile is resolved.</p>
     *
     * @param profile the effective (pre-merged) salience profile
     */
    public void setSalienceProfile(SalienceProfile profile) {
        this.salienceProfile = profile != null ? profile : SalienceProfile.NEUTRAL;
    }

    /**
     * Updates the current soul version for encoding state stamping.
     *
     * <p>Called by the synapse layer when the soul is loaded or updated.
     * The version is stamped into the synaptic header at ingestion time
     * so that stale memories can be detected for re-fusion.</p>
     *
     * @param version the current soul version (monotonically increasing)
     */
    public void setSoulVersion(short version) {
        this.currentSoulVersion = version;
    }

    /** Returns the current soul version (for testing/introspection). */
    public short currentSoulVersion() {
        return currentSoulVersion;
    }

    /** Returns the current salience profile (for testing/introspection). */
    public SalienceProfile salienceProfile() {
        return salienceProfile;
    }

    /** Returns the configured tag extractor (LLM or content-based). */
    public TagExtractor tagExtractor() {
        return tagExtractor;
    }

    /**
     * Legacy constructor  --  defaults normalizeAtIngest to {@code true}, no graph components.
     */
    public CognitiveIngestionTarget(ScalarQuantizer quantizer,
                                     SurpriseDetector surpriseDetector,
                                     FlashbulbPolicy flashbulbPolicy,
                                     CognitiveMemoryRouter cognitiveRouter,
                                     MemoryIndex index,
                                     MemoryWal wal,
                                     WorkingMemory workingStore,
                                     IcnuWeights icnuWeights,
                                     VectorIndex semanticIndex,
                                     TagExtractor tagExtractor,
                                     com.spectrayan.spector.memory.session.SessionRegistry sessionRegistry) {
        this(quantizer, surpriseDetector, flashbulbPolicy, cognitiveRouter,
                index, wal, workingStore, icnuWeights, semanticIndex,
                tagExtractor, true,
                null, null, null, null, null, null,
                null, null, -1,
                null, null, null, null, sessionRegistry);
    }

    // ===============================================================
    // IngestionTarget  --  from unified pipeline (bulk ingestion)
    // ===============================================================

    /**
     * Ingests a pre-embedded chunk using SEMANTIC defaults.
     *
     * <p>Called by the unified IngestionPipeline during bulk file ingestion.
     * Auto-extracts synaptic tags via the configured {@link TagExtractor},
     * uses {@code MemoryType.SEMANTIC} and {@code MemorySource.OBSERVED}.</p>
     */
    @Override
    public void ingest(String id, String text, float[] vector) {
        // Step 1b: Auto-extract synaptic tags + emotional context from content
        long tagStartNs = System.nanoTime();
        TagExtractionResult extraction = hook.observe(TAG_EXTRACTION, java.util.Map.of(TAG_MEMORY_ID, id), () -> tagExtractor.extractWithContext(id, text));
        String[] tags = extraction.tags();
        long tagMs = (System.nanoTime() - tagStartNs) / 1_000_000;

        log.info("[Ingest] '{}'  ->  {} tags in {}ms via {} [{}]{}",
                id.length() > 60 ? "..." + id.substring(id.length() - 57) : id,
                tags.length, tagMs,
                tagExtractor.getClass().getSimpleName(),
                String.join(", ", tags),
                extraction.hasEmotionalContext()
                        ? String.format(" (valence=%d, arousal=%d)", extraction.valence(), Byte.toUnsignedInt(extraction.arousal()))
                        : "");

        // Build IngestionHints from LLM emotional context (if available)
        IngestionHints hints = extraction.hasEmotionalContext()
                ? new IngestionHints(0f, 0f, 0f, extraction.valence(), extraction.arousal())
                : null;

        ingestCognitive(id, text, vector, MemoryType.SEMANTIC,
                tags, MemorySource.OBSERVED, hints);
    }

    // ===============================================================
    // Full cognitive entry point  --  from SpectorMemory.remember()
    // ===============================================================

    /**
     * Full cognitive ingestion with all parameters.
     *
     * <p>Called by {@code SpectorMemory.remember()} with type, tags, source,
     * and optional ICNU hints from LLM assessment.</p>
     *
     * @param id     unique memory identifier
     * @param text   the memory content
     * @param vector pre-computed embedding vector
     * @param type   cognitive memory tier
     * @param tags   synaptic tag strings
     * @param source provenance source
     * @param hints  optional LLM-provided ICNU hints (null = novelty-only)
     */
    public void ingestCognitive(String id, String text, float[] vector,
                                 MemoryType type, String[] tags,
                                 MemorySource source, IngestionHints hints) {
        //  Dedup guard: skip if this ID is already indexed 
        // The MemoryIndex (loaded from disk on startup) tracks all known IDs.
        // Without this check, re-ingesting the same files would:
        //   - Append orphaned records to tier stores (semantic.mem grows)
        //   - Add duplicate nodes to the HNSW index (index.spct grows)
        //   - Append redundant WAL entries
        // The VectorStore.put() already deduplicates, but tier stores do not.
        if (index.locate(id) != null) {
            log.debug("Skipping duplicate memory '{}'  --  already indexed", sanitize(id));
            return;
        }

        // Step 2: Encode synaptic tags (keyed HMAC when encryption is active)
        long synapticTags = encodeTags(tags);

        // Step 1c: L2-normalize vector (required for Parabolic RBF lateral scoring)
        if (normalizeAtIngest) {
            vector = l2Normalize(vector);
        }

        // Step 5 (early): Quantize vector to INT8  --  needed for WM distance scan
        byte[] quantized = quantizer.encode(vector);

        // Step 3: Compute surprise  ->  auto-set importance (Dopamine engine)
        float nearestDist;
        if (workingStore != null && workingStore.count() > 0) {
            nearestDist = workingStore.nearestDistance(
                    vector, quantizer.mins(), quantizer.scales());
        } else {
            nearestDist = computeL2Norm(vector);
        }

        ImportanceContext importanceCtx = new ImportanceContext(
                text, vector, hints, salienceProfile, type,
                nearestDist, surpriseDetector.stats().zScore(nearestDist), false);
        ImportanceResult importanceResult = importanceProvider.score(importanceCtx);
        float importance = importanceResult.importance();

        // Step 4: Flashbulb check
        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, type.ordinal());
        if (importanceResult.isFlashbulb()) {
            flags = (byte) (flags | SynapticHeaderConstants.FLAG_PINNED);
        }

        // Step 6: Build cognitive header (with emotional context and encoding state)
        float l2Norm = computeL2Norm(vector);
        byte rawValence = (hints != null) ? hints.valence() : (byte) 0;
        byte rawArousal = (hints != null) ? hints.effectiveArousal() : (byte) 0;
        // Step 6a: Personality-modulated emotional encoding
        byte valence = salienceProfile.modulateValence(rawValence);
        byte arousal = salienceProfile.modulateArousal(rawArousal);
        // Step 6b: Encoding state stamp — captures cognitive context at formation time
        float surpriseZScore = (float) surpriseDetector.stats().zScore(nearestDist);
        byte encodingProfile = computeEncodingProfile(salienceProfile);
        byte encodingAlpha = computeEncodingAlpha(salienceProfile);
        byte encodingBeta = computeEncodingBeta(salienceProfile);
        CognitiveHeader header = new CognitiveHeader(
                System.currentTimeMillis(), synapticTags, l2Norm, importance,
                0, (short) 0, valence, flags, arousal, 1.0f,
                encodingProfile, encodingAlpha, encodingBeta,
                currentSoulVersion, surpriseZScore, (byte) 0);

        // Step 7: Route to tier store and write (with automatic partition rolling)
        long offset;
        try {
            try {
                offset = cognitiveRouter.write(type, header, quantized);
            } catch (com.spectrayan.spector.memory.error.SpectorPartitionFrozenException e) {
                offset = cognitiveRouter.write(type, header, quantized);
            }
        } catch (SpectorMemoryTierFullException e) {
            if (partitionRollCallback != null) {
                log.info("Tier {} full ({} records)  --  rolling to new partition",
                        type, e.getCapacity());
                partitionRollCallback.run();
                // Retry with the new router (swapped by callback)
                offset = cognitiveRouter.write(type, header, quantized);
            } else {
                throw e;  // No rolling configured  --  propagate
            }
        }

        // Steps 7b-9a: Index synchronization (HNSW, ID index, WAL, BM25, SPLADE)
        var syncParams = new PostIngestSync.SyncParams(
                id, text, vector, quantized, type, tags, source, offset);
        int graphSlot = postIngestSync.syncIndexes(syncParams);

        // Steps 9b + 9c: Hebbian + Temporal linking (co-ingestion within session)
        // #497: use the monotonic graphSlot allocated during syncIndexes, not
        // index.size()-1 which is non-monotonic (shrinks on forget, causing reuse).
        int memoryIdx = graphSlot;
        String tsid = com.spectrayan.spector.commons.concurrent.MemoryScope.sessionId();
        String nsid = com.spectrayan.spector.commons.concurrent.MemoryScope.namespaceId();
        int sessionIntId = sessionRegistry != null ? sessionRegistry.resolve(tsid) : 0;
        int previousIdx = lastIngestedMemoryIdx.getAndSet(memoryIdx);
        postIngestSync.syncGraphEdges(memoryIdx, previousIdx, sessionIntId);

        // Step 9d: Entity extraction and graph population (skip for raw EPISODIC logs)
        if (type != MemoryType.EPISODIC) {
            if (asyncEntityExtractionQueue != null && entityExtractor != null && entityExtractor.isAvailable()) {
                hook.observe(ENTITY_EXTRACTION, java.util.Map.of(TAG_MEMORY_ID, id), () ->
                    asyncEntityExtractionQueue.submit(id, text, memoryIdx, header.timestampMs() / 1000, tsid, nsid)
                );
            } else {
                hook.observe(ENTITY_EXTRACTION, java.util.Map.of(TAG_MEMORY_ID, id), () -> {
                    List<ExtractedEntity> extractedEntities = postIngestSync.syncEntityExtraction(id, text, memoryIdx);
                    postIngestSync.syncTemporalFacts(extractedEntities, memoryIdx, id, header.timestampMs() / 1000);
                });
            }
        }

        log.debug("Ingested '{}' as {} (importance={}, {} tags, graphSlot={}, source={})",
                id, type, importance, tags.length, graphSlot, source);
    }

    // ===============================================================
    // Migration entry point  --  preserves full cognitive state
    // ===============================================================

    /**
     * Migration-aware ingestion that preserves the original {@link CognitiveHeader}.
     *
     * <p>Used during dimension migration to re-ingest memories with new embeddings
     * while preserving all cognitive metadata (importance, recall count, valence,
     * arousal, storage strength, flags, timestamps). Bypasses:
     * <ul>
     *   <li>Surprise detection (step 3)  --  importance already computed</li>
     *   <li>Flashbulb check (step 4)  --  pinned flag already set</li>
     *   <li>ICNU fusion (step 3b)  --  importance already fused</li>
     * </ul>
     *
     * <p>The dedup guard is <em>not</em> bypassed  --  caller must remove
     * the old index entry before calling this method.</p>
     *
     * @param id             unique memory identifier (preserved from source)
     * @param text           the memory content (preserved verbatim)
     * @param vector         <em>new</em> embedding vector (re-embedded with new model)
     * @param type           cognitive memory tier (preserved)
     * @param tags           synaptic tag strings (preserved)
     * @param source         provenance source (preserved)
     * @param preservedHeader the original cognitive header to preserve
     */
    public void ingestCognitiveWithHeader(String id, String text, float[] vector,
                                           MemoryType type, String[] tags,
                                           MemorySource source,
                                           CognitiveHeader preservedHeader) {
        // Dedup guard  --  same as normal ingestion
        if (index.locate(id) != null) {
            log.debug("Migration: skipping duplicate '{}'  --  already indexed", id);
            return;
        }

        // Step 2: Encode synaptic tags (preserve original if no new strings provided)
        long synapticTags = (tags != null && tags.length > 0) ? encodeTags(tags) : preservedHeader.synapticTags();

        // Step 1c: L2-normalize vector
        if (normalizeAtIngest) {
            vector = l2Normalize(vector);
        }

        // Step 5: Quantize vector to INT8
        byte[] quantized = quantizer.encode(vector);

        // Step 6: Build header  --  preserve original fields, recompute vector-derived ones
        float l2Norm = computeL2Norm(vector);
        CognitiveHeader header = new CognitiveHeader(
                preservedHeader.timestampMs(),       // [x] original timestamp
                synapticTags,                        // 🔄 re-encoded (same tags)
                l2Norm,                              // 🔄 from new vector
                preservedHeader.importance(),         // [x] original importance
                preservedHeader.agentRecallCount(),   // [x] original recall count
                (short) 0,                           // 🔄 centroidId recomputed
                preservedHeader.valence(),            // [x] original valence
                preservedHeader.flags(),              // [x] original flags
                preservedHeader.arousal(),            // [x] original arousal
                preservedHeader.storageStrength(),    // [x] original storage strength
                preservedHeader.encodingProfile(),    // [x] original encoding profile
                preservedHeader.encodingAlpha(),      // [x] original encoding alpha
                preservedHeader.encodingBeta(),       // [x] original encoding beta
                preservedHeader.soulVersion(),        // [x] original soul version
                preservedHeader.encodingSurprise(),   // [x] original encoding surprise
                preservedHeader.consolidationFlags()  // [x] original consolidation flags
        );

        // Step 7: Route to tier store and write
        long offset;
        try {
            try {
                offset = cognitiveRouter.write(type, header, quantized);
            } catch (com.spectrayan.spector.memory.error.SpectorPartitionFrozenException e) {
                offset = cognitiveRouter.write(type, header, quantized);
            }
        } catch (SpectorMemoryTierFullException e) {
            if (partitionRollCallback != null) {
                log.info("Migration: tier {} full  --  rolling partition", type);
                partitionRollCallback.run();
                offset = cognitiveRouter.write(type, header, quantized);
            } else {
                throw e;
            }
        }

        // Steps 7b-9a: Index synchronization (HNSW, ID index, WAL, BM25, SPLADE)
        var syncParams = new PostIngestSync.SyncParams(
                id, text, vector, quantized, type, tags, source, offset);
        postIngestSync.syncIndexes(syncParams);

        // Note: Hebbian, Temporal, Entity graph edges are NOT created here.
        // They are bulk-imported separately by MigrationService after all
        // memories are ingested, using the preserved edge data from the bundle.

        log.info("Migration: ingested '{}' as {} (importance={}, recallCount={}, flags=0x{}, ts={})",
                id, type, preservedHeader.importance(), preservedHeader.agentRecallCount(),
                Integer.toHexString(preservedHeader.flags() & 0xFF),
                preservedHeader.timestampMs());
    }

    /**
     * Full cognitive ingestion with consolidated {@link com.spectrayan.spector.memory.model.IngestionContext}.
     *
     * <p>Delegates core ingestion (steps 2-9c) to the existing pipeline, then
     * processes pre-extracted entities, Hebbian edge hints, and temporal link
     * hints from the context. When context provides entities, the
     * {@code EntityExtractor} SPI is bypassed.</p>
     *
     * @param id      unique memory identifier
     * @param text    the memory content
     * @param vector  pre-computed embedding vector
     * @param type    cognitive memory tier
     * @param tags    synaptic tag strings
     * @param source  provenance source
     * @param context consolidated cognitive metadata
     */
    public void ingestCognitive(String id, String text, float[] vector,
                                 MemoryType type, String[] tags,
                                 MemorySource source,
                                 com.spectrayan.spector.memory.model.IngestionContext context) {
        if (context == null) {
            ingestCognitive(id, text, vector, type, tags, source, (IngestionHints) null);
            return;
        }

        // Delegate core ingestion with hints  --  BUT we need to control entity extraction.
        // If context has pre-extracted entities, we skip entity extraction in the base method
        // by temporarily using a flag approach. Instead, we inline the logic here.

        // Step 9d override: Use context entities instead of EntityExtractor
        // First, run the base ingestion (which handles Steps 2-9c + 9d with extractor)
        // We'll re-implement 9d below if context has entities.

        // Use context hints for ICNU fusion
        IngestionHints hints = context.hints();

        // Dedup guard
        if (index.locate(id) != null) {
            log.debug("Skipping duplicate memory '{}'  --  already indexed", sanitize(id));
            return;
        }

        // Step 2: Encode synaptic tags (keyed HMAC when encryption is active)
        long synapticTags = encodeTags(tags);

        // Step 1c: L2-normalize vector
        if (normalizeAtIngest) {
            vector = l2Normalize(vector);
        }

        // Step 5 (early): Quantize vector to INT8
        byte[] quantized = quantizer.encode(vector);

        // Step 3: Compute surprise  ->  importance
        float nearestDist;
        if (workingStore != null && workingStore.count() > 0) {
            nearestDist = workingStore.nearestDistance(
                    vector, quantizer.mins(), quantizer.scales());
        } else {
            nearestDist = computeL2Norm(vector);
        }

        ImportanceContext importanceCtx = new ImportanceContext(
                text, vector, hints, salienceProfile, type,
                nearestDist, surpriseDetector.stats().zScore(nearestDist), false);
        ImportanceResult importanceResult = importanceProvider.score(importanceCtx);
        float importance = importanceResult.importance();

        // Step 4: Flashbulb check
        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, type.ordinal());
        if (importanceResult.isFlashbulb()) {
            flags = (byte) (flags | SynapticHeaderConstants.FLAG_PINNED);
        }

        // Step 4b: Encode source modality from metadata (if provided)
        SourceModality modality = context.sourceModality();
        if (modality != null && modality != SourceModality.TEXT) {
            flags = SynapticHeaderConstants.withSourceModality(flags, modality.ordinal());
        }

        // Step 6: Build cognitive header (use override timestamp if provided)
        float l2Norm = computeL2Norm(vector);
        byte rawValence = (hints != null) ? hints.valence() : (byte) 0;
        byte rawArousal = (hints != null) ? hints.effectiveArousal() : (byte) 0;
        // Personality-modulated emotional encoding
        byte valence = salienceProfile.modulateValence(rawValence);
        byte arousal = salienceProfile.modulateArousal(rawArousal);
        long timestampMs = context.effectiveTimestampMs();
        // Encoding state stamp
        float surpriseZScore = (float) surpriseDetector.stats().zScore(nearestDist);
        byte encodingProfile = computeEncodingProfile(salienceProfile);
        byte encodingAlpha = computeEncodingAlpha(salienceProfile);
        byte encodingBeta = computeEncodingBeta(salienceProfile);
        CognitiveHeader header = new CognitiveHeader(
                timestampMs, synapticTags, l2Norm, importance,
                0, (short) 0, valence, flags, arousal, 1.0f,
                encodingProfile, encodingAlpha, encodingBeta,
                currentSoulVersion, surpriseZScore, (byte) 0);

        // Step 7: Route to tier store and write
        long offset;
        try {
            try {
                offset = cognitiveRouter.write(type, header, quantized);
            } catch (com.spectrayan.spector.memory.error.SpectorPartitionFrozenException e) {
                offset = cognitiveRouter.write(type, header, quantized);
            }
        } catch (SpectorMemoryTierFullException e) {
            if (partitionRollCallback != null) {
                partitionRollCallback.run();
                offset = cognitiveRouter.write(type, header, quantized);
            } else {
                throw e;
            }
        }

        // Steps 7b-9a: Index synchronization (HNSW, ID index, WAL, BM25, SPLADE)
        java.util.Map<String, String> metadata = context.hasMetadata() ? context.metadata() : null;
        var syncParams = new PostIngestSync.SyncParams(
                id, text, vector, quantized, type, tags, source, offset, metadata);
        int graphSlot = postIngestSync.syncIndexes(syncParams);

        // Steps 9b + 9c: Hebbian + Temporal linking (co-ingestion within session)
        // #497: use the monotonic graphSlot allocated during syncIndexes, not
        // index.size()-1 which is non-monotonic (shrinks on forget, causing reuse).
        int memoryIdx = graphSlot;
        String tsid = com.spectrayan.spector.commons.concurrent.MemoryScope.sessionId();
        int sessionIntId = sessionRegistry != null ? sessionRegistry.resolve(tsid) : 0;
        int previousIdx = lastIngestedMemoryIdx.getAndSet(memoryIdx);
        postIngestSync.syncGraphEdges(memoryIdx, previousIdx, sessionIntId);

        // Step 9b-ext: Pre-computed Hebbian edges from IngestionContext
        if (context.hasHebbianEdges()) {
            postIngestSync.syncHebbianEdgeHints(memoryIdx, id, context.hebbianEdges());
        }

        // Step 9c-ext: Pre-computed temporal links from IngestionContext
        if (context.hasTemporalLinks()) {
            postIngestSync.syncTemporalLinkHints(memoryIdx, id, context.temporalLinks());
        }

        // Step 9d: Entity extraction and graph population
        if (context.hasEntities()) {
            hook.observe(GRAPH_SYNC, java.util.Map.of(TAG_MEMORY_ID, id), () -> {
                postIngestSync.syncPreExtractedEntities(context.entities(), memoryIdx, id);
                postIngestSync.syncTemporalFacts(context.entities(), memoryIdx, id, header.timestampMs() / 1000);
            });
        } else if (type != MemoryType.EPISODIC) {
            if (asyncEntityExtractionQueue != null && entityExtractor != null && entityExtractor.isAvailable()) {
                String nsid = com.spectrayan.spector.commons.concurrent.MemoryScope.namespaceId();
                hook.observe(ENTITY_EXTRACTION, java.util.Map.of(TAG_MEMORY_ID, id), () ->
                    asyncEntityExtractionQueue.submit(id, text, memoryIdx, header.timestampMs() / 1000, tsid, nsid)
                );
            } else {
                hook.observe(ENTITY_EXTRACTION, java.util.Map.of(TAG_MEMORY_ID, id), () -> {
                    List<ExtractedEntity> extractedEntities = postIngestSync.syncEntityExtraction(id, text, memoryIdx);
                    postIngestSync.syncTemporalFacts(extractedEntities, memoryIdx, id, header.timestampMs() / 1000);
                });
            }
        }

        log.debug("Ingested '{}' as {} with IngestionContext (importance={}, {} tags, entities={}, hebbianEdges={}, temporalLinks={})",
                id, type, importance, tags.length,
                context.hasEntities() ? context.entities().size() : 0,
                context.hasHebbianEdges() ? context.hebbianEdges().size() : 0,
                context.hasTemporalLinks() ? context.temporalLinks().size() : 0);
    }

    // ===============================================================

    /**
     * Encodes tags using the active encryptor (keyed HMAC or standard MurmurHash).
     */
    private long encodeTags(String[] tags) {
        if (encryptor.isEnabled()) {
            long filter = 0L;
            for (String tag : tags) {
                filter |= encryptor.encodeTag(tag);
            }
            return filter;
        }
        return SynapticTagEncoder.encode(tags);
    }

    private static float computeL2Norm(float[] vector) {
        return VectorOps.magnitude(vector);
    }

    /**
     * Returns a new L2-normalized copy of the vector.
     * Required for Parabolic RBF scoring to work correctly
     * (L2 ²=2.0 only equals orthogonality when ||u|| = ||v|| = 1).
     */
    private static float[] l2Normalize(float[] vector) {
        float norm = computeL2Norm(vector);
        if (norm == 0f || Math.abs(norm - 1.0f) < 1e-6f) return vector; // already normalized or zero
        return VectorOps.normalize(vector);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', '_').replace('\r', '_');
    }

    // ══════════════════════════════════════════════════════════════
    // ENCODING STATE HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Determines the encoding profile byte from the current SalienceProfile.
     *
     * <p>If the profile has custom α/β overrides (soul-derived), the byte
     * encodes bit7=1 (soul flag) + SOUL_DERIVED ordinal. Otherwise, it
     * encodes the preset CognitiveProfile ordinal.</p>
     */
    private static byte computeEncodingProfile(SalienceProfile profile) {
        if (profile.alpha() != null || profile.beta() != null) {
            // Soul-derived: custom α/β from InsulaSelfModel
            return SynapticHeaderConstants.soulDerivedEncodingProfile();
        }
        // Preset profile
        com.spectrayan.spector.memory.model.CognitiveProfile cogProfile =
                profile.defaultProfile() != null
                        ? profile.defaultProfile()
                        : com.spectrayan.spector.memory.model.CognitiveProfile.BALANCED;
        return SynapticHeaderConstants.presetEncodingProfile(cogProfile.ordinal());
    }

    /**
     * Quantizes the effective alpha weight to unsigned byte [0, 255].
     * Uses the SalienceProfile override if present, otherwise the default profile's alpha.
     */
    private static byte computeEncodingAlpha(SalienceProfile profile) {
        float alpha;
        if (profile.alpha() != null) {
            alpha = profile.alpha();
        } else {
            com.spectrayan.spector.memory.model.CognitiveProfile cogProfile =
                    profile.defaultProfile() != null
                            ? profile.defaultProfile()
                            : com.spectrayan.spector.memory.model.CognitiveProfile.BALANCED;
            alpha = cogProfile.alpha();
        }
        return SynapticHeaderConstants.quantizeWeight(alpha);
    }

    /**
     * Quantizes the effective beta weight to unsigned byte [0, 255].
     * Uses the SalienceProfile override if present, otherwise the default profile's beta.
     */
    private static byte computeEncodingBeta(SalienceProfile profile) {
        float beta;
        if (profile.beta() != null) {
            beta = profile.beta();
        } else {
            com.spectrayan.spector.memory.model.CognitiveProfile cogProfile =
                    profile.defaultProfile() != null
                            ? profile.defaultProfile()
                            : com.spectrayan.spector.memory.model.CognitiveProfile.BALANCED;
            beta = cogProfile.beta();
        }
        return SynapticHeaderConstants.quantizeWeight(beta);
    }

    /**
     * Returns the asynchronous entity extraction queue.
     */
    public AsyncEntityExtractionQueue asyncEntityExtractionQueue() {
        return asyncEntityExtractionQueue;
    }

    public void close() {
        if (asyncEntityExtractionQueue != null) {
            asyncEntityExtractionQueue.close();
        }
    }
}
