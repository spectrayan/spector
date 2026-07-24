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

import com.spectrayan.spector.commons.TextChunker;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.ingestion.sensory.AssetStore;
import com.spectrayan.spector.ingestion.sensory.SensoryExtractor;
import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.graph.HyperEntityGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.id.IdStrategy;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.pipeline.TagExtractor;
import com.spectrayan.spector.memory.synapse.TwoFactorConfig;

import java.nio.file.Path;
import java.util.List;

/**
 * Fluent builder for creating {@link SpectorMemory} instances.
 *
 * <p>Configures all subsystems  --  embedding, persistence, graphs, quantization,
 * entity extraction, text search, encryption, and multimodal attachments  -- 
 * before assembling a {@link DefaultSpectorMemory}.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * SpectorMemory memory = SpectorMemoryBuilder.create()
 *     .dimensions(768)
 *     .embeddingProvider(ollamaProvider)
 *     .persistence(Path.of("/data/memory"))
 *     .build();
 * }</pre>
 *
 * @since 1.0.0
 * @see DefaultSpectorMemory
 * @see SpectorMemory
 */
public final class SpectorMemoryBuilder {

    // -€-€ Core configuration -€-€
    boolean managedByRegistry = false;
    int dimensions;
    EmbeddingProvider embeddingProvider;
    Path persistencePath;
    MemoryPersistenceMode persistenceMode = MemoryPersistenceMode.DISK;
    boolean persistWorkingMemory = false;
    CircadianPolicy circadianPolicy = CircadianPolicy.DEFAULT;
    int workingCapacity = 100;
    int episodicPartitionCapacity = 1_000;
    int semanticCapacity = 10_000;
    int nodesPerPartition = 10_000;
    int proceduralCapacity = 1_000;
    int surpriseWarmup = 10;
    double flashbulbThreshold = 3.0;
    float valenceLearningRate = 0.3f;
    float deduplicationRadius = 0.05f;
    LlmProvider LlmProvider;
    ScalarQuantizer quantizer;
    com.spectrayan.spector.index.VectorIndex semanticIndex;
    long inhibitionTtlMs = 300_000L;
    float inhibitionFloor = 0.1f;
    IcnuWeights icnuWeights;
    boolean pinSourceEpisodes = false;
    int pinnedQuota = 10_000;
    TagExtractor tagExtractor;
    CognitiveProfileConfig profileConfig = CognitiveProfileConfig.allEnabled();

    // -€-€ 3-Layer Cognitive Graph configuration -€-€
    int hebbianGraphCapacity = 0;
    int temporalChainCapacity = 0;
    EntityExtractionMode entityExtractionMode = EntityExtractionMode.NONE;
    EntityExtractor entityExtractor;
    int entityGraphCapacity = 50_000;
    int maxEntitiesPerMemory = 10;
    int maxRelationsPerMemory = 20;
    GenerationOptions llmGenerationOptions;
    GraphScoringPolicy graphScoringPolicy = GraphScoringPolicy.DEFAULT;
    int temporalRetentionDays = 7;
    boolean hyperEntityGraphEnabled = true;
    TwoFactorConfig twoFactorConfig = TwoFactorConfig.DEFAULT;

    // -€-€ Edge importance configuration -€-€
    EdgeImportance edgeImportance = EdgeImportance.DEFAULT;
    int hebbianMaxDegree = HebbianGraph.DEFAULT_MAX_DEGREE;
    int entityMaxDegree = EntityGraph.DEFAULT_MAX_DEGREE;

    // -€-€ ID generation strategy -€-€
    IdStrategy idStrategy = IdStrategy.TSID;
    MemoryIdGenerator idGenerator;

    // -€-€ SPLADE + ColBERT providers -€-€
    SparseEmbeddingProvider SparseEmbeddingProvider;
    TokenEmbeddingProvider tokenEmbeddingProvider;

    // -€-€ Checkpoint daemon configuration -€-€
    int checkpointIntervalSeconds = 30;

    // -€-€ Chunking for remember() -€-€
    com.spectrayan.spector.commons.chunker.TextChunker chunker = new com.spectrayan.spector.commons.chunker.MarkdownChunker();
    com.spectrayan.spector.commons.chunker.ChunkConfig chunkConfig = com.spectrayan.spector.commons.chunker.ChunkConfig.markdown(2500, 200);

    // -€-€ Embedding pipeline batch size -€-€
    int embedBatchSize = 32;

    // -€-€ Salience profile provider (enterprise SPI) -€-€
    SalienceProfileProvider salienceProfileProvider;

    // -€-€ Data encryption SPI -€-€
    DataEncryptor dataEncryptor = DataEncryptor.NOOP;

    // -€-€ Multimodal attachment processing -€-€
    List<SensoryExtractor> sensoryExtractors = List.of();
    AssetStore assetStore;

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = 
    // FACTORY
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = 

    /** Creates a new builder instance. */
    public static SpectorMemoryBuilder create() { return new SpectorMemoryBuilder(); }

    SpectorMemoryBuilder() {}

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = 
    // FLUENT SETTERS
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = 

    public SpectorMemoryBuilder dimensions(int dimensions) { this.dimensions = dimensions; return this; }
    public SpectorMemoryBuilder managedByRegistry(boolean managed) { this.managedByRegistry = managed; return this; }
    public SpectorMemoryBuilder embeddingProvider(EmbeddingProvider p) { this.embeddingProvider = p; return this; }
    public SpectorMemoryBuilder persistence(Path p) { this.persistencePath = p; return this; }
    /** Sets the persistence mode (default: {@link MemoryPersistenceMode#DISK}). */
    public SpectorMemoryBuilder persistenceMode(MemoryPersistenceMode mode) { this.persistenceMode = mode; return this; }
    /** If true, Working memory is also persisted to disk in DISK mode (default: false). */
    public SpectorMemoryBuilder persistWorkingMemory(boolean persist) { this.persistWorkingMemory = persist; return this; }
    public SpectorMemoryBuilder reflectPolicy(CircadianPolicy p) { this.circadianPolicy = p; return this; }

    /**
     * Sets the text chunker for remember() auto-chunking.
     *
     * @deprecated Use {@link #chunker(com.spectrayan.spector.commons.chunker.TextChunker, com.spectrayan.spector.commons.chunker.ChunkConfig)} instead.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public SpectorMemoryBuilder chunker(TextChunker chunker) {
        if (chunker != null) {
            this.chunker = new com.spectrayan.spector.commons.chunker.SentenceChunker();
            this.chunkConfig = new com.spectrayan.spector.commons.chunker.ChunkConfig(
                    chunker.chunkSize(), chunker.overlap(),
                    "text/plain", "text/plain",
                    false, false, false
            );
        }
        return this;
    }

    /**
     * Sets the text chunker and configuration for remember() auto-chunking.
     *
     * @param chunker the SPI text chunker implementation
     * @param config the chunking configuration
     */
    public SpectorMemoryBuilder chunker(com.spectrayan.spector.commons.chunker.TextChunker chunker,
                                        com.spectrayan.spector.commons.chunker.ChunkConfig config) {
        this.chunker = chunker != null ? chunker : new com.spectrayan.spector.commons.chunker.MarkdownChunker();
        this.chunkConfig = config != null ? config : com.spectrayan.spector.commons.chunker.ChunkConfig.DEFAULT;
        return this;
    }

    /** Sets the embedding batch size for parallel chunk embedding (default: 32). */
    public SpectorMemoryBuilder embedBatchSize(int size) { this.embedBatchSize = size; return this; }

    public SpectorMemoryBuilder workingCapacity(int c) { this.workingCapacity = c; return this; }
    public SpectorMemoryBuilder episodicPartitionCapacity(int c) { this.episodicPartitionCapacity = c; return this; }
    public SpectorMemoryBuilder semanticCapacity(int c) { this.semanticCapacity = c; return this; }
    /** Nodes per semantic partition before rolling to a new file (default: 10,000). */
    public SpectorMemoryBuilder nodesPerPartition(int n) { this.nodesPerPartition = n; return this; }
    public SpectorMemoryBuilder proceduralCapacity(int c) { this.proceduralCapacity = c; return this; }
    public SpectorMemoryBuilder surpriseWarmup(int w) { this.surpriseWarmup = w; return this; }
    public SpectorMemoryBuilder flashbulbThreshold(double t) { this.flashbulbThreshold = t; return this; }
    public SpectorMemoryBuilder valenceLearningRate(float r) { this.valenceLearningRate = r; return this; }
    public SpectorMemoryBuilder deduplicationRadius(float r) { this.deduplicationRadius = r; return this; }
    public SpectorMemoryBuilder LlmProvider(LlmProvider p) { this.LlmProvider = p; return this; }
    public SpectorMemoryBuilder quantizer(ScalarQuantizer quantizer) { this.quantizer = quantizer; return this; }

    /** Optional HNSW/IVF index for fused semantic recall (default: null = header-only fallback). */
    public SpectorMemoryBuilder semanticIndex(com.spectrayan.spector.index.VectorIndex idx) { this.semanticIndex = idx; return this; }


    /** Inhibition of Return TTL in millis (default: 300_000 = 5 minutes). */
    public SpectorMemoryBuilder inhibitionTtlMs(long ms) { this.inhibitionTtlMs = ms; return this; }

    /** Inhibition of Return floor multiplier (default: 0.1). */
    public SpectorMemoryBuilder inhibitionFloor(float floor) { this.inhibitionFloor = floor; return this; }

    /** ICNU fusion weights for neurodivergent importance computation (default: IcnuWeights.DEFAULT). */
    public SpectorMemoryBuilder icnuWeights(IcnuWeights w) { this.icnuWeights = w; return this; }

    /** Enable lossless consolidation  --  pin source episodes during REM sleep (default: false). */
    public SpectorMemoryBuilder pinSourceEpisodes(boolean pin) { this.pinSourceEpisodes = pin; return this; }

    /** Maximum number of pinned records (default: 10,000). */
    public SpectorMemoryBuilder pinnedQuota(int quota) { this.pinnedQuota = quota; return this; }

    /** Pluggable tag extraction strategy for cognitive ingestion (default: ContentTagExtractor). */
    public SpectorMemoryBuilder tagExtractor(TagExtractor te) { this.tagExtractor = te; return this; }

    /** Cognitive profile configuration (default: all profiles enabled). */
    public SpectorMemoryBuilder profileConfig(CognitiveProfileConfig config) { this.profileConfig = config; return this; }

    // -€-€ 3-Layer Cognitive Graph configuration -€-€

    /** Hebbian graph capacity (default: same as episodicPartitionCapacity). */
    public SpectorMemoryBuilder hebbianGraphCapacity(int c) { this.hebbianGraphCapacity = c; return this; }

    /** Temporal chain capacity (default: same as hebbianGraphCapacity). */
    public SpectorMemoryBuilder temporalChainCapacity(int c) { this.temporalChainCapacity = c; return this; }

    /** Entity extraction mode (default: NONE). */
    public SpectorMemoryBuilder entityExtractionMode(EntityExtractionMode mode) { this.entityExtractionMode = mode; return this; }

    /** Custom entity extractor (used when mode = CUSTOM). */
    public SpectorMemoryBuilder entityExtractor(EntityExtractor extractor) { this.entityExtractor = extractor; return this; }

    /** Entity graph capacity  --  max entities (default: 50,000). */
    public SpectorMemoryBuilder entityGraphCapacity(int c) { this.entityGraphCapacity = c; return this; }

    /** Enable/disable the HyperEntityGraph layer (default: true). */
    public SpectorMemoryBuilder hyperEntityGraphEnabled(boolean enabled) { this.hyperEntityGraphEnabled = enabled; return this; }

    /** Max entities to extract per memory (default: 10). */
    public SpectorMemoryBuilder maxEntitiesPerMemory(int c) { this.maxEntitiesPerMemory = c; return this; }

    /** Max relations to extract per memory (default: 20). */
    public SpectorMemoryBuilder maxRelationsPerMemory(int c) { this.maxRelationsPerMemory = c; return this; }

    /** LLM generation options for entity extraction (temperature, maxTokens, topP). */
    public SpectorMemoryBuilder llmGenerationOptions(GenerationOptions opts) { this.llmGenerationOptions = opts; return this; }

    /** Graph scoring policy  --  configurable weights for cognitive graph steps (default: GraphScoringPolicy.DEFAULT). */
    public SpectorMemoryBuilder graphScoringPolicy(GraphScoringPolicy policy) { this.graphScoringPolicy = policy; return this; }

    /** Temporal chain retention in days  --  links older than this are pruned during reflect() (default: 7). */
    public SpectorMemoryBuilder temporalRetentionDays(int days) { this.temporalRetentionDays = days; return this; }

    /** Checkpoint interval in seconds (default: 30). Set to 0 to disable automatic checkpointing. */
    public SpectorMemoryBuilder checkpointIntervalSeconds(int seconds) { this.checkpointIntervalSeconds = seconds; return this; }

    /** Two-Factor Memory (Bjork & Bjork) configuration (default: TwoFactorConfig.DEFAULT). */
    public SpectorMemoryBuilder twoFactorConfig(TwoFactorConfig config) { this.twoFactorConfig = config; return this; }

    /** Edge importance scorer with configurable signal weights (default: EdgeImportance.DEFAULT). */
    public SpectorMemoryBuilder edgeImportance(EdgeImportance importance) { this.edgeImportance = importance; return this; }

    /** Maximum edges per node in the Hebbian graph (default: 24). */
    public SpectorMemoryBuilder hebbianMaxDegree(int maxDegree) { this.hebbianMaxDegree = maxDegree; return this; }

    /** Maximum edges per entity in the entity graph (default: 48). */
    public SpectorMemoryBuilder entityMaxDegree(int maxDegree) { this.entityMaxDegree = maxDegree; return this; }

    /**
     * Parses a cognitive profile config from a YAML string value.
     * Supports: "ALL", "CORE_ONLY", "WITH_NEURODIVERGENT", or comma-separated profile names.
     * @see CognitiveProfileConfig#fromConfigValue(String)
     */
    public SpectorMemoryBuilder cognitiveProfiles(String configValue) { this.profileConfig = CognitiveProfileConfig.fromConfigValue(configValue); return this; }

    // -€-€ ID Generation -€-€

    /**
     * Sets the ID generation strategy for auto-generated memory IDs.
     *
     * <p>Default: {@link IdStrategy#TSID}  --  13-char time-sorted, distributed-safe.
     * This is only used when {@link SpectorMemory#remember(String, MemoryType, MemorySource, String...)}
     * is called without an explicit ID.</p>
     *
     * @param strategy the built-in strategy to use
     * @return this builder
     */
    public SpectorMemoryBuilder idStrategy(IdStrategy strategy) { this.idStrategy = strategy; return this; }

    /**
     * Sets a custom ID generator, overriding the built-in {@link #idStrategy(IdStrategy)}.
     *
     * <p>Use this for custom ID schemes (e.g., database-sequence-backed, ULID, etc.).
     * The generator must be thread-safe.</p>
     *
     * @param generator the custom generator
     * @return this builder
     */
    public SpectorMemoryBuilder idGenerator(MemoryIdGenerator generator) { this.idGenerator = generator; return this; }

    /**
     * Sets the sparse encoding provider for SPLADE retrieval.
     *
     * <p>When provided, a {@code MemorySpladeIndex} is automatically created and wired
     * into both the ingestion and recall pipelines, enabling SPLADE, SPLADE_HYBRID,
     * and FULL_STACK text search modes.</p>
     *
     * @param provider the sparse encoding provider (e.g., OllamaSparseEmbeddingProvider)
     * @return this builder
     */
    public SpectorMemoryBuilder SparseEmbeddingProvider(SparseEmbeddingProvider provider) { this.SparseEmbeddingProvider = provider; return this; }

    /**
     * Sets the token embedding provider for ColBERT reranking.
     *
     * <p>When provided, a {@code ColBERTReranker} with a {@code ColBERTTokenCache}
     * is automatically created and wired into the recall pipeline, enabling
     * COLBERT_RERANK and FULL_STACK text search modes.</p>
     *
     * @param provider the token embedding provider (e.g., DenseDerivedTokenProvider)
     * @return this builder
     */
    public SpectorMemoryBuilder tokenEmbeddingProvider(TokenEmbeddingProvider provider) { this.tokenEmbeddingProvider = provider; return this; }

    /** Registers sensory extractors for multimodal attachment processing. */
    public SpectorMemoryBuilder sensoryExtractors(List<SensoryExtractor> extractors) {
        this.sensoryExtractors = extractors != null ? extractors : List.of();
        return this;
    }

    /** Sets the asset store for persisting original attachment files. */
    public SpectorMemoryBuilder assetStore(AssetStore store) {
        this.assetStore = store;
        return this;
    }

    /**
     * Sets the data encryption provider for text.dat, WAL, and tag encryption.
     *
     * <p>Default: {@link DataEncryptor#NOOP} (no encryption, OSS mode).
     * Enterprise callers inject a {@link DataEncryptor} implementation
     * (e.g., {@code TenantDataEncryptor} or {@code ContextualDataEncryptor})
     * to enable AES-256-GCM encryption of text content and WAL payloads,
     * plus HMAC-SHA256 blind indexing for synaptic tags.</p>
     *
     * @param encryptor the data encryptor (null treated as NOOP)
     * @return this builder
     */
    public SpectorMemoryBuilder dataEncryptor(DataEncryptor encryptor) {
        this.dataEncryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        return this;
    }

    /**
     * Sets the salience profile provider for user-configurable importance scoring.
     *
     * <p>Enterprise callers supply a {@link TenantSalienceResolver} that merges
     * tenant  ->  agent  ->  user profiles. The effective profile is applied during
     * ingestion (ICNU weights + topic boost) and optionally at recall time
     * (alpha/beta override).</p>
     *
     * @param provider the salience profile provider (null = noop/NEUTRAL)
     * @return this builder
     */
    public SpectorMemoryBuilder salienceProfileProvider(SalienceProfileProvider provider) {
        this.salienceProfileProvider = provider;
        return this;
    }

    // ==============================================================
    // BUILD
    // ==============================================================

    /**
     * Builds and returns a fully-initialized {@link SpectorMemory} instance.
     *
     * @return the constructed SpectorMemory
     * @throws com.spectrayan.spector.memory.SpectorValidationException if required fields are missing
     */
    public SpectorMemory build() {
        if (dimensions <= 0 && embeddingProvider != null) {
            dimensions = embeddingProvider.dimensions();
        }
        return new DefaultSpectorMemory(this);
    }
}
