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
 *   // Get individual config sections as Maps
 *   int dims = SpectorConfigFactory.engineDimensions(props);
 *   String model = SpectorConfigFactory.embeddingModel(props);
 *
 *   // Or use the full config accessor
 *   EngineDefaults engine = SpectorConfigFactory.engineDefaults(props);
 * }</pre>
 *
 * <p>Module-level config records (SpectorConfig, EmbeddingConfig, etc.)
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
                props.getInt("spector.hnsw.m", 16),
                props.getInt("spector.hnsw.ef-construction", 200),
                props.getInt("spector.hnsw.ef-search", 50)
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
                props.getInt("spector.ivf.nlist", 0),
                props.getInt("spector.ivf.nprobe", 0),
                props.getInt("spector.ivf.pq-subspaces", 0)
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
                props.getInt("spector.spectrum.n-centroids", 256),
                props.getInt("spector.spectrum.n-probe", 16),
                props.getInt("spector.spectrum.shard-threshold", 20_000),
                props.getInt("spector.spectrum.oversampling-factor", 3),
                props.getInt("spector.spectrum.kmeans-iterations", 25)
        );
    }

    // ─────────────── Embedding Properties ───────────────

    /**
     * Loads embedding provider properties from properties.
     */
    public static EmbeddingProperties embeddingDefaults(SpectorProperties props) {
        EmbeddingProperties properties = new EmbeddingProperties();

        String type = props.getString("spector.provider.embedding.type",
                props.getString("spector.embedding.base-url", "").contains("localhost") ? "ollama" : "ollama");
        String model = props.getString("spector.provider.embedding.model",
                props.getString("spector.embedding.model", "nomic-embed-text"));
        String apiKey = props.getString("spector.provider.embedding.api-key", "");
        String baseUrl = props.getString("spector.provider.embedding.base-url",
                props.getString("spector.embedding.base-url", "http://localhost:11434"));
        int dimensions = props.getInt("spector.provider.embedding.dimensions", 768);
        int batchSize = props.getInt("spector.provider.embedding.batch-size",
                props.getInt("spector.embedding.batch-size", 32));
        int maxRetries = props.getInt("spector.provider.embedding.max-retries",
                props.getInt("spector.embedding.max-retries", 3));
        int maxConcurrent = props.getInt("spector.provider.embedding.max-concurrent",
                props.getInt("spector.embedding.max-concurrent", 0));
        Duration timeout = props.getDuration("spector.provider.embedding.timeout",
                props.getDuration("spector.embedding.timeout", Duration.ofSeconds(30)));
        boolean cacheEnabled = props.getBoolean("spector.provider.embedding.cache.enabled",
                props.getBoolean("spector.embedding.cache.enabled", true));
        int cacheMaxSize = props.getInt("spector.provider.embedding.cache.max-size",
                props.getInt("spector.embedding.cache.max-size", 1000));
        Duration cacheTtl = props.getDuration("spector.provider.embedding.cache.ttl",
                props.getDuration("spector.embedding.cache.ttl", Duration.ofMinutes(60)));
        Duration cacheStatsLogInterval = props.getDuration("spector.provider.embedding.cache.stats-log-interval",
                props.getDuration("spector.embedding.cache.stats-log-interval", Duration.ofMinutes(5)));

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
                props.getBoolean("spector.reranker.enabled", false),
                props.getString("spector.reranker.ollama-url", "http://localhost:11434"),
                props.getString("spector.reranker.model", "llama3.2"),
                props.getInt("spector.reranker.max-candidates", 20)
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
                props.getInt("spector.cluster.shard-count", 1),
                props.getInt("spector.cluster.replica-count", 0),
                props.getString("spector.cluster.shard-strategy", "HASH")
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
                props.getFloat("spector.memory.llm.temperature", 0.3f),
                props.getInt("spector.memory.llm.max-tokens", 1024),
                props.getFloat("spector.memory.llm.top-p", 0.95f),
                props.getString("spector.memory.llm.entity-model", "")
        );
        return new MemoryDefaults(
                props.getBoolean("spector.memory.enabled", false),
                props.getString("spector.memory.persistence-mode", "DISK"),
                props.getPath("spector.memory.persistence-path", Path.of(".spector", "memory")),
                props.getInt("spector.memory.dimensions", 384),
                props.getInt("spector.memory.capacity", 100_000),
                props.getInt("spector.memory.nodes-per-partition", 10_000),
                props.getBoolean("spector.memory.decay-enabled", true),
                props.getDuration("spector.memory.consolidation-interval", Duration.ofSeconds(60)),
                props.getString("spector.memory.default-ingestion-tier", "SEMANTIC"),
                props.getString("spector.memory.hnsw-prefilter", "auto"),
                props.getString("spector.memory.tag-extractor", "content"),
                props.getString("spector.memory.tag-extractor-model", ""),
                props.getString("spector.memory.text-search-mode", "HYBRID"),
                props.getBoolean("spector.memory.splade-enabled", true),
                props.getBoolean("spector.memory.colbert-enabled", true),
                props.getBoolean("spector.memory.bm25-enabled", true),
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
        String raw = props.getString("spector.mode", "search");
        return SpectorMode.valueOf(raw.toUpperCase());
    }

    // ─────────────── Ingestion Properties ───────────────

    /**
     * Loads ingestion properties from properties.
     */
    public static IngestionProperties ingestionDefaults(SpectorProperties props) {
        IngestionProperties properties = new IngestionProperties();
        properties.setRootDirectory(props.getPath("spector.ingestion.root-directory", Path.of(".")));
        properties.setFilePattern(props.getString("spector.ingestion.file-pattern", "**/*.md"));
        properties.setSkipDirs(props.getString("spector.ingestion.skip-dirs", ".git,.idea,.mvn,target,node_modules,.github"));
        properties.setChunkSize(props.getInt("spector.ingestion.chunk-size", 2500));
        properties.setChunkOverlap(props.getInt("spector.ingestion.chunk-overlap", 200));
        properties.setParallelism(props.getInt("spector.ingestion.parallelism", 4));
        properties.setMaxRetries(props.getInt("spector.ingestion.max-retries", 3));
        properties.setRetryDelayMs(props.getInt("spector.ingestion.retry-delay-ms", 2000));
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
        String embType = props.getString("spector.provider.embedding.type",
                props.getString("spector.embedding.base-url", "").contains("localhost") ? "ollama" : "ollama");
        String embModel = props.getString("spector.provider.embedding.model",
                props.getString("spector.embedding.model", "nomic-embed-text"));
        String embApiKey = props.getString("spector.provider.embedding.api-key", "");
        String embBaseUrl = props.getString("spector.provider.embedding.base-url",
                props.getString("spector.embedding.base-url", "http://localhost:11434"));
        int embDims = props.getInt("spector.provider.embedding.dimensions", 768);

        emb.setType(embType);
        emb.setModel(embModel);
        emb.setApiKey(embApiKey);
        emb.setBaseUrl(embBaseUrl);
        if (embDims > 0) emb.setDimensions(embDims);

        GenerationProperties gen = providerProperties.getGeneration();
        String genType = props.getString("spector.provider.generation.type", embType);
        String genModel = props.getString("spector.provider.generation.model", "");
        String genApiKey = props.getString("spector.provider.generation.api-key", embApiKey);
        String genBaseUrl = props.getString("spector.provider.generation.base-url", embBaseUrl);

        gen.setType(genType);
        gen.setModel(genModel);
        gen.setApiKey(genApiKey);
        gen.setBaseUrl(genBaseUrl);

        return providerProperties;
    }
}

