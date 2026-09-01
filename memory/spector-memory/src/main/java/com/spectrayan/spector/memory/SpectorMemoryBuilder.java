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

import com.spectrayan.spector.memory.assembly.*;
import com.spectrayan.spector.memory.pathway.*;
import com.spectrayan.spector.memory.persist.*;
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
    public boolean managedByRegistry = false;
    public boolean useBundleMode = true;   // V4 bundle architecture (ADR-0004)
    public int dimensions;
    public EmbeddingProvider embeddingProvider;
    public Path persistencePath;
    public MemoryPersistenceMode persistenceMode = MemoryPersistenceMode.valueOf(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PERSISTENCE_MODE_NAME);
    public int maxActiveNamespaces = Integer.getInteger(
            com.spectrayan.spector.config.SpectorPropertyConstants.MEMORY_MAX_NAMESPACES,
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_MAX_NAMESPACES);
    public String namespaceId = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_NAMESPACE_ID;
    public boolean persistWorkingMemory = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PERSIST_WORKING_MEMORY;
    public CircadianPolicy circadianPolicy = CircadianPolicy.DEFAULT;
    public int workingCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_WORKING_CAPACITY;
    public int episodicPartitionCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_EPISODIC_PARTITION_CAPACITY;
    public int semanticCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SEMANTIC_CAPACITY;
    public int nodesPerPartition = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_NODES_PER_PARTITION;
    public int proceduralCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PROCEDURAL_CAPACITY;
    public int surpriseWarmup = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SURPRISE_WARMUP;
    public double flashbulbThreshold = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_FLASHBULB_THRESHOLD;
    public float valenceLearningRate = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_VALENCE_LEARNING_RATE;
    public float deduplicationRadius = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_DEDUPLICATION_RADIUS;
    public LlmProvider LlmProvider;
    public ScalarQuantizer quantizer;
    public com.spectrayan.spector.index.VectorIndex semanticIndex;
    public long inhibitionTtlMs = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_INHIBITION_TTL_MS;
    public float inhibitionFloor = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_INHIBITION_FLOOR;
    public IcnuWeights icnuWeights;
    public boolean pinSourceEpisodes = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PIN_SOURCE_EPISODES;
    public int pinnedQuota = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_PINNED_QUOTA;
    public TagExtractor tagExtractor;
    public CognitiveProfileConfig profileConfig = CognitiveProfileConfig.allEnabled();
    public MemoryObservationHook hook;

    // ─── 3-Layer Cognitive Graph configuration ───
    public int hebbianGraphCapacity = 0;
    public int temporalChainCapacity = 0;
    public EntityExtractionMode entityExtractionMode = EntityExtractionMode.valueOf(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_EXTRACTION_MODE);
    public EntityExtractor entityExtractor;
    public int entityGraphCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_GRAPH_CAPACITY;
    public int maxEntitiesPerMemory = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_MAX_PER_MEM;
    public int maxRelationsPerMemory = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_RELATION_MAX_PER_MEM;
    public GenerationOptions llmGenerationOptions;
    public GraphScoringPolicy graphScoringPolicy = GraphScoringPolicy.DEFAULT;
    public int temporalRetentionDays = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_RETENTION_DAYS;
    public TwoFactorConfig twoFactorConfig = TwoFactorConfig.DEFAULT;
    
    // Entity resolution config
    public boolean entityResolutionEnabled = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_RESOLUTION_ENABLED;
    public boolean entityShadowMode = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_SHADOW_MODE;
    public float entityCosineThreshold = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_COSINE_THRESHOLD;
    
    // Ontology config
    public com.spectrayan.spector.memory.graph.OntologyConfig ontologyConfig;

    // ─── Edge importance configuration ───
    public EdgeImportance edgeImportance = EdgeImportance.DEFAULT;
    public int hebbianMaxDegree = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE;
    public int entityMaxDegree = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_MAX_DEGREE;

    // ─── ID generation strategy ───
    public IdStrategy idStrategy = IdStrategy.valueOf(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ID_STRATEGY);
    public MemoryIdGenerator idGenerator;

    //  SPLADE + ColBERT providers 
    public SparseEmbeddingProvider SparseEmbeddingProvider;
    public TokenEmbeddingProvider tokenEmbeddingProvider;

    //  Checkpoint daemon configuration 
    public int checkpointIntervalSeconds = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_CHECKPOINT_INTERVAL_SECONDS;

    //  Chunking for remember() 
    public com.spectrayan.spector.commons.chunker.TextChunker chunker = new com.spectrayan.spector.commons.chunker.MarkdownChunker();
    public com.spectrayan.spector.commons.chunker.ChunkConfig chunkConfig = com.spectrayan.spector.commons.chunker.ChunkConfig.markdown(
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_INGESTION_CHUNK_SIZE,
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_INGESTION_CHUNK_OVERLAP);

    //  Embedding pipeline batch size 
    public int embedBatchSize = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_PROVIDER_EMBEDDING_BATCH_SIZE;

    //  Asynchronous entity extraction queue configuration
    public int entityExtractionParallelism = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_EXTRACTION_PARALLELISM;
    public int entityExtractionQueueCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY;

    //  Salience profile provider (enterprise SPI) 
    public SalienceProfileProvider salienceProfileProvider;
    public com.spectrayan.spector.memory.model.SalienceProfile salienceProfile;

    //  Importance provider SPI (#481) 
    public ImportanceProvider importanceProvider;

    //  Data encryption SPI 
    public DataEncryptor dataEncryptor = DataEncryptor.NOOP;

    //  Multimodal attachment processing 
    public List<SensoryExtractor> sensoryExtractors = List.of();
    public AssetStore assetStore;

    // ── Cache Manager SPI ──
    public com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager;

    // Configurable capacities/sizes for runtime bundle regions
    public int coactivationPairCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_PAIR_CAPACITY;
    public int coactivationEdgeCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_EDGE_CAPACITY;
    public long temporalFactsInitialSize = SpectorPropertyConstants.DEFAULT_MEMORY_TEMPORAL_FACTS_INITIAL_SIZE;
    public int indexMidxCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_INDEX_MIDX_CAPACITY;
    public long indexIdplSize = SpectorPropertyConstants.DEFAULT_MEMORY_INDEX_IDPL_SIZE;
    public int typeRegistryCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_TYPE_REGISTRY_CAPACITY;
    public long typeRegistrySize = SpectorPropertyConstants.DEFAULT_MEMORY_TYPE_REGISTRY_SIZE;
    public long insulaSize = SpectorPropertyConstants.DEFAULT_MEMORY_INSULA_SIZE;

    // Eager consolidation (#526)
    public int eagerConsolidationQueueCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_EAGER_CONSOLIDATION_QUEUE_CAPACITY;

    // Cognitive Pathway Engine (#561) — default engine (legacy pipeline deprecated)
    public boolean usePathwayEngine = Boolean.parseBoolean(System.getProperty("spector.pathway.enabled", "true"));

    // Active Inference Self-Model Engine (AISME) (#597)
    public com.spectrayan.spector.memory.aisme.config.AismeConfig aismeConfig = com.spectrayan.spector.memory.aisme.config.AismeConfig.disabled();
    public com.spectrayan.spector.memory.model.AgentSoul agentSoul;
    public com.spectrayan.spector.memory.model.SoulContext soul;
    public java.util.List<com.spectrayan.spector.memory.model.SoulContext> soulContexts;

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

    public com.spectrayan.spector.memory.dream.relay.DreamConfig dreamConfig = com.spectrayan.spector.memory.dream.relay.DreamConfig.defaultConfig();

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

    public java.util.concurrent.Executor suppliedExecutor;
    public com.spectrayan.spector.memory.scheduler.MemoryScheduler scheduler;
    public org.quartz.Scheduler customQuartzScheduler;

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
}
