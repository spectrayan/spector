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

import static com.spectrayan.spector.config.SpectorPropertyKeys.*;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Central factory for building typed configuration objects from {@link SpectorProperties}.
 *
 * <p>This is the bridge between the hierarchical property file system and the
 * strongly-typed config records used by each Spector module. Each factory method
 * reads from the unified property namespace and produces the corresponding
 * module-level configuration.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   SpectorProperties props = SpectorProperties.load();
 *
 *   // Get individual config sections
 *   EmbeddingProperties embed = SpectorConfigFactory.embeddingDefaults(props);
 *   ProviderProperties provider = SpectorConfigFactory.providerDefaults(props);
 * }</pre>
 *
 * <p>Module-level config objects (SpectorConfig, EmbeddingProperties, etc.)
 * can use these factory methods to construct themselves from properties,
 * keeping the dependency on commons lightweight.</p>
 */
public final class SpectorConfigFactory {

    private SpectorConfigFactory() {}

    // ─────────────── HNSW Defaults ───────────────

    /**
     * Default values for HNSW index parameters.
     *
     * @param m              max bi-directional connections per node per layer
     * @param efConstruction beam width during index construction
     * @param efSearch       beam width during search
     */
    public record HnswDefaults(int m, int efConstruction, int efSearch) {}

    /**
     * Loads HNSW defaults from properties.
     */
    public static HnswDefaults hnswDefaults(SpectorProperties props) {
        return new HnswDefaults(
                props.getInt(HNSW_M, 16),
                props.getInt(HNSW_EF_CONSTRUCTION, 200),
                props.getInt(HNSW_EF_SEARCH, 50)
        );
    }

    // ─────────────── IVF/PQ Defaults ───────────────

    /**
     * Default values for IVF/PQ index parameters.
     */
    public record IvfDefaults(int nlist, int nprobe, int pqSubspaces) {}

    /**
     * Loads IVF defaults from properties.
     */
    public static IvfDefaults ivfDefaults(SpectorProperties props) {
        return new IvfDefaults(
                props.getInt(IVF_NLIST, 0),
                props.getInt(IVF_NPROBE, 0),
                props.getInt(IVF_PQ_SUBSPACES, 0)
        );
    }

    // ─────────────── Spectrum Defaults ───────────────

    /**
     * Default values for the SPECTRUM adaptive index.
     */
    public record SpectrumDefaults(
            int nCentroids, int nProbe, int shardThreshold,
            int oversamplingFactor, int kmeansIterations
    ) {}

    /**
     * Loads Spectrum defaults from properties.
     */
    public static SpectrumDefaults spectrumDefaults(SpectorProperties props) {
        return new SpectrumDefaults(
                props.getInt(SPECTRUM_N_CENTROIDS, 256),
                props.getInt(SPECTRUM_N_PROBE, 16),
                props.getInt(SPECTRUM_SHARD_THRESHOLD, 20_000),
                props.getInt(SPECTRUM_OVERSAMPLING_FACTOR, 3),
                props.getInt(SPECTRUM_KMEANS_ITERATIONS, 25)
        );
    }

    // ─────────────── Embedding Properties ───────────────

    /**
     * Loads embedding provider properties from properties.
     */
    public static EmbeddingProperties embeddingDefaults(SpectorProperties props) {
        EmbeddingProperties properties = new EmbeddingProperties();

        String type = props.getString(PROVIDER_EMBEDDING_TYPE,
                props.getString(LEGACY_EMBEDDING_BASE_URL, "").contains("localhost") ? "ollama" : "ollama");
        String model = props.getString(PROVIDER_EMBEDDING_MODEL,
                props.getString(LEGACY_EMBEDDING_MODEL, "nomic-embed-text"));
        String apiKey = props.getString(PROVIDER_EMBEDDING_API_KEY, "");
        String baseUrl = props.getString(PROVIDER_EMBEDDING_BASE_URL,
                props.getString(LEGACY_EMBEDDING_BASE_URL, "http://localhost:11434"));
        int dimensions = props.getInt(PROVIDER_EMBEDDING_DIMENSIONS, 768);
        int batchSize = props.getInt(PROVIDER_EMBEDDING_BATCH_SIZE,
                props.getInt(LEGACY_EMBEDDING_BATCH_SIZE, 32));
        int maxRetries = props.getInt(PROVIDER_EMBEDDING_MAX_RETRIES,
                props.getInt(LEGACY_EMBEDDING_MAX_RETRIES, 3));
        int maxConcurrent = props.getInt(PROVIDER_EMBEDDING_MAX_CONCURRENT,
                props.getInt(LEGACY_EMBEDDING_MAX_CONCURRENT, 0));
        Duration timeout = props.getDuration(PROVIDER_EMBEDDING_TIMEOUT,
                props.getDuration(LEGACY_EMBEDDING_TIMEOUT, Duration.ofSeconds(30)));
        boolean cacheEnabled = props.getBoolean(PROVIDER_EMBEDDING_CACHE_ENABLED,
                props.getBoolean(LEGACY_EMBEDDING_CACHE_ENABLED, true));
        int cacheMaxSize = props.getInt(PROVIDER_EMBEDDING_CACHE_MAX_SIZE,
                props.getInt(LEGACY_EMBEDDING_CACHE_MAX_SIZE, 1000));
        Duration cacheTtl = props.getDuration(PROVIDER_EMBEDDING_CACHE_TTL,
                props.getDuration(LEGACY_EMBEDDING_CACHE_TTL, Duration.ofMinutes(60)));
        Duration cacheStatsLogInterval = props.getDuration(PROVIDER_EMBEDDING_CACHE_STATS_LOG_INTERVAL,
                props.getDuration(LEGACY_EMBEDDING_CACHE_STATS_LOG_INTERVAL, Duration.ofMinutes(5)));

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

    // ─────────────── Reranker Defaults ───────────────

    /**
     * Default values for the LLM reranker.
     */
    public record RerankerDefaults(
            boolean enabled, String ollamaUrl, String model, int maxCandidates
    ) {}

    /**
     * Loads reranker defaults from properties.
     */
    public static RerankerDefaults rerankerDefaults(SpectorProperties props) {
        return new RerankerDefaults(
                props.getBoolean(RERANKER_ENABLED, false),
                props.getString(RERANKER_OLLAMA_URL, "http://localhost:11434"),
                props.getString(RERANKER_MODEL, "llama3.2"),
                props.getInt(RERANKER_MAX_CANDIDATES, 20)
        );
    }

    // ─────────────── Cluster Defaults ───────────────

    /**
     * Default values for clustering.
     */
    public record ClusterDefaults(int shardCount, int replicaCount, String shardStrategy) {}

    /**
     * Loads cluster defaults from properties.
     */
    public static ClusterDefaults clusterDefaults(SpectorProperties props) {
        return new ClusterDefaults(
                props.getInt(CLUSTER_SHARD_COUNT, 1),
                props.getInt(CLUSTER_REPLICA_COUNT, 0),
                props.getString(CLUSTER_SHARD_STRATEGY, "HASH")
        );
    }

    // ─────────────── Memory Defaults ───────────────

    /**
     * Default values for the cognitive memory module.
     *
     * @param enabled          whether cognitive memory is enabled
     * @param persistenceMode  DISK or IN_MEMORY
     * @param persistencePath  directory for memory tier persistence files
     * @param dimensions       vector dimensionality for memory embeddings
     * @param capacity         maximum memory entries
     * @param decayEnabled     whether temporal decay is enabled
     * @param consolidationInterval  time between memory consolidation runs
     * @param defaultIngestionTier   default memory tier for ingestion (e.g., "SEMANTIC")
     * @param hnswPrefilter          HNSW pre-filter mode ("auto", "enabled", "disabled")
     * @param tagExtractor           tag extraction mode ("content", "llm", "none")
     * @param tagExtractorModel      LLM model for tag extraction (e.g., "qwen3:1.7b"); only used when tagExtractor=llm
     */
    public record MemoryDefaults(
            boolean enabled,
            String persistenceMode, Path persistencePath,
            int dimensions, int capacity, int nodesPerPartition,
            boolean decayEnabled, Duration consolidationInterval,
            String defaultIngestionTier, String hnswPrefilter,
            String tagExtractor, String tagExtractorModel,
            String textSearchMode,
            boolean spladeEnabled, boolean colbertEnabled, boolean bm25Enabled,
            LlmDefaults llm
    ) {}

    /**
     * LLM generation parameters for tag extraction, entity extraction, etc.
     *
     * @param temperature  sampling temperature (0.0 = deterministic, 1.0 = creative)
     * @param maxTokens    maximum tokens to generate per call
     * @param topP         nucleus sampling threshold
     * @param entityModel  optional separate model for entity extraction (falls back to tag-extractor-model)
     */
    public record LlmDefaults(
            float temperature, int maxTokens, float topP,
            String entityModel
    ) {}

    /**
     * Loads memory defaults from properties.
     */
    public static MemoryDefaults memoryDefaults(SpectorProperties props) {
        var llm = new LlmDefaults(
                props.getFloat(MEMORY_LLM_TEMPERATURE, 0.3f),
                props.getInt(MEMORY_LLM_MAX_TOKENS, 1024),
                props.getFloat(MEMORY_LLM_TOP_P, 0.95f),
                props.getString(MEMORY_LLM_ENTITY_MODEL, "")
        );
        return new MemoryDefaults(
                props.getBoolean(MEMORY_ENABLED, false),
                props.getString(MEMORY_PERSISTENCE_MODE, "DISK"),
                props.getPath(MEMORY_PERSISTENCE_PATH, Path.of(".spector", "memory")),
                props.getInt(MEMORY_DIMENSIONS, 384),
                props.getInt(MEMORY_CAPACITY, 100_000),
                props.getInt(MEMORY_NODES_PER_PARTITION, 10_000),
                props.getBoolean(MEMORY_DECAY_ENABLED, true),
                props.getDuration(MEMORY_CONSOLIDATION_INTERVAL, Duration.ofSeconds(60)),
                props.getString(MEMORY_DEFAULT_INGESTION_TIER, "SEMANTIC"),
                props.getString(MEMORY_HNSW_PREFILTER, "auto"),
                props.getString(MEMORY_TAG_EXTRACTOR, "content"),
                props.getString(MEMORY_TAG_EXTRACTOR_MODEL, ""),
                props.getString(MEMORY_TEXT_SEARCH_MODE, "HYBRID"),
                props.getBoolean(MEMORY_SPLADE_ENABLED, true),
                props.getBoolean(MEMORY_COLBERT_ENABLED, true),
                props.getBoolean(MEMORY_BM25_ENABLED, true),
                llm
        );
    }

    // ─────────────── Global Mode ───────────────

    /**
     * Resolves the global operating mode: {@code SEARCH} or {@code MEMORY}.
     *
     * <p>Reads {@code spector.mode} from properties (default: {@code "search"}).
     * In MEMORY mode, the runtime auto-enables cognitive memory and routes
     * ingestion/search through the unified memory pipeline.</p>
     *
     * @param props hierarchical configuration
     * @return the resolved mode
     */
    public static SpectorMode mode(SpectorProperties props) {
        String raw = props.getString(MODE, "search");
        return SpectorMode.valueOf(raw.toUpperCase());
    }

    // ─────────────── Ingestion Properties ───────────────

    /**
     * Loads ingestion properties from properties.
     */
    public static IngestionProperties ingestionDefaults(SpectorProperties props) {
        IngestionProperties properties = new IngestionProperties();
        properties.setRootDirectory(props.getPath(INGESTION_ROOT_DIRECTORY, Path.of(".")));
        properties.setFilePattern(props.getString(INGESTION_FILE_PATTERN, "**/*.md"));
        properties.setSkipDirs(props.getString(INGESTION_SKIP_DIRS, ".git,.idea,.mvn,target,node_modules,.github"));
        properties.setChunkSize(props.getInt(INGESTION_CHUNK_SIZE, 2500));
        properties.setChunkOverlap(props.getInt(INGESTION_CHUNK_OVERLAP, 200));
        properties.setParallelism(props.getInt(INGESTION_PARALLELISM, 4));
        properties.setMaxRetries(props.getInt(INGESTION_MAX_RETRIES, 3));
        properties.setRetryDelayMs(props.getInt(INGESTION_RETRY_DELAY_MS, 2000));
        return properties;
    }

    // ─────────────── Provider Properties ───────────────

    /**
     * Loads provider properties from properties.
     *
     * <p>Checks new-style config keys first, then falls back to legacy
     * embedding section keys for backward compatibility.</p>
     *
     * @param props hierarchical configuration
     * @return resolved provider properties
     */
    public static ProviderProperties providerDefaults(SpectorProperties props) {
        ProviderProperties providerProperties = new ProviderProperties();

        EmbeddingProperties emb = providerProperties.getEmbedding();
        String embType = props.getString(PROVIDER_EMBEDDING_TYPE,
                props.getString(LEGACY_EMBEDDING_BASE_URL, "").contains("localhost") ? "ollama" : "ollama");
        String embModel = props.getString(PROVIDER_EMBEDDING_MODEL,
                props.getString(LEGACY_EMBEDDING_MODEL, "nomic-embed-text"));
        String embApiKey = props.getString(PROVIDER_EMBEDDING_API_KEY, "");
        String embBaseUrl = props.getString(PROVIDER_EMBEDDING_BASE_URL,
                props.getString(LEGACY_EMBEDDING_BASE_URL, "http://localhost:11434"));
        int embDims = props.getInt(PROVIDER_EMBEDDING_DIMENSIONS, 768);

        emb.setType(embType);
        emb.setModel(embModel);
        emb.setApiKey(embApiKey);
        emb.setBaseUrl(embBaseUrl);
        if (embDims > 0) emb.setDimensions(embDims);

        GenerationProperties gen = providerProperties.getGeneration();
        String genType = props.getString(PROVIDER_GENERATION_TYPE, embType);
        String genModel = props.getString(PROVIDER_GENERATION_MODEL, "");
        String genApiKey = props.getString(PROVIDER_GENERATION_API_KEY, embApiKey);
        String genBaseUrl = props.getString(PROVIDER_GENERATION_BASE_URL, embBaseUrl);

        gen.setType(genType);
        gen.setModel(genModel);
        gen.setApiKey(genApiKey);
        gen.setBaseUrl(genBaseUrl);

        return providerProperties;
    }
}

