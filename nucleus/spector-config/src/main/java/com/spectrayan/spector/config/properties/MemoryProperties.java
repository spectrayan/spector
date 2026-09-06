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
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxMemories() { return maxMemories; }
    public void setMaxMemories(int maxMemories) { this.maxMemories = maxMemories; }

    public PersistenceMode getPersistenceMode() { return persistenceMode; }
    public void setPersistenceMode(PersistenceMode persistenceMode) {
        if (persistenceMode != null) this.persistenceMode = persistenceMode;
    }
    public void setPersistenceMode(String persistenceMode) {
        if (persistenceMode != null && !persistenceMode.isBlank()) {
            try {
                this.persistenceMode = PersistenceMode.valueOf(persistenceMode.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public String getPersistencePath() { return persistencePath; }
    public void setPersistencePath(String persistencePath) {
        if (persistencePath != null && !persistencePath.isBlank()) {
            this.persistencePath = persistencePath;
        }
    }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) {
        if (dimensions > 0) this.dimensions = dimensions;
    }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) {
        if (capacity > 0) this.capacity = capacity;
    }

    public int getNodesPerPartition() { return nodesPerPartition; }
    public void setNodesPerPartition(int nodesPerPartition) {
        if (nodesPerPartition > 0) this.nodesPerPartition = nodesPerPartition;
    }

    public RememberTier getDefaultIngestionTier() { return defaultIngestionTier; }
    public void setDefaultIngestionTier(RememberTier defaultIngestionTier) {
        if (defaultIngestionTier != null) this.defaultIngestionTier = defaultIngestionTier;
    }
    public void setDefaultIngestionTier(String defaultIngestionTier) {
        if (defaultIngestionTier != null && !defaultIngestionTier.isBlank()) {
            try {
                this.defaultIngestionTier = RememberTier.valueOf(defaultIngestionTier.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public HnswPrefilterMode getHnswPrefilter() { return hnswPrefilter; }
    public void setHnswPrefilter(HnswPrefilterMode hnswPrefilter) {
        if (hnswPrefilter != null) this.hnswPrefilter = hnswPrefilter;
    }
    public void setHnswPrefilter(String hnswPrefilter) {
        if (hnswPrefilter != null && !hnswPrefilter.isBlank()) {
            try {
                this.hnswPrefilter = HnswPrefilterMode.valueOf(hnswPrefilter.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public TagExtractorMode getTagExtractor() { return tagExtractor; }
    public void setTagExtractor(TagExtractorMode tagExtractor) {
        if (tagExtractor != null) this.tagExtractor = tagExtractor;
    }
    public void setTagExtractor(String tagExtractor) {
        if (tagExtractor != null && !tagExtractor.isBlank()) {
            try {
                this.tagExtractor = TagExtractorMode.valueOf(tagExtractor.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public String getTagExtractorModel() { return tagExtractorModel; }
    public void setTagExtractorModel(String tagExtractorModel) {
        if (tagExtractorModel != null && !tagExtractorModel.isBlank()) {
            this.tagExtractorModel = tagExtractorModel;
        }
    }

    public TextSearchMode getTextSearchMode() { return textSearchMode; }
    public void setTextSearchMode(TextSearchMode textSearchMode) {
        if (textSearchMode != null) this.textSearchMode = textSearchMode;
    }
    public void setTextSearchMode(String textSearchMode) {
        if (textSearchMode != null && !textSearchMode.isBlank()) {
            try {
                this.textSearchMode = TextSearchMode.valueOf(textSearchMode.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public boolean isSpladeEnabled() { return spladeEnabled; }
    public void setSpladeEnabled(boolean spladeEnabled) { this.spladeEnabled = spladeEnabled; }

    public boolean isColbertEnabled() { return colbertEnabled; }
    public void setColbertEnabled(boolean colbertEnabled) { this.colbertEnabled = colbertEnabled; }

    public boolean isBm25Enabled() { return bm25Enabled; }
    public void setBm25Enabled(boolean bm25Enabled) { this.bm25Enabled = bm25Enabled; }

    public boolean isBundleMode() { return bundleMode; }
    public void setBundleMode(boolean bundleMode) { this.bundleMode = bundleMode; }

    public DecayProperties getDecay() { return decay; }
    public void setDecay(DecayProperties decay) { this.decay = decay; }

    public ConsolidationProperties getConsolidation() { return consolidation; }
    public void setConsolidation(ConsolidationProperties consolidation) { this.consolidation = consolidation; }

    public LlmProperties getLlm() { return llm; }
    public void setLlm(LlmProperties llm) {
        if (llm != null) this.llm = llm;
    }

    public TaskQueueProperties getTaskQueue() { return taskQueue; }
    public void setTaskQueue(TaskQueueProperties taskQueue) {
        if (taskQueue != null) this.taskQueue = taskQueue;
    }
    public TaskQueueProperties taskQueue() { return getTaskQueue(); }

    public TaskQueueProperties getEntityExtractionTaskQueue() { return entityExtractionTaskQueue; }
    public void setEntityExtractionTaskQueue(TaskQueueProperties entityExtractionTaskQueue) {
        if (entityExtractionTaskQueue != null) this.entityExtractionTaskQueue = entityExtractionTaskQueue;
    }
    public TaskQueueProperties entityExtractionTaskQueue() { return getEntityExtractionTaskQueue(); }

    public TaskQueueProperties getConsolidationTaskQueue() { return consolidationTaskQueue; }
    public void setConsolidationTaskQueue(TaskQueueProperties consolidationTaskQueue) {
        if (consolidationTaskQueue != null) this.consolidationTaskQueue = consolidationTaskQueue;
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
    public void setCoactivationPairCapacity(int coactivationPairCapacity) { this.coactivationPairCapacity = coactivationPairCapacity; }
    public int coactivationPairCapacity() { return coactivationPairCapacity; }

    public int getCoactivationEdgeCapacity() { return coactivationEdgeCapacity; }
    public void setCoactivationEdgeCapacity(int coactivationEdgeCapacity) { this.coactivationEdgeCapacity = coactivationEdgeCapacity; }
    public int coactivationEdgeCapacity() { return coactivationEdgeCapacity; }

    public long getTemporalFactsInitialSize() { return temporalFactsInitialSize; }
    public void setTemporalFactsInitialSize(long temporalFactsInitialSize) { this.temporalFactsInitialSize = temporalFactsInitialSize; }
    public long temporalFactsInitialSize() { return temporalFactsInitialSize; }

    public int getIndexMidxCapacity() { return indexMidxCapacity; }
    public void setIndexMidxCapacity(int indexMidxCapacity) { this.indexMidxCapacity = indexMidxCapacity; }
    public int indexMidxCapacity() { return indexMidxCapacity; }

    public long getIndexIdplSize() { return indexIdplSize; }
    public void setIndexIdplSize(long indexIdplSize) { this.indexIdplSize = indexIdplSize; }
    public long indexIdplSize() { return indexIdplSize; }

    public int getTypeRegistryCapacity() { return typeRegistryCapacity; }
    public void setTypeRegistryCapacity(int typeRegistryCapacity) { this.typeRegistryCapacity = typeRegistryCapacity; }
    public int typeRegistryCapacity() { return typeRegistryCapacity; }

    public long getTypeRegistrySize() { return typeRegistrySize; }
    public void setTypeRegistrySize(long typeRegistrySize) { this.typeRegistrySize = typeRegistrySize; }
    public long typeRegistrySize() { return typeRegistrySize; }

    public long getInsulaSize() { return insulaSize; }
    public void setInsulaSize(long insulaSize) { this.insulaSize = insulaSize; }
    public long insulaSize() { return insulaSize; }

    public int getEntityExtractionParallelism() { return entityExtractionTaskQueue.getParallelism(); }
    public void setEntityExtractionParallelism(int entityExtractionParallelism) { this.entityExtractionTaskQueue.setParallelism(entityExtractionParallelism); }
    public int entityExtractionParallelism() { return getEntityExtractionParallelism(); }

    public int getEntityExtractionQueueCapacity() { return entityExtractionTaskQueue.getCapacity(); }
    public void setEntityExtractionQueueCapacity(int entityExtractionQueueCapacity) { this.entityExtractionTaskQueue.setCapacity(entityExtractionQueueCapacity); }
    public int entityExtractionQueueCapacity() { return getEntityExtractionQueueCapacity(); }

    public AismeProperties getAisme() { return aisme; }
    public void setAisme(AismeProperties aisme) {
        if (aisme != null) this.aisme = aisme;
    }
    public AismeProperties aisme() { return getAisme(); }

    public String getGraphExpansionMode() { return graphExpansionMode; }
    public void setGraphExpansionMode(String graphExpansionMode) {
        if (graphExpansionMode != null && !graphExpansionMode.isBlank()) {
            this.graphExpansionMode = graphExpansionMode;
        }
    }
    public String graphExpansionMode() { return graphExpansionMode; }

    public float getGraphExpansionThreshold() { return graphExpansionThreshold; }
    public void setGraphExpansionThreshold(float graphExpansionThreshold) {
        this.graphExpansionThreshold = graphExpansionThreshold;
    }
    public float graphExpansionThreshold() { return graphExpansionThreshold; }

    @Deprecated(forRemoval = true)
    public boolean isEnableMmr() {
        return recall != null && recall.getMmr() != null ? recall.getMmr().isEnabled() : enableMmr;
    }

    @Deprecated(forRemoval = true)
    public void setEnableMmr(boolean enableMmr) {
        this.enableMmr = enableMmr;
        if (recall != null && recall.getMmr() != null) {
            recall.getMmr().setEnabled(enableMmr);
        }
    }

    @Deprecated(forRemoval = true)
    public boolean enableMmr() { return isEnableMmr(); }

    @Deprecated(forRemoval = true)
    public float getMmrLambda() {
        return recall != null && recall.getMmr() != null ? recall.getMmr().getLambda() : mmrLambda;
    }

    @Deprecated(forRemoval = true)
    public void setMmrLambda(float mmrLambda) {
        this.mmrLambda = mmrLambda;
        if (recall != null && recall.getMmr() != null) {
            recall.getMmr().setLambda(mmrLambda);
        }
    }

    @Deprecated(forRemoval = true)
    public float mmrLambda() { return getMmrLambda(); }

    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }
    public boolean schedulerEnabled() { return schedulerEnabled; }

    public boolean isWanderEnabled() { return wanderEnabled; }
    public void setWanderEnabled(boolean wanderEnabled) { this.wanderEnabled = wanderEnabled; }
    public boolean wanderEnabled() { return wanderEnabled; }

    public boolean isDreamEnabled() { return dreamEnabled; }
    public void setDreamEnabled(boolean dreamEnabled) { this.dreamEnabled = dreamEnabled; }
    public boolean dreamEnabled() { return dreamEnabled; }

    // ─── Sub-Domain Children Accessors ───

    public RecallProperties getRecall() { return recall; }
    public void setRecall(RecallProperties recall) {
        if (recall != null) this.recall = recall;
    }
    public RecallProperties recall() { return recall; }

    public RememberProperties getRemember() { return remember; }
    public void setRemember(RememberProperties remember) {
        if (remember != null) this.remember = remember;
    }
    public RememberProperties remember() { return remember; }

    public GraphProperties getGraph() { return graph; }
    public void setGraph(GraphProperties graph) {
        if (graph != null) this.graph = graph;
    }
    public GraphProperties graph() { return graph; }

    public CircadianProperties getCircadian() { return circadian; }
    public void setCircadian(CircadianProperties circadian) {
        if (circadian != null) this.circadian = circadian;
    }
    public CircadianProperties circadian() { return circadian; }

    public int getMaxNamespaces() { return maxNamespaces; }
    public void setMaxNamespaces(int maxNamespaces) {
        if (maxNamespaces > 0) this.maxNamespaces = maxNamespaces;
    }
    public int maxNamespaces() { return maxNamespaces; }

    public boolean isPathwayEnabled() { return pathwayEnabled; }
    public void setPathwayEnabled(boolean pathwayEnabled) { this.pathwayEnabled = pathwayEnabled; }
    public boolean pathwayEnabled() { return pathwayEnabled; }

    public int getWorkingCapacity() { return workingCapacity; }
    public void setWorkingCapacity(int workingCapacity) { this.workingCapacity = workingCapacity; }
    public int workingCapacity() { return workingCapacity; }

    public int getEpisodicPartitionCapacity() { return episodicPartitionCapacity; }
    public void setEpisodicPartitionCapacity(int episodicPartitionCapacity) { this.episodicPartitionCapacity = episodicPartitionCapacity; }
    public int episodicPartitionCapacity() { return episodicPartitionCapacity; }

    public int getSemanticCapacity() { return semanticCapacity; }
    public void setSemanticCapacity(int semanticCapacity) { this.semanticCapacity = semanticCapacity; }
    public int semanticCapacity() { return semanticCapacity; }

    public int getProceduralCapacity() { return proceduralCapacity; }
    public void setProceduralCapacity(int proceduralCapacity) { this.proceduralCapacity = proceduralCapacity; }
    public int proceduralCapacity() { return proceduralCapacity; }

    public int getEntityGraphCapacity() { return entityGraphCapacity; }
    public void setEntityGraphCapacity(int entityGraphCapacity) { this.entityGraphCapacity = entityGraphCapacity; }
    public int entityGraphCapacity() { return entityGraphCapacity; }

    public int getPinnedQuota() { return pinnedQuota; }
    public void setPinnedQuota(int pinnedQuota) { this.pinnedQuota = pinnedQuota; }
    public int pinnedQuota() { return pinnedQuota; }

    public int getCheckpointIntervalSeconds() { return checkpointIntervalSeconds; }
    public void setCheckpointIntervalSeconds(int checkpointIntervalSeconds) { this.checkpointIntervalSeconds = checkpointIntervalSeconds; }
    public int checkpointIntervalSeconds() { return checkpointIntervalSeconds; }

    public long getTextSegmentSize() { return textSegmentSize; }
    public void setTextSegmentSize(long textSegmentSize) { this.textSegmentSize = textSegmentSize; }
    public long textSegmentSize() { return textSegmentSize; }

    public long getEpisodicSegmentSize() { return episodicSegmentSize; }
    public void setEpisodicSegmentSize(long episodicSegmentSize) { this.episodicSegmentSize = episodicSegmentSize; }
    public long episodicSegmentSize() { return episodicSegmentSize; }

    public boolean isPersistWorkingMemory() { return persistWorkingMemory; }
    public void setPersistWorkingMemory(boolean persistWorkingMemory) { this.persistWorkingMemory = persistWorkingMemory; }
    public boolean persistWorkingMemory() { return persistWorkingMemory; }

    public boolean isPinSourceEpisodes() { return pinSourceEpisodes; }
    public void setPinSourceEpisodes(boolean pinSourceEpisodes) { this.pinSourceEpisodes = pinSourceEpisodes; }
    public boolean pinSourceEpisodes() { return pinSourceEpisodes; }

    public String getIdStrategy() { return idStrategy; }
    public void setIdStrategy(String idStrategy) { this.idStrategy = idStrategy; }
    public String idStrategy() { return idStrategy; }

    public String getEdgeImportance() { return edgeImportance; }
    public void setEdgeImportance(String edgeImportance) { this.edgeImportance = edgeImportance; }
    public String edgeImportance() { return edgeImportance; }

    public String getNamespaceId() { return namespaceId; }
    public void setNamespaceId(String namespaceId) { this.namespaceId = namespaceId; }
    public String namespaceId() { return namespaceId; }

    public DreamProperties getDream() { return dream; }
    public void setDream(DreamProperties dream) {
        if (dream != null) this.dream = dream;
    }
    public DreamProperties dream() { return dream; }

    public TwoFactorProperties getTwofactor() { return twofactor; }
    public void setTwofactor(TwoFactorProperties twofactor) {
        if (twofactor != null) this.twofactor = twofactor;
    }
    public TwoFactorProperties twofactor() { return twofactor; }

    public WalProperties getWal() { return wal; }
    public void setWal(WalProperties wal) {
        if (wal != null) this.wal = wal;
    }
    public WalProperties wal() { return wal; }

    public VacuumProperties getVacuum() { return vacuum; }
    public void setVacuum(VacuumProperties vacuum) {
        if (vacuum != null) this.vacuum = vacuum;
    }
    public VacuumProperties vacuum() { return vacuum; }

    public SessionProperties getSession() { return session; }
    public void setSession(SessionProperties session) {
        if (session != null) this.session = session;
    }
    public SessionProperties session() { return session; }
}
