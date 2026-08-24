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
package com.spectrayan.spector.config;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import com.spectrayan.spector.config.model.*;
import com.spectrayan.spector.config.properties.*;

import java.time.Duration;

/**
 * Central factory for building typed configuration objects from {@link SpectorProperties}.
 *
 * <p>This is the bridge between the hierarchical property file system and the
 * strongly-typed configuration POJOs used by each Spector module.</p>
 */
public final class SpectorConfigFactory {

    private SpectorConfigFactory() {}

    // ─────────────── HNSW Properties ───────────────

    /**
     * Loads HNSW properties from configuration.
     */
    public static HnswProperties hnswProperties(SpectorProperties props) {
        return new HnswProperties(
                props.getInt(HNSW_M, DEFAULT_HNSW_M),
                props.getInt(HNSW_EF_CONSTRUCTION, DEFAULT_HNSW_EF_CONSTRUCTION),
                props.getInt(HNSW_EF_SEARCH, DEFAULT_HNSW_EF_SEARCH)
        );
    }

    // ─────────────── IVF Properties ───────────────

    /**
     * Loads IVF properties from configuration.
     */
    public static IvfProperties ivfProperties(SpectorProperties props) {
        return new IvfProperties(
                props.getInt(IVF_NLIST, DEFAULT_IVF_NLIST),
                props.getInt(IVF_NPROBE, DEFAULT_IVF_NPROBE),
                props.getInt(IVF_PQ_SUBSPACES, DEFAULT_IVF_PQ_SUBSPACES)
        );
    }

    // ─────────────── Spectrum Properties ───────────────

    /**
     * Loads Spectrum properties from configuration.
     */
    public static SpectrumProperties spectrumProperties(SpectorProperties props) {
        return new SpectrumProperties(
                props.getInt(SPECTRUM_N_CENTROIDS, DEFAULT_SPECTRUM_N_CENTROIDS),
                props.getInt(SPECTRUM_N_PROBE, DEFAULT_SPECTRUM_N_PROBE),
                props.getInt(SPECTRUM_SHARD_THRESHOLD, DEFAULT_SPECTRUM_SHARD_THRESHOLD),
                props.getInt(SPECTRUM_OVERSAMPLING_FACTOR, DEFAULT_SPECTRUM_OVERSAMPLING_FACTOR),
                props.getInt(SPECTRUM_KMEANS_ITERATIONS, DEFAULT_SPECTRUM_KMEANS_ITERATIONS)
        );
    }

    // ─────────────── Embedding Properties ───────────────

    /**
     * Loads embedding provider properties from configuration.
     */
    public static EmbeddingProperties embeddingProperties(SpectorProperties props) {
        EmbeddingProperties properties = new EmbeddingProperties();

        String type = props.getString(PROVIDER_EMBEDDING_TYPE, DEFAULT_PROVIDER_EMBEDDING_TYPE);
        String model = props.getString(PROVIDER_EMBEDDING_MODEL, DEFAULT_PROVIDER_EMBEDDING_MODEL);
        String apiKey = props.getString(PROVIDER_EMBEDDING_API_KEY, DEFAULT_PROVIDER_EMBEDDING_API_KEY);
        String baseUrl = props.getString(PROVIDER_EMBEDDING_BASE_URL, DEFAULT_PROVIDER_EMBEDDING_BASE_URL);
        int dimensions = props.getInt(PROVIDER_EMBEDDING_DIMENSIONS, DEFAULT_PROVIDER_EMBEDDING_DIMENSIONS);
        int batchSize = props.getInt(PROVIDER_EMBEDDING_BATCH_SIZE, DEFAULT_PROVIDER_EMBEDDING_BATCH_SIZE);
        int maxRetries = props.getInt(PROVIDER_EMBEDDING_MAX_RETRIES, DEFAULT_PROVIDER_EMBEDDING_MAX_RETRIES);
        int maxConcurrent = props.getInt(PROVIDER_EMBEDDING_MAX_CONCURRENT, DEFAULT_PROVIDER_EMBEDDING_MAX_CONCURRENT);
        Duration timeout = props.getDuration(PROVIDER_EMBEDDING_TIMEOUT, DEFAULT_PROVIDER_EMBEDDING_TIMEOUT);
        boolean cacheEnabled = props.getBoolean(PROVIDER_EMBEDDING_CACHE_ENABLED, DEFAULT_PROVIDER_EMBEDDING_CACHE_ENABLED);
        int cacheMaxSize = props.getInt(PROVIDER_EMBEDDING_CACHE_MAX_SIZE, DEFAULT_PROVIDER_EMBEDDING_CACHE_MAX_SIZE);
        Duration cacheTtl = props.getDuration(PROVIDER_EMBEDDING_CACHE_TTL, DEFAULT_PROVIDER_EMBEDDING_CACHE_TTL);
        Duration cacheStatsLogInterval = props.getDuration(PROVIDER_EMBEDDING_CACHE_STATS_LOG_INTERVAL, DEFAULT_PROVIDER_EMBEDDING_CACHE_STATS_LOG_INTERVAL);

        properties.setType(type);
        properties.setModel(model);
        properties.setApiKey(apiKey);
        properties.setBaseUrl(baseUrl);
        properties.setDimensions(dimensions);
        properties.setBatchSize(batchSize);
        properties.setMaxRetries(maxRetries);
        properties.setMaxConcurrent(maxConcurrent);
        properties.setTimeout(timeout);
        properties.setCacheEnabled(cacheEnabled);
        properties.setCacheMaxSize(cacheMaxSize);
        properties.setCacheTtl(cacheTtl);
        properties.setCacheStatsLogInterval(cacheStatsLogInterval);

        return properties;
    }

    // ─────────────── Memory Properties ───────────────

    /**
     * Loads memory properties POJO from configuration.
     */
    public static MemoryProperties memoryProperties(SpectorProperties props) {
        MemoryProperties properties = new MemoryProperties();
        properties.setEnabled(props.getBoolean(MEMORY_ENABLED, DEFAULT_MEMORY_ENABLED));
        properties.setPersistenceMode(props.getEnum(MEMORY_PERSISTENCE_MODE, PersistenceMode.class, DEFAULT_MEMORY_PERSISTENCE_MODE));
        properties.setPersistencePath(props.getPath(MEMORY_PERSISTENCE_PATH, DEFAULT_MEMORY_PERSISTENCE_PATH).toString());
        properties.setDimensions(props.getInt(MEMORY_DIMENSIONS, DEFAULT_MEMORY_DIMENSIONS));
        properties.setCapacity(props.getInt(MEMORY_CAPACITY, DEFAULT_MEMORY_CAPACITY));
        properties.setNodesPerPartition(props.getInt(MEMORY_NODES_PER_PARTITION, DEFAULT_MEMORY_NODES_PER_PARTITION));
        properties.setDefaultIngestionTier(props.getEnum(MEMORY_DEFAULT_INGESTION_TIER, IngestionTierMode.class, DEFAULT_MEMORY_DEFAULT_INGESTION_TIER));
        properties.setHnswPrefilter(props.getEnum(MEMORY_HNSW_PREFILTER, HnswPrefilterMode.class, DEFAULT_MEMORY_HNSW_PREFILTER));
        properties.setTagExtractor(props.getEnum(MEMORY_TAG_EXTRACTOR, TagExtractorMode.class, DEFAULT_MEMORY_TAG_EXTRACTOR));
        properties.setTagExtractorModel(props.getString(MEMORY_TAG_EXTRACTOR_MODEL, DEFAULT_MEMORY_TAG_EXTRACTOR_MODEL));
        properties.setTextSearchMode(props.getEnum(MEMORY_TEXT_SEARCH_MODE, TextSearchMode.class, DEFAULT_MEMORY_TEXT_SEARCH_MODE));
        properties.setSpladeEnabled(props.getBoolean(MEMORY_SPLADE_ENABLED, DEFAULT_MEMORY_SPLADE_ENABLED));
        properties.setColbertEnabled(props.getBoolean(MEMORY_COLBERT_ENABLED, DEFAULT_MEMORY_COLBERT_ENABLED));
        properties.setBm25Enabled(props.getBoolean(MEMORY_BM25_ENABLED, DEFAULT_MEMORY_BM25_ENABLED));

        properties.setCoactivationPairCapacity(props.getInt(MEMORY_COACTIVATION_PAIR_CAPACITY, DEFAULT_MEMORY_COACTIVATION_PAIR_CAPACITY));
        properties.setCoactivationEdgeCapacity(props.getInt(MEMORY_COACTIVATION_EDGE_CAPACITY, DEFAULT_MEMORY_COACTIVATION_EDGE_CAPACITY));
        properties.setTemporalFactsInitialSize(props.getLong(MEMORY_TEMPORAL_FACTS_INITIAL_SIZE, DEFAULT_MEMORY_TEMPORAL_FACTS_INITIAL_SIZE));
        properties.setIndexMidxCapacity(props.getInt(MEMORY_INDEX_MIDX_CAPACITY, DEFAULT_MEMORY_INDEX_MIDX_CAPACITY));
        properties.setIndexIdplSize(props.getLong(MEMORY_INDEX_IDPL_SIZE, DEFAULT_MEMORY_INDEX_IDPL_SIZE));
        properties.setTypeRegistryCapacity(props.getInt(MEMORY_TYPE_REGISTRY_CAPACITY, DEFAULT_MEMORY_TYPE_REGISTRY_CAPACITY));
        properties.setTypeRegistrySize(props.getLong(MEMORY_TYPE_REGISTRY_SIZE, DEFAULT_MEMORY_TYPE_REGISTRY_SIZE));
        properties.setInsulaSize(props.getLong(MEMORY_INSULA_SIZE, DEFAULT_MEMORY_INSULA_SIZE));
        properties.setEntityExtractionParallelism(props.getInt(MEMORY_ENTITY_EXTRACTION_PARALLELISM, DEFAULT_MEMORY_ENTITY_EXTRACTION_PARALLELISM));
        properties.setEntityExtractionQueueCapacity(props.getInt(MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY, DEFAULT_MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY));

        var taskQueue = properties.getTaskQueue();
        taskQueue.setPollTimeoutMs(props.getLong(MEMORY_TASKQUEUE_POLL_TIMEOUT_MS, DEFAULT_MEMORY_TASKQUEUE_POLL_TIMEOUT_MS));
        taskQueue.setDrainTimeoutMs(props.getLong(MEMORY_TASKQUEUE_DRAIN_TIMEOUT_MS, DEFAULT_MEMORY_TASKQUEUE_DRAIN_TIMEOUT_MS));
        taskQueue.setMaxRetries(props.getInt(MEMORY_TASKQUEUE_MAX_RETRIES, DEFAULT_MEMORY_TASKQUEUE_MAX_RETRIES));
        taskQueue.setRetryBackoffMs(props.getLong(MEMORY_TASKQUEUE_RETRY_BACKOFF_MS, DEFAULT_MEMORY_TASKQUEUE_RETRY_BACKOFF_MS));
        taskQueue.setBackpressurePolicy(props.getString(MEMORY_TASKQUEUE_BACKPRESSURE_POLICY, DEFAULT_MEMORY_TASKQUEUE_BACKPRESSURE_POLICY));

        var eeQueue = properties.getEntityExtractionTaskQueue();
        eeQueue.setParallelism(props.getInt(MEMORY_ENTITY_EXTRACTION_PARALLELISM, DEFAULT_MEMORY_ENTITY_EXTRACTION_PARALLELISM));
        eeQueue.setCapacity(props.getInt(MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY, DEFAULT_MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY));
        eeQueue.setPollTimeoutMs(taskQueue.getPollTimeoutMs());
        eeQueue.setDrainTimeoutMs(taskQueue.getDrainTimeoutMs());
        eeQueue.setMaxRetries(taskQueue.getMaxRetries());
        eeQueue.setRetryBackoffMs(taskQueue.getRetryBackoffMs());
        eeQueue.setBackpressurePolicy(taskQueue.getBackpressurePolicy());

        var consolQueue = properties.getConsolidationTaskQueue();
        consolQueue.setParallelism(props.getInt(MEMORY_CONSOLIDATION_PARALLELISM, DEFAULT_MEMORY_CONSOLIDATION_PARALLELISM));
        consolQueue.setCapacity(props.getInt(MEMORY_CONSOLIDATION_QUEUE_CAPACITY, DEFAULT_MEMORY_CONSOLIDATION_QUEUE_CAPACITY));
        consolQueue.setPollTimeoutMs(taskQueue.getPollTimeoutMs());
        consolQueue.setDrainTimeoutMs(taskQueue.getDrainTimeoutMs());
        consolQueue.setMaxRetries(taskQueue.getMaxRetries());
        consolQueue.setRetryBackoffMs(taskQueue.getRetryBackoffMs());
        consolQueue.setBackpressurePolicy(taskQueue.getBackpressurePolicy());

        var llm = new LlmProperties(
                props.getFloat(MEMORY_LLM_TEMPERATURE, DEFAULT_MEMORY_LLM_TEMPERATURE),
                props.getInt(MEMORY_LLM_MAX_TOKENS, DEFAULT_MEMORY_LLM_MAX_TOKENS),
                props.getFloat(MEMORY_LLM_TOP_P, DEFAULT_MEMORY_LLM_TOP_P),
                props.getString(MEMORY_LLM_ENTITY_MODEL, DEFAULT_MEMORY_LLM_ENTITY_MODEL)
        );
        properties.setLlm(llm);

        var decay = properties.getDecay();
        if (!props.getBoolean(MEMORY_DECAY_ENABLED, DEFAULT_MEMORY_DECAY_ENABLED)) {
            decay.setMinThreshold(0.0);
        }

        var consolidation = properties.getConsolidation();
        Duration interval = props.getDuration(MEMORY_CONSOLIDATION_INTERVAL, DEFAULT_MEMORY_CONSOLIDATION_INTERVAL);
        consolidation.setInterval(interval.toMillis());

        var aisme = aismeProperties(props);
        properties.setAisme(aisme);

        return properties;
    }

    // ─────────────── AISME Properties ───────────────

    /**
     * Loads Active Inference Self-Model Engine (AISME) properties from configuration.
     */
    public static AismeProperties aismeProperties(SpectorProperties props) {
        AismeProperties properties = new AismeProperties();
        properties.setEnabled(props.getBoolean(MEMORY_AISME_ENABLED, DEFAULT_MEMORY_AISME_ENABLED));
        properties.setEnableHomeostasis(props.getBoolean(MEMORY_AISME_HOMEOSTASIS_ENABLED, DEFAULT_MEMORY_AISME_HOMEOSTASIS_ENABLED));
        properties.setEnableFreeEnergy(props.getBoolean(MEMORY_AISME_FREE_ENERGY_ENABLED, DEFAULT_MEMORY_AISME_FREE_ENERGY_ENABLED));
        properties.setEnableHopfield(props.getBoolean(MEMORY_AISME_HOPFIELD_ENABLED, DEFAULT_MEMORY_AISME_HOPFIELD_ENABLED));
        properties.setEnableManifold(props.getBoolean(MEMORY_AISME_MANIFOLD_ENABLED, DEFAULT_MEMORY_AISME_MANIFOLD_ENABLED));
        properties.setEnablePredictiveCoding(props.getBoolean(MEMORY_AISME_PREDICTIVE_CODING_ENABLED, DEFAULT_MEMORY_AISME_PREDICTIVE_CODING_ENABLED));
        properties.setEnableConsciousnessContinuity(props.getBoolean(MEMORY_AISME_CONSCIOUSNESS_CONTINUITY_ENABLED, DEFAULT_MEMORY_AISME_CONSCIOUSNESS_CONTINUITY_ENABLED));
        properties.setEnableGlobalWorkspace(props.getBoolean(MEMORY_AISME_GLOBAL_WORKSPACE_ENABLED, DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_ENABLED));
        properties.setGlobalWorkspaceCapacity(props.getInt(MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY, DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY));
        properties.setHopfieldTemperature(props.getFloat(MEMORY_AISME_HOPFIELD_TEMPERATURE, DEFAULT_MEMORY_AISME_HOPFIELD_TEMPERATURE));
        properties.setManifoldSigma(props.getFloat(MEMORY_AISME_MANIFOLD_SIGMA, DEFAULT_MEMORY_AISME_MANIFOLD_SIGMA));
        properties.setPhiCohesionThreshold(props.getFloat(MEMORY_AISME_PHI_COHESION_THRESHOLD, DEFAULT_MEMORY_AISME_PHI_COHESION_THRESHOLD));
        properties.setEnableSoftIdentityAnchor(props.getBoolean(MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED, DEFAULT_MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED));
        properties.setIdentityAnchorEta(props.getFloat(MEMORY_AISME_IDENTITY_ANCHOR_ETA, DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA));
        properties.setIdentityLyapunovThreshold(props.getFloat(MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD, DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD));
        properties.setIdentityCoreSnapshotEpochs(props.getInt(MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS, DEFAULT_MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS));
        properties.setEnableEventDensity(props.getBoolean(MEMORY_AISME_EVENT_DENSITY_ENABLED, DEFAULT_MEMORY_AISME_EVENT_DENSITY_ENABLED));
        properties.setEventDensityThreshold(props.getFloat(MEMORY_AISME_EVENT_DENSITY_THRESHOLD, DEFAULT_MEMORY_AISME_EVENT_DENSITY_THRESHOLD));
        properties.setEventDensityAlphaKl(props.getFloat(MEMORY_AISME_EVENT_DENSITY_ALPHA_KL, DEFAULT_MEMORY_AISME_EVENT_DENSITY_ALPHA_KL));
        properties.setEventDensityBetaGradient(props.getFloat(MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT, DEFAULT_MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT));
        properties.setEventDensityGammaSurprise(props.getFloat(MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE, DEFAULT_MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE));
        properties.setEventDensitySamplingMinHz(props.getFloat(MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ, DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ));
        properties.setEventDensitySamplingMaxHz(props.getFloat(MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ, DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ));
        properties.setEnableBocpd(props.getBoolean(MEMORY_AISME_BOCPD_ENABLED, DEFAULT_MEMORY_AISME_BOCPD_ENABLED));
        properties.setBocpdHazardLambda(props.getFloat(MEMORY_AISME_BOCPD_HAZARD_LAMBDA, DEFAULT_MEMORY_AISME_BOCPD_HAZARD_LAMBDA));
        properties.setBocpdChangePointThreshold(props.getFloat(MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD, DEFAULT_MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD));
        properties.setBocpdSurprisalCutThreshold(props.getFloat(MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD, DEFAULT_MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD));
        properties.setBocpdMaxEpisodeFrames(props.getInt(MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES, DEFAULT_MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES));
        properties.setBocpdMaxRunLength(props.getInt(MEMORY_AISME_BOCPD_MAX_RUN_LENGTH, DEFAULT_MEMORY_AISME_BOCPD_MAX_RUN_LENGTH));
        properties.setEnablePrivacy(props.getBoolean(MEMORY_AISME_PRIVACY_ENABLED, DEFAULT_MEMORY_AISME_PRIVACY_ENABLED));
        properties.setPrivacyEpsilon(props.getFloat(MEMORY_AISME_PRIVACY_EPSILON, DEFAULT_MEMORY_AISME_PRIVACY_EPSILON));
        properties.setPrivacyDelta(props.getFloat(MEMORY_AISME_PRIVACY_DELTA, DEFAULT_MEMORY_AISME_PRIVACY_DELTA));
        properties.setPrivacyClippingNorm(props.getFloat(MEMORY_AISME_PRIVACY_CLIPPING_NORM, DEFAULT_MEMORY_AISME_PRIVACY_CLIPPING_NORM));
        properties.setPrivacyAnonymizePii(props.getBoolean(MEMORY_AISME_PRIVACY_ANONYMIZE_PII, DEFAULT_MEMORY_AISME_PRIVACY_ANONYMIZE_PII));
        properties.setPrivacyPseudonymizationSalt(props.getString(MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT, DEFAULT_MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT));
        properties.setEnableImportance(props.getBoolean(MEMORY_AISME_IMPORTANCE_ENABLED, DEFAULT_MEMORY_AISME_IMPORTANCE_ENABLED));
        properties.setImportanceWeightSurprise(props.getFloat(MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE, DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE));
        properties.setImportanceWeightAffect(props.getFloat(MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT, DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT));
        properties.setImportanceWeightGoal(props.getFloat(MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL, DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL));
        properties.setImportanceWeightSocial(props.getFloat(MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL, DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL));
        properties.setImportanceWeightNovelty(props.getFloat(MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY, DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY));
        properties.setImportanceFlashbulbThreshold(props.getFloat(MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD, DEFAULT_MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD));
        return properties;
    }

    // ─────────────── Global Mode ───────────────

    /**
     * Resolves the global operating mode: {@link SpectorMode#MEMORY}.
     */
    public static SpectorMode mode(SpectorProperties props) {
        return SpectorMode.MEMORY;
    }

    // ─────────────── Ingestion Properties ───────────────

    /**
     * Loads ingestion properties POJO from configuration.
     */
    public static IngestionProperties ingestionProperties(SpectorProperties props) {
        IngestionProperties properties = new IngestionProperties();
        properties.setRootDirectory(props.getPath(INGESTION_ROOT_DIRECTORY, DEFAULT_INGESTION_ROOT_DIRECTORY));
        properties.setFilePattern(props.getString(INGESTION_FILE_PATTERN, DEFAULT_INGESTION_FILE_PATTERN));
        properties.setSkipDirs(props.getString(INGESTION_SKIP_DIRS, DEFAULT_INGESTION_SKIP_DIRS));
        properties.setChunkSize(props.getInt(INGESTION_CHUNK_SIZE, DEFAULT_INGESTION_CHUNK_SIZE));
        properties.setChunkOverlap(props.getInt(INGESTION_CHUNK_OVERLAP, DEFAULT_INGESTION_CHUNK_OVERLAP));
        properties.setParallelism(props.getInt(INGESTION_PARALLELISM, DEFAULT_INGESTION_PARALLELISM));
        properties.setMaxRetries(props.getInt(INGESTION_MAX_RETRIES, DEFAULT_INGESTION_MAX_RETRIES));
        properties.setRetryDelayMs(props.getInt(INGESTION_RETRY_DELAY_MS, DEFAULT_INGESTION_RETRY_DELAY_MS));
        return properties;
    }

    // ─────────────── Provider Properties ───────────────

    /**
     * Loads provider properties POJO from configuration.
     */
    public static ProviderProperties providerProperties(SpectorProperties props) {
        ProviderProperties providerProperties = new ProviderProperties();

        EmbeddingProperties emb = embeddingProperties(props);
        providerProperties.setEmbedding(emb);

        GenerationProperties gen = providerProperties.getGeneration();
        String genType = props.getString(PROVIDER_GENERATION_TYPE, emb.type());
        String genModel = props.getString(PROVIDER_GENERATION_MODEL, DEFAULT_PROVIDER_GENERATION_MODEL);
        String genApiKey = props.getString(PROVIDER_GENERATION_API_KEY, emb.apiKey());
        String genBaseUrl = props.getString(PROVIDER_GENERATION_BASE_URL, emb.baseUrl());

        gen.setType(genType);
        gen.setModel(genModel);
        gen.setApiKey(genApiKey);
        gen.setBaseUrl(genBaseUrl);

        return providerProperties;
    }

    // ─────────────── Deprecated Bridge Accessors ───────────────

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static EmbeddingProperties embeddingDefaults(SpectorProperties props) {
        return embeddingProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static MemoryProperties memoryDefaults(SpectorProperties props) {
        return memoryProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static IngestionProperties ingestionDefaults(SpectorProperties props) {
        return ingestionProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static ProviderProperties providerDefaults(SpectorProperties props) {
        return providerProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static SpectrumProperties spectrumDefaults(SpectorProperties props) {
        return spectrumProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static HnswProperties hnswDefaults(SpectorProperties props) {
        return hnswProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static IvfProperties ivfDefaults(SpectorProperties props) {
        return ivfProperties(props);
    }
}
