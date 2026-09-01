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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.bootstrap.BiologicalSubsystemsBuilder;
import com.spectrayan.spector.memory.bootstrap.CognitiveCortexBuilder;
import com.spectrayan.spector.memory.bootstrap.CognitiveGraphBuilder;
import com.spectrayan.spector.memory.bootstrap.RetrievalIndexBuilder;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.cortex.WorkingRecordMemory;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.pipeline.AsyncEntityExtractionQueue;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.pipeline.ContentTagExtractor;
import com.spectrayan.spector.memory.pipeline.PostIngestSync;
import com.spectrayan.spector.memory.pipeline.TagExtractor;
import com.spectrayan.spector.memory.remember.relay.CorticalWriteTransactionRelay;
import com.spectrayan.spector.memory.remember.relay.DedupGuardRelay;
import com.spectrayan.spector.memory.remember.relay.DopaminergicSurpriseRelay;
import com.spectrayan.spector.memory.remember.relay.KnowledgeGraphEnrichmentRelay;
import com.spectrayan.spector.memory.remember.relay.RememberPathwayFactory;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;
import com.spectrayan.spector.memory.remember.relay.SynapticGraphLinkingRelay;
import com.spectrayan.spector.memory.remember.relay.SynapticTagTransductionRelay;
import com.spectrayan.spector.memory.session.SessionRegistry;
import com.spectrayan.spector.memory.sync.MemoryWal;

import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.persist.DataEncryptor;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.cortex.WorkingRecordMemory;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.pipeline.AsyncEntityExtractionQueue;
import com.spectrayan.spector.memory.pipeline.ContentTagExtractor;
import com.spectrayan.spector.memory.pipeline.PostIngestSync;
import com.spectrayan.spector.memory.pipeline.TagExtractor;
import com.spectrayan.spector.memory.remember.relay.CorticalWriteTransactionRelay;
import com.spectrayan.spector.memory.remember.relay.DedupGuardRelay;
import com.spectrayan.spector.memory.remember.relay.DopaminergicSurpriseRelay;
import com.spectrayan.spector.memory.remember.relay.KnowledgeGraphEnrichmentRelay;
import com.spectrayan.spector.memory.remember.relay.RememberPathwayFactory;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;
import com.spectrayan.spector.memory.remember.relay.SynapticGraphLinkingRelay;
import com.spectrayan.spector.memory.remember.relay.SynapticTagTransductionRelay;
import com.spectrayan.spector.memory.session.SessionRegistry;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates memory consolidation and ingestion using the composable Cognitive Pathway Engine architecture.
 *
 * <p>Replaces procedural ingestion in {@code CognitiveIngestionTarget} with a type-safe,
 * observable synaptic relay chain.</p>
 */
public final class RememberPathway implements IngestionTarget, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RememberPathway.class);

    private final CognitivePathway<RememberSignal> pathway;
    private final CorticalWriteTransactionRelay corticalWriteRelay;
    private final TagExtractor tagExtractor;
    private final AsyncEntityExtractionQueue asyncEntityExtractionQueue;
    private final AtomicInteger lastIngestedMemoryIdx = new AtomicInteger(-1);

    private volatile SalienceProfile salienceProfile = SalienceProfile.NEUTRAL;
    private volatile short currentSoulVersion = 0;
    private volatile java.util.List<com.spectrayan.spector.memory.model.SoulContext> soulContexts = java.util.List.of();

    private RememberPathway(final Builder builder) {
        final ScalarQuantizer quantizer = builder.cortex.quantizer();
        final SurpriseDetector surpriseDetector = builder.bio.surpriseDetector();
        final ImportanceProvider importanceProvider = builder.importanceProvider != null
                ? builder.importanceProvider
                : ImportanceProvider.baseline();
        final WorkingRecordMemory workingStore = builder.cortex.workingStore();
        final TagExtractor extractor = builder.tagExtractor != null
                ? builder.tagExtractor
                : new ContentTagExtractor();
        this.tagExtractor = extractor;

        final SessionRegistry sessionRegistry = new SessionRegistry();

        final PostIngestSync postIngestSync = new PostIngestSync(
                builder.cortex.cognitiveRouter(),
                builder.index,
                builder.wal,
                builder.semanticIndex,
                builder.graphs.hebbianGraph(),
                builder.graphs.temporalChain(),
                builder.graphs.entityExtractor(),
                builder.graphs.entityDirectory(),
                builder.retrieval.bm25Index(),
                builder.retrieval.textDataStore(),
                builder.activePartitionIndex,
                builder.retrieval.memorySpladeIndex(),
                builder.sparseEmbeddingProvider,
                builder.dataEncryptor != null ? builder.dataEncryptor : DataEncryptor.NOOP,
                builder.graphs.hyperEntityGraph(),
                builder.graphs.temporalKnowledgeGraph()
        );

        final EntityExtractor entityExtractor = builder.graphs.entityExtractor();
        if (entityExtractor != null && entityExtractor.isAvailable() && builder.entityExtractionParallelism > 0) {
            this.asyncEntityExtractionQueue = new AsyncEntityExtractionQueue(
                    entityExtractor,
                    postIngestSync,
                    builder.entityExtractionParallelism,
                    builder.entityExtractionQueueCapacity
            );
        } else {
            this.asyncEntityExtractionQueue = null;
        }

        final DedupGuardRelay dedupGuardRelay = new DedupGuardRelay(builder.index);
        final SynapticTagTransductionRelay tagTransductionRelay = new SynapticTagTransductionRelay(
                this.tagExtractor,
                builder.dataEncryptor,
                builder.normalizeAtIngest
        );
        final DopaminergicSurpriseRelay surpriseRelay = new DopaminergicSurpriseRelay(
                surpriseDetector,
                importanceProvider,
                workingStore,
                quantizer
        );
        this.corticalWriteRelay = new CorticalWriteTransactionRelay(
                quantizer,
                builder.cortex.cognitiveRouter(),
                postIngestSync,
                surpriseDetector,
                builder.normalizeAtIngest
        );
        final SynapticGraphLinkingRelay graphLinkingRelay = new SynapticGraphLinkingRelay(
                postIngestSync,
                this.lastIngestedMemoryIdx,
                sessionRegistry
        );
        final KnowledgeGraphEnrichmentRelay kgEnrichmentRelay = new KnowledgeGraphEnrichmentRelay(
                postIngestSync,
                this.asyncEntityExtractionQueue,
                entityExtractor
        );

        this.pathway = RememberPathwayFactory.create(
                builder.interceptor,
                dedupGuardRelay,
                tagTransductionRelay,
                surpriseRelay,
                this.corticalWriteRelay,
                graphLinkingRelay,
                kgEnrichmentRelay
        );

        // Seed active partition sequence
        this.corticalWriteRelay.postIngestSync().updateActivePartitionSeq(builder.cortex.initialPartitionSeq());
    }

    /**
     * Ingests a pre-embedded text content using default SEMANTIC tier.
     *
     * @param id     unique memory identifier
     * @param text   the memory content
     * @param vector pre-computed embedding vector
     */
    public void ingest(final String id, final String text, final float[] vector) {
        ingestCognitive(id, text, vector, MemoryType.SEMANTIC, null, MemorySource.OBSERVED, (IngestionHints) null);
    }

    /**
     * Ingests a memory with full cognitive parameters.
     *
     * @param id     unique memory identifier
     * @param text   the memory content
     * @param vector pre-computed embedding vector
     * @param type   target cognitive tier
     * @param tags   synaptic tags
     * @param source provenance source
     * @param hints  optional ingestion hints
     */
    public void ingestCognitive(
            final String id,
            final String text,
            final float[] vector,
            final MemoryType type,
            final String[] tags,
            final MemorySource source,
            final IngestionHints hints) {
        final RememberSignal signal = RememberSignal.forCognitive(
                id, text, vector, type, tags, source, hints,
                salienceProfile, currentSoulVersion
        );
        signal.soulContexts(this.soulContexts);
        pathway.conduct(signal);
    }

    /**
     * Ingests a memory with rich consolidated {@link IngestionContext}.
     *
     * @param id      unique memory identifier
     * @param text    the memory content
     * @param vector  pre-computed embedding vector
     * @param type    target cognitive tier
     * @param tags    synaptic tags
     * @param source  provenance source
     * @param context rich ingestion context
     */
    public void ingestCognitive(
            final String id,
            final String text,
            final float[] vector,
            final MemoryType type,
            final String[] tags,
            final MemorySource source,
            final IngestionContext context) {
        final SalienceProfile effectiveSalience = (context != null && context.salienceProfile() != null)
                ? context.salienceProfile()
                : this.salienceProfile;
        final short effectiveSoulVersion = (context != null && context.soulVersion() != null)
                ? context.soulVersion()
                : this.currentSoulVersion;
        final java.util.List<com.spectrayan.spector.memory.model.SoulContext> effectiveSoulStack =
                (context != null && context.soulContexts() != null && !context.soulContexts().isEmpty())
                ? context.soulContexts()
                : this.soulContexts;

        final RememberSignal signal = RememberSignal.forCognitiveWithContext(
                id, text, vector, type, tags, source, context,
                effectiveSalience, effectiveSoulVersion
        );
        signal.soulContexts(effectiveSoulStack);
        pathway.conduct(signal);
    }

    /**
     * Updates the cognitive memory router after a partition roll.
     */
    public void updateCognitiveRouter(final CognitiveMemoryRouter newRouter) {
        this.corticalWriteRelay.updateCognitiveRouter(newRouter);
    }

    /**
     * Updates the partition-scoped {@code text.dat} store after a roll.
     */
    public void updateTextDataStore(final TextAppendMemory newText) {
        this.corticalWriteRelay.postIngestSync().updateTextDataStore(newText);
    }

    /**
     * Updates the active colocated partition sequence after a roll.
     */
    public void updateActivePartitionSeq(final int seq) {
        this.corticalWriteRelay.postIngestSync().updateActivePartitionSeq(seq);
    }

    /**
     * Sets the partition roll callback.
     */
    public void setPartitionRollCallback(final Runnable callback) {
        this.corticalWriteRelay.setPartitionRollCallback(callback);
    }

    public void setSalienceProfile(final SalienceProfile profile) {
        this.salienceProfile = profile != null ? profile : SalienceProfile.NEUTRAL;
    }

    public SalienceProfile salienceProfile() {
        return salienceProfile;
    }

    public void setSoulVersion(final short version) {
        this.currentSoulVersion = version;
    }

    public short currentSoulVersion() {
        return currentSoulVersion;
    }

    public void setSoulContexts(final java.util.List<com.spectrayan.spector.memory.model.SoulContext> contexts) {
        this.soulContexts = contexts != null ? java.util.List.copyOf(contexts) : java.util.List.of();
    }

    public java.util.List<com.spectrayan.spector.memory.model.SoulContext> soulContexts() {
        return soulContexts;
    }

    public TagExtractor tagExtractor() {
        return tagExtractor;
    }

    public AsyncEntityExtractionQueue asyncEntityExtractionQueue() {
        return asyncEntityExtractionQueue;
    }

    public ScalarQuantizer quantizer() {
        return corticalWriteRelay.quantizer();
    }

    /**
     * Ingests a cognitive memory preserving the provided cognitive header.
     */
    public void ingestCognitiveWithHeader(
            final String id,
            final String text,
            final float[] vector,
            final MemoryType type,
            final String[] tags,
            final MemorySource source,
            final com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader preservedHeader) {
        final RememberSignal signal = RememberSignal.forCognitiveWithHeader(
                id, text, vector, type, tags, source, preservedHeader
        );
        pathway.conduct(signal);
    }

    @Override
    public void close() {
        if (asyncEntityExtractionQueue != null) {
            asyncEntityExtractionQueue.close();
        }
    }

    /**
     * Builder for {@link RememberPathway}.
     */
    public static final class Builder {
        private CognitiveCortexBuilder.CortexFoundation cortex;
        private BiologicalSubsystemsBuilder.BiologicalSubsystems bio;
        private CognitiveGraphBuilder.CognitiveGraphs graphs;
        private RetrievalIndexBuilder.RetrievalIndices retrieval;
        private MemoryIndex index;
        private MemoryWal wal;
        private int activePartitionIndex = 0;
        private ImportanceProvider importanceProvider;
        private TagExtractor tagExtractor;
        private VectorIndex semanticIndex;
        private SparseEmbeddingProvider sparseEmbeddingProvider;
        private DataEncryptor dataEncryptor = DataEncryptor.NOOP;
        private int entityExtractionParallelism = 1;
        private int entityExtractionQueueCapacity = 1000;
        private boolean normalizeAtIngest = true;
        private java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<RememberSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<RememberSignal>> interceptor;

        public Builder() {}

        public Builder interceptor(
                final java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<RememberSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<RememberSignal>> interceptor) {
            this.interceptor = interceptor;
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

        public Builder wal(final MemoryWal wal) {
            this.wal = wal;
            return this;
        }

        public Builder activePartitionIndex(final int activePartitionIndex) {
            this.activePartitionIndex = activePartitionIndex;
            return this;
        }

        public Builder importanceProvider(final ImportanceProvider importanceProvider) {
            this.importanceProvider = importanceProvider;
            return this;
        }

        public Builder tagExtractor(final TagExtractor tagExtractor) {
            this.tagExtractor = tagExtractor;
            return this;
        }

        public Builder semanticIndex(final VectorIndex semanticIndex) {
            this.semanticIndex = semanticIndex;
            return this;
        }

        public Builder sparseEmbeddingProvider(final SparseEmbeddingProvider sparseEmbeddingProvider) {
            this.sparseEmbeddingProvider = sparseEmbeddingProvider;
            return this;
        }

        public Builder dataEncryptor(final DataEncryptor dataEncryptor) {
            this.dataEncryptor = dataEncryptor;
            return this;
        }

        public Builder entityExtractionParallelism(final int parallelism) {
            this.entityExtractionParallelism = parallelism;
            return this;
        }

        public Builder entityExtractionQueueCapacity(final int capacity) {
            this.entityExtractionQueueCapacity = capacity;
            return this;
        }

        public Builder normalizeAtIngest(final boolean normalizeAtIngest) {
            this.normalizeAtIngest = normalizeAtIngest;
            return this;
        }

        public RememberPathway build() {
            Objects.requireNonNull(cortex, "cortex cannot be null");
            Objects.requireNonNull(bio, "bio cannot be null");
            Objects.requireNonNull(graphs, "graphs cannot be null");
            Objects.requireNonNull(retrieval, "retrieval cannot be null");
            Objects.requireNonNull(index, "index cannot be null");
            Objects.requireNonNull(wal, "wal cannot be null");
            return new RememberPathway(this);
        }
    }
}
