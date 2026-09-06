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
 * Central factory for building typed configuration objects from {@link SpectorConfigSource}.
 *
 * <p>This is the bridge between the hierarchical property file system and the
 * strongly-typed configuration POJOs used by each Spector module.</p>
 */
public final class SpectorConfigFactory {

    private SpectorConfigFactory() {}

    // ─────────────── Aggregate Root ───────────────

    /**
     * Builds the aggregate {@link SpectorProperties} root POJO from a raw
     * {@link SpectorConfigSource}.
     *
     * <p>This is the canonical construction path for the aggregate configuration
     * object. It hydrates all typed sub-domain POJOs by delegating to the
     * individual factory methods.</p>
     *
     * @param source the raw configuration source
     * @return fully hydrated SpectorProperties aggregate
     */
    public static SpectorProperties spectorProperties(SpectorConfigSource source) {
        return new SpectorProperties(
                memoryProperties(source),
                providerProperties(source),
                ingestionProperties(source),
                hnswProperties(source),
                ivfProperties(source),
                spectrumProperties(source),
                telemetryProperties(source),
                multimodalProperties(source),
                hardwareProperties(source),
                eventsProperties(source),
                concurrencyProperties(source),
                source
        );
    }

    // ─────────────── HNSW Properties ───────────────

    /**
     * Loads HNSW properties from configuration.
     */
    public static HnswProperties hnswProperties(SpectorConfigSource props) {
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
    public static IvfProperties ivfProperties(SpectorConfigSource props) {
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
    public static SpectrumProperties spectrumProperties(SpectorConfigSource props) {
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
    public static EmbeddingProperties embeddingProperties(SpectorConfigSource props) {
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
        properties.setSequential(props.getBoolean(EMBEDDING_SEQUENTIAL, DEFAULT_EMBEDDING_SEQUENTIAL));

        return properties;
    }

    // ─────────────── Memory Properties ───────────────

    /**
     * Loads memory properties POJO from configuration.
     */
    public static MemoryProperties memoryProperties(SpectorConfigSource props) {
        MemoryProperties properties = new MemoryProperties();
        properties.setEnabled(props.getBoolean(MEMORY_ENABLED, DEFAULT_MEMORY_ENABLED));
        properties.setPersistenceMode(props.getEnum(MEMORY_PERSISTENCE_MODE, PersistenceMode.class, DEFAULT_MEMORY_PERSISTENCE_MODE));
        properties.setPersistencePath(props.getPath(MEMORY_PERSISTENCE_PATH, DEFAULT_MEMORY_PERSISTENCE_PATH).toString());
        properties.setDimensions(props.getInt(MEMORY_DIMENSIONS, DEFAULT_MEMORY_DIMENSIONS));
        properties.setCapacity(props.getInt(MEMORY_CAPACITY, DEFAULT_MEMORY_CAPACITY));
        properties.setNodesPerPartition(props.getInt(MEMORY_NODES_PER_PARTITION, DEFAULT_MEMORY_NODES_PER_PARTITION));
        properties.setDefaultIngestionTier(props.getEnum(MEMORY_DEFAULT_INGESTION_TIER, RememberTier.class, DEFAULT_MEMORY_DEFAULT_INGESTION_TIER));
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

        properties.setGraphExpansionMode(props.getString(MEMORY_GRAPH_EXPANSION_MODE,
                props.getString(GRAPH_EXPANSION_MODE_PROPERTY, DEFAULT_MEMORY_GRAPH_EXPANSION_MODE)));
        properties.setGraphExpansionThreshold((float) props.getDouble(MEMORY_GRAPH_EXPANSION_THRESHOLD,
                props.getDouble(MEMORY_GRAPH_EXPANSION_THRESHOLD_CAMEL,
                props.getDouble(GRAPH_EXPANSION_THRESHOLD_PROPERTY,
                props.getDouble(GRAPH_EXPANSION_THRESHOLD_BENCH_ALIAS, DEFAULT_MEMORY_GRAPH_EXPANSION_THRESHOLD)))));

        properties.setSchedulerEnabled(props.getBoolean(MEMORY_SCHEDULER_ENABLED, DEFAULT_MEMORY_SCHEDULER_ENABLED));
        properties.setWanderEnabled(props.getBoolean(MEMORY_WANDER_ENABLED, DEFAULT_MEMORY_WANDER_ENABLED));
        properties.setDreamEnabled(props.getBoolean(MEMORY_DREAM_ENABLED, DEFAULT_MEMORY_DREAM_ENABLED));

        // Sub-domain children
        properties.setRecall(recallProperties(props));
        properties.setRemember(rememberProperties(props));
        properties.setGraph(graphProperties(props));
        properties.setCircadian(circadianProperties(props));
        properties.setDream(dreamProperties(props));
        properties.setTwofactor(twoFactorProperties(props));
        properties.setWal(walProperties(props));
        properties.setVacuum(vacuumProperties(props));
        properties.setSession(sessionProperties(props));

        properties.setWorkingCapacity(props.getInt(MEMORY_WORKING_CAPACITY, DEFAULT_MEMORY_WORKING_CAPACITY));
        properties.setEpisodicPartitionCapacity(props.getInt(MEMORY_EPISODIC_PARTITION_CAPACITY, DEFAULT_MEMORY_EPISODIC_PARTITION_CAPACITY));
        properties.setSemanticCapacity(props.getInt(MEMORY_SEMANTIC_CAPACITY, DEFAULT_MEMORY_SEMANTIC_CAPACITY));
        properties.setProceduralCapacity(props.getInt(MEMORY_PROCEDURAL_CAPACITY, DEFAULT_MEMORY_PROCEDURAL_CAPACITY));
        properties.setEntityGraphCapacity(props.getInt(MEMORY_ENTITY_GRAPH_CAPACITY, DEFAULT_MEMORY_ENTITY_GRAPH_CAPACITY));
        properties.setPinnedQuota(props.getInt(MEMORY_PINNED_QUOTA, DEFAULT_MEMORY_PINNED_QUOTA));
        properties.setCheckpointIntervalSeconds(props.getInt(MEMORY_CHECKPOINT_INTERVAL_SECONDS, DEFAULT_MEMORY_CHECKPOINT_INTERVAL_SECONDS));

        properties.setTextSegmentSize(props.getLong(MEMORY_TEXT_SEGMENT_SIZE, DEFAULT_MEMORY_TEXT_SEGMENT_SIZE));
        properties.setEpisodicSegmentSize(props.getLong(MEMORY_EPISODIC_SEGMENT_SIZE, DEFAULT_MEMORY_EPISODIC_SEGMENT_SIZE));
        properties.setPersistWorkingMemory(props.getBoolean(MEMORY_PERSIST_WORKING_MEMORY, DEFAULT_MEMORY_PERSIST_WORKING_MEMORY));
        properties.setPinSourceEpisodes(props.getBoolean(MEMORY_PIN_SOURCE_EPISODES, DEFAULT_MEMORY_PIN_SOURCE_EPISODES));

        properties.setIdStrategy(props.getString(MEMORY_ID_STRATEGY, DEFAULT_MEMORY_ID_STRATEGY));
        properties.setEdgeImportance(props.getString(MEMORY_EDGE_IMPORTANCE, DEFAULT_MEMORY_EDGE_IMPORTANCE));
        properties.setNamespaceId(props.getString(MEMORY_NAMESPACE_ID, DEFAULT_MEMORY_NAMESPACE_ID));

        properties.setMaxNamespaces(props.getInt("spector.memory.max-namespaces", 100));

        // Pathway enabled — respect explicit boolean, or derive from recall.engine
        boolean pathwayDefault = true;
        if (properties.getRecall() != null && properties.getRecall().getEngine() != null) {
            String eng = properties.getRecall().getEngine();
            if ("direct".equalsIgnoreCase(eng) || "legacy".equalsIgnoreCase(eng)) {
                pathwayDefault = false;
            }
        }
        properties.setPathwayEnabled(props.getBoolean("spector.memory.pathway.enabled", pathwayDefault));

        // Sync deprecated top-level retrieval fields with unified recall.mmr
        properties.setEnableMmr(properties.getRecall().getMmr().isEnabled());
        properties.setMmrLambda(properties.getRecall().getMmr().getLambda());

        return properties;
    }

    // ─────────────── AISME Properties ───────────────

    /**
     * Loads Active Inference Self-Model Engine (AISME) properties from configuration.
     */
    public static AismeProperties aismeProperties(SpectorConfigSource props) {
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
        properties.setEnableLifespan(props.getBoolean(MEMORY_AISME_LIFESPAN_ENABLED, DEFAULT_MEMORY_AISME_LIFESPAN_ENABLED));
        properties.setLifespanTau0(props.getFloat(MEMORY_AISME_LIFESPAN_TAU_0, DEFAULT_MEMORY_AISME_LIFESPAN_TAU_0));
        properties.setLifespanK(props.getFloat(MEMORY_AISME_LIFESPAN_K, DEFAULT_MEMORY_AISME_LIFESPAN_K));
        properties.setLifespanT0Epochs(props.getLong(MEMORY_AISME_LIFESPAN_T0_EPOCHS, DEFAULT_MEMORY_AISME_LIFESPAN_T0_EPOCHS));
        properties.setLifespanVTarget(props.getLong(MEMORY_AISME_LIFESPAN_V_TARGET, DEFAULT_MEMORY_AISME_LIFESPAN_V_TARGET));
        properties.setLifespanGamma(props.getFloat(MEMORY_AISME_LIFESPAN_GAMMA, DEFAULT_MEMORY_AISME_LIFESPAN_GAMMA));
        properties.setLifespanFlashbulbProtect(props.getBoolean(MEMORY_AISME_LIFESPAN_FLASHBULB_PROTECT, DEFAULT_MEMORY_AISME_LIFESPAN_FLASHBULB_PROTECT));
        return properties;
    }

    // ─────────────── Recall Properties ───────────────

    /**
     * Loads recall pipeline properties from configuration.
     *
     * <p>Reads from canonical {@code spector.memory.recall.*} keys with
     * fallback alias resolution from legacy {@code spector.recall.*} keys.</p>
     */
    public static RecallProperties recallProperties(SpectorConfigSource props) {
        RecallProperties recall = new RecallProperties();

        // Top-level recall — try canonical spector.memory.recall.* first, fall back to spector.recall.*
        recall.setScoringMode(props.getString("spector.memory.recall.scoring-mode",
                props.getString("spector.recall.scoring-mode", "COGNITIVE")));
        recall.setStrictnessCoefficient((float) props.getDouble("spector.memory.recall.strictness-coefficient",
                props.getDouble("spector.recall.strictness-coefficient", 1.0)));
        recall.setTraceEnabled(props.getBoolean("spector.memory.recall.trace.enabled",
                props.getBoolean("spector.recall.trace.enabled", false)));
        recall.setMode(props.getString("spector.memory.recall.mode",
                props.getString("spector.recall.mode", "LEARN")));
        recall.setEngine(props.getString("spector.memory.recall.engine",
                props.getString("spector.recall.engine",
                        props.getString("spector.memory.engine", "pathway"))));
        recall.setMaxReplayEvents(props.getInt("spector.memory.recall.max-replay-events",
                props.getInt("spector.recall.max-replay-events", 100000)));
        recall.setIncludeContradictions(props.getBoolean("spector.memory.recall.include-contradictions",
                props.getBoolean("spector.recall.include-contradictions", false)));

        var mmr = recall.getMmr();
        boolean defaultMmr = false;
        boolean mmrEnabled = props.getBoolean("spector.memory.recall.mmr.enabled",
                props.getBoolean("spector.recall.mmr.enabled",
                        props.getBoolean("spector.memory.retrieval.enable-mmr",
                                props.getBoolean("spector.memory.enable-mmr",
                                        props.getBoolean("spector.memory.retrieval.mmr.enabled", defaultMmr)))));
        mmr.setEnabled(mmrEnabled);

        float mmrLambda = (float) props.getDouble("spector.memory.recall.mmr.lambda",
                props.getDouble("spector.recall.mmr.lambda",
                        props.getDouble("spector.memory.retrieval.mmr-lambda",
                                props.getDouble("spector.memory.retrieval.mmr.lambda",
                                        props.getDouble("spector.memory.mmr-lambda", 0.5)))));
        mmr.setLambda(mmrLambda);

        var textSearch = recall.getTextSearch();
        textSearch.setEnabled(props.getBoolean("spector.memory.recall.text-search.enabled",
                props.getBoolean("spector.recall.text-search.enabled", true)));
        textSearch.setMode(props.getString("spector.memory.recall.text-search.mode",
                props.getString("spector.recall.text-search.mode", "HYBRID")));

        var reranker = recall.getReranker();
        reranker.setEnabled(props.getBoolean("spector.memory.recall.reranker.enabled",
                props.getBoolean("spector.recall.reranker.enabled", false)));
        reranker.setDepth(props.getInt("spector.memory.recall.reranker.depth",
                props.getInt("spector.recall.reranker.depth", 50)));

        var lateral = recall.getLateral();
        lateral.setEnabled(props.getBoolean("spector.memory.recall.lateral.enabled",
                props.getBoolean("spector.recall.lateral.enabled", false)));
        lateral.setDistanceThreshold((float) props.getDouble("spector.memory.recall.lateral.distance-threshold",
                props.getDouble("spector.recall.lateral.distance-threshold", 1.2)));
        lateral.setMinTagOverlap((float) props.getDouble("spector.memory.recall.lateral.min-tag-overlap",
                props.getDouble("spector.recall.lateral.min-tag-overlap", 0.5)));

        var autoProfile = recall.getAutoProfile();
        autoProfile.setEnabled(props.getBoolean("spector.memory.recall.auto-profile.enabled",
                props.getBoolean("spector.recall.auto-profile.enabled", false)));

        var valenceAlignment = recall.getValenceAlignment();
        valenceAlignment.setEnabled(props.getBoolean("spector.memory.recall.valence-alignment.enabled",
                props.getBoolean("spector.recall.valence-alignment.enabled", false)));

        return recall;
    }

    // ─────────────── Remember Properties ───────────────

    /**
     * Loads remember pipeline properties from configuration.
     *
     * <p>Reads from canonical {@code spector.memory.remember.*} keys with
     * fallback alias resolution from legacy {@code spector.ingestion.*} and
     * {@code spector.memory.default-ingestion-tier} keys.</p>
     */
    public static RememberProperties rememberProperties(SpectorConfigSource props) {
        RememberProperties remember = new RememberProperties();

        // Default tier — canonical: spector.memory.remember.default-tier, legacy: spector.memory.default-ingestion-tier
        remember.setDefaultTier(props.getString("spector.memory.remember.default-tier",
                props.getString("spector.memory.default-ingestion-tier", "SEMANTIC")));

        // Chunk config — canonical: spector.memory.remember.chunk.*, legacy: spector.ingestion.chunk-*
        var chunk = remember.getChunk();
        chunk.setSize(props.getInt("spector.memory.remember.chunk.size",
                props.getInt("spector.ingestion.chunk-size", 2500)));
        chunk.setOverlap(props.getInt("spector.memory.remember.chunk.overlap",
                props.getInt("spector.ingestion.chunk-overlap", 200)));
        chunk.setStrategy(props.getString("spector.memory.remember.chunk.strategy", "markdown"));

        // File crawler config — canonical: spector.memory.remember.files.*, legacy: spector.ingestion.*
        var files = remember.getFiles();
        files.setRootDirectory(props.getString("spector.memory.remember.files.root-directory",
                props.getPath("spector.ingestion.root-directory", java.nio.file.Path.of(".")).toString()));
        files.setPattern(props.getString("spector.memory.remember.files.pattern",
                props.getString("spector.ingestion.file-pattern", "**/*.md")));
        files.setSkipDirs(props.getString("spector.memory.remember.files.skip-dirs",
                props.getString("spector.ingestion.skip-dirs", ".git,.idea,.mvn,target,node_modules,.github")));
        files.setParallelism(props.getInt("spector.memory.remember.files.parallelism",
                props.getInt("spector.ingestion.parallelism", 4)));
        files.setMaxRetries(props.getInt("spector.memory.remember.files.max-retries",
                props.getInt("spector.ingestion.max-retries", 3)));
        files.setRetryDelayMs(props.getInt("spector.memory.remember.files.retry-delay-ms",
                props.getInt("spector.ingestion.retry-delay-ms", 2000)));

        // ICNU weights — canonical: spector.memory.remember.icnu.*, legacy: spector.memory.icnu.*
        var icnu = remember.getIcnu();
        icnu.setThreshold((float) props.getDouble("spector.memory.remember.icnu.threshold",
                props.getDouble("spector.memory.icnu.threshold", 0.2)));
        icnu.setSteepness((float) props.getDouble("spector.memory.remember.icnu.steepness",
                props.getDouble("spector.memory.icnu.steepness", 8.0)));
        icnu.setWeightInterest((float) props.getDouble("spector.memory.remember.icnu.weight-interest",
                props.getDouble("spector.memory.icnu.weight-interest", 0.30)));
        icnu.setWeightChallenge((float) props.getDouble("spector.memory.remember.icnu.weight-challenge",
                props.getDouble("spector.memory.icnu.weight-challenge", 0.10)));
        icnu.setWeightNovelty((float) props.getDouble("spector.memory.remember.icnu.weight-novelty",
                props.getDouble("spector.memory.icnu.weight-novelty", 0.40)));
        icnu.setWeightUrgency((float) props.getDouble("spector.memory.remember.icnu.weight-urgency",
                props.getDouble("spector.memory.icnu.weight-urgency", 0.20)));

        // Cognitive write parameters — canonical spector.memory.remember.* with spector.memory.* fallback
        remember.setSurpriseWarmup(props.getInt("spector.memory.remember.surprise-warmup",
                props.getInt("spector.memory.surprise-warmup", 10)));
        remember.setFlashbulbThreshold((float) props.getDouble("spector.memory.remember.flashbulb-threshold",
                props.getDouble("spector.memory.flashbulb-threshold", 3.0)));
        remember.setValenceLearningRate((float) props.getDouble("spector.memory.remember.valence-learning-rate",
                props.getDouble("spector.memory.valence-learning-rate", 0.3)));
        remember.setDeduplicationRadius((float) props.getDouble("spector.memory.remember.deduplication-radius",
                props.getDouble("spector.memory.deduplication-radius", 0.05)));
        remember.setInhibitionTtlMs(props.getLong("spector.memory.remember.inhibition-ttl-ms",
                props.getLong("spector.memory.inhibition-ttl-ms", 300000L)));
        remember.setInhibitionFloor((float) props.getDouble("spector.memory.remember.inhibition-floor",
                props.getDouble("spector.memory.inhibition-floor", 0.1)));
        remember.setHabituationDecayRate((float) props.getDouble("spector.memory.remember.habituation-decay-rate",
                props.getDouble("spector.memory.habituation-decay-rate", 0.2)));
        remember.setLtpCooldownMs(props.getLong("spector.memory.remember.ltp-cooldown-ms",
                props.getLong("spector.memory.ltp-cooldown-ms", 300000L)));
        remember.setPinSourceEpisodes(props.getBoolean("spector.memory.remember.pin-source-episodes",
                props.getBoolean("spector.memory.pin-source-episodes", false)));
        remember.setPinnedQuota(props.getInt("spector.memory.remember.pinned-quota",
                props.getInt("spector.memory.pinned-quota", 10000)));

        return remember;
    }

    // ─────────────── Graph Properties ───────────────

    /**
     * Loads graph memory properties from configuration.
     *
     * <p>Reads from {@code spector.memory.graph.*}, {@code spector.memory.hebbian.*},
     * {@code spector.memory.stdp.*}, {@code spector.memory.bridge.*}, and
     * {@code spector.memory.entity.*} namespaces.</p>
     */
    public static GraphProperties graphProperties(SpectorConfigSource props) {
        GraphProperties graph = new GraphProperties();

        graph.setExpansionMode(props.getString(MEMORY_GRAPH_EXPANSION_MODE, DEFAULT_MEMORY_GRAPH_EXPANSION_MODE));
        graph.setExpansionThreshold((float) props.getDouble(MEMORY_GRAPH_EXPANSION_THRESHOLD, DEFAULT_MEMORY_GRAPH_EXPANSION_THRESHOLD));
        graph.setCausalBoost((float) props.getDouble(MEMORY_GRAPH_CAUSAL_BOOST, DEFAULT_MEMORY_GRAPH_CAUSAL_BOOST));
        graph.setHebbianBoost((float) props.getDouble(MEMORY_GRAPH_HEBBIAN_BOOST, DEFAULT_MEMORY_GRAPH_HEBBIAN_BOOST));
        graph.setTemporalForward((float) props.getDouble(MEMORY_GRAPH_TEMPORAL_FWD, DEFAULT_MEMORY_GRAPH_TEMPORAL_FWD));
        graph.setTemporalBackward((float) props.getDouble(MEMORY_GRAPH_TEMPORAL_BWD, DEFAULT_MEMORY_GRAPH_TEMPORAL_BWD));
        graph.setEntityAttenuation((float) props.getDouble(MEMORY_GRAPH_ENTITY_ATTENUATION, DEFAULT_MEMORY_GRAPH_ENTITY_ATTENUATION));

        var hebbian = graph.getHebbian();
        hebbian.setMaxDegree(props.getInt(MEMORY_HEBBIAN_MAX_DEGREE, DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE));
        hebbian.setSessionBoundaryMs(props.getLong(MEMORY_HEBBIAN_SESSION_BOUNDARY_MS, DEFAULT_MEMORY_HEBBIAN_SESSION_BOUNDARY_MS));
        hebbian.setPromotionMinWeight((float) props.getDouble(MEMORY_HEBBIAN_PROMOTION_MIN_WEIGHT, DEFAULT_MEMORY_HEBBIAN_PROMOTION_MIN_WEIGHT));
        hebbian.setDecayFactor((float) props.getDouble(MEMORY_HEBBIAN_DECAY_FACTOR, DEFAULT_MEMORY_HEBBIAN_DECAY_FACTOR));
        hebbian.setDecayFloor((float) props.getDouble(MEMORY_HEBBIAN_DECAY_FLOOR, DEFAULT_MEMORY_HEBBIAN_DECAY_FLOOR));
        hebbian.setActivationCutoff((float) props.getDouble(MEMORY_HEBBIAN_ACTIVATION_CUTOFF, DEFAULT_MEMORY_HEBBIAN_ACTIVATION_CUTOFF));
        hebbian.setHopAttenuation((float) props.getDouble(MEMORY_HEBBIAN_HOP_ATTENUATION, DEFAULT_MEMORY_HEBBIAN_HOP_ATTENUATION));
        hebbian.setDefaultWeightDelta((float) props.getDouble(MEMORY_HEBBIAN_DEFAULT_WEIGHT_DELTA, DEFAULT_MEMORY_HEBBIAN_DEFAULT_WEIGHT_DELTA));
        hebbian.setNeutralBridgeScore(props.getInt(MEMORY_HEBBIAN_NEUTRAL_BRIDGE_SCORE, DEFAULT_MEMORY_HEBBIAN_NEUTRAL_BRIDGE_SCORE));

        var stdp = graph.getStdp();
        stdp.setAPlus((float) props.getDouble(MEMORY_STDP_A_PLUS, DEFAULT_MEMORY_STDP_A_PLUS));
        stdp.setAMinus((float) props.getDouble(MEMORY_STDP_A_MINUS, DEFAULT_MEMORY_STDP_A_MINUS));
        stdp.setTauPlus((float) props.getDouble(MEMORY_STDP_TAU_PLUS, DEFAULT_MEMORY_STDP_TAU_PLUS));
        stdp.setTauMinus((float) props.getDouble(MEMORY_STDP_TAU_MINUS, DEFAULT_MEMORY_STDP_TAU_MINUS));

        var bridge = graph.getBridge();
        bridge.setSampleCount(props.getInt("spector.memory.bridge.sample-count", 15));
        bridge.setBudgetMs(props.getLong("spector.memory.bridge.budget-ms", 500L));

        var entity = graph.getEntity();
        entity.setExtractionMode(props.getString("spector.memory.entity.extraction-mode", "NONE"));
        entity.setResolutionEnabled(props.getBoolean("spector.memory.entity.resolution-enabled", false));
        entity.setShadowMode(props.getBoolean("spector.memory.entity.shadow-mode", true));
        entity.setMaxDegree(props.getInt("spector.memory.entity.max-degree", 16));
        entity.setMaxPerMemory(props.getInt("spector.memory.entity.max-per-memory", 10));
        entity.setCosineThreshold((float) props.getDouble("spector.memory.entity.cosine-threshold", 0.85));
        entity.setRetentionDays(props.getInt("spector.memory.entity.retention-days", 7));
        entity.setDecayFactor((float) props.getDouble("spector.memory.entity.decay-factor", 0.95));
        entity.setPruneThreshold((float) props.getDouble("spector.memory.entity.prune-threshold", 0.5));
        entity.setAdjDecayFactor((float) props.getDouble("spector.memory.entity.adj-decay-factor", 0.95));
        entity.setAdjPruneThreshold((float) props.getDouble("spector.memory.entity.adj-prune-threshold", 0.2));
        entity.setMergeDistance(props.getInt("spector.memory.entity.merge-distance", 2));

        return graph;
    }

    // ─────────────── Circadian Properties ───────────────

    /**
     * Loads circadian / sleep consolidation properties from configuration.
     *
     * <p>Reads from {@code spector.memory.circadian.*} namespace.</p>
     */
    public static CircadianProperties circadianProperties(SpectorConfigSource props) {
        CircadianProperties circadian = new CircadianProperties();
        circadian.setEnabled(props.getBoolean(MEMORY_CIRCADIAN_ENABLED, DEFAULT_MEMORY_CIRCADIAN_ENABLED));
        circadian.setVolumeTrigger(props.getInt(MEMORY_CIRCADIAN_VOLUME_TRIGGER, DEFAULT_MEMORY_CIRCADIAN_VOLUME_TRIGGER));
        circadian.setTimeTriggerSeconds(props.getDuration(MEMORY_CIRCADIAN_TIME_TRIGGER, DEFAULT_MEMORY_CIRCADIAN_TIME_TRIGGER).toSeconds());
        circadian.setTombstoneThreshold((float) props.getDouble(MEMORY_CIRCADIAN_TOMBSTONE_THRESHOLD, DEFAULT_MEMORY_CIRCADIAN_TOMBSTONE_THRESHOLD));
        circadian.setDecayPruneThreshold((float) props.getDouble(MEMORY_CIRCADIAN_DECAY_PRUNE_THRESHOLD, DEFAULT_MEMORY_CIRCADIAN_DECAY_PRUNE_THRESHOLD));
        circadian.setInterferenceThreshold((float) props.getDouble("spector.memory.circadian.interference-threshold", 0.12f));
        circadian.setInterferenceDecayFactor((float) props.getDouble("spector.memory.circadian.interference-decay-factor", 0.7f));
        circadian.setOrchestrator(props.getString(MEMORY_REFLECT_ORCHESTRATOR, DEFAULT_MEMORY_REFLECT_ORCHESTRATOR));
        return circadian;
    }

    // ─────────────── Global Mode ───────────────

    /**
     * Resolves the global operating mode: {@link SpectorMode#MEMORY}.
     */
    public static SpectorMode mode(SpectorConfigSource props) {
        return SpectorMode.MEMORY;
    }

    // ─────────────── Ingestion Properties ───────────────

    /**
     * Loads ingestion properties POJO from configuration.
     */
    public static IngestionProperties ingestionProperties(SpectorConfigSource props) {
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
    public static ProviderProperties providerProperties(SpectorConfigSource props) {
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

        providerProperties.setSslInsecure(props.getBoolean(PROVIDER_SSL_INSECURE, DEFAULT_PROVIDER_SSL_INSECURE));

        return providerProperties;
    }

    // ─────────────── Dream Properties ───────────────

    /**
     * Loads dream and thought experiment properties from configuration.
     */
    public static DreamProperties dreamProperties(SpectorConfigSource props) {
        DreamProperties dream = new DreamProperties();
        dream.setEnabled(props.getBoolean(MEMORY_DREAM_ENABLED, DEFAULT_MEMORY_DREAM_ENABLED));
        dream.setNoiseScale((float) props.getDouble(MEMORY_DREAM_NOISE_SCALE, DEFAULT_MEMORY_DREAM_NOISE_SCALE));
        dream.setTemperatureRem((float) props.getDouble(MEMORY_DREAM_TEMPERATURE_REM, DEFAULT_MEMORY_DREAM_TEMPERATURE_REM));
        dream.setTemperatureDaydream((float) props.getDouble(MEMORY_DREAM_TEMPERATURE_DAYDREAM, DEFAULT_MEMORY_DREAM_TEMPERATURE_DAYDREAM));
        dream.setTemperatureThought((float) props.getDouble(MEMORY_DREAM_TEMPERATURE_THOUGHT, DEFAULT_MEMORY_DREAM_TEMPERATURE_THOUGHT));
        dream.setMaxDreamsPerCycle(props.getInt(MEMORY_DREAM_MAX_DREAMS_PER_CYCLE, DEFAULT_MEMORY_DREAM_MAX_DREAMS_PER_CYCLE));
        dream.setMaxCounterfactualsPerSeed(props.getInt(MEMORY_DREAM_MAX_COUNTERFACTUALS_PER_SEED, DEFAULT_MEMORY_DREAM_MAX_COUNTERFACTUALS_PER_SEED));
        dream.setPersistenceThreshold((float) props.getDouble(MEMORY_DREAM_PERSISTENCE_THRESHOLD, DEFAULT_MEMORY_DREAM_PERSISTENCE_THRESHOLD));
        dream.setLangevinStepSize((float) props.getDouble(MEMORY_DREAM_LANGEVIN_STEP_SIZE, DEFAULT_MEMORY_DREAM_LANGEVIN_STEP_SIZE));
        dream.setLangevinSteps(props.getInt(MEMORY_DREAM_LANGEVIN_STEPS, DEFAULT_MEMORY_DREAM_LANGEVIN_STEPS));
        dream.setNoveltyRadius((float) props.getDouble(MEMORY_DREAM_NOVELTY_RADIUS, DEFAULT_MEMORY_DREAM_NOVELTY_RADIUS));
        dream.setHebbianInhibitionDelta((float) props.getDouble(MEMORY_DREAM_HEBBIAN_INHIBITION_DELTA, DEFAULT_MEMORY_DREAM_HEBBIAN_INHIBITION_DELTA));
        dream.setJournalEnabled(props.getBoolean(MEMORY_DREAM_JOURNAL_ENABLED, DEFAULT_MEMORY_DREAM_JOURNAL_ENABLED));
        dream.setCycleFrequency(props.getInt(MEMORY_DREAM_CYCLE_FREQUENCY, DEFAULT_MEMORY_DREAM_CYCLE_FREQUENCY));
        dream.setSeedWeightRecency((float) props.getDouble(MEMORY_DREAM_SEED_WEIGHT_RECENCY, DEFAULT_MEMORY_DREAM_SEED_WEIGHT_RECENCY));
        dream.setSeedWeightNovelty((float) props.getDouble(MEMORY_DREAM_SEED_WEIGHT_NOVELTY, DEFAULT_MEMORY_DREAM_SEED_WEIGHT_NOVELTY));
        dream.setSeedWeightSoul((float) props.getDouble(MEMORY_DREAM_SEED_WEIGHT_SOUL, DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SOUL));
        dream.setSeedWeightSalience((float) props.getDouble(MEMORY_DREAM_SEED_WEIGHT_SALIENCE, DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SALIENCE));
        dream.setIdentityResonanceThreshold((float) props.getDouble(MEMORY_DREAM_IDENTITY_RESONANCE_THRESHOLD, DEFAULT_MEMORY_DREAM_IDENTITY_RESONANCE_THRESHOLD));
        dream.setEthicalViolationThreshold((float) props.getDouble(MEMORY_DREAM_ETHICAL_VIOLATION_THRESHOLD, DEFAULT_MEMORY_DREAM_ETHICAL_VIOLATION_THRESHOLD));
        dream.setLangevinSoulAttractorLambda((float) props.getDouble(MEMORY_DREAM_LANGEVIN_SOUL_ATTRACTOR_LAMBDA, DEFAULT_MEMORY_DREAM_LANGEVIN_SOUL_ATTRACTOR_LAMBDA));
        dream.setHartmannOpennessMultiplier((float) props.getDouble(MEMORY_DREAM_HARTMANN_OPENNESS_MULTIPLIER, DEFAULT_MEMORY_DREAM_HARTMANN_OPENNESS_MULTIPLIER));
        dream.setHartmannVigilanceMultiplier((float) props.getDouble(MEMORY_DREAM_HARTMANN_VIGILANCE_MULTIPLIER, DEFAULT_MEMORY_DREAM_HARTMANN_VIGILANCE_MULTIPLIER));
        return dream;
    }

    // ─────────────── Two-Factor Properties ───────────────

    /**
     * Loads Two-Factor memory properties from configuration.
     */
    public static TwoFactorProperties twoFactorProperties(SpectorConfigSource props) {
        TwoFactorProperties tf = new TwoFactorProperties();
        tf.setEnabled(props.getBoolean(MEMORY_TWOFACTOR_ENABLED, DEFAULT_MEMORY_TWOFACTOR_ENABLED));
        tf.setSGain((float) props.getDouble(MEMORY_TWOFACTOR_S_GAIN, DEFAULT_MEMORY_TWOFACTOR_S_GAIN));
        tf.setSMax((float) props.getDouble(MEMORY_TWOFACTOR_S_MAX, DEFAULT_MEMORY_TWOFACTOR_S_MAX));
        tf.setSExponent((float) props.getDouble(MEMORY_TWOFACTOR_S_EXPONENT, DEFAULT_MEMORY_TWOFACTOR_S_EXPONENT));
        return tf;
    }

    // ─────────────── WAL Properties ───────────────

    /**
     * Loads memory WAL properties from configuration.
     */
    public static WalProperties walProperties(SpectorConfigSource props) {
        return new WalProperties(props.getLong(MEMORY_WAL_MAX_CHUNK_BYTES, DEFAULT_MEMORY_WAL_MAX_CHUNK_BYTES));
    }

    // ─────────────── Vacuum Properties ───────────────

    /**
     * Loads memory vacuum properties from configuration.
     */
    public static VacuumProperties vacuumProperties(SpectorConfigSource props) {
        return new VacuumProperties((float) props.getDouble(MEMORY_VACUUM_THRESHOLD, DEFAULT_MEMORY_VACUUM_THRESHOLD));
    }

    // ─────────────── Session Properties ───────────────

    /**
     * Loads memory session buffer properties from configuration.
     */
    public static SessionProperties sessionProperties(SpectorConfigSource props) {
        return new SessionProperties(
                props.getInt(MEMORY_SESSION_BUFFER_SIZE, DEFAULT_MEMORY_SESSION_BUFFER_SIZE),
                props.getLong(MEMORY_SESSION_BUFFER_TTL_MS, DEFAULT_MEMORY_SESSION_BUFFER_TTL_MS)
        );
    }

    // ─────────────── Telemetry Properties ───────────────

    /**
     * Loads telemetry properties from configuration.
     */
    public static TelemetryProperties telemetryProperties(SpectorConfigSource props) {
        return TelemetryProperties.from(props);
    }

    // ─────────────── Multimodal Properties ───────────────

    /**
     * Loads multimodal sensory processing properties from configuration.
     */
    public static MultimodalProperties multimodalProperties(SpectorConfigSource props) {
        return MultimodalProperties.from(props);
    }

    // ─────────────── Hardware Properties ───────────────

    /**
     * Loads hardware & GPU acceleration properties from configuration.
     */
    public static HardwareProperties hardwareProperties(SpectorConfigSource props) {
        HardwareProperties hw = new HardwareProperties();
        int threshold = props.getInt(HARDWARE_GPU_BATCH_THRESHOLD,
                props.getInt("spector.hardware.gpu-batch-threshold",
                        props.getInt(HARDWARE_GPU_BATCH_THRESHOLD_LEGACY, DEFAULT_HARDWARE_GPU_BATCH_THRESHOLD)));
        hw.setGpuBatchThreshold(threshold);
        hw.setGpuBatchMinWindowMs(props.getLong(GPU_BATCH_MIN_WINDOW_MS, DEFAULT_GPU_BATCH_MIN_WINDOW_MS));
        hw.setGpuBatchMaxWindowMs(props.getLong(GPU_BATCH_MAX_WINDOW_MS, DEFAULT_GPU_BATCH_MAX_WINDOW_MS));
        hw.setGpuBatchDefaultMaxBatch(props.getInt(GPU_BATCH_DEFAULT_MAX_BATCH, DEFAULT_GPU_BATCH_DEFAULT_MAX_BATCH));
        hw.setGpuMemoryMinBudgetBytes(props.getLong(GPU_MEMORY_MIN_BUDGET_BYTES, DEFAULT_GPU_MEMORY_MIN_BUDGET_BYTES));
        return hw;
    }

    // ─────────────── Events Properties ───────────────

    /**
     * Loads event bus properties from configuration.
     */
    public static EventsProperties eventsProperties(SpectorConfigSource props) {
        return new EventsProperties(props.getBoolean(EVENTS_ASYNC, DEFAULT_EVENTS_ASYNC));
    }

    // ─────────────── Concurrency Properties ───────────────

    /**
     * Loads concurrency properties from configuration.
     */
    public static ConcurrencyProperties concurrencyProperties(SpectorConfigSource props) {
        return new ConcurrencyProperties(props.getBoolean(CONCURRENCY_STRUCTURED, DEFAULT_CONCURRENCY_STRUCTURED));
    }

    // ─────────────── Deprecated Bridge Accessors ───────────────

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static EmbeddingProperties embeddingDefaults(SpectorConfigSource props) {
        return embeddingProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static MemoryProperties memoryDefaults(SpectorConfigSource props) {
        return memoryProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static IngestionProperties ingestionDefaults(SpectorConfigSource props) {
        return ingestionProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static ProviderProperties providerDefaults(SpectorConfigSource props) {
        return providerProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static SpectrumProperties spectrumDefaults(SpectorConfigSource props) {
        return spectrumProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static HnswProperties hnswDefaults(SpectorConfigSource props) {
        return hnswProperties(props);
    }

    @Deprecated(since = "0.1.0", forRemoval = true)
    public static IvfProperties ivfDefaults(SpectorConfigSource props) {
        return ivfProperties(props);
    }
}
