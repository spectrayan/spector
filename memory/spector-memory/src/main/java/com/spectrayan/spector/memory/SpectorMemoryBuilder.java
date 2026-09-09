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

import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.commons.chunker.ChunkConfig;
import com.spectrayan.spector.commons.chunker.MarkdownChunker;
import com.spectrayan.spector.commons.chunker.TextChunker;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.spi.AcceleratorRegistry;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.ingestion.sensory.AssetStore;
import com.spectrayan.spector.ingestion.sensory.SensoryExtractor;
import com.spectrayan.spector.memory.api.CognitiveProfileConfig;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.api.SalienceProfileProvider;
import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.OntologyConfig;
import com.spectrayan.spector.memory.kernel.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.pathway.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.pathway.pipeline.TagExtractor;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.scheduler.MemoryScheduler;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

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

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryBuilder.class);

    // ── Configuration Snapshot ───────────────────────────────────
    private SpectorProperties properties;

    // ── Instance Coordinates & Execution Engine ─────────────────
    private Path persistencePath;
    private MemoryPersistenceMode persistenceMode;
    private String namespaceId;
    private boolean managedByRegistry = false;
    private boolean useBundleMode = true;   // V4 bundle architecture (ADR-0004)

    // ── Collaborators & SPI Providers ───────────────────────────
    private EmbeddingProvider embeddingProvider;
    private LlmProvider llmProvider;
    private SparseEmbeddingProvider sparseEmbeddingProvider;
    private TokenEmbeddingProvider tokenEmbeddingProvider;
    private ScalarQuantizer quantizer;
    private VectorIndex semanticIndex;
    private DataEncryptor dataEncryptor = DataEncryptor.NOOP;
    private MemoryObservationHook hook;
    private TagExtractor tagExtractor;
    private EntityExtractor entityExtractor;
    private List<SensoryExtractor> sensoryExtractors = List.of();
    private AssetStore assetStore;
    private SpectorCacheManager cacheManager;
    private MemoryScheduler scheduler;
    private org.quartz.Scheduler customQuartzScheduler;
    private Executor suppliedExecutor;
    private TextChunker chunker = new MarkdownChunker();
    private ChunkConfig chunkConfig = ChunkConfig.markdown(
            SpectorPropertyConstants.DEFAULT_INGESTION_CHUNK_SIZE,
            SpectorPropertyConstants.DEFAULT_INGESTION_CHUNK_OVERLAP);
    private MemoryIdGenerator idGenerator;
    private ImportanceProvider importanceProvider;
    private SalienceProfileProvider salienceProfileProvider;
    private SalienceProfile salienceProfile;
    private OntologyConfig ontologyConfig;
    private GenerationOptions llmGenerationOptions;
    private GraphScoringPolicy graphScoringPolicy = GraphScoringPolicy.DEFAULT;
    private EdgeImportance edgeImportance = EdgeImportance.DEFAULT;
    private RecallOptions defaultRecallOptions = RecallOptions.DEFAULT;
    private CognitiveProfileConfig profileConfig = CognitiveProfileConfig.allEnabled();
    private SoulContext soul;
    private AgentSoul agentSoul;
    private List<SoulContext> soulContexts;
    private IcnuWeights icnuWeights;
    private com.spectrayan.spector.config.properties.AismeProperties aismeConfig;
    private com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutor reflectSweepExecutor;

    // ==============================================================
    // CONSTRUCTORS & FACTORY
    // ==============================================================

    /**
     * Creates an unseeded builder with empty default configuration.
     * <p>Production applications should use {@link #create()} or {@link SpectorMemory#builder(SpectorProperties)}.</p>
     */
    public SpectorMemoryBuilder() {
        this(SpectorProperties.builder().build());
    }

    /**
     * Creates a builder initialized with the specified aggregate snapshot.
     */
    public SpectorMemoryBuilder(SpectorProperties properties) {
        fromProperties(properties != null ? properties : SpectorProperties.builder().build());
    }

    /**
     * Creates a new builder instance seeded with defaults from {@link SpectorProperties#load()}.
     *
     * <p><b>Process Bootstrap Semantics:</b> This method is intended for standalone process startup.
     * In addition to loading environment defaults, it initializes JVM-wide runtime systems
     * (e.g., GPU acceleration threshold in {@link AcceleratorRegistry}, structured concurrency in
     * {@link ConcurrentTasks}, and sleep sweep orchestrators in {@link com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutors}).</p>
     *
     * <p>For pure instance creation without JVM-wide side-effects (e.g., in unit tests or multi-tenant
     * environments), prefer {@link #create(SpectorProperties)} or {@link #createEmpty()}.</p>
     */
    public static SpectorMemoryBuilder create() {
        SpectorProperties props = SpectorProperties.load();
        if (props.hardware() != null) {
            AcceleratorRegistry.setBatchThreshold(
                    props.hardware().getGpuBatchThreshold());
        }
        if (props.concurrency() != null) {
            ConcurrentTasks.setStructuredEnabled(
                    props.concurrency().isStructured());
        }
        if (props.memory() != null && props.memory().getCircadian() != null
                && props.memory().getCircadian().getOrchestrator() != null
                && !props.memory().getCircadian().getOrchestrator().isBlank()) {
            com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutors.setOrchestrator(
                    props.memory().getCircadian().getOrchestrator());
        }
        return new SpectorMemoryBuilder(props);
    }

    /**
     * Creates a new unseeded builder instance with empty/default properties without loading
     * system defaults or mutating JVM runtime singletons.
     *
     * <p>Particularly useful for unit tests requiring isolated, predictable configuration state.</p>
     */
    public static SpectorMemoryBuilder createEmpty() {
        return new SpectorMemoryBuilder(SpectorProperties.builder().build());
    }

    /**
     * Creates a new builder instance initialized from explicit {@link SpectorProperties} without
     * mutating JVM process globals or loading environment defaults.
     *
     * <p>This pure factory is ideal for containerized or framework-managed environments (such as Spring Boot)
     * where configuration lifecycle is externally controlled.</p>
     *
     * @param props the explicit aggregate properties to initialize the builder with
     */
    public static SpectorMemoryBuilder create(SpectorProperties props) {
        return new SpectorMemoryBuilder(props);
    }

    /**
     * Returns the aggregate root configuration.
     */
    public SpectorProperties properties() {
        return this.properties;
    }

    /**
     * Applies configuration from the full aggregate {@link SpectorProperties}.
     */
    public SpectorMemoryBuilder fromProperties(SpectorProperties props) {
        this.properties = props != null ? props : SpectorProperties.builder().build();
        if (this.properties.memory() != null) {
            var mem = this.properties.memory();
            if (mem.getRecall() != null) {
                this.defaultRecallOptions = RecallOptions.from(mem.getRecall());
            }
            if (mem.getRemember() != null) {
                var chunk = mem.getRemember().getChunk();
                if (chunk != null) {
                    this.chunkConfig = ChunkConfig.markdown(chunk.getSize(), chunk.getOverlap());
                }
                var icnu = mem.getRemember().getIcnu();
                if (icnu != null) {
                    this.icnuWeights = new IcnuWeights(
                            icnu.getWeightInterest(), icnu.getWeightChallenge(),
                            icnu.getWeightNovelty(), icnu.getWeightUrgency());
                }
            }
            if (mem.getGraph() != null) {
                var graph = mem.getGraph();
                try {
                    var mode = com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionMode.valueOf(
                            graph.getExpansionMode() != null ? graph.getExpansionMode().toUpperCase(java.util.Locale.ROOT) : "AUTO");
                    this.graphScoringPolicy = new GraphScoringPolicy(
                            graph.getCausalBoost(),
                            graph.getHebbianBoost(),
                            graph.getTemporalForward(),
                            graph.getTemporalBackward(),
                            graph.getEntityAttenuation(),
                            graphScoringPolicy != null ? graphScoringPolicy.hebbianMaxDepth() : 2,
                            graphScoringPolicy != null ? graphScoringPolicy.temporalMaxHops() : 3,
                            graphScoringPolicy != null ? graphScoringPolicy.entityMaxHops() : 2,
                            graph.getExpansionThreshold(),
                            mode
                    );
                } catch (Exception e) {
                    log.warn("Failed to parse graph expansion mode '{}', keeping default", graph.getExpansionMode(), e);
                }
            } else if (mem.getGraphExpansionMode() != null || mem.getGraphExpansionThreshold() > 0) {
                try {
                    var mode = com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionMode.valueOf(
                            mem.getGraphExpansionMode() != null ? mem.getGraphExpansionMode().toUpperCase(java.util.Locale.ROOT) : "AUTO");
                    this.graphScoringPolicy = new GraphScoringPolicy(
                            1.2f, 1.1f, 1.05f, 0.95f, 0.9f,
                            graphScoringPolicy != null ? graphScoringPolicy.hebbianMaxDepth() : 2,
                            graphScoringPolicy != null ? graphScoringPolicy.temporalMaxHops() : 3,
                            graphScoringPolicy != null ? graphScoringPolicy.entityMaxHops() : 2,
                            mem.getGraphExpansionThreshold() > 0 ? mem.getGraphExpansionThreshold() : 0.4f,
                            mode
                    );
                } catch (Exception e) {
                    log.warn("Failed to parse graph expansion mode '{}', keeping default", mem.getGraphExpansionMode(), e);
                }
            }
            if (mem.getNamespaceId() != null && !mem.getNamespaceId().isBlank()) {
                this.namespaceId = mem.getNamespaceId();
            }
        }
        return this;
    }

    /**
     * Applies configuration from {@link com.spectrayan.spector.config.properties.MemoryProperties}
     * by wrapping it in an aggregate {@link SpectorProperties}.
     *
     * @deprecated Use {@link #fromProperties(SpectorProperties)} or {@link SpectorMemory#builder(SpectorProperties)}
     */
    @Deprecated(forRemoval = true)
    public SpectorMemoryBuilder fromProperties(com.spectrayan.spector.config.properties.MemoryProperties props) {
        return fromProperties(SpectorProperties.of(props));
    }

    /**
     * Compatibility setter for AISME configuration.
     * @deprecated Configure on {@code props.memory().setAisme(...)} instead.
     */
    @Deprecated(forRemoval = true)
    public SpectorMemoryBuilder aismeConfig(com.spectrayan.spector.config.properties.AismeProperties config) {
        this.aismeConfig = config;
        return this;
    }

    // ==============================================================
    // FLUENT SETTERS (Collaborators & Instance Coordinates Only)
    // ==============================================================

    public SpectorMemoryBuilder persistence(Path p) {
        this.persistencePath = p;
        if (this.properties != null && this.properties.memory() != null && p != null) {
            this.properties.memory().setPersistencePath(p.toString());
        }
        return this;
    }

    public SpectorMemoryBuilder persistenceMode(MemoryPersistenceMode mode) {
        this.persistenceMode = mode;
        if (this.properties != null && this.properties.memory() != null && mode != null) {
            this.properties.memory().setPersistenceMode(mode.name());
        }
        return this;
    }

    public SpectorMemoryBuilder namespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
        if (this.properties != null && this.properties.memory() != null) {
            this.properties.memory().setNamespaceId(namespaceId);
        }
        return this;
    }

    public SpectorMemoryBuilder managedByRegistry(boolean managed) {
        this.managedByRegistry = managed;
        return this;
    }

    /** Enable V4 bundle architecture (ADR-0004) — packs partition stores into .bundle files. */
    public SpectorMemoryBuilder bundleMode(boolean enable) {
        this.useBundleMode = enable;
        if (this.properties != null && this.properties.memory() != null) {
            this.properties.memory().setBundleMode(enable);
        }
        return this;
    }


    public SpectorMemoryBuilder embeddingProvider(EmbeddingProvider p) {
        this.embeddingProvider = p;
        return this;
    }

    public SpectorMemoryBuilder llmProvider(LlmProvider p) {
        this.llmProvider = p;
        return this;
    }

    public SpectorMemoryBuilder LlmProvider(LlmProvider p) {
        return llmProvider(p);
    }

    public SpectorMemoryBuilder sparseEmbeddingProvider(SparseEmbeddingProvider provider) {
        this.sparseEmbeddingProvider = provider;
        return this;
    }

    public SpectorMemoryBuilder SparseEmbeddingProvider(SparseEmbeddingProvider provider) {
        return sparseEmbeddingProvider(provider);
    }

    public SpectorMemoryBuilder tokenEmbeddingProvider(TokenEmbeddingProvider provider) {
        this.tokenEmbeddingProvider = provider;
        return this;
    }

    public SpectorMemoryBuilder quantizer(ScalarQuantizer quantizer) {
        this.quantizer = quantizer;
        return this;
    }

    public SpectorMemoryBuilder semanticIndex(VectorIndex idx) {
        this.semanticIndex = idx;
        return this;
    }

    public SpectorMemoryBuilder dataEncryptor(DataEncryptor encryptor) {
        this.dataEncryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        return this;
    }

    public SpectorMemoryBuilder observationHook(MemoryObservationHook hook) {
        this.hook = hook;
        return this;
    }

    public SpectorMemoryBuilder tagExtractor(TagExtractor te) {
        this.tagExtractor = te;
        return this;
    }

    public SpectorMemoryBuilder entityExtractor(EntityExtractor extractor) {
        this.entityExtractor = extractor;
        return this;
    }

    public SpectorMemoryBuilder sensoryExtractors(List<SensoryExtractor> extractors) {
        this.sensoryExtractors = extractors != null ? extractors : List.of();
        return this;
    }

    public SpectorMemoryBuilder assetStore(AssetStore store) {
        this.assetStore = store;
        return this;
    }

    public SpectorMemoryBuilder cacheManager(SpectorCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        return this;
    }

    public SpectorMemoryBuilder scheduler(MemoryScheduler scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    public SpectorMemoryBuilder quartzScheduler(org.quartz.Scheduler quartzScheduler) {
        this.customQuartzScheduler = quartzScheduler;
        return this;
    }

    public SpectorMemoryBuilder suppliedExecutor(Executor executor) {
        this.suppliedExecutor = executor;
        return this;
    }

    public SpectorMemoryBuilder chunker(TextChunker chunker, ChunkConfig config) {
        this.chunker = chunker != null ? chunker : new MarkdownChunker();
        this.chunkConfig = config != null ? config : ChunkConfig.DEFAULT;
        return this;
    }

    public SpectorMemoryBuilder chunkConfig(ChunkConfig config) {
        this.chunkConfig = config != null ? config : ChunkConfig.DEFAULT;
        return this;
    }

    public SpectorMemoryBuilder idGenerator(MemoryIdGenerator generator) {
        this.idGenerator = generator;
        return this;
    }

    public SpectorMemoryBuilder importanceProvider(ImportanceProvider provider) {
        this.importanceProvider = provider;
        return this;
    }

    public SpectorMemoryBuilder salienceProfileProvider(SalienceProfileProvider provider) {
        this.salienceProfileProvider = provider;
        return this;
    }

    public SpectorMemoryBuilder salienceProfile(SalienceProfile profile) {
        this.salienceProfile = profile;
        return this;
    }

    public SpectorMemoryBuilder soul(SoulContext soul) {
        this.soul = soul;
        if (soul instanceof AgentSoul agent) {
            this.agentSoul = agent;
        }
        return this;
    }

    public SpectorMemoryBuilder agentSoul(AgentSoul soul) {
        this.agentSoul = soul;
        this.soul = soul;
        return this;
    }

    public SpectorMemoryBuilder soulContexts(List<SoulContext> contexts) {
        this.soulContexts = contexts != null ? List.copyOf(contexts) : null;
        return this;
    }

    public SpectorMemoryBuilder ontologyConfig(OntologyConfig config) {
        this.ontologyConfig = config;
        return this;
    }

    public SpectorMemoryBuilder profileConfig(CognitiveProfileConfig config) {
        this.profileConfig = config;
        return this;
    }

    public SpectorMemoryBuilder graphScoringPolicy(GraphScoringPolicy policy) {
        this.graphScoringPolicy = policy;
        return this;
    }

    public SpectorMemoryBuilder edgeImportance(EdgeImportance importance) {
        this.edgeImportance = importance;
        return this;
    }

    public SpectorMemoryBuilder llmGenerationOptions(GenerationOptions opts) {
        this.llmGenerationOptions = opts;
        return this;
    }

    public SpectorMemoryBuilder icnuWeights(IcnuWeights w) {
        this.icnuWeights = w;
        return this;
    }

    public SpectorMemoryBuilder defaultRecallOptions(RecallOptions options) {
        this.defaultRecallOptions = options != null ? options : RecallOptions.DEFAULT;
        return this;
    }

    public SpectorMemoryBuilder reflectSweepExecutor(com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutor executor) {
        this.reflectSweepExecutor = executor;
        return this;
    }

    // ==============================================================
    // BUILD
    // ==============================================================

    public SpectorMemory build() {
        if (this.properties != null) {
            this.properties = this.properties.copy();
        } else {
            this.properties = SpectorProperties.builder().build();
        }
        if (embeddingProvider != null) {
            try {
                int dims = embeddingProvider.dimensions();
                if (dims > 0) {
                    this.properties.memory().setDimensions(dims);
                }
            } catch (Exception e) {
                log.debug("[Spector] Could not probe embedding provider dimensions eagerly: {}", e.getMessage());
            }
        }
        return new DefaultSpectorMemory(this);
    }

    // ==============================================================
    // ACCESSORS (Collaborators & Coordinates Only)
    // ==============================================================

    public SpectorProperties spectorProperties() { return properties; }
    public Path persistencePath() {
        return persistencePath != null ? persistencePath
                : (properties != null && properties.memory() != null && properties.memory().getPersistencePath() != null
                ? Path.of(properties.memory().getPersistencePath()) : null);
    }
    public MemoryPersistenceMode persistenceMode() {
        return persistenceMode != null ? persistenceMode
                : (properties != null && properties.memory() != null && properties.memory().getPersistenceMode() != null
                ? MemoryPersistenceMode.valueOf(properties.memory().getPersistenceMode().name()) : MemoryPersistenceMode.DISK);
    }
    public String namespaceId() {
        return namespaceId != null ? namespaceId
                : (properties != null && properties.memory() != null ? properties.memory().getNamespaceId() : null);
    }
    public boolean managedByRegistry() { return managedByRegistry; }
    public boolean useBundleMode() { return useBundleMode; }
    public EmbeddingProvider embeddingProvider() { return embeddingProvider; }
    public LlmProvider llmProvider() { return llmProvider; }
    public LlmProvider LlmProvider() { return llmProvider; }
    public SparseEmbeddingProvider sparseEmbeddingProvider() { return sparseEmbeddingProvider; }
    public SparseEmbeddingProvider SparseEmbeddingProvider() { return sparseEmbeddingProvider; }
    public TokenEmbeddingProvider tokenEmbeddingProvider() { return tokenEmbeddingProvider; }
    public ScalarQuantizer quantizer() { return quantizer; }
    public VectorIndex semanticIndex() { return semanticIndex; }
    public DataEncryptor dataEncryptor() { return dataEncryptor; }
    public MemoryObservationHook hook() { return hook; }
    public TagExtractor tagExtractor() { return tagExtractor; }
    public EntityExtractor entityExtractor() { return entityExtractor; }
    public List<SensoryExtractor> sensoryExtractors() { return sensoryExtractors; }
    public AssetStore assetStore() { return assetStore; }
    public SpectorCacheManager cacheManager() { return cacheManager; }
    public MemoryScheduler scheduler() { return scheduler; }
    public org.quartz.Scheduler customQuartzScheduler() { return customQuartzScheduler; }
    public Executor suppliedExecutor() { return suppliedExecutor; }
    public TextChunker chunker() { return chunker; }
    public ChunkConfig chunkConfig() { return chunkConfig; }
    public MemoryIdGenerator idGenerator() { return idGenerator; }
    public ImportanceProvider importanceProvider() { return importanceProvider; }
    public SalienceProfileProvider salienceProfileProvider() { return salienceProfileProvider; }
    public SalienceProfile salienceProfile() { return salienceProfile; }
    public SoulContext soul() { return soul; }
    public AgentSoul agentSoul() { return agentSoul; }
    public List<SoulContext> soulContexts() { return soulContexts; }
    public OntologyConfig ontologyConfig() { return ontologyConfig; }
    public CognitiveProfileConfig profileConfig() { return profileConfig; }
    public GraphScoringPolicy graphScoringPolicy() { return graphScoringPolicy; }
    public EdgeImportance edgeImportance() { return edgeImportance; }
    public GenerationOptions llmGenerationOptions() { return llmGenerationOptions; }
    public IcnuWeights icnuWeights() { return icnuWeights; }
    public RecallOptions defaultRecallOptions() { return defaultRecallOptions; }
    public com.spectrayan.spector.config.properties.AismeProperties aismeConfig() {
        if (aismeConfig != null) return aismeConfig;
        return properties != null && properties.memory() != null && properties.memory().getAisme() != null
                ? com.spectrayan.spector.config.properties.AismeProperties.fromProperties(properties.memory().getAisme()) : null;
    }
    public com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutor reflectSweepExecutor() {
        return reflectSweepExecutor;
    }
}
