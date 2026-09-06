/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import com.spectrayan.spector.config.model.HnswPrefilterMode;
import com.spectrayan.spector.config.model.RememberTier;
import com.spectrayan.spector.config.model.PersistenceMode;
import com.spectrayan.spector.config.model.TagExtractorMode;
import com.spectrayan.spector.config.model.TextSearchMode;

import java.io.Serializable;
import java.util.Locale;

/**
 * Canonical configuration properties POJO for Spector Cognitive Memory.
 */
public class MemoryProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = DEFAULT_MEMORY_ENABLED;
    private int maxMemories = 0;
    private PersistenceMode persistenceMode = DEFAULT_MEMORY_PERSISTENCE_MODE;
    private String persistencePath = DEFAULT_MEMORY_PERSISTENCE_PATH.toString();
    private int dimensions = DEFAULT_MEMORY_DIMENSIONS;
    private int capacity = DEFAULT_MEMORY_CAPACITY;
    private int nodesPerPartition = DEFAULT_MEMORY_NODES_PER_PARTITION;
    private int workingCapacity = DEFAULT_MEMORY_WORKING_CAPACITY;
    private int episodicPartitionCapacity = DEFAULT_MEMORY_EPISODIC_PARTITION_CAPACITY;
    private int semanticCapacity = DEFAULT_MEMORY_SEMANTIC_CAPACITY;
    private int proceduralCapacity = DEFAULT_MEMORY_PROCEDURAL_CAPACITY;
    private int entityGraphCapacity = DEFAULT_MEMORY_ENTITY_GRAPH_CAPACITY;
    private int pinnedQuota = DEFAULT_MEMORY_PINNED_QUOTA;
    private int checkpointIntervalSeconds = DEFAULT_MEMORY_CHECKPOINT_INTERVAL_SECONDS;

    private long textSegmentSize = DEFAULT_MEMORY_TEXT_SEGMENT_SIZE;
    private long episodicSegmentSize = DEFAULT_MEMORY_EPISODIC_SEGMENT_SIZE;
    private boolean persistWorkingMemory = DEFAULT_MEMORY_PERSIST_WORKING_MEMORY;
    private boolean pinSourceEpisodes = DEFAULT_MEMORY_PIN_SOURCE_EPISODES;

    private String idStrategy = DEFAULT_MEMORY_ID_STRATEGY;
    private String edgeImportance = DEFAULT_MEMORY_EDGE_IMPORTANCE;
    private String namespaceId = DEFAULT_MEMORY_NAMESPACE_ID;

    private RememberTier defaultIngestionTier = DEFAULT_MEMORY_DEFAULT_INGESTION_TIER;
    private HnswPrefilterMode hnswPrefilter = DEFAULT_MEMORY_HNSW_PREFILTER;
    private TagExtractorMode tagExtractor = DEFAULT_MEMORY_TAG_EXTRACTOR;
    private String tagExtractorModel = DEFAULT_MEMORY_TAG_EXTRACTOR_MODEL;
    private TextSearchMode textSearchMode = DEFAULT_MEMORY_TEXT_SEARCH_MODE;

    private boolean spladeEnabled = DEFAULT_MEMORY_SPLADE_ENABLED;
    private boolean colbertEnabled = DEFAULT_MEMORY_COLBERT_ENABLED;
    private boolean bm25Enabled = DEFAULT_MEMORY_BM25_ENABLED;
    private boolean bundleMode = false;
    private int coactivationPairCapacity = DEFAULT_MEMORY_COACTIVATION_PAIR_CAPACITY;
    private int coactivationEdgeCapacity = DEFAULT_MEMORY_COACTIVATION_EDGE_CAPACITY;
    private long temporalFactsInitialSize = DEFAULT_MEMORY_TEMPORAL_FACTS_INITIAL_SIZE;
    private int indexMidxCapacity = DEFAULT_MEMORY_INDEX_MIDX_CAPACITY;
    private long indexIdplSize = DEFAULT_MEMORY_INDEX_IDPL_SIZE;
    private int typeRegistryCapacity = DEFAULT_MEMORY_TYPE_REGISTRY_CAPACITY;
    private long typeRegistrySize = DEFAULT_MEMORY_TYPE_REGISTRY_SIZE;
    private long insulaSize = DEFAULT_MEMORY_INSULA_SIZE;
    private int provenanceCapacity = DEFAULT_MEMORY_PROVENANCE_CAPACITY;
    private int hebbianGraphCapacity = 0;
    private int temporalChainCapacity = 0;

    private String graphExpansionMode = DEFAULT_MEMORY_GRAPH_EXPANSION_MODE;
    private float graphExpansionThreshold = DEFAULT_MEMORY_GRAPH_EXPANSION_THRESHOLD;
    private boolean enableMmr = DEFAULT_MEMORY_RETRIEVAL_ENABLE_MMR;
    private float mmrLambda = DEFAULT_MEMORY_RETRIEVAL_MMR_LAMBDA;
    private boolean schedulerEnabled = DEFAULT_MEMORY_SCHEDULER_ENABLED;
    private boolean wanderEnabled = DEFAULT_MEMORY_WANDER_ENABLED;
    private boolean dreamEnabled = DEFAULT_MEMORY_DREAM_ENABLED;

    private DecayProperties decay = new DecayProperties();
    private ConsolidationProperties consolidation = new ConsolidationProperties();
    private LlmProperties llm = new LlmProperties();
    private AismeProperties aisme = new AismeProperties();

    private TaskQueueProperties taskQueue = new TaskQueueProperties();
    private TaskQueueProperties entityExtractionTaskQueue = new TaskQueueProperties();
    private TaskQueueProperties consolidationTaskQueue = new TaskQueueProperties();

    private int entityExtractionParallelism = DEFAULT_MEMORY_ENTITY_EXTRACTION_PARALLELISM;
    private int entityExtractionQueueCapacity = DEFAULT_MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY;

    // ─── Sub-Domain Children ───
    private RecallProperties recall = new RecallProperties();
    private RememberProperties remember = new RememberProperties();
    private GraphProperties graph = new GraphProperties();
    private CircadianProperties circadian = new CircadianProperties();
    private DreamProperties dream = new DreamProperties();
    private TwoFactorProperties twofactor = new TwoFactorProperties();
    private WalProperties wal = new WalProperties();
    private VacuumProperties vacuum = new VacuumProperties();
    private SessionProperties session = new SessionProperties();
    private int maxNamespaces = 100;
    private boolean pathwayEnabled = true;

    public MemoryProperties() {}

    public MemoryProperties(int maxMemories, int dimensions) {
        if (maxMemories > 0) this.maxMemories = maxMemories;
        if (dimensions > 0) this.dimensions = dimensions;
    }

    public MemoryProperties(int maxMemories, int dimensions, ConsolidationProperties consolidation) {
        this(maxMemories, dimensions);
        if (consolidation != null) this.consolidation = consolidation;
    }

    public boolean isEnabled() { return enabled; }
    public MemoryProperties setEnabled(boolean enabled) { this.enabled = enabled; return this; }

    public int getMaxMemories() { return maxMemories; }
    public MemoryProperties setMaxMemories(int maxMemories) { this.maxMemories = maxMemories; return this; }

    public PersistenceMode getPersistenceMode() { return persistenceMode; }
    public MemoryProperties setPersistenceMode(PersistenceMode persistenceMode) {
        if (persistenceMode != null) this.persistenceMode = persistenceMode;
        return this;
    }
    public MemoryProperties setPersistenceMode(String persistenceMode) {
        if (persistenceMode != null && !persistenceMode.isBlank()) {
            try {
                this.persistenceMode = PersistenceMode.valueOf(persistenceMode.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
        return this;
    }

    public String getPersistencePath() { return persistencePath; }
    public MemoryProperties setPersistencePath(String persistencePath) {
        if (persistencePath != null && !persistencePath.isBlank()) {
            this.persistencePath = persistencePath;
        }
        return this;
    }

    public int getDimensions() { return dimensions; }
    public MemoryProperties setDimensions(int dimensions) {
        if (dimensions > 0) this.dimensions = dimensions;
        return this;
    }

    public int getCapacity() { return capacity; }
    public MemoryProperties setCapacity(int capacity) {
        if (capacity > 0) this.capacity = capacity;
        return this;
    }

    public int getNodesPerPartition() { return nodesPerPartition; }
    public MemoryProperties setNodesPerPartition(int nodesPerPartition) {
        if (nodesPerPartition > 0) this.nodesPerPartition = nodesPerPartition;
        return this;
    }

    public RememberTier getDefaultIngestionTier() { return defaultIngestionTier; }
    public MemoryProperties setDefaultIngestionTier(RememberTier defaultIngestionTier) {
        if (defaultIngestionTier != null) this.defaultIngestionTier = defaultIngestionTier;
        return this;
    }
    public MemoryProperties setDefaultIngestionTier(String defaultIngestionTier) {
        if (defaultIngestionTier != null && !defaultIngestionTier.isBlank()) {
            try {
                this.defaultIngestionTier = RememberTier.valueOf(defaultIngestionTier.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
        return this;
    }

    public HnswPrefilterMode getHnswPrefilter() { return hnswPrefilter; }
    public MemoryProperties setHnswPrefilter(HnswPrefilterMode hnswPrefilter) {
        if (hnswPrefilter != null) this.hnswPrefilter = hnswPrefilter;
        return this;
    }
    public MemoryProperties setHnswPrefilter(String hnswPrefilter) {
        if (hnswPrefilter != null && !hnswPrefilter.isBlank()) {
            try {
                this.hnswPrefilter = HnswPrefilterMode.valueOf(hnswPrefilter.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
        return this;
    }

    public TagExtractorMode getTagExtractor() { return tagExtractor; }
    public MemoryProperties setTagExtractor(TagExtractorMode tagExtractor) {
        if (tagExtractor != null) this.tagExtractor = tagExtractor;
        return this;
    }
    public MemoryProperties setTagExtractor(String tagExtractor) {
        if (tagExtractor != null && !tagExtractor.isBlank()) {
            try {
                this.tagExtractor = TagExtractorMode.valueOf(tagExtractor.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
        return this;
    }

    public String getTagExtractorModel() { return tagExtractorModel; }
    public MemoryProperties setTagExtractorModel(String tagExtractorModel) {
        if (tagExtractorModel != null && !tagExtractorModel.isBlank()) {
            this.tagExtractorModel = tagExtractorModel;
        }
        return this;
    }

    public TextSearchMode getTextSearchMode() { return textSearchMode; }
    public MemoryProperties setTextSearchMode(TextSearchMode textSearchMode) {
        if (textSearchMode != null) this.textSearchMode = textSearchMode;
        return this;
    }
    public MemoryProperties setTextSearchMode(String textSearchMode) {
        if (textSearchMode != null && !textSearchMode.isBlank()) {
            try {
                this.textSearchMode = TextSearchMode.valueOf(textSearchMode.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
        return this;
    }

    public boolean isSpladeEnabled() { return spladeEnabled; }
    public MemoryProperties setSpladeEnabled(boolean spladeEnabled) { this.spladeEnabled = spladeEnabled; return this; }

    public boolean isColbertEnabled() { return colbertEnabled; }
    public MemoryProperties setColbertEnabled(boolean colbertEnabled) { this.colbertEnabled = colbertEnabled; return this; }

    public boolean isBm25Enabled() { return bm25Enabled; }
    public MemoryProperties setBm25Enabled(boolean bm25Enabled) { this.bm25Enabled = bm25Enabled; return this; }

    public boolean isBundleMode() { return bundleMode; }
    public MemoryProperties setBundleMode(boolean bundleMode) { this.bundleMode = bundleMode; return this; }

    public DecayProperties getDecay() { return decay; }
    public MemoryProperties setDecay(DecayProperties decay) { this.decay = decay; return this; }

    public ConsolidationProperties getConsolidation() { return consolidation; }
    public MemoryProperties setConsolidation(ConsolidationProperties consolidation) { this.consolidation = consolidation; return this; }

    public LlmProperties getLlm() { return llm; }
    public MemoryProperties setLlm(LlmProperties llm) {
        if (llm != null) this.llm = llm;
        return this;
    }

    public TaskQueueProperties getTaskQueue() { return taskQueue; }
    public MemoryProperties setTaskQueue(TaskQueueProperties taskQueue) {
        if (taskQueue != null) this.taskQueue = taskQueue;
        return this;
    }
    public TaskQueueProperties taskQueue() { return getTaskQueue(); }

    public TaskQueueProperties getEntityExtractionTaskQueue() { return entityExtractionTaskQueue; }
    public MemoryProperties setEntityExtractionTaskQueue(TaskQueueProperties entityExtractionTaskQueue) {
        if (entityExtractionTaskQueue != null) this.entityExtractionTaskQueue = entityExtractionTaskQueue;
        return this;
    }
    public TaskQueueProperties entityExtractionTaskQueue() { return getEntityExtractionTaskQueue(); }

    public TaskQueueProperties getConsolidationTaskQueue() { return consolidationTaskQueue; }
    public MemoryProperties setConsolidationTaskQueue(TaskQueueProperties consolidationTaskQueue) {
        if (consolidationTaskQueue != null) this.consolidationTaskQueue = consolidationTaskQueue;
        return this;
    }
    public TaskQueueProperties consolidationTaskQueue() { return getConsolidationTaskQueue(); }

    public boolean enabled() { return isEnabled(); }
    public int maxMemories() { return getMaxMemories(); }
    public PersistenceMode persistenceMode() { return getPersistenceMode(); }
    public String persistencePath() { return getPersistencePath(); }
    public int dimensions() { return getDimensions(); }
    public int capacity() { return getCapacity(); }
    public int nodesPerPartition() { return getNodesPerPartition(); }
    public RememberTier defaultIngestionTier() { return getDefaultIngestionTier(); }
    public HnswPrefilterMode hnswPrefilter() { return getHnswPrefilter(); }
    public TagExtractorMode tagExtractor() { return getTagExtractor(); }
    public String tagExtractorModel() { return getTagExtractorModel(); }
    public TextSearchMode textSearchMode() { return getTextSearchMode(); }
    public boolean spladeEnabled() { return isSpladeEnabled(); }
    public boolean colbertEnabled() { return isColbertEnabled(); }
    public boolean bm25Enabled() { return isBm25Enabled(); }
    public boolean bundleMode() { return isBundleMode(); }
    public DecayProperties decay() { return getDecay(); }
    public ConsolidationProperties consolidation() { return getConsolidation(); }
    public LlmProperties llm() { return getLlm(); }

    public int getCoactivationPairCapacity() { return coactivationPairCapacity; }
    public MemoryProperties setCoactivationPairCapacity(int coactivationPairCapacity) { this.coactivationPairCapacity = coactivationPairCapacity; return this; }
    public int coactivationPairCapacity() { return coactivationPairCapacity; }

    public int getCoactivationEdgeCapacity() { return coactivationEdgeCapacity; }
    public MemoryProperties setCoactivationEdgeCapacity(int coactivationEdgeCapacity) { this.coactivationEdgeCapacity = coactivationEdgeCapacity; return this; }
    public int coactivationEdgeCapacity() { return coactivationEdgeCapacity; }

    public long getTemporalFactsInitialSize() { return temporalFactsInitialSize; }
    public MemoryProperties setTemporalFactsInitialSize(long temporalFactsInitialSize) { this.temporalFactsInitialSize = temporalFactsInitialSize; return this; }
    public long temporalFactsInitialSize() { return temporalFactsInitialSize; }

    public int getIndexMidxCapacity() { return indexMidxCapacity; }
    public MemoryProperties setIndexMidxCapacity(int indexMidxCapacity) { this.indexMidxCapacity = indexMidxCapacity; return this; }
    public int indexMidxCapacity() { return indexMidxCapacity; }

    public long getIndexIdplSize() { return indexIdplSize; }
    public MemoryProperties setIndexIdplSize(long indexIdplSize) { this.indexIdplSize = indexIdplSize; return this; }
    public long indexIdplSize() { return indexIdplSize; }

    public int getTypeRegistryCapacity() { return typeRegistryCapacity; }
    public MemoryProperties setTypeRegistryCapacity(int typeRegistryCapacity) { this.typeRegistryCapacity = typeRegistryCapacity; return this; }
    public int typeRegistryCapacity() { return typeRegistryCapacity; }

    public long getTypeRegistrySize() { return typeRegistrySize; }
    public MemoryProperties setTypeRegistrySize(long typeRegistrySize) { this.typeRegistrySize = typeRegistrySize; return this; }
    public long typeRegistrySize() { return typeRegistrySize; }

    public long getInsulaSize() { return insulaSize; }
    public MemoryProperties setInsulaSize(long insulaSize) { this.insulaSize = insulaSize; return this; }
    public long insulaSize() { return insulaSize; }

    public int getProvenanceCapacity() { return provenanceCapacity; }
    public MemoryProperties setProvenanceCapacity(int provenanceCapacity) { if (provenanceCapacity > 0) this.provenanceCapacity = provenanceCapacity; return this; }
    public int provenanceCapacity() { return provenanceCapacity; }

    public int getEntityExtractionParallelism() { return entityExtractionTaskQueue.getParallelism(); }
    public MemoryProperties setEntityExtractionParallelism(int entityExtractionParallelism) { this.entityExtractionTaskQueue.setParallelism(entityExtractionParallelism); return this; }
    public int entityExtractionParallelism() { return getEntityExtractionParallelism(); }

    public int getEntityExtractionQueueCapacity() { return entityExtractionTaskQueue.getCapacity(); }
    public MemoryProperties setEntityExtractionQueueCapacity(int entityExtractionQueueCapacity) { this.entityExtractionTaskQueue.setCapacity(entityExtractionQueueCapacity); return this; }
    public int entityExtractionQueueCapacity() { return getEntityExtractionQueueCapacity(); }

    public AismeProperties getAisme() { return aisme; }
    public MemoryProperties setAisme(AismeProperties aisme) {
        if (aisme != null) this.aisme = aisme;
        return this;
    }
    public AismeProperties aisme() { return getAisme(); }

    public String getGraphExpansionMode() { return graphExpansionMode; }
    public MemoryProperties setGraphExpansionMode(String graphExpansionMode) {
        if (graphExpansionMode != null && !graphExpansionMode.isBlank()) {
            this.graphExpansionMode = graphExpansionMode;
        }
        return this;
    }
    public String graphExpansionMode() { return graphExpansionMode; }

    public float getGraphExpansionThreshold() { return graphExpansionThreshold; }
    public MemoryProperties setGraphExpansionThreshold(float graphExpansionThreshold) {
        this.graphExpansionThreshold = graphExpansionThreshold;
        return this;
    }
    public float graphExpansionThreshold() { return graphExpansionThreshold; }

    @Deprecated(forRemoval = true)
    public boolean isEnableMmr() {
        return recall != null && recall.getMmr() != null ? recall.getMmr().isEnabled() : enableMmr;
    }

    @Deprecated(forRemoval = true)
    public MemoryProperties setEnableMmr(boolean enableMmr) {
        this.enableMmr = enableMmr;
        if (recall != null && recall.getMmr() != null) {
            recall.getMmr().setEnabled(enableMmr);
        }
        return this;
    }

    @Deprecated(forRemoval = true)
    public boolean enableMmr() { return isEnableMmr(); }

    @Deprecated(forRemoval = true)
    public float getMmrLambda() {
        return recall != null && recall.getMmr() != null ? recall.getMmr().getLambda() : mmrLambda;
    }

    @Deprecated(forRemoval = true)
    public MemoryProperties setMmrLambda(float mmrLambda) {
        this.mmrLambda = mmrLambda;
        if (recall != null && recall.getMmr() != null) {
            recall.getMmr().setLambda(mmrLambda);
        }
        return this;
    }

    @Deprecated(forRemoval = true)
    public float mmrLambda() { return getMmrLambda(); }

    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public MemoryProperties setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; return this; }
    public boolean schedulerEnabled() { return schedulerEnabled; }

    public boolean isWanderEnabled() { return wanderEnabled; }
    public MemoryProperties setWanderEnabled(boolean wanderEnabled) { this.wanderEnabled = wanderEnabled; return this; }
    public boolean wanderEnabled() { return wanderEnabled; }

    public boolean isDreamEnabled() { return dreamEnabled; }
    public MemoryProperties setDreamEnabled(boolean dreamEnabled) { this.dreamEnabled = dreamEnabled; return this; }
    public boolean dreamEnabled() { return dreamEnabled; }

    // ─── Sub-Domain Children Accessors ───

    public RecallProperties getRecall() { return recall; }
    public MemoryProperties setRecall(RecallProperties recall) {
        if (recall != null) this.recall = recall;
        return this;
    }
    public RecallProperties recall() { return recall; }

    public RememberProperties getRemember() { return remember; }
    public MemoryProperties setRemember(RememberProperties remember) {
        if (remember != null) this.remember = remember;
        return this;
    }
    public RememberProperties remember() { return remember; }

    public GraphProperties getGraph() { return graph; }
    public MemoryProperties setGraph(GraphProperties graph) {
        if (graph != null) this.graph = graph;
        return this;
    }
    public GraphProperties graph() { return graph; }

    public CircadianProperties getCircadian() { return circadian; }
    public MemoryProperties setCircadian(CircadianProperties circadian) {
        if (circadian != null) this.circadian = circadian;
        return this;
    }
    public CircadianProperties circadian() { return circadian; }

    public int getMaxNamespaces() { return maxNamespaces; }
    public MemoryProperties setMaxNamespaces(int maxNamespaces) {
        if (maxNamespaces > 0) this.maxNamespaces = maxNamespaces;
        return this;
    }
    public int maxNamespaces() { return maxNamespaces; }

    public boolean isPathwayEnabled() { return pathwayEnabled; }
    public MemoryProperties setPathwayEnabled(boolean pathwayEnabled) { this.pathwayEnabled = pathwayEnabled; return this; }
    public boolean pathwayEnabled() { return pathwayEnabled; }

    public int getWorkingCapacity() { return workingCapacity; }
    public MemoryProperties setWorkingCapacity(int workingCapacity) { this.workingCapacity = workingCapacity; return this; }
    public int workingCapacity() { return workingCapacity; }

    public int getEpisodicPartitionCapacity() { return episodicPartitionCapacity; }
    public MemoryProperties setEpisodicPartitionCapacity(int episodicPartitionCapacity) { this.episodicPartitionCapacity = episodicPartitionCapacity; return this; }
    public int episodicPartitionCapacity() { return episodicPartitionCapacity; }

    public int getSemanticCapacity() { return semanticCapacity; }
    public MemoryProperties setSemanticCapacity(int semanticCapacity) { this.semanticCapacity = semanticCapacity; return this; }
    public int semanticCapacity() { return semanticCapacity; }

    public int getProceduralCapacity() { return proceduralCapacity; }
    public MemoryProperties setProceduralCapacity(int proceduralCapacity) { this.proceduralCapacity = proceduralCapacity; return this; }
    public int proceduralCapacity() { return proceduralCapacity; }

    public int getEntityGraphCapacity() { return entityGraphCapacity; }
    public MemoryProperties setEntityGraphCapacity(int entityGraphCapacity) { this.entityGraphCapacity = entityGraphCapacity; return this; }
    public int entityGraphCapacity() { return entityGraphCapacity; }

    public int getPinnedQuota() { return pinnedQuota; }
    public MemoryProperties setPinnedQuota(int pinnedQuota) { this.pinnedQuota = pinnedQuota; return this; }
    public int pinnedQuota() { return pinnedQuota; }

    public int getCheckpointIntervalSeconds() { return checkpointIntervalSeconds; }
    public MemoryProperties setCheckpointIntervalSeconds(int checkpointIntervalSeconds) { this.checkpointIntervalSeconds = checkpointIntervalSeconds; return this; }
    public int checkpointIntervalSeconds() { return checkpointIntervalSeconds; }

    public long getTextSegmentSize() { return textSegmentSize; }
    public MemoryProperties setTextSegmentSize(long textSegmentSize) { this.textSegmentSize = textSegmentSize; return this; }
    public long textSegmentSize() { return textSegmentSize; }

    public long getEpisodicSegmentSize() { return episodicSegmentSize; }
    public MemoryProperties setEpisodicSegmentSize(long episodicSegmentSize) { this.episodicSegmentSize = episodicSegmentSize; return this; }
    public long episodicSegmentSize() { return episodicSegmentSize; }

    public boolean isPersistWorkingMemory() { return persistWorkingMemory; }
    public MemoryProperties setPersistWorkingMemory(boolean persistWorkingMemory) { this.persistWorkingMemory = persistWorkingMemory; return this; }
    public boolean persistWorkingMemory() { return persistWorkingMemory; }

    public boolean isPinSourceEpisodes() { return pinSourceEpisodes; }
    public MemoryProperties setPinSourceEpisodes(boolean pinSourceEpisodes) { this.pinSourceEpisodes = pinSourceEpisodes; return this; }
    public boolean pinSourceEpisodes() { return pinSourceEpisodes; }

    public String getIdStrategy() { return idStrategy; }
    public MemoryProperties setIdStrategy(String idStrategy) { this.idStrategy = idStrategy; return this; }
    public String idStrategy() { return idStrategy; }

    public String getEdgeImportance() { return edgeImportance; }
    public MemoryProperties setEdgeImportance(String edgeImportance) { this.edgeImportance = edgeImportance; return this; }
    public String edgeImportance() { return edgeImportance; }

    public String getNamespaceId() { return namespaceId; }
    public MemoryProperties setNamespaceId(String namespaceId) { this.namespaceId = namespaceId; return this; }
    public String namespaceId() { return namespaceId; }

    public DreamProperties getDream() { return dream; }
    public MemoryProperties setDream(DreamProperties dream) {
        if (dream != null) this.dream = dream;
        return this;
    }
    public DreamProperties dream() { return dream; }

    public TwoFactorProperties getTwofactor() { return twofactor; }
    public MemoryProperties setTwofactor(TwoFactorProperties twofactor) {
        if (twofactor != null) this.twofactor = twofactor;
        return this;
    }
    public TwoFactorProperties twofactor() { return twofactor; }

    public WalProperties getWal() { return wal; }
    public MemoryProperties setWal(WalProperties wal) {
        if (wal != null) this.wal = wal;
        return this;
    }
    public WalProperties wal() { return wal; }

    public VacuumProperties getVacuum() { return vacuum; }
    public MemoryProperties setVacuum(VacuumProperties vacuum) {
        if (vacuum != null) this.vacuum = vacuum;
        return this;
    }
    public VacuumProperties vacuum() { return vacuum; }

    public SessionProperties getSession() { return session; }
    public MemoryProperties setSession(SessionProperties session) {
        if (session != null) this.session = session;
        return this;
    }
    public SessionProperties session() { return session; }

    public int getHebbianGraphCapacity() { return hebbianGraphCapacity; }
    public MemoryProperties setHebbianGraphCapacity(int hebbianGraphCapacity) { this.hebbianGraphCapacity = hebbianGraphCapacity; return this; }
    public int hebbianGraphCapacity() { return hebbianGraphCapacity; }

    public int getTemporalChainCapacity() { return temporalChainCapacity; }
    public MemoryProperties setTemporalChainCapacity(int temporalChainCapacity) { this.temporalChainCapacity = temporalChainCapacity; return this; }
    public int temporalChainCapacity() { return temporalChainCapacity; }

    // ─────────────── Fluent Configuration API ───────────────

    public MemoryProperties dimensions(int d) { setDimensions(d); return this; }
    public MemoryProperties capacity(int c) { setCapacity(c); return this; }
    public MemoryProperties workingCapacity(int c) { setWorkingCapacity(c); return this; }
    public MemoryProperties semanticCapacity(int c) { setSemanticCapacity(c); return this; }
    public MemoryProperties episodicPartitionCapacity(int c) { setEpisodicPartitionCapacity(c); return this; }
    public MemoryProperties proceduralCapacity(int c) { setProceduralCapacity(c); return this; }
    public MemoryProperties entityGraphCapacity(int c) { setEntityGraphCapacity(c); return this; }
    public MemoryProperties hebbianGraphCapacity(int c) { setHebbianGraphCapacity(c); return this; }
    public MemoryProperties temporalChainCapacity(int c) { setTemporalChainCapacity(c); return this; }
    public MemoryProperties nodesPerPartition(int n) { setNodesPerPartition(n); return this; }
    public MemoryProperties textSegmentSize(long s) { setTextSegmentSize(s); return this; }
    public MemoryProperties episodicSegmentSize(long s) { setEpisodicSegmentSize(s); return this; }
    public MemoryProperties persistenceMode(PersistenceMode m) { setPersistenceMode(m); return this; }
    public MemoryProperties persistencePath(String p) { setPersistencePath(p); return this; }
    public MemoryProperties persistencePath(java.nio.file.Path p) { if (p != null) setPersistencePath(p.toString()); return this; }
    public MemoryProperties bundleMode(boolean b) { setBundleMode(b); return this; }
    public MemoryProperties persistWorkingMemory(boolean b) { setPersistWorkingMemory(b); return this; }
    public MemoryProperties pinSourceEpisodes(boolean b) { setPinSourceEpisodes(b); if (remember != null) remember.setPinSourceEpisodes(b); return this; }
    public MemoryProperties pinnedQuota(int q) { setPinnedQuota(q); if (remember != null) remember.setPinnedQuota(q); return this; }
    public MemoryProperties checkpointIntervalSeconds(int s) { setCheckpointIntervalSeconds(s); return this; }
    public MemoryProperties idStrategy(String s) { setIdStrategy(s); return this; }
    public MemoryProperties edgeImportance(String s) { setEdgeImportance(s); return this; }
    public MemoryProperties namespaceId(String s) { setNamespaceId(s); return this; }
    public MemoryProperties maxNamespaces(int m) { setMaxNamespaces(m); return this; }
    public MemoryProperties pathwayEnabled(boolean b) { setPathwayEnabled(b); return this; }
    public MemoryProperties coactivationPairCapacity(int c) { setCoactivationPairCapacity(c); return this; }
    public MemoryProperties coactivationEdgeCapacity(int c) { setCoactivationEdgeCapacity(c); return this; }
    public MemoryProperties temporalFactsInitialSize(long s) { setTemporalFactsInitialSize(s); return this; }
    public MemoryProperties indexMidxCapacity(int c) { setIndexMidxCapacity(c); return this; }
    public MemoryProperties indexIdplSize(long s) { setIndexIdplSize(s); return this; }
    public MemoryProperties typeRegistryCapacity(int c) { setTypeRegistryCapacity(c); return this; }
    public MemoryProperties typeRegistrySize(long s) { setTypeRegistrySize(s); return this; }
    public MemoryProperties insulaSize(long s) { setInsulaSize(s); return this; }
    public MemoryProperties provenanceCapacity(int c) { setProvenanceCapacity(c); return this; }
    public MemoryProperties entityExtractionParallelism(int p) { setEntityExtractionParallelism(p); return this; }
    public MemoryProperties entityExtractionQueueCapacity(int c) { setEntityExtractionQueueCapacity(c); return this; }

    // Remember forwarders
    public int getSurpriseWarmup() { return remember != null ? remember.getSurpriseWarmup() : 10; }
    public int surpriseWarmup() { return getSurpriseWarmup(); }
    public MemoryProperties surpriseWarmup(int w) { if (remember != null) remember.setSurpriseWarmup(w); return this; }

    public float getFlashbulbThreshold() { return remember != null ? remember.getFlashbulbThreshold() : 3.0f; }
    public float flashbulbThreshold() { return getFlashbulbThreshold(); }
    public MemoryProperties flashbulbThreshold(double t) { if (remember != null) remember.setFlashbulbThreshold((float) t); return this; }

    public float getValenceLearningRate() { return remember != null ? remember.getValenceLearningRate() : 0.3f; }
    public float valenceLearningRate() { return getValenceLearningRate(); }
    public MemoryProperties valenceLearningRate(float r) { if (remember != null) remember.setValenceLearningRate(r); return this; }

    public float getDeduplicationRadius() { return remember != null ? remember.getDeduplicationRadius() : 0.05f; }
    public float deduplicationRadius() { return getDeduplicationRadius(); }
    public MemoryProperties deduplicationRadius(float r) { if (remember != null) remember.setDeduplicationRadius(r); return this; }

    public long getInhibitionTtlMs() { return remember != null ? remember.getInhibitionTtlMs() : 300000L; }
    public long inhibitionTtlMs() { return getInhibitionTtlMs(); }
    public MemoryProperties inhibitionTtlMs(long ms) { if (remember != null) remember.setInhibitionTtlMs(ms); return this; }

    public float getInhibitionFloor() { return remember != null ? remember.getInhibitionFloor() : 0.1f; }
    public float inhibitionFloor() { return getInhibitionFloor(); }
    public MemoryProperties inhibitionFloor(float f) { if (remember != null) remember.setInhibitionFloor(f); return this; }

    // Child POJOs
    public MemoryProperties decay(DecayProperties d) { setDecay(d); return this; }
    public MemoryProperties consolidation(ConsolidationProperties c) { setConsolidation(c); return this; }
    public MemoryProperties aisme(AismeProperties a) { setAisme(a); return this; }
    public MemoryProperties dream(DreamProperties d) { setDream(d); return this; }
    public MemoryProperties circadian(CircadianProperties c) { setCircadian(c); return this; }
    public MemoryProperties twofactor(TwoFactorProperties t) { setTwofactor(t); return this; }
    public MemoryProperties recall(RecallProperties r) { setRecall(r); return this; }
    public MemoryProperties remember(RememberProperties r) { setRemember(r); return this; }
    public MemoryProperties graph(GraphProperties g) { setGraph(g); return this; }
    public MemoryProperties wal(WalProperties w) { setWal(w); return this; }
    public MemoryProperties vacuum(VacuumProperties v) { setVacuum(v); return this; }
    public MemoryProperties session(SessionProperties s) { setSession(s); return this; }

    /**
     * Creates a full copy of this {@link MemoryProperties} instance.
     */
    public MemoryProperties copy() {
        MemoryProperties cp = new MemoryProperties();
        cp.enabled = this.enabled;
        cp.maxMemories = this.maxMemories;
        cp.persistenceMode = this.persistenceMode;
        cp.persistencePath = this.persistencePath;
        cp.dimensions = this.dimensions;
        cp.capacity = this.capacity;
        cp.nodesPerPartition = this.nodesPerPartition;
        cp.workingCapacity = this.workingCapacity;
        cp.episodicPartitionCapacity = this.episodicPartitionCapacity;
        cp.semanticCapacity = this.semanticCapacity;
        cp.proceduralCapacity = this.proceduralCapacity;
        cp.entityGraphCapacity = this.entityGraphCapacity;
        cp.pinnedQuota = this.pinnedQuota;
        cp.checkpointIntervalSeconds = this.checkpointIntervalSeconds;
        cp.textSegmentSize = this.textSegmentSize;
        cp.episodicSegmentSize = this.episodicSegmentSize;
        cp.persistWorkingMemory = this.persistWorkingMemory;
        cp.pinSourceEpisodes = this.pinSourceEpisodes;
        cp.idStrategy = this.idStrategy;
        cp.edgeImportance = this.edgeImportance;
        cp.namespaceId = this.namespaceId;
        cp.defaultIngestionTier = this.defaultIngestionTier;
        cp.hnswPrefilter = this.hnswPrefilter;
        cp.tagExtractor = this.tagExtractor;
        cp.tagExtractorModel = this.tagExtractorModel;
        cp.textSearchMode = this.textSearchMode;
        cp.spladeEnabled = this.spladeEnabled;
        cp.colbertEnabled = this.colbertEnabled;
        cp.bm25Enabled = this.bm25Enabled;
        cp.bundleMode = this.bundleMode;
        cp.coactivationPairCapacity = this.coactivationPairCapacity;
        cp.coactivationEdgeCapacity = this.coactivationEdgeCapacity;
        cp.temporalFactsInitialSize = this.temporalFactsInitialSize;
        cp.indexMidxCapacity = this.indexMidxCapacity;
        cp.indexIdplSize = this.indexIdplSize;
        cp.typeRegistryCapacity = this.typeRegistryCapacity;
        cp.typeRegistrySize = this.typeRegistrySize;
        cp.insulaSize = this.insulaSize;
        cp.provenanceCapacity = this.provenanceCapacity;
        cp.hebbianGraphCapacity = this.hebbianGraphCapacity;
        cp.temporalChainCapacity = this.temporalChainCapacity;
        cp.graphExpansionMode = this.graphExpansionMode;
        cp.graphExpansionThreshold = this.graphExpansionThreshold;
        cp.enableMmr = this.enableMmr;
        cp.mmrLambda = this.mmrLambda;
        cp.schedulerEnabled = this.schedulerEnabled;
        cp.wanderEnabled = this.wanderEnabled;
        cp.dreamEnabled = this.dreamEnabled;
        cp.maxNamespaces = this.maxNamespaces;
        cp.pathwayEnabled = this.pathwayEnabled;
        cp.entityExtractionParallelism = this.entityExtractionParallelism;
        cp.entityExtractionQueueCapacity = this.entityExtractionQueueCapacity;

        cp.decay = this.decay != null ? this.decay.copy() : new DecayProperties();
        cp.consolidation = this.consolidation != null ? this.consolidation.copy() : new ConsolidationProperties();
        cp.llm = this.llm != null ? this.llm.copy() : new LlmProperties();
        cp.aisme = this.aisme != null ? this.aisme.copy() : new AismeProperties();
        cp.taskQueue = this.taskQueue != null ? this.taskQueue.copy() : new TaskQueueProperties();
        cp.entityExtractionTaskQueue = this.entityExtractionTaskQueue != null ? this.entityExtractionTaskQueue.copy() : null;
        cp.consolidationTaskQueue = this.consolidationTaskQueue != null ? this.consolidationTaskQueue.copy() : null;
        cp.recall = this.recall != null ? this.recall.copy() : new RecallProperties();
        cp.remember = this.remember != null ? this.remember.copy() : new RememberProperties();
        cp.graph = this.graph != null ? this.graph.copy() : new GraphProperties();
        cp.circadian = this.circadian != null ? this.circadian.copy() : new CircadianProperties();
        cp.dream = this.dream != null ? this.dream.copy() : new DreamProperties();
        cp.twofactor = this.twofactor != null ? this.twofactor.copy() : new TwoFactorProperties();
        cp.wal = this.wal != null ? this.wal.copy() : new WalProperties();
        cp.vacuum = this.vacuum != null ? this.vacuum.copy() : new VacuumProperties();
        cp.session = this.session != null ? this.session.copy() : new SessionProperties();
        return cp;
    }
}
