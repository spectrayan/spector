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

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.api.CognitiveProfileConfig;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.api.SalienceProfileProvider;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.dopamine.DefaultImportanceProvider;
import com.spectrayan.spector.memory.dream.relay.DreamConfig;
import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.OntologyConfig;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.id.IdStrategy;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.OrgUnitSoul;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.pipeline.ContentTagExtractor;
import com.spectrayan.spector.memory.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.pipeline.TagExtractor;
import com.spectrayan.spector.memory.pipeline.reranker.ColBERTReranker;
import com.spectrayan.spector.memory.pipeline.reranker.ColBERTTokenCache;
import com.spectrayan.spector.memory.scheduler.MemoryScheduler;
import com.spectrayan.spector.memory.synapse.TwoFactorConfig;

import com.spectrayan.spector.memory.api.CognitiveProfileConfig;
import com.spectrayan.spector.memory.persist.DataEncryptor;

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
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.id.IdStrategy;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.pipeline.TagExtractor;
import com.spectrayan.spector.memory.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.synapse.TwoFactorConfig;
import com.spectrayan.spector.commons.observation.MemoryObservationHook;

import java.nio.file.Path;
import java.util.List;
import com.spectrayan.spector.config.SpectorPropertyConstants;

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

    //  Core configuration 
    private boolean managedByRegistry = false;
    private boolean useBundleMode = true;   // V4 bundle architecture (ADR-0004)
    private int dimensions;
    private EmbeddingProvider embeddingProvider;
    private Path persistencePath;
    private MemoryPersistenceMode persistenceMode = MemoryPersistenceMode.valueOf(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PERSISTENCE_MODE_NAME);
    private int maxActiveNamespaces = Integer.getInteger(
            com.spectrayan.spector.config.SpectorPropertyConstants.MEMORY_MAX_NAMESPACES,
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_MAX_NAMESPACES);
    private String namespaceId = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_NAMESPACE_ID;
    private boolean persistWorkingMemory = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PERSIST_WORKING_MEMORY;
    private CircadianPolicy circadianPolicy = CircadianPolicy.DEFAULT;
    private int workingCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_WORKING_CAPACITY;
    private int episodicPartitionCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_EPISODIC_PARTITION_CAPACITY;
    private int semanticCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SEMANTIC_CAPACITY;
    private int nodesPerPartition = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_NODES_PER_PARTITION;
    private int proceduralCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PROCEDURAL_CAPACITY;
    private int surpriseWarmup = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SURPRISE_WARMUP;
    private double flashbulbThreshold = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_FLASHBULB_THRESHOLD;
    private float valenceLearningRate = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_VALENCE_LEARNING_RATE;
    private float deduplicationRadius = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_DEDUPLICATION_RADIUS;
    private LlmProvider LlmProvider;
    private ScalarQuantizer quantizer;
    public com.spectrayan.spector.index.VectorIndex semanticIndex;
    private long inhibitionTtlMs = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_INHIBITION_TTL_MS;
    private float inhibitionFloor = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_INHIBITION_FLOOR;
    private IcnuWeights icnuWeights;
    private boolean pinSourceEpisodes = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PIN_SOURCE_EPISODES;
    private int pinnedQuota = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PINNED_QUOTA;
    private TagExtractor tagExtractor;
    private CognitiveProfileConfig profileConfig = CognitiveProfileConfig.allEnabled();
    private MemoryObservationHook hook;

    // ─── 3-Layer Cognitive Graph configuration ───
    private int hebbianGraphCapacity = 0;
    private int temporalChainCapacity = 0;
    private EntityExtractionMode entityExtractionMode = EntityExtractionMode.valueOf(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_EXTRACTION_MODE);
    private EntityExtractor entityExtractor;
    private int entityGraphCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_GRAPH_CAPACITY;
    private int maxEntitiesPerMemory = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_MAX_PER_MEM;
    private int maxRelationsPerMemory = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_RELATION_MAX_PER_MEM;
    private GenerationOptions llmGenerationOptions;
    private GraphScoringPolicy graphScoringPolicy = GraphScoringPolicy.DEFAULT;
    private int temporalRetentionDays = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_RETENTION_DAYS;
    private TwoFactorConfig twoFactorConfig = TwoFactorConfig.DEFAULT;
    
    // Entity resolution config
    private boolean entityResolutionEnabled = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_RESOLUTION_ENABLED;
    private boolean entityShadowMode = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_SHADOW_MODE;
    private float entityCosineThreshold = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_COSINE_THRESHOLD;
    
    // Ontology config
    public com.spectrayan.spector.memory.graph.OntologyConfig ontologyConfig;

    // ─── Edge importance configuration ───
    private EdgeImportance edgeImportance = EdgeImportance.DEFAULT;
    private int hebbianMaxDegree = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE;
    private int entityMaxDegree = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_MAX_DEGREE;

    // ─── ID generation strategy ───
    private IdStrategy idStrategy = IdStrategy.valueOf(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ID_STRATEGY);
    private MemoryIdGenerator idGenerator;

    //  SPLADE + ColBERT providers 
    private SparseEmbeddingProvider SparseEmbeddingProvider;
    private TokenEmbeddingProvider tokenEmbeddingProvider;

    //  Checkpoint daemon configuration 
    private int checkpointIntervalSeconds = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_CHECKPOINT_INTERVAL_SECONDS;

    //  Chunking for remember() 
    public com.spectrayan.spector.commons.chunker.TextChunker chunker = new com.spectrayan.spector.commons.chunker.MarkdownChunker();
    public com.spectrayan.spector.commons.chunker.ChunkConfig chunkConfig = com.spectrayan.spector.commons.chunker.ChunkConfig.markdown(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_INGESTION_CHUNK_SIZE,
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_INGESTION_CHUNK_OVERLAP);

    //  Embedding pipeline batch size 
    private int embedBatchSize = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_PROVIDER_EMBEDDING_BATCH_SIZE;

    //  Asynchronous entity extraction queue configuration
    private int entityExtractionParallelism = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_EXTRACTION_PARALLELISM;
    private int entityExtractionQueueCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY;

    //  Salience profile provider (enterprise SPI) 
    private SalienceProfileProvider salienceProfileProvider;
    public com.spectrayan.spector.memory.model.SalienceProfile salienceProfile;

    //  Importance provider SPI (#481) 
    private ImportanceProvider importanceProvider;

    //  Data encryption SPI 
    private DataEncryptor dataEncryptor = DataEncryptor.NOOP;

    //  Multimodal attachment processing 
    private List<SensoryExtractor> sensoryExtractors = List.of();
    private AssetStore assetStore;

    // ── Cache Manager SPI ──
    public com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager;

    // Configurable capacities/sizes for runtime bundle regions
    private int coactivationPairCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_PAIR_CAPACITY;
    private int coactivationEdgeCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_EDGE_CAPACITY;
    private long temporalFactsInitialSize = SpectorPropertyConstants.DEFAULT_MEMORY_TEMPORAL_FACTS_INITIAL_SIZE;
    private int indexMidxCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_INDEX_MIDX_CAPACITY;
    private long indexIdplSize = SpectorPropertyConstants.DEFAULT_MEMORY_INDEX_IDPL_SIZE;
    private int typeRegistryCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_TYPE_REGISTRY_CAPACITY;
    private long typeRegistrySize = SpectorPropertyConstants.DEFAULT_MEMORY_TYPE_REGISTRY_SIZE;
    private long insulaSize = SpectorPropertyConstants.DEFAULT_MEMORY_INSULA_SIZE;

    // Eager consolidation (#526)
    private int eagerConsolidationQueueCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_EAGER_CONSOLIDATION_QUEUE_CAPACITY;

    // Cognitive Pathway Engine (#561) — default engine (legacy pipeline deprecated)
    private boolean usePathwayEngine = Boolean.parseBoolean(System.getProperty("spector.pathway.enabled", "true"));

    // Active Inference Self-Model Engine (AISME) (#597)
    private com.spectrayan.spector.memory.aisme.config.AismeConfig aismeConfig = com.spectrayan.spector.memory.aisme.config.AismeConfig.disabled();
    private com.spectrayan.spector.memory.model.AgentSoul agentSoul;
    private com.spectrayan.spector.memory.model.SoulContext soul;
    private java.util.List<com.spectrayan.spector.memory.model.SoulContext> soulContexts;

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
    /** Enable V4 bundle architecture (ADR-0004) — packs partition stores into .bundle files. */
    public SpectorMemoryBuilder bundleMode(boolean enable) { this.useBundleMode = enable; return this; }
    public SpectorMemoryBuilder embeddingProvider(EmbeddingProvider p) { this.embeddingProvider = p; return this; }
    public SpectorMemoryBuilder persistence(Path p) { this.persistencePath = p; return this; }
    /** Sets the persistence mode (default: {@link MemoryPersistenceMode#DISK}). */
    public SpectorMemoryBuilder persistenceMode(MemoryPersistenceMode mode) { this.persistenceMode = mode; return this; }
    /** If true, Working memory is also persisted to disk in DISK mode (default: false). */
    public SpectorMemoryBuilder persistWorkingMemory(boolean persist) { this.persistWorkingMemory = persist; return this; }
    public SpectorMemoryBuilder reflectPolicy(CircadianPolicy p) { this.circadianPolicy = p; return this; }
    public SpectorMemoryBuilder circadianPolicy(CircadianPolicy p) { this.circadianPolicy = p; return this; }

    public SpectorMemoryBuilder coactivationPairCapacity(int c) { this.coactivationPairCapacity = c; return this; }
    public SpectorMemoryBuilder coactivationEdgeCapacity(int c) { this.coactivationEdgeCapacity = c; return this; }
    public SpectorMemoryBuilder temporalFactsInitialSize(long s) { this.temporalFactsInitialSize = s; return this; }
    public SpectorMemoryBuilder indexMidxCapacity(int c) { this.indexMidxCapacity = c; return this; }
    public SpectorMemoryBuilder indexIdplSize(long s) { this.indexIdplSize = s; return this; }
    public SpectorMemoryBuilder typeRegistryCapacity(int c) { this.typeRegistryCapacity = c; return this; }
    public SpectorMemoryBuilder typeRegistrySize(long s) { this.typeRegistrySize = s; return this; }
    public SpectorMemoryBuilder insulaSize(long s) { this.insulaSize = s; return this; }
    public SpectorMemoryBuilder eagerConsolidationQueueCapacity(int c) { this.eagerConsolidationQueueCapacity = c; return this; }
    /**
     * Sets whether to use the Cognitive Pathway Engine.
     * @deprecated Since 1.4.0. The pathway engine is the sole default memory execution engine.
     */
    @Deprecated
    public SpectorMemoryBuilder usePathwayEngine(boolean enable) { this.usePathwayEngine = enable; return this; }

    /** Sets the Active Inference Self-Model Engine (AISME) configuration (#597). */
    public SpectorMemoryBuilder aismeConfig(com.spectrayan.spector.memory.aisme.config.AismeConfig config) {
        this.aismeConfig = config != null ? config : com.spectrayan.spector.memory.aisme.config.AismeConfig.disabled();
        return this;
    }

    /** Enables or disables AISME with default configuration (#597). */
    public SpectorMemoryBuilder enableAisme(boolean enable) {
        this.aismeConfig = enable ? com.spectrayan.spector.memory.aisme.config.AismeConfig.defaultConfig() : com.spectrayan.spector.memory.aisme.config.AismeConfig.disabled();
        return this;
    }

    private com.spectrayan.spector.memory.dream.relay.DreamConfig dreamConfig = com.spectrayan.spector.memory.dream.relay.DreamConfig.defaultConfig();

    /** Sets the Generative Dreaming &amp; Thought Experiment configuration (#679). */
    public SpectorMemoryBuilder dreamConfig(com.spectrayan.spector.memory.dream.relay.DreamConfig config) {
        this.dreamConfig = config != null ? config : com.spectrayan.spector.memory.dream.relay.DreamConfig.defaultConfig();
        return this;
    }

    /** Enables or disables Generative Dreaming with default configuration (#679). */
    public SpectorMemoryBuilder enableDreaming(boolean enable) {
        this.dreamConfig = enable ? com.spectrayan.spector.memory.dream.relay.DreamConfig.defaultConfig() : com.spectrayan.spector.memory.dream.relay.DreamConfig.disabled();
        return this;
    }

    /** Sets the primary SoulContext defining identity, purpose, and values for conscious self-modeling (#597, #623). */
    public SpectorMemoryBuilder soul(com.spectrayan.spector.memory.model.SoulContext soul) {
        this.soul = soul;
        if (soul instanceof com.spectrayan.spector.memory.model.AgentSoul agent) {
            this.agentSoul = agent;
        }
        return this;
    }

    /** Sets the multi-soul hierarchy contexts (AgentSoul, UserSoul, TenantSoul, OrgUnitSoul) for composite EFE and self-modeling (#623). */
    public SpectorMemoryBuilder soulContexts(java.util.List<com.spectrayan.spector.memory.model.SoulContext> contexts) {
        this.soulContexts = contexts != null ? java.util.List.copyOf(contexts) : null;
        return this;
    }

    /** Sets the AgentSoul defining identity, purpose, and values for conscious self-modeling (#597). */
    public SpectorMemoryBuilder agentSoul(com.spectrayan.spector.memory.model.AgentSoul soul) {
        this.agentSoul = soul;
        this.soul = soul;
        return this;
    }

    private java.util.concurrent.Executor suppliedExecutor;
    private com.spectrayan.spector.memory.scheduler.MemoryScheduler scheduler;
    private org.quartz.Scheduler customQuartzScheduler;

    /** Supplies a custom Executor for background task execution (defaults to ConcurrentTasks.virtualExecutor()). */
    public SpectorMemoryBuilder suppliedExecutor(java.util.concurrent.Executor executor) {
        this.suppliedExecutor = executor;
        return this;
    }

    /** Supplies a custom MemoryScheduler implementation. */
    public SpectorMemoryBuilder scheduler(com.spectrayan.spector.memory.scheduler.MemoryScheduler scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    /** Supplies a custom or shared Quartz Scheduler (e.g. from Spring Boot). If omitted, a default standalone in-memory scheduler is used. */
    public SpectorMemoryBuilder quartzScheduler(org.quartz.Scheduler quartzScheduler) {
        this.customQuartzScheduler = quartzScheduler;
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

    //  3-Layer Cognitive Graph configuration 

    /** Hebbian graph capacity (default: same as episodicPartitionCapacity). */
    public SpectorMemoryBuilder hebbianGraphCapacity(int c) { this.hebbianGraphCapacity = c; return this; }

    /** Temporal chain capacity (default: same as hebbianGraphCapacity). */
    public SpectorMemoryBuilder temporalChainCapacity(int c) { this.temporalChainCapacity = c; return this; }

    /** Entity extraction mode (default: NONE). */
    public SpectorMemoryBuilder entityExtractionMode(EntityExtractionMode mode) { this.entityExtractionMode = mode; return this; }

    /** Custom entity extractor (used when mode = CUSTOM). */
    public SpectorMemoryBuilder entityExtractor(EntityExtractor extractor) {
        this.entityExtractor = extractor;
        if (this.entityExtractionMode == EntityExtractionMode.NONE || this.entityExtractionMode == null) {
            this.entityExtractionMode = EntityExtractionMode.CUSTOM;
        }
        return this;
    }

    /** Entity graph capacity  --  max entities (default: 50,000). */
    public SpectorMemoryBuilder entityGraphCapacity(int c) { this.entityGraphCapacity = c; return this; }

    /** Max entities to extract per memory (default: 10). */
    public SpectorMemoryBuilder maxEntitiesPerMemory(int c) { this.maxEntitiesPerMemory = c; return this; }

    /** Ontology config for typing (default: null). */
    public SpectorMemoryBuilder ontologyConfig(com.spectrayan.spector.memory.graph.OntologyConfig config) { this.ontologyConfig = config; return this; }

    /** Max relations to extract per memory (default: 20). */
    public SpectorMemoryBuilder maxRelationsPerMemory(int c) { this.maxRelationsPerMemory = c; return this; }

    /** LLM generation options for entity extraction (temperature, maxTokens, topP). */
    public SpectorMemoryBuilder llmGenerationOptions(GenerationOptions opts) { this.llmGenerationOptions = opts; return this; }

    /** Graph scoring policy  --  configurable weights for cognitive graph steps (default: GraphScoringPolicy.DEFAULT). */
    public SpectorMemoryBuilder graphScoringPolicy(GraphScoringPolicy policy) { this.graphScoringPolicy = policy; return this; }

    /**
     * Sets the observation hook for pipeline telemetry.
     * @param hook the observation hook (defaults to NOOP)
     * @return this builder
     */
    public SpectorMemoryBuilder observationHook(MemoryObservationHook hook) {
        this.hook = hook;
        return this;
    }

    /** Temporal chain retention in days  --  links older than this are pruned during reflect() (default: 7). */
    public SpectorMemoryBuilder temporalRetentionDays(int days) { this.temporalRetentionDays = days; return this; }

    /** Checkpoint interval in seconds (default: 30). Set to 0 to disable automatic checkpointing. */
    public SpectorMemoryBuilder checkpointIntervalSeconds(int seconds) { this.checkpointIntervalSeconds = seconds; return this; }

    /** Two-Factor Memory (Bjork &amp; Bjork) configuration (default: TwoFactorConfig.DEFAULT). */
    public SpectorMemoryBuilder twoFactorConfig(TwoFactorConfig config) { this.twoFactorConfig = config; return this; }

    /** Edge importance scorer with configurable signal weights (default: EdgeImportance.DEFAULT). */
    public SpectorMemoryBuilder edgeImportance(EdgeImportance importance) { this.edgeImportance = importance; return this; }

    /** Maximum edges per node in the Hebbian graph (default: 24). */
    public SpectorMemoryBuilder hebbianMaxDegree(int maxDegree) { this.hebbianMaxDegree = maxDegree; return this; }

    /** Maximum edges per entity in the entity graph (default: 48). */
    public SpectorMemoryBuilder entityMaxDegree(int maxDegree) { this.entityMaxDegree = maxDegree; return this; }
    
    public SpectorMemoryBuilder entityResolutionEnabled(boolean enabled) {
        this.entityResolutionEnabled = enabled;
        return this;
    }

    public SpectorMemoryBuilder entityShadowMode(boolean shadow) {
        this.entityShadowMode = shadow;
        return this;
    }

    public SpectorMemoryBuilder entityCosineThreshold(float threshold) {
        this.entityCosineThreshold = threshold;
        return this;
    }

    /**
     * Parses a cognitive profile config from a YAML string value.
     * Supports: "ALL", "CORE_ONLY", "WITH_NEURODIVERGENT", or comma-separated profile names.
     * @see CognitiveProfileConfig#fromConfigValue(String)
     */
    public SpectorMemoryBuilder cognitiveProfiles(String configValue) { this.profileConfig = CognitiveProfileConfig.fromConfigValue(configValue); return this; }

    //  ID Generation 

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
     * <p>Enterprise callers supply a {@code TenantSalienceResolver} that merges
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

    /**
     * Sets the default salience profile for user/agent interest-driven importance and dream seeding.
     *
     * @param profile the salience profile
     * @return this builder
     */
    public SpectorMemoryBuilder salienceProfile(com.spectrayan.spector.memory.model.SalienceProfile profile) {
        this.salienceProfile = profile;
        return this;
    }

    /**
     * Sets a custom importance provider to replace the default importance scoring pipeline.
     *
     * <p>If not set, the engine uses {@link com.spectrayan.spector.memory.dopamine.DefaultImportanceProvider}
     * which preserves the existing Welford + ICNU + Flashbulb + salience-boost pipeline.</p>
     *
     * @param provider the custom importance provider (null = use default)
     * @return this builder
     * @since 1.2.0
     * @see ImportanceProvider
     */
    public SpectorMemoryBuilder importanceProvider(ImportanceProvider provider) {
        this.importanceProvider = provider;
        return this;
    }

    public SpectorMemoryBuilder maxActiveNamespaces(int maxActiveNamespaces) {
        this.maxActiveNamespaces = maxActiveNamespaces;
        return this;
    }

    public SpectorMemoryBuilder namespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
        return this;
    }

    /**
     * Injects the {@link com.spectrayan.spector.commons.cache.SpectorCacheManager} for managing query, topology, and graph caches.
     *
     * <p>When null (default), a standalone in-memory cache manager is automatically configured.</p>
     *
     * @param cacheManager cache manager instance (or null for default standalone)
     * @return this builder
     */
    public SpectorMemoryBuilder cacheManager(com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        return this;
    }

    /**
     * Sets the number of virtual threads processing the asynchronous entity extraction queue.
     *
     * <p>Default: 1 (sequential FIFO execution to prevent Ollama/LLM congestion).</p>
     *
     * @param parallelism number of worker threads (must be >= 1)
     * @return this builder
     */
    public SpectorMemoryBuilder entityExtractionParallelism(int parallelism) {
        this.entityExtractionParallelism = Math.max(1, parallelism);
        return this;
    }

    /**
     * Sets the bounded capacity of the asynchronous entity extraction queue.
     *
     * @param capacity maximum tasks in queue (must be >= 16)
     * @return this builder
     */
    public SpectorMemoryBuilder entityExtractionQueueCapacity(int capacity) {
        this.entityExtractionQueueCapacity = Math.max(16, capacity);
        return this;
    }

    /**
     * Applies configuration properties from a {@link com.spectrayan.spector.config.properties.MemoryProperties} instance (#605).
     *
     * @param properties memory configuration properties
     * @return this builder
     */
    public SpectorMemoryBuilder fromProperties(com.spectrayan.spector.config.properties.MemoryProperties properties) {
        if (properties == null) {
            return this;
        }
        if (properties.getDimensions() > 0) {
            this.dimensions = properties.getDimensions();
        }
        if (properties.getCapacity() > 0) {
            this.semanticCapacity = properties.getCapacity();
            this.hebbianGraphCapacity = properties.getCapacity();
            this.temporalChainCapacity = properties.getCapacity();
            this.entityGraphCapacity = properties.getCapacity();
        }
        if (properties.getNodesPerPartition() > 0) {
            this.nodesPerPartition = properties.getNodesPerPartition();
        }
        this.useBundleMode = properties.isBundleMode();
        if (properties.getPersistencePath() != null && !properties.getPersistencePath().isBlank()) {
            this.persistencePath = java.nio.file.Path.of(properties.getPersistencePath());
        }
        if (properties.getPersistenceMode() != null) {
            this.persistenceMode = MemoryPersistenceMode.valueOf(properties.getPersistenceMode().name());
        }
        if (properties.getAisme() != null) {
            this.aismeConfig = com.spectrayan.spector.memory.aisme.config.AismeConfig.fromProperties(properties.getAisme());
        }
        return this;
    }

    // ==============================================================
    // BUILD
    // ==============================================================

    /**
     * Builds and returns a fully-initialized {@link SpectorMemory} instance.
     *
     * @return the constructed SpectorMemory
     * @throws com.spectrayan.spector.commons.error.SpectorValidationException if required fields are missing
     */
    public SpectorMemory build() {
        if (dimensions <= 0 && embeddingProvider != null) {
            dimensions = embeddingProvider.dimensions();
        }
        return new DefaultSpectorMemory(this);
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = 
    // ACCESSORS
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = 

    public java.util.concurrent.Executor suppliedExecutor() { return suppliedExecutor; }
    public com.spectrayan.spector.memory.scheduler.MemoryScheduler scheduler() { return scheduler; }
    public org.quartz.Scheduler customQuartzScheduler() { return customQuartzScheduler; }
    public com.spectrayan.spector.memory.dream.relay.DreamConfig dreamConfig() { return dreamConfig; }
    public boolean managedByRegistry() { return managedByRegistry; }
    public boolean useBundleMode() { return useBundleMode; }
    public int dimensions() { return dimensions; }
    public EmbeddingProvider embeddingProvider() { return embeddingProvider; }
    public Path persistencePath() { return persistencePath; }
    public MemoryPersistenceMode persistenceMode() { return persistenceMode; }
    public int maxActiveNamespaces() { return maxActiveNamespaces; }
    public String namespaceId() { return namespaceId; }
    public boolean persistWorkingMemory() { return persistWorkingMemory; }
    public CircadianPolicy circadianPolicy() { return circadianPolicy; }
    public int workingCapacity() { return workingCapacity; }
    public int episodicPartitionCapacity() { return episodicPartitionCapacity; }
    public int semanticCapacity() { return semanticCapacity; }
    public int nodesPerPartition() { return nodesPerPartition; }
    public int proceduralCapacity() { return proceduralCapacity; }
    public int surpriseWarmup() { return surpriseWarmup; }
    public double flashbulbThreshold() { return flashbulbThreshold; }
    public float valenceLearningRate() { return valenceLearningRate; }
    public float deduplicationRadius() { return deduplicationRadius; }
    public LlmProvider LlmProvider() { return LlmProvider; }
    public ScalarQuantizer quantizer() { return quantizer; }
    public com.spectrayan.spector.index.VectorIndex semanticIndex() { return semanticIndex; }
    public long inhibitionTtlMs() { return inhibitionTtlMs; }
    public float inhibitionFloor() { return inhibitionFloor; }
    public IcnuWeights icnuWeights() { return icnuWeights; }
    public boolean pinSourceEpisodes() { return pinSourceEpisodes; }
    public int pinnedQuota() { return pinnedQuota; }
    public TagExtractor tagExtractor() { return tagExtractor; }
    public com.spectrayan.spector.memory.api.CognitiveProfileConfig profileConfig() { return profileConfig; }
    public MemoryObservationHook hook() { return hook; }
    public int hebbianGraphCapacity() { return hebbianGraphCapacity; }
    public int temporalChainCapacity() { return temporalChainCapacity; }
    public EntityExtractionMode entityExtractionMode() { return entityExtractionMode; }
    public EntityExtractor entityExtractor() { return entityExtractor; }
    public int entityGraphCapacity() { return entityGraphCapacity; }
    public int maxEntitiesPerMemory() { return maxEntitiesPerMemory; }
    public int maxRelationsPerMemory() { return maxRelationsPerMemory; }
    public GenerationOptions llmGenerationOptions() { return llmGenerationOptions; }
    public GraphScoringPolicy graphScoringPolicy() { return graphScoringPolicy; }
    public int temporalRetentionDays() { return temporalRetentionDays; }
    public TwoFactorConfig twoFactorConfig() { return twoFactorConfig; }
    public boolean entityResolutionEnabled() { return entityResolutionEnabled; }
    public boolean entityShadowMode() { return entityShadowMode; }
    public float entityCosineThreshold() { return entityCosineThreshold; }
    public com.spectrayan.spector.memory.graph.OntologyConfig ontologyConfig() { return ontologyConfig; }
    public EdgeImportance edgeImportance() { return edgeImportance; }
    public int hebbianMaxDegree() { return hebbianMaxDegree; }
    public int entityMaxDegree() { return entityMaxDegree; }
    public IdStrategy idStrategy() { return idStrategy; }
    public MemoryIdGenerator idGenerator() { return idGenerator; }
    public SparseEmbeddingProvider SparseEmbeddingProvider() { return SparseEmbeddingProvider; }
    public TokenEmbeddingProvider tokenEmbeddingProvider() { return tokenEmbeddingProvider; }
    public int checkpointIntervalSeconds() { return checkpointIntervalSeconds; }
    public com.spectrayan.spector.commons.chunker.TextChunker chunker() { return chunker; }
    public com.spectrayan.spector.commons.chunker.ChunkConfig chunkConfig() { return chunkConfig; }
    public int embedBatchSize() { return embedBatchSize; }
    public int entityExtractionParallelism() { return entityExtractionParallelism; }
    public int entityExtractionQueueCapacity() { return entityExtractionQueueCapacity; }
    public com.spectrayan.spector.memory.api.SalienceProfileProvider salienceProfileProvider() { return salienceProfileProvider; }
    public com.spectrayan.spector.memory.model.SalienceProfile salienceProfile() { return salienceProfile; }
    public com.spectrayan.spector.memory.api.ImportanceProvider importanceProvider() { return importanceProvider; }
    public com.spectrayan.spector.memory.persist.DataEncryptor dataEncryptor() { return dataEncryptor; }
    public List<SensoryExtractor> sensoryExtractors() { return sensoryExtractors; }
    public AssetStore assetStore() { return assetStore; }
    public com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager() { return cacheManager; }
    public int coactivationPairCapacity() { return coactivationPairCapacity; }
    public int coactivationEdgeCapacity() { return coactivationEdgeCapacity; }
    public long temporalFactsInitialSize() { return temporalFactsInitialSize; }
    public int indexMidxCapacity() { return indexMidxCapacity; }
    public long indexIdplSize() { return indexIdplSize; }
    public int typeRegistryCapacity() { return typeRegistryCapacity; }
    public long typeRegistrySize() { return typeRegistrySize; }
    public long insulaSize() { return insulaSize; }
    public int eagerConsolidationQueueCapacity() { return eagerConsolidationQueueCapacity; }
    public boolean usePathwayEngine() { return usePathwayEngine; }
    public com.spectrayan.spector.memory.aisme.config.AismeConfig aismeConfig() { return aismeConfig; }
    public com.spectrayan.spector.memory.model.AgentSoul agentSoul() { return agentSoul; }
    public com.spectrayan.spector.memory.model.SoulContext soul() { return soul; }
    public java.util.List<com.spectrayan.spector.memory.model.SoulContext> soulContexts() { return soulContexts; }

}
