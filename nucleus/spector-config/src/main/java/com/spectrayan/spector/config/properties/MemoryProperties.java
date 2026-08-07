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
import com.spectrayan.spector.config.model.IngestionTierMode;
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

    private IngestionTierMode defaultIngestionTier = DEFAULT_MEMORY_DEFAULT_INGESTION_TIER;
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

    private DecayProperties decay = new DecayProperties();
    private ConsolidationProperties consolidation = new ConsolidationProperties();
    private LlmProperties llm = new LlmProperties();

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
    public void setPersistencePath(String persistencePath) { this.persistencePath = persistencePath; }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getNodesPerPartition() { return nodesPerPartition; }
    public void setNodesPerPartition(int nodesPerPartition) {
        if (nodesPerPartition > 0) this.nodesPerPartition = nodesPerPartition;
    }

    public IngestionTierMode getDefaultIngestionTier() { return defaultIngestionTier; }
    public void setDefaultIngestionTier(IngestionTierMode defaultIngestionTier) {
        if (defaultIngestionTier != null) this.defaultIngestionTier = defaultIngestionTier;
    }
    public void setDefaultIngestionTier(String defaultIngestionTier) {
        if (defaultIngestionTier != null && !defaultIngestionTier.isBlank()) {
            try {
                this.defaultIngestionTier = IngestionTierMode.valueOf(defaultIngestionTier.toUpperCase(Locale.ROOT).replace('-', '_'));
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
        if (tagExtractorModel != null) this.tagExtractorModel = tagExtractorModel;
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

    public boolean enabled() { return isEnabled(); }
    public int maxMemories() { return getMaxMemories(); }
    public PersistenceMode persistenceMode() { return getPersistenceMode(); }
    public String persistencePath() { return getPersistencePath(); }
    public int dimensions() { return getDimensions(); }
    public int capacity() { return getCapacity(); }
    public int nodesPerPartition() { return getNodesPerPartition(); }
    public IngestionTierMode defaultIngestionTier() { return getDefaultIngestionTier(); }
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
}
