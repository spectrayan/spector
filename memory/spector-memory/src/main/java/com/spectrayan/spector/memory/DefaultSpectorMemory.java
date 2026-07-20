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

import com.spectrayan.spector.memory.adaptor.ProfileAdaptor;
import com.spectrayan.spector.memory.model.SalienceProfile;

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.provider.embedding.EmbedConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.provider.embedding.PipelineEmbeddingResult;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.index.BM25Index;
import com.spectrayan.spector.index.ColBERTReranker;
import com.spectrayan.spector.index.ColBERTTokenCache;
import com.spectrayan.spector.memory.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.cortex.EpisodicMemoryStore;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.ProceduralMemoryStore;
import com.spectrayan.spector.memory.cortex.SemanticMemoryStore;

import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.cortex.WorkingMemoryStore;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.TextDataStore;
import com.spectrayan.spector.memory.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.graph.HyperEntityGraph;
import com.spectrayan.spector.memory.graph.LlmEntityExtractor;
import com.spectrayan.spector.memory.graph.NoOpEntityExtractor;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationTracker;
import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hebbian.HebbianGraphCsr;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.hippocampus.ReflectDaemon;
import com.spectrayan.spector.memory.consolidation.ConsolidationService;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;

import com.spectrayan.spector.memory.interference.SemanticDeduplicator;
import com.spectrayan.spector.memory.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ImportanceEstimate;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.WhyNotExplanation;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pipeline.HebbianCoActivationListener;
import com.spectrayan.spector.memory.pipeline.ContentTagExtractor;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.pipeline.LtpReconsolidationListener;
import com.spectrayan.spector.memory.pipeline.RecallPipeline;
import com.spectrayan.spector.memory.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.prospective.Reminder;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.sync.CheckpointDaemon;
import com.spectrayan.spector.memory.sync.CompactionResult;
import com.spectrayan.spector.memory.sync.VacuumCompactor;
import com.spectrayan.spector.commons.concurrent.DaemonSupervisor;
import com.spectrayan.spector.commons.concurrent.DaemonPolicy;
import com.spectrayan.spector.memory.synapse.ActRActivation;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.memory.namespace.NamespaceQuotas;
import com.spectrayan.spector.memory.temporal.TemporalChain;
import com.spectrayan.spector.commons.TextChunker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.commons.error.ErrorCode;

import com.spectrayan.spector.memory.error.SpectorGraphDecayException;
import com.spectrayan.spector.memory.id.IdStrategy;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.model.CognitiveRecord;

/**
 * Default implementation of {@link SpectorMemory}  --  the Zero-GC Cognitive Backbone for Autonomous Agents.
 *
 * <h3>Design Pattern: FaÃ§ade</h3>
 * <p>{@code DefaultSpectorMemory} is a thin faÃ§ade that composes and delegates to focused subsystems:</p>
 * <ul>
 *   <li>{@link CognitiveIngestionTarget}  --  10-step ingest (embed  ->  quantize  ->  route  ->  WAL)</li>
 *   <li>{@link RecallPipeline}  --  8-step recall (embed  ->  score  ->  filter  ->  sort)</li>
 *   <li>{@link PartitionManager}  --  DISK partition discovery, creation, and rolling</li>
 *   <li>{@link ImportanceEstimator}  --  read-only novelty/ICNU/flashbulb pipeline</li>
 *   <li>{@link ReflectionOrchestrator}  --  sleep consolidation, graph decay, cross-layer promotion</li>
 *   <li>{@link ReinforcementHandler}  --  valence, LTP, ACT-R, Two-Factor, ICNU re-fusion</li>
 *   <li>{@link PersistenceManager}  --  flush-on-close and resource cleanup</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   var memory = DefaultSpectorMemory.builder()
 *       .dimensions(768)
 *       .embeddingProvider(ollamaProvider)
 *       .persistence(Path.of("/data/agent-memory"))
 *       .build();
 *
 *   memory.remember("user-pref", "User prefers dark mode.",
 *       MemoryType.SEMANTIC, MemorySource.USER_STATED, "ui", "preferences").join();
 *
 *   List<CognitiveResult> results = memory.recall("what theme?",
 *       RecallOptions.builder().topK(5).synapticFilter("preferences").build());
 * }</pre>
 */
public final class DefaultSpectorMemory implements SpectorMemory, SpectorMemoryAdmin {

    private static final Logger log = LoggerFactory.getLogger(DefaultSpectorMemory.class);

    // -€-€ Core Subsystems (FaÃ§ade composition) -€-€
    private final CognitiveIngestionTarget cognitiveTarget;
    private final EmbeddingProvider embeddingProvider;
    private final RecallPipeline recallPipeline;
    private final MemoryIndex index;
    private final ScalarQuantizer quantizer;

    // -€-€ Extracted Strategy/Handler Components -€-€
    private final PartitionManager partitionManager;     // owns volatile tierRouter
    private final ImportanceEstimator importanceEstimator;
    private final ReflectionOrchestrator reflectionOrchestrator;
    private final ReinforcementHandler reinforcementHandler;
    private final ConsolidationService consolidationService;


    // -€-€ Biological Subsystems -€-€
    private final ValenceTracker valenceTracker;
    private final CoActivationTracker coActivationTracker;
    private final SuppressionSet suppressionSet;
    private final HabituationPenalty habituationPenalty;
    private final ProspectiveScheduler prospectiveScheduler;
    private final MemoryIntrospector introspector;
    private final LateralEvaluator lateralEvaluator;
    private final MemoryWal wal;

    // -€-€ 3-Layer Cognitive Graph -€-€
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChain temporalChain;
    private final EntityGraph entityGraph;
    private final HyperEntityGraph hyperEntityGraph;
    private final CognitiveGraphFacade graphFacade;

    // -€-€ Configuration -€-€
    private final int dimensions;
    private final MemoryPersistenceMode persistenceMode;
    private final Path persistencePath;
    private final CircadianPolicy circadianPolicy;
    private final CognitiveProfileConfig profileConfig;

    // -€-€ Multi-Tenant Namespace -€-€
    private final SpectorNamespaceManager namespaceManager;

    // -€-€ ID Generation -€-€
    private final MemoryIdGenerator idGenerator;

    // -€-€ Circadian trigger counter -€-€
    private final AtomicInteger episodicIngestCount = new AtomicInteger(0);

    // -€-€ Automatic Checkpointing -€-€
    private final CheckpointDaemon checkpointDaemon;
    private final DaemonSupervisor daemonSupervisor;

    // -€-€ Shutdown Hook (auto-registered for DISK mode) -€-€
    private final Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // -€-€ BM25 Binary Persistence (P1: save on close for instant next startup) -€-€
    private final MemoryBM25Index bm25Index;

    // -€-€ Chunking for remember() -€-€
    private final com.spectrayan.spector.commons.chunker.TextChunker chunker;
    private volatile com.spectrayan.spector.commons.chunker.ChunkConfig chunkConfig;
    private final ParallelEmbeddingPipeline parallelPipeline;
    private final EmbedConfig embedConfig;

    // -€-€ Multimodal Attachment Processing -€-€
    private final com.spectrayan.spector.memory.pipeline.AttachmentProcessor attachmentProcessor;

    // -€-€ Contextual Bandit (ProfileAdaptor) -€-€
    private final ProfileAdaptor profileAdaptor;

    // -€-€ Semantic Index Reference -€-€
    private final com.spectrayan.spector.index.VectorIndex semanticIndex;

    DefaultSpectorMemory(SpectorMemoryBuilder builder) {
        var bundle = SpectorMemoryFactory.assemble(builder);
        this.cognitiveTarget = bundle.cognitiveTarget();
        this.embeddingProvider = bundle.embeddingProvider();
        this.recallPipeline = bundle.recallPipeline();
        this.index = bundle.index();
        this.quantizer = bundle.quantizer();
        this.partitionManager = bundle.partitionManager();
        this.importanceEstimator = bundle.importanceEstimator();
        this.reflectionOrchestrator = bundle.reflectionOrchestrator();
        this.reinforcementHandler = bundle.reinforcementHandler();
        this.consolidationService = new ConsolidationService(builder.LlmProvider, this.embeddingProvider);

        this.valenceTracker = bundle.valenceTracker();
        this.coActivationTracker = bundle.coActivationTracker();
        this.suppressionSet = bundle.suppressionSet();
        this.habituationPenalty = bundle.habituationPenalty();
        this.prospectiveScheduler = bundle.prospectiveScheduler();
        this.introspector = bundle.introspector();
        this.lateralEvaluator = bundle.lateralEvaluator();
        this.wal = bundle.wal();
        this.hebbianGraph = bundle.hebbianGraph();
        this.temporalChain = bundle.temporalChain();
        this.entityGraph = bundle.entityGraph();
        this.hyperEntityGraph = bundle.hyperEntityGraph();
        this.graphFacade = bundle.graphFacade();
        this.dimensions = builder.dimensions;
        this.persistenceMode = builder.persistenceMode;
        this.persistencePath = builder.persistencePath;
        this.circadianPolicy = builder.circadianPolicy;
        this.profileConfig = builder.profileConfig;
        this.namespaceManager = bundle.namespaceManager();
        this.idGenerator = bundle.idGenerator();
        this.checkpointDaemon = bundle.checkpointDaemon();
        this.daemonSupervisor = bundle.daemonSupervisor();
        this.bm25Index = bundle.bm25Index();
        this.chunker = builder.chunker;
        this.chunkConfig = builder.chunkConfig;
        this.parallelPipeline = bundle.parallelPipeline();
        this.embedConfig = bundle.embedConfig();
        this.attachmentProcessor = bundle.attachmentProcessor();
        this.profileAdaptor = bundle.profileAdaptor();
        this.semanticIndex = builder.semanticIndex;

        // -€-€ JVM Shutdown Hook -€-€ (DISK mode only)
        if (persistenceMode == MemoryPersistenceMode.DISK && bundle.basePath() != null) {
            this.shutdownHook = new Thread(() -> {
                if (closed.compareAndSet(false, true)) {
                    log.info("JVM shutdown hook: flushing SpectorMemory...");
                    doClose();
                    log.info("JVM shutdown hook: SpectorMemory flushed successfully");
                }
            }, "spector-memory-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else {
            this.shutdownHook = null;
        }
    }

    // ==============================================================
    // INGESTION TARGET  --  for unified IngestionPipeline
    // ==============================================================

    @Override
    public CognitiveIngestionTarget target() {
        return cognitiveTarget;
    }

    /** Returns the embedding provider for reconsolidation/update operations. */
    public EmbeddingProvider embeddingProvider() {
        return embeddingProvider;
    }

    // ==============================================================
    // CORE API  --  remember / recall / forget / reflect
    // ==============================================================

    @Override
    public CompletableFuture<Void> remember(String id, String text, MemoryType type,
                                              MemorySource source, String... tags) {
        return remember(id, text, type, source,
                (com.spectrayan.spector.memory.neurodivergent.IngestionHints) null, tags);
    }

    @Override
    public CompletableFuture<Void> remember(String id, String text, MemoryType type,
                                              MemorySource source,
                                              com.spectrayan.spector.memory.neurodivergent.IngestionHints hints,
                                              String... tags) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (shouldChunk(text)) {
                    rememberChunked(id, text, type, source, hints, null, tags);
                } else {
                    String[] finalTags = tags;
                    if (tags == null || tags.length == 0) {
                        var tagExtractor = cognitiveTarget.tagExtractor();
                        if (tagExtractor != null) {
                            finalTags = tagExtractor.extract(id, text);
                        }
                    }
                    float[] vector = embeddingProvider.embed(text).vector();
                    cognitiveTarget.ingestCognitive(id, text, vector, type, finalTags, source, hints);
                }
                checkCircadianTrigger(type);
            } catch (RuntimeException e) {
                log.error("Failed to remember '{}': {}", id, e.getMessage(), e);
                throw new SpectorServerException(ErrorCode.INGESTION_PIPELINE_FAILED, e, id);
            }
        }, ConcurrentTasks.virtualExecutor());
    }

    @Override
    public CompletableFuture<Void> remember(String id, String text, MemoryType type,
                                              String... tags) {
        return remember(id, text, type, MemorySource.OBSERVED, tags);
    }

    @Override
    public CompletableFuture<String> remember(String text, MemoryType type,
                                               MemorySource source, String... tags) {
        String generatedId = idGenerator.generate();
        return remember(generatedId, text, type, source, tags)
                .thenApply(v -> generatedId);
    }

    @Override
    public CompletableFuture<String> remember(String text, MemoryType type,
                                               MemorySource source,
                                               com.spectrayan.spector.memory.neurodivergent.IngestionHints hints,
                                               String... tags) {
        String generatedId = idGenerator.generate();
        return remember(generatedId, text, type, source, hints, tags)
                .thenApply(v -> generatedId);
    }

    @Override
    public CompletableFuture<String> remember(String text, MemoryType type,
                                               MemorySource source,
                                               IngestionContext context,
                                               String... tags) {
        String generatedId = idGenerator.generate();
        return remember(generatedId, text, type, source, context, tags)
                .thenApply(v -> generatedId);
    }

    @Override
    public CompletableFuture<Void> remember(String id, String text, MemoryType type,
                                              MemorySource source,
                                              IngestionContext context,
                                              String... tags) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (shouldChunk(text)) {
                    rememberChunked(id, text, type, source, null, context, tags);
                } else {
                    String[] finalTags = tags;
                    if (tags == null || tags.length == 0) {
                        var tagExtractor = cognitiveTarget.tagExtractor();
                        if (tagExtractor != null) {
                            finalTags = tagExtractor.extract(id, text);
                        }
                    }
                    float[] vector = embeddingProvider.embed(text).vector();
                    cognitiveTarget.ingestCognitive(id, text, vector, type, finalTags, source, context);
                }

                // Process attachments if present in context metadata
                if (context != null && context.hasAttachments()) {
                    processAttachments(id, context, type, source, tags);
                }

                checkCircadianTrigger(type);
            } catch (RuntimeException e) {
                log.error("Failed to remember '{}': {}", id, e.getMessage(), e);
                throw new SpectorServerException(ErrorCode.INGESTION_PIPELINE_FAILED, e, id);
            }
        }, ConcurrentTasks.virtualExecutor());
    }

    /**
     * Checks if episodic ingestion volume has reached the circadian trigger threshold.
     * If so, fires a background reflection cycle.
     */
    private void checkCircadianTrigger(MemoryType type) {
        if (type == MemoryType.EPISODIC) {
            int count = episodicIngestCount.incrementAndGet();
            if (count >= circadianPolicy.volumeTrigger()) {
                episodicIngestCount.set(0);
                ConcurrentTasks.fireAndForget(() -> {
                    log.info("Circadian volume trigger: {} episodic memories  ->  auto-reflect", count);
                    reflect();
                });
            }
        }
    }

    /**
     * Returns true if the text should be chunked before ingestion.
     */
    private boolean shouldChunk(String text) {
        return chunker != null && text != null && text.length() > chunkConfig.maxChunkSize();
    }

    /**
     * Chunks text, parallel-embeds all chunks, and ingests each with the caller's
     * cognitive metadata. The parent memory ID is added as a tag to all chunks.
     *
     * <p>If the caller provided tags, those tags are applied to every chunk.
     * Each chunk ID follows the convention {@code parentId::chunk-N}.</p>
     *
     * @param id      parent memory ID
     * @param text    full text to chunk
     * @param type    memory tier
     * @param source  memory source
     * @param hints   ICNU hints (nullable  --  used when context is null)
     * @param context ingestion context (nullable  --  used when hints is null)
     * @param tags    caller-provided tags to apply to all chunks
     */
    private void rememberChunked(String id, String text, MemoryType type,
                                  MemorySource source,
                                  com.spectrayan.spector.memory.neurodivergent.IngestionHints hints,
                                  IngestionContext context,
                                  String... tags) {
        var chunks = chunker.chunk(id, text, chunkConfig);
        if (chunks.isEmpty()) {
            log.warn("[Remember] Chunker returned empty for '{}' ({} chars), skipping", id, text.length());
            return;
        }

        // Provenance tags from the caller (e.g., "file-ingest", filename)  --  shared across chunks
        String parentTag = sanitizeTag(id);
        String[] provenanceTags;
        if (tags != null && tags.length > 0) {
            provenanceTags = new String[tags.length + 1];
            System.arraycopy(tags, 0, provenanceTags, 0, tags.length);
            provenanceTags[tags.length] = parentTag;
        } else {
            provenanceTags = new String[]{ parentTag };
        }

        // Per-chunk tag extraction  --  each chunk gets its own content-derived tags
        // merged with the shared provenance tags.
        // Use the configured tag extractor (LLM when available, content-based fallback).
        var chunkTagExtractor = cognitiveTarget.tagExtractor();

        // Split chunks into parent and child chunks
        List<com.spectrayan.spector.commons.chunker.Chunk> parentChunks = new ArrayList<>();
        List<com.spectrayan.spector.commons.chunker.Chunk> childChunks = new ArrayList<>();
        for (var chunk : chunks) {
            if ("parent".equals(chunk.metadata().get("chunk_role"))) {
                parentChunks.add(chunk);
            } else {
                childChunks.add(chunk);
            }
        }

        // Parallel-embed only child chunks
        List<String> childTexts = childChunks.stream().map(com.spectrayan.spector.commons.chunker.Chunk::text).toList();
        List<PipelineEmbeddingResult> childEmbeddings = childTexts.isEmpty() ? List.of() : parallelPipeline.embed(childTexts, embedConfig);

        int stored = 0;
        List<String> failures = new ArrayList<>();

        // 1. Ingest parent chunks (bypass embedding via zero vector)
        float[] dummyVector = new float[this.dimensions];
        for (var chunk : parentChunks) {
            IngestionContext parentContext = IngestionContext.builder()
                    .metadata(chunk.metadata())
                    .build();
            try {
                cognitiveTarget.ingestCognitive(chunk.chunkId(), chunk.text(),
                        dummyVector, type, provenanceTags, source, parentContext);
                stored++;
            } catch (RuntimeException e) {
                failures.add(chunk.chunkId());
                log.warn("[Remember] Ingestion failed for parent chunk '{}': {}", chunk.chunkId(), e.getMessage());
            }
        }

        // 2. Ingest child chunks
        for (int i = 0; i < childChunks.size(); i++) {
            var chunk = childChunks.get(i);
            var embedding = childEmbeddings.get(i);

            if (!embedding.success()) {
                failures.add(chunk.chunkId());
                log.warn("[Remember] Embedding failed for chunk '{}': {}", chunk.chunkId(), embedding.error());
                continue;
            }

            // Extract content-specific tags from this chunk's text
            String[] contentTags = chunkTagExtractor.extract(chunk.chunkId(), chunk.text());

            // Merge provenance + per-chunk content tags (deduplicated)
            var mergedSet = new java.util.LinkedHashSet<String>();
            for (String pt : provenanceTags) mergedSet.add(pt);
            for (String ct : contentTags) mergedSet.add(ct);
            String[] chunkTags = mergedSet.toArray(String[]::new);

            // Construct child IngestionContext merging metadata
            IngestionContext childContext;
            if (context != null) {
                var mergedMeta = new java.util.HashMap<>(context.metadata());
                mergedMeta.putAll(chunk.metadata());
                childContext = IngestionContext.builder()
                        .hints(context.hints())
                        .entities(context.entities())
                        .hebbianEdges(context.hebbianEdges())
                        .temporalLinks(context.temporalLinks())
                        .metadata(mergedMeta)
                        .build();
            } else {
                childContext = IngestionContext.builder()
                        .hints(hints)
                        .metadata(chunk.metadata())
                        .build();
            }

            try {
                cognitiveTarget.ingestCognitive(chunk.chunkId(), chunk.text(),
                        embedding.embedding(), type, chunkTags, source, childContext);
                stored++;
            } catch (RuntimeException e) {
                failures.add(chunk.chunkId());
                log.warn("[Remember] Ingestion failed for chunk '{}': {}", chunk.chunkId(), e.getMessage());
            }
        }

        log.info("[Remember] Chunked '{}'  ->  {} chunks stored ({} failed) from {} chars (chunkSize={}, overlap={})",
                id.length() > 60 ? "..." + id.substring(id.length() - 57) : id,
                stored, failures.size(), text.length(), chunkConfig.maxChunkSize(), chunkConfig.overlap());
    }

    /** Sanitizes a memory ID into a valid tag (lowercase, hyphens, no special chars). */
    private static String sanitizeTag(String id) {
        if (id == null) return "unknown";
        return id.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Processes attachments from IngestionContext and creates sub-memories.
     *
     * <p>Each extracted chunk from an attachment is stored as a separate memory
     * with a Hebbian edge linking it to the parent memory.</p>
     */
    private void processAttachments(String parentId, IngestionContext context,
                                     MemoryType type, MemorySource source, String[] tags) {
        if (attachmentProcessor == null) {
            log.debug("No AttachmentProcessor configured  --  skipping attachments for '{}'", parentId);
            return;
        }

        List<com.spectrayan.spector.memory.pipeline.AttachmentProcessor.AttachmentResult> results =
                attachmentProcessor.processAttachments(parentId, context);

        if (results.isEmpty()) {
            log.debug("No attachment chunks produced for '{}'", parentId);
            return;
        }

        int ingested = 0;
        for (var result : results) {
            try {
                // Build sub-memory context with Hebbian edge to parent
                var subContext = IngestionContext.builder()
                        .metadata(result.metadata())
                        .hebbianEdge(parentId, 0.8f)  // strong link to parent
                        .build();

                float[] vector = embeddingProvider.embed(result.text()).vector();
                cognitiveTarget.ingestCognitive(
                        result.chunkId(), result.text(), vector, type, tags, source, subContext);
                ingested++;
            } catch (RuntimeException e) {
                log.warn("[Attachment] Failed to ingest chunk '{}': {}", result.chunkId(), e.getMessage());
            }
        }

        log.info("[Attachment] Processed {} attachment chunks for parent '{}' ({} ingested)",
                results.size(), parentId, ingested);
    }

    @Override
    public ImportanceEstimate estimateImportance(String text,
                                                  com.spectrayan.spector.memory.neurodivergent.IngestionHints hints) {
        return importanceEstimator.estimate(text, hints, embeddingProvider,
                partitionManager.tierRouter(), index);
    }

    @Override
    public List<CognitiveResult> recall(String queryText, RecallOptions options) {
        // Auto-profile resolution: use ProfileAdaptor to suggest best profile
        if (options.autoProfile() && options.profile() == null) {
            CognitiveProfile suggested = null;
            if (profileAdaptor != null) {
                // Extract context tags from the query text
                String[] tags = cognitiveTarget.tagExtractor() != null
                        ? cognitiveTarget.tagExtractor().extract("query", queryText)
                        : new String[0];
                suggested = profileAdaptor.suggest(tags);
            }
            if (suggested == null) {
                SalienceProfile sp = cognitiveTarget.salienceProfile();
                if (sp != null) {
                    suggested = sp.defaultProfile();
                }
            }
            if (suggested == null) {
                suggested = CognitiveProfile.BALANCED;
            }
            log.debug("Auto-profile resolved to {} for query '{}'", suggested, queryText);
            options = RecallOptions.builder()
                    .topK(options.topK())
                    .profile(suggested)
                    .scoringMode(options.scoringMode())
                    .recallMode(options.recallMode())
                    .autoProfile(true)
                    .build();
        }
        return recallPipeline.recall(queryText, options);
    }

    @Override
    public List<CognitiveResult> recall(String queryText, CognitiveProfile profile) {
        CognitiveProfile effective = profileConfig.validate(profile);
        return recall(queryText, RecallOptions.builder().profile(effective).build());
    }

    @Override
    public List<CognitiveResult> recall(String queryText) {
        return recall(queryText, RecallOptions.DEFAULT);
    }

    @Override
    public void forget(String id) {
        if (id == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "id"); }
        MemoryLocation loc = index.locate(id);
        if (loc == null) {
            log.warn("Forget: memory '{}' not found in index", id);
            return;
        }
        MemorySegment segment = partitionManager.tierRouter().segmentFor(loc.type());
        if (segment != null) {
            CognitiveRecordLayout layout = partitionManager.tierRouter().layoutFor(loc.type());
            layout.tombstone(segment, loc.offset());
        }
        wal.appendForget(id);
        index.remove(id);
        log.debug("Forget: '{}' tombstoned", id);
    }

    @Override
    public ReflectReport reflect() {
        return reflectionOrchestrator.reflect(partitionManager.tierRouter(), index);
    }

    @Override
    public void consolidate() {
        consolidationService.consolidate(
                partitionManager.tierRouter(),
                index,
                quantizer,
                entityGraph,
                cognitiveTarget,
                wal,
                this::inspect
        );
    }

    @Override
    public void updateChunkConfig(com.spectrayan.spector.commons.chunker.ChunkConfig config) {
        if (config != null) {
            this.chunkConfig = config;
            log.info("Updated chunking configuration dynamically: maxChunkSize={}, overlap={}, parentChildLinking={}",
                    config.maxChunkSize(), config.overlap(), config.parentChildLinking());
        }
    }


    // ==============================================================
    // EXTENDED API  --  reinforce / suppress / introspect
    // ==============================================================

    @Override
    public void reinforce(String memoryId, byte valence) {
        reinforcementHandler.reinforce(memoryId, valence,
                partitionManager.tierRouter(), index);
    }

    @Override
    public void reinforce(String memoryId, byte valence,
                           com.spectrayan.spector.memory.neurodivergent.IngestionHints updatedHints) {
        reinforcementHandler.reinforceWithHints(memoryId, valence, updatedHints,
                partitionManager.tierRouter(), index);
    }

    @Override
    public void suppress(String memoryId, String reason) {
        suppressionSet.suppress(memoryId, reason);
        MemoryLocation loc = index.locate(memoryId);
        if (loc != null) {
            suppressionSet.registerOffset(loc.type().ordinal(), loc.offset());
        }
        if (recallPipeline.wasLateral(memoryId)) {
            lateralEvaluator.recordLateralSuppression();
            log.debug("Lateral suppression: '{}' (reason={})", memoryId, reason);
        }
    }

    @Override
    public void suppress(String memoryId) { suppress(memoryId, null); }

    @Override
    public void unsuppress(String memoryId) { suppressionSet.unsuppress(memoryId); }

    @Override
    public void markResolved(String memoryId) {
        var loc = index.locate(memoryId);
        if (loc == null) return;
        partitionManager.tierRouter().layoutFor(loc.type())
                .markResolved(partitionManager.tierRouter().segmentFor(loc.type()), loc.offset());
        log.debug("Zeigarnik: marked '{}' as RESOLVED", memoryId);
    }

    @Override
    public void markUnresolved(String memoryId) {
        var loc = index.locate(memoryId);
        if (loc == null) return;
        partitionManager.tierRouter().layoutFor(loc.type())
                .markUnresolved(partitionManager.tierRouter().segmentFor(loc.type()), loc.offset());
        log.debug("Zeigarnik: marked '{}' as UNRESOLVED", memoryId);
    }

    @Override
    public MemoryInsight introspect(String topic) {
        List<CognitiveResult> results = recall(topic, RecallOptions.builder().topK(20).build());
        return introspector.analyze(topic, results);
    }

    @Override
    public WhyNotExplanation whyNot(String memoryId, String queryText, RecallOptions options) {
        var loc = index.locate(memoryId);
        if (loc == null) {
            return new WhyNotExplanation(memoryId, queryText, false, false,
                    null, 0f, WhyNotExplanation.Reason.NOT_FOUND,
                    "Memory '" + memoryId + "' does not exist in the index.");
        }

        var layout = partitionManager.tierRouter().layoutFor(loc.type());
        var segment = partitionManager.tierRouter().segmentFor(loc.type());
        if (layout != null && segment != null) {
            byte flags = segment.get(SynapticHeaderConstants.LAYOUT_FLAGS,
                    loc.offset() + SynapticHeaderConstants.OFFSET_FLAGS);
            if (SynapticHeaderConstants.isTombstoned(flags)) {
                return new WhyNotExplanation(memoryId, queryText, true, false,
                        null, 0f, WhyNotExplanation.Reason.TOMBSTONED,
                        "Memory '" + memoryId + "' has been deleted (tombstone flag set).");
            }
        }

        if (suppressionSet.isSuppressed(memoryId)) {
            return new WhyNotExplanation(memoryId, queryText, true, true,
                    null, 0f, WhyNotExplanation.Reason.SUPPRESSED,
                    "Memory '" + memoryId + "' is in the suppression set. "
                    + "Use unsuppress(\"" + memoryId + "\") to allow recall.");
        }

        int originalTopK = options != null ? options.topK() : 5;
        RecallOptions observeOptions = RecallOptions.builder()
                .recallMode(RecallMode.OBSERVE)
                .topK(Math.max(originalTopK, 20))
                .build();

        List<CognitiveResult> results = recall(queryText, observeOptions);

        CognitiveResult found = null;
        for (CognitiveResult r : results) {
            if (memoryId.equals(r.id())) {
                found = r;
                break;
            }
        }

        if (found != null) {
            return new WhyNotExplanation(memoryId, queryText, true, false,
                    found.breakdown(), 0f, WhyNotExplanation.Reason.OUTRANKED,
                    "Memory '" + memoryId + "' WAS found in extended recall "
                    + "(score=" + String.format("%.4f", found.score()) + "). "
                    + "It may have been outside your original topK cutoff.");
        }

        float cutoffScore = results.isEmpty() ? 0f : results.get(results.size() - 1).score();
        return new WhyNotExplanation(memoryId, queryText, true, false,
                null, cutoffScore, WhyNotExplanation.Reason.FILTERED,
                "Memory '" + memoryId + "' was eliminated by pre-filters "
                + "(tag gate, valence range, importance floor, or time decay). "
                + "TopK cutoff score: " + String.format("%.4f", cutoffScore) + ". "
                + (cutoffScore > 0 ? "Check if tags/valence/importance match your query options." : ""));
    }

    // ==============================================================
    // INSPECT  --  Full Cognitive X-Ray
    // ==============================================================

    @Override
    public CognitiveRecord inspect(String id) {
        if (id == null) return null;

        MemoryLocation loc = index.locate(id);
        if (loc == null) return null;

        String text = index.text(id);
        MemorySource source = index.source(id);
        String[] memTags = index.tags(id);

        MemorySegment segment = partitionManager.tierRouter().segmentFor(loc.type());
        CognitiveRecordLayout layout = partitionManager.tierRouter().layoutFor(loc.type());

        if (segment == null || layout == null) return null;

        // Read the 64-byte cognitive header
        var header = layout.readHeader(segment, loc.offset());

        // Read the quantized vector payload
        int vecBytes = layout.quantizedVecBytes();
        byte[] quantizedVec = new byte[vecBytes];
        long vecOffset = layout.vectorOffset(loc.offset());
        MemorySegment.copy(
                segment, java.lang.foreign.ValueLayout.JAVA_BYTE, vecOffset,
                MemorySegment.ofArray(quantizedVec),
                java.lang.foreign.ValueLayout.JAVA_BYTE, 0, vecBytes);

        // Read extended fields that aren't in the base CognitiveHeader
        int spectorRecallCount = layout.readSpectorRecallCount(segment, loc.offset());
        byte consolidationFlags = layout.readConsolidationFlags(segment, loc.offset());

        // Metadata and suppression state
        var metadata = java.util.Map.<String, String>of();
        boolean suppressed = suppressionSet.isSuppressed(id);

        return new CognitiveRecord(
                id, text, loc.type(), source, memTags,
                header.timestampMs(), header.synapticTags(), header.exactNorm(),
                header.importance(), header.agentRecallCount(), spectorRecallCount,
                header.centroidId(), header.valence(), header.arousal(),
                header.storageStrength(), header.flags(), consolidationFlags,
                quantizedVec, loc.partitionIndex(), loc.offset(),
                metadata, suppressed);
    }

    // ==============================================================
    // BROWSE  --  Tag-Based Iteration
    // ==============================================================

    @Override
    public List<CognitiveRecord> browse(String... tags) {
        if (tags == null || tags.length == 0) return List.of();

        // O(1) inverted tag index lookup  --  intersects tag sets for AND semantics
        var matchingIds = index.idsByAllTags(tags);
        if (matchingIds.isEmpty()) return List.of();

        var results = new java.util.ArrayList<CognitiveRecord>(matchingIds.size());

        for (String memId : matchingIds) {
            MemoryLocation loc = index.locate(memId);
            if (loc == null) continue;

            MemorySegment segment = partitionManager.tierRouter().segmentFor(loc.type());
            CognitiveRecordLayout layout = partitionManager.tierRouter().layoutFor(loc.type());

            if (segment != null && layout != null) {
                var header = layout.readHeader(segment, loc.offset());
                if (!SynapticHeaderConstants.isTombstoned(header.flags())) {
                    int spectorRecallCount = layout.readSpectorRecallCount(segment, loc.offset());
                    byte consolidationFlags = layout.readConsolidationFlags(segment, loc.offset());
                    String[] memTags = index.tags(memId);
                    results.add(new CognitiveRecord(
                            memId, index.text(memId), loc.type(),
                            index.source(memId), memTags,
                            header.timestampMs(), header.synapticTags(), header.exactNorm(),
                            header.importance(), header.agentRecallCount(), spectorRecallCount,
                            header.centroidId(), header.valence(), header.arousal(),
                            header.storageStrength(), header.flags(), consolidationFlags,
                            null, // no vector for browse (use inspect for full detail)
                            loc.partitionIndex(), loc.offset(),
                            java.util.Map.of(), suppressionSet.isSuppressed(memId)));
                }
            }
        }

        return List.copyOf(results);
    }

    // ==============================================================
    // EXPORT  --  Bulk Memory Export
    // ==============================================================

    @Override
    public String exportJson() {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var arrayNode = mapper.createArrayNode();

        for (var entry : index.locationMap().entrySet()) {
            String memId = entry.getKey();
            CognitiveRecord record = inspect(memId);
            if (record != null && !record.isTombstoned()) {
                // Parse record's JSON into a node to avoid double-encoding
                arrayNode.add(mapper.readTree(record.toJson()));
            }
        }

        return arrayNode.toString();
    }

    // ==============================================================
    // PROSPECTIVE / SCRATCHPAD / STATS
    // ==============================================================

    @Override
    public Reminder scheduleReminder(String text, Instant triggerAt, String... tags) {
        return prospectiveScheduler.schedule(text, triggerAt, tags);
    }

    @Override
    public Reminder scheduleReminder(String text, Duration delay, String... tags) {
        return prospectiveScheduler.scheduleAfter(text, delay, tags);
    }

    @Override
    public CompletableFuture<Void> scratchpad(String text) {
        return remember("scratchpad-" + System.nanoTime(), text, MemoryType.WORKING);
    }

    @Override
    public int totalMemories() { return partitionManager.tierRouter().totalCount(); }

    @Override
    public int memoryCount(MemoryType type) { return partitionManager.tierRouter().countFor(type); }

    @Override
    public int decay(Duration olderThan, float factor) {
        if (olderThan == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "olderThan"); }
        if (factor < 0f || factor > 1f) throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "factor", 0, 1, 0);

        long nowMs = System.currentTimeMillis();
        long thresholdMs = nowMs - olderThan.toMillis();

        var partitions = partitionManager.tierRouter().episodic().partitions();
        if (partitions.isEmpty()) return 0;

        try {
            java.util.List<java.util.concurrent.Callable<Integer>> tasks = new java.util.ArrayList<>(partitions.size());
            for (var partition : partitions) {
                tasks.add(() -> {
                    int count = 0;
                    CognitiveRecordLayout layout = partition.layout();
                    MemorySegment segment = partition.segment();
                    for (int i = 0; i < partition.count(); i++) {
                        long offset = partition.recordOffset(i);
                        byte flags = layout.readFlags(segment, offset);
                        if (SynapticHeaderConstants.isTombstoned(flags)) continue;
                        long ts = layout.readTimestamp(segment, offset);
                        if (ts < thresholdMs) {
                            float oldImp = layout.readImportance(segment, offset);
                            layout.writeImportance(segment, offset, oldImp * factor);
                            count++;
                        }
                    }
                    return count;
                });
            }
            java.util.List<Integer> results = ConcurrentTasks.forkJoinAll(tasks);
            int affected = 0;
            for (int c : results) affected += c;
            log.info("Decay: {} memories older than {} multiplied by {}", affected, olderThan, factor);
            return affected;
        } catch (ConcurrentExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Parallel decay failed, falling back to sequential: {}", e.getMessage());
            int affected = 0;
            for (var partition : partitions) {
                CognitiveRecordLayout layout = partition.layout();
                MemorySegment segment = partition.segment();
                for (int i = 0; i < partition.count(); i++) {
                    long offset = partition.recordOffset(i);
                    byte flags = layout.readFlags(segment, offset);
                    if (SynapticHeaderConstants.isTombstoned(flags)) continue;
                    long ts = layout.readTimestamp(segment, offset);
                    if (ts < thresholdMs) {
                        float oldImp = layout.readImportance(segment, offset);
                        layout.writeImportance(segment, offset, oldImp * factor);
                        affected++;
                    }
                }
            }
            log.info("Decay: {} memories older than {} multiplied by {}", affected, olderThan, factor);
            return affected;
        }
    }

    // ==============================================================
    // SALIENCE PROFILE  --  runtime personality & interest configuration
    // ==============================================================

    @Override
    public void setSalienceProfile(SalienceProfile profile) {
        cognitiveTarget.setSalienceProfile(profile);
        log.info("Salience profile updated: interests={}, disinterests={}, persona={}, icnuOverride={}",
                profile != null ? profile.interests().size() : 0,
                profile != null ? profile.disinterests().size() : 0,
                profile != null && profile.hasPersona(),
                profile != null && profile.hasIcnuOverride());
    }

    @Override
    public SalienceProfile salienceProfile() {
        return cognitiveTarget.salienceProfile();
    }

    @Override
    public float computeTopicBoost(String text) {
        if (text == null || text.isBlank()) return 1.0f;
        SalienceProfile profile = cognitiveTarget.salienceProfile();
        if (profile == null || !profile.hasInterests()) return 1.0f;

        float[] embedding = embeddingProvider.embed(text).vector();
        return profile.computeTopicBoost(embedding);
    }

    @Override
    public float computeSelfRelevanceBoost(String text) {
        if (text == null || text.isBlank()) return 1.0f;
        SalienceProfile profile = cognitiveTarget.salienceProfile();
        if (profile == null || !profile.hasPersona()) return 1.0f;

        float[] embedding = embeddingProvider.embed(text).vector();
        return profile.computeSelfRelevanceBoost(embedding);
    }

    // ==============================================================
    // ADMIN INTERFACE
    // ==============================================================

    @Override public SpectorMemoryAdmin admin() { return this; }

    // ==============================================================
    // SUBSYSTEM ACCESSORS (implements both SpectorMemory + SpectorMemoryAdmin)
    // ==============================================================

    @Override public CoActivationTracker coActivation() { return coActivationTracker; }
    @Override public MemoryWal wal() { return wal; }
    @Override public ProspectiveScheduler prospective() { return prospectiveScheduler; }
    @Override public SuppressionSet suppression() { return suppressionSet; }
    @Override public HabituationPenalty habituation() { return habituationPenalty; }
    @Override public ScalarQuantizer quantizer() { return quantizer; }
    @Override public CognitiveIngestionTarget cognitiveTarget() { return cognitiveTarget; }
    @Override public RecallPipeline recallPipeline() { return recallPipeline; }
    @Override public TierRouter tierRouter() { return partitionManager.tierRouter(); }
    @Override public MemoryIndex index() { return index; }
    @Override public LateralEvaluator lateralEvaluator() { return lateralEvaluator; }
    @Override public CognitiveGraphFacade graph() { return graphFacade; }
    @SuppressWarnings("deprecation")
    @Override public HebbianGraphBase hebbianGraph() { return graphFacade.hebbianGraph(); }
    @SuppressWarnings("deprecation")
    @Override public TemporalChain temporalChain() { return graphFacade.temporalChain(); }
    @SuppressWarnings("deprecation")
    @Override public EntityGraph entityGraph() { return graphFacade.entityGraph(); }
    @SuppressWarnings("deprecation")
    @Override public HyperEntityGraph hyperEntityGraph() { return graphFacade.hyperEntityGraph(); }
    @Override public com.spectrayan.spector.index.VectorIndex semanticIndex() { return semanticIndex; }

    // -€-€ listAll implementations -€-€

    @Override
    public List<CognitiveRecord> listAll() {
        var results = new ArrayList<CognitiveRecord>();
        for (var entry : index.locationMap().entrySet()) {
            CognitiveRecord record = inspect(entry.getKey());
            if (record != null && !record.isTombstoned()) {
                results.add(record);
            }
        }
        return List.copyOf(results);
    }

    @Override
    public List<CognitiveRecord> listAll(MemoryType tier, int offset, int limit) {
        var results = new ArrayList<CognitiveRecord>();
        int skipped = 0;
        for (var entry : index.locationMap().entrySet()) {
            if (results.size() >= limit) break;
            MemoryIndex.MemoryLocation loc = entry.getValue();
            if (loc.type() != tier) continue;
            CognitiveRecord record = inspect(entry.getKey());
            if (record != null && !record.isTombstoned()) {
                if (skipped < offset) { skipped++; continue; }
                results.add(record);
            }
        }
        return List.copyOf(results);
    }

    /** Returns the namespace manager (null if IN_MEMORY mode). */
    public SpectorNamespaceManager namespaceManager() { return namespaceManager; }

    // ==============================================================
    // VACUUM / COMPACTION
    // ==============================================================

    private final ReentrantLock vacuumLock = new ReentrantLock();

    @Override
    public CompactionResult vacuum(MemoryType tier) {
        TierRouter router = partitionManager.tierRouter();
        com.spectrayan.spector.memory.cortex.TierStore store = router.get(tier);
        if (!(store instanceof com.spectrayan.spector.memory.cortex.AbstractTierStore ats)) {
            log.warn("Vacuum: tier {} is not compactable", tier);
            return null;
        }
        vacuumLock.lock();
        try {
            return VacuumCompactor.compact(ats, tier, index);
        } finally {
            vacuumLock.unlock();
        }
    }

    @Override
    public java.util.Map<MemoryType, Float> tombstoneRatios() {
        TierRouter router = partitionManager.tierRouter();
        java.util.Map<MemoryType, Float> ratios = new java.util.EnumMap<>(MemoryType.class);
        for (MemoryType type : MemoryType.values()) {
            com.spectrayan.spector.memory.cortex.TierStore store = router.get(type);
            if (store instanceof com.spectrayan.spector.memory.cortex.AbstractTierStore ats) {
                ratios.put(type, ats.tombstoneRatio());
            }
        }
        return ratios;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            log.debug("SpectorMemory.close() already called, skipping");
            return;
        }

        // Deregister shutdown hook to avoid double-flush
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down  --  hook is running or already ran
            }
        }

        doClose();
    }

    /**
     * Internal close logic  --  called by both {@link #close()} and the JVM shutdown hook.
     * Guarded by the {@code closed} AtomicBoolean so it runs at most once.
     */
    private void doClose() {
        log.info("SpectorMemory closing ({} total memories, mode={})",
                totalMemories(), persistenceMode);

        // Stop daemon supervisor (stops all managed daemons)
        if (daemonSupervisor != null) {
            daemonSupervisor.close();
        }

        // Final checkpoint flush before closing storage
        if (checkpointDaemon != null) {
            try {
                // Snapshot ProfileAdaptor bandit stats to CoActivationTracker before checkpoint
                if (profileAdaptor != null && coActivationTracker != null) {
                    coActivationTracker.updateBanditStats(profileAdaptor.statsSnapshot());
                }
                checkpointDaemon.checkpoint();
            } catch (Exception e) {
                log.warn("Final checkpoint on close failed: {}", e.getMessage());
            }
        }

        // Save BM25 binary index for instant load on next startup
        if (persistenceMode == MemoryPersistenceMode.DISK
                && partitionManager.activePartitionDir() != null
                && bm25Index != null && bm25Index.totalDocuments() > 0) {
            try {
                bm25Index.partition(0).save(
                        StorageLayout.bm25Bidx(partitionManager.activePartitionDir()));
            } catch (Exception e) {
                log.warn("Failed to save BM25 index on close: {}", e.getMessage());
            }
        }

        PersistenceManager.flushAndClose(
                persistenceMode, persistencePath,
                partitionManager.activePartitionDir(),
                index, hebbianGraph, temporalChain, entityGraph, hyperEntityGraph,
                coActivationTracker, partitionManager.tierRouter(), wal);
    }

    // ==============================================================
    // BUILDER
    // ==============================================================

    /** Creates a new builder for configuring and assembling a SpectorMemory instance. */
    public static SpectorMemoryBuilder builder() { return new SpectorMemoryBuilder(); }
}
