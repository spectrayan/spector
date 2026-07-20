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

    // ─────────────── Engine Defaults ───────────────

    /**
     * Default values for the Spector engine, loaded from properties.
     *
     * @param dimensions      vector dimensionality
     * @param capacity        maximum document count
     * @param similarity      similarity function name (COSINE, EUCLIDEAN, DOT_PRODUCT)
     * @param indexType        vector index type name (HNSW, IVF_PQ, SPECTRUM)
     * @param quantization    quantization type name (NONE, BINARY, INT8, SVASQ4, SVASQ8)
     * @param persistenceMode persistence mode name (IN_MEMORY, DISK)
     * @param dataDirectory   data directory path
     * @param gpuEnabled      whether GPU acceleration is enabled
     * @param oversamplingFactor  rescore oversampling factor
     */
    public record EngineDefaults(
            int dimensions,
            int capacity,
            String similarity,
            String indexType,
            String quantization,
            String persistenceMode,
            Path dataDirectory,
            boolean gpuEnabled,
            int oversamplingFactor
    ) {}

    /**
     * Loads engine defaults from properties.
     */
    public static EngineDefaults engineDefaults(SpectorProperties props) {
        return new EngineDefaults(
                props.getInt("spector.engine.dimensions", 384),
                props.getInt("spector.engine.capacity", 100_000),
                props.getString("spector.engine.similarity", "COSINE"),
                props.getString("spector.engine.index-type", "HNSW"),
                props.getString("spector.engine.quantization", "NONE"),
                props.getString("spector.engine.persistence-mode", "IN_MEMORY"),
                props.getPath("spector.engine.data-directory", Path.of(".spector", "index")),
                props.getBoolean("spector.engine.gpu-enabled", false),
                props.getInt("spector.engine.oversampling-factor", 0)
        );
    }

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

    // ─────────────── Embedding Defaults ───────────────

    /**
     * Default values for the embedding provider.
     *
     * @param model                 embedding model name
     * @param baseUrl               API base URL
     * @param timeout               HTTP request timeout
     * @param batchSize             maximum texts per batch request
     * @param maxRetries            maximum retry attempts
     * @param maxConcurrent         maximum concurrent embedding HTTP calls (0 = unlimited)
     * @param cacheEnabled          whether the LRU embedding cache is enabled
     * @param cacheMaxSize          maximum number of cached embeddings
     * @param cacheTtl              time-to-live for cached embeddings (0 = no expiry)
     * @param cacheStatsLogInterval how often cache statistics are logged (0 = never)
     */
    public record EmbeddingDefaults(
            String model, String baseUrl, Duration timeout,
            int batchSize, int maxRetries, int maxConcurrent,
            boolean cacheEnabled, int cacheMaxSize, Duration cacheTtl, Duration cacheStatsLogInterval
    ) {}

    /**
     * Loads embedding defaults from properties.
     */
    public static EmbeddingDefaults embeddingDefaults(SpectorProperties props) {
        return new EmbeddingDefaults(
                props.getString("spector.embedding.model", "nomic-embed-text"),
                props.getString("spector.embedding.base-url", "http://localhost:11434"),
                props.getDuration("spector.embedding.timeout", Duration.ofSeconds(30)),
                props.getInt("spector.embedding.batch-size", 32),
                props.getInt("spector.embedding.max-retries", 3),
                props.getInt("spector.embedding.max-concurrent", 0),
                props.getBoolean("spector.embedding.cache.enabled", true),
                props.getInt("spector.embedding.cache.max-size", 1000),
                props.getDuration("spector.embedding.cache.ttl", Duration.ofMinutes(60)),
                props.getDuration("spector.embedding.cache.stats-log-interval", Duration.ofMinutes(5))
        );
    }

    // ─────────────── Chunking Defaults ───────────────

    /**
     * Default values for text chunking.
     *
     * @param maxTokens      maximum token count per chunk
     * @param overlapTokens  overlapping tokens between consecutive chunks
     */
    public record ChunkingDefaults(int maxTokens, int overlapTokens) {}

    /**
     * Loads chunking defaults from properties.
     */
    public static ChunkingDefaults chunkingDefaults(SpectorProperties props) {
        return new ChunkingDefaults(
                props.getInt("spector.chunking.max-tokens", 512),
                props.getInt("spector.chunking.overlap-tokens", 50)
        );
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

    // ─────────────── RAG Defaults ───────────────

    /**
     * Default values for RAG retrieval.
     */
    public record RagDefaults(int topK, float similarityThreshold, int tokenLimit) {}

    /**
     * Loads RAG defaults from properties.
     */
    public static RagDefaults ragDefaults(SpectorProperties props) {
        return new RagDefaults(
                props.getInt("spector.rag.top-k", 5),
                props.getFloat("spector.rag.similarity-threshold", 0.7f),
                props.getInt("spector.rag.token-limit", 4096)
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

    // ─────────────── Ingestion Defaults ───────────────

    /**
     * Default values for file ingestion.
     *
     * @param rootDirectory root directory for file discovery (default: .)
     * @param filePattern   glob pattern for file discovery (e.g., "**\/*.md")
     * @param skipDirs      comma-separated directories to skip
     * @param chunkSize     target chunk size in characters
     * @param chunkOverlap  overlap between consecutive chunks
     */
    public record IngestionDefaults(
            Path rootDirectory, String filePattern, String skipDirs,
            int chunkSize, int chunkOverlap,
            int parallelism, int maxRetries, int retryDelayMs
    ) {}

    /**
     * Loads ingestion defaults from properties.
     */
    public static IngestionDefaults ingestionDefaults(SpectorProperties props) {
        return new IngestionDefaults(
                props.getPath("spector.ingestion.root-directory", Path.of(".")),
                props.getString("spector.ingestion.file-pattern", "**/*.md"),
                props.getString("spector.ingestion.skip-dirs", ".git,.idea,.mvn,target,node_modules,.github"),
                props.getInt("spector.ingestion.chunk-size", 2500),
                props.getInt("spector.ingestion.chunk-overlap", 200),
                props.getInt("spector.ingestion.parallelism", 4),
                props.getInt("spector.ingestion.max-retries", 3),
                props.getInt("spector.ingestion.retry-delay-ms", 2000)
        );
    }

    // ─────────────── Provider Defaults ───────────────

    /**
     * Default values for LLM/embedding provider configuration.
     *
     * <p>Parsed from {@code spector.provider.embedding.*} and
     * {@code spector.provider.generation.*} config sections. Falls back
     * to the legacy {@code spector.embedding.*} section for backward
     * compatibility.</p>
     *
     * <p><strong>Note:</strong> This record uses simple types only — no
     * dependency on {@code spector-provider-api}. The runtime layer
     * converts these values into {@code ProviderConfig} instances.</p>
     *
     * @param embeddingType       provider type (e.g., "ollama", "openai")
     * @param embeddingModel      model identifier
     * @param embeddingApiKey     API key (empty for local providers)
     * @param embeddingBaseUrl    API base URL
     * @param embeddingDimensions vector dimensions (0 = model default)
     * @param generationType      generation provider type
     * @param generationModel     generation model identifier
     * @param generationApiKey    generation API key
     * @param generationBaseUrl   generation API base URL
     */
    public record ProviderDefaults(
            String embeddingType, String embeddingModel, String embeddingApiKey,
            String embeddingBaseUrl, int embeddingDimensions,
            String generationType, String generationModel, String generationApiKey,
            String generationBaseUrl
    ) {}

    /**
     * Loads provider defaults from properties.
     *
     * <p>Checks new-style config keys first, then falls back to legacy
     * embedding section keys for backward compatibility.</p>
     *
     * @param props hierarchical configuration
     * @return resolved provider defaults
     */
    public static ProviderDefaults providerDefaults(SpectorProperties props) {
        // Check new-style config first, fall back to legacy embedding section
        String embType = props.getString("spector.provider.embedding.type",
                props.getString("spector.embedding.base-url", "").contains("localhost")
                        ? "ollama" : "ollama");
        String embModel = props.getString("spector.provider.embedding.model",
                props.getString("spector.embedding.model", "nomic-embed-text"));
        String embApiKey = props.getString("spector.provider.embedding.api-key", "");
        String embBaseUrl = props.getString("spector.provider.embedding.base-url",
                props.getString("spector.embedding.base-url", "http://localhost:11434"));
        int embDims = props.getInt("spector.provider.embedding.dimensions", 0);

        String genType = props.getString("spector.provider.generation.type", embType);
        String genModel = props.getString("spector.provider.generation.model", "");
        String genApiKey = props.getString("spector.provider.generation.api-key", embApiKey);
        String genBaseUrl = props.getString("spector.provider.generation.base-url", embBaseUrl);

        return new ProviderDefaults(
                embType, embModel, embApiKey, embBaseUrl, embDims,
                genType, genModel, genApiKey, genBaseUrl);
    }
}

