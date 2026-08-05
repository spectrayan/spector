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

/**
 * Central registry of configuration property keys across Spector namespaces.
 *
 * <p>Avoids string literal duplication and typos when reading properties in
 * factories, Spring auto-configuration, CLI, or runtime classes.</p>
 */
public final class SpectorPropertyKeys {

    private SpectorPropertyKeys() {}

    // Global
    public static final String MODE = "spector.mode";

    // Provider — Embedding
    public static final String PROVIDER_EMBEDDING_TYPE = "spector.provider.embedding.type";
    public static final String PROVIDER_EMBEDDING_MODEL = "spector.provider.embedding.model";
    public static final String PROVIDER_EMBEDDING_API_KEY = "spector.provider.embedding.api-key";
    public static final String PROVIDER_EMBEDDING_BASE_URL = "spector.provider.embedding.base-url";
    public static final String PROVIDER_EMBEDDING_DIMENSIONS = "spector.provider.embedding.dimensions";
    public static final String PROVIDER_EMBEDDING_BATCH_SIZE = "spector.provider.embedding.batch-size";
    public static final String PROVIDER_EMBEDDING_MAX_RETRIES = "spector.provider.embedding.max-retries";
    public static final String PROVIDER_EMBEDDING_MAX_CONCURRENT = "spector.provider.embedding.max-concurrent";
    public static final String PROVIDER_EMBEDDING_TIMEOUT = "spector.provider.embedding.timeout";
    public static final String PROVIDER_EMBEDDING_CACHE_ENABLED = "spector.provider.embedding.cache.enabled";
    public static final String PROVIDER_EMBEDDING_CACHE_MAX_SIZE = "spector.provider.embedding.cache.max-size";
    public static final String PROVIDER_EMBEDDING_CACHE_TTL = "spector.provider.embedding.cache.ttl";
    public static final String PROVIDER_EMBEDDING_CACHE_STATS_LOG_INTERVAL = "spector.provider.embedding.cache.stats-log-interval";

    // Legacy Embedding Keys (backward compatibility)
    public static final String LEGACY_EMBEDDING_MODEL = "spector.embedding.model";
    public static final String LEGACY_EMBEDDING_BASE_URL = "spector.embedding.base-url";
    public static final String LEGACY_EMBEDDING_TIMEOUT = "spector.embedding.timeout";
    public static final String LEGACY_EMBEDDING_BATCH_SIZE = "spector.embedding.batch-size";
    public static final String LEGACY_EMBEDDING_MAX_RETRIES = "spector.embedding.max-retries";
    public static final String LEGACY_EMBEDDING_MAX_CONCURRENT = "spector.embedding.max-concurrent";
    public static final String LEGACY_EMBEDDING_CACHE_ENABLED = "spector.embedding.cache.enabled";
    public static final String LEGACY_EMBEDDING_CACHE_MAX_SIZE = "spector.embedding.cache.max-size";
    public static final String LEGACY_EMBEDDING_CACHE_TTL = "spector.embedding.cache.ttl";
    public static final String LEGACY_EMBEDDING_CACHE_STATS_LOG_INTERVAL = "spector.embedding.cache.stats-log-interval";

    // Provider — Generation
    public static final String PROVIDER_GENERATION_TYPE = "spector.provider.generation.type";
    public static final String PROVIDER_GENERATION_MODEL = "spector.provider.generation.model";
    public static final String PROVIDER_GENERATION_API_KEY = "spector.provider.generation.api-key";
    public static final String PROVIDER_GENERATION_BASE_URL = "spector.provider.generation.base-url";

    // Memory
    public static final String MEMORY_ENABLED = "spector.memory.enabled";
    public static final String MEMORY_PERSISTENCE_MODE = "spector.memory.persistence-mode";
    public static final String MEMORY_PERSISTENCE_PATH = "spector.memory.persistence-path";
    public static final String MEMORY_DIMENSIONS = "spector.memory.dimensions";
    public static final String MEMORY_CAPACITY = "spector.memory.capacity";
    public static final String MEMORY_NODES_PER_PARTITION = "spector.memory.nodes-per-partition";
    public static final String MEMORY_DECAY_ENABLED = "spector.memory.decay-enabled";
    public static final String MEMORY_CONSOLIDATION_INTERVAL = "spector.memory.consolidation-interval";
    public static final String MEMORY_DEFAULT_INGESTION_TIER = "spector.memory.default-ingestion-tier";
    public static final String MEMORY_HNSW_PREFILTER = "spector.memory.hnsw-prefilter";
    public static final String MEMORY_TAG_EXTRACTOR = "spector.memory.tag-extractor";
    public static final String MEMORY_TAG_EXTRACTOR_MODEL = "spector.memory.tag-extractor-model";
    public static final String MEMORY_TEXT_SEARCH_MODE = "spector.memory.text-search-mode";
    public static final String MEMORY_SPLADE_ENABLED = "spector.memory.splade-enabled";
    public static final String MEMORY_COLBERT_ENABLED = "spector.memory.colbert-enabled";
    public static final String MEMORY_BM25_ENABLED = "spector.memory.bm25-enabled";
    public static final String MEMORY_LLM_TEMPERATURE = "spector.memory.llm.temperature";
    public static final String MEMORY_LLM_MAX_TOKENS = "spector.memory.llm.max-tokens";
    public static final String MEMORY_LLM_TOP_P = "spector.memory.llm.top-p";
    public static final String MEMORY_LLM_ENTITY_MODEL = "spector.memory.llm.entity-model";

    // Ingestion
    public static final String INGESTION_ROOT_DIRECTORY = "spector.ingestion.root-directory";
    public static final String INGESTION_FILE_PATTERN = "spector.ingestion.file-pattern";
    public static final String INGESTION_SKIP_DIRS = "spector.ingestion.skip-dirs";
    public static final String INGESTION_CHUNK_SIZE = "spector.ingestion.chunk-size";
    public static final String INGESTION_CHUNK_OVERLAP = "spector.ingestion.chunk-overlap";
    public static final String INGESTION_PARALLELISM = "spector.ingestion.parallelism";
    public static final String INGESTION_MAX_RETRIES = "spector.ingestion.max-retries";
    public static final String INGESTION_RETRY_DELAY_MS = "spector.ingestion.retry-delay-ms";

    // Engine
    public static final String ENGINE_DIMENSIONS = "spector.engine.dimensions";
    public static final String ENGINE_CAPACITY = "spector.engine.capacity";
    public static final String ENGINE_SIMILARITY = "spector.engine.similarity";
    public static final String ENGINE_INDEX_TYPE = "spector.engine.index-type";
    public static final String ENGINE_QUANTIZATION = "spector.engine.quantization";
    public static final String ENGINE_PERSISTENCE_MODE = "spector.engine.persistence-mode";
    public static final String ENGINE_DATA_DIRECTORY = "spector.engine.data-directory";
    public static final String ENGINE_GPU_ENABLED = "spector.engine.gpu-enabled";
    public static final String ENGINE_OVERSAMPLING_FACTOR = "spector.engine.oversampling-factor";

    // HNSW
    public static final String HNSW_M = "spector.hnsw.m";
    public static final String HNSW_EF_CONSTRUCTION = "spector.hnsw.ef-construction";
    public static final String HNSW_EF_SEARCH = "spector.hnsw.ef-search";

    // IVF
    public static final String IVF_NLIST = "spector.ivf.nlist";
    public static final String IVF_NPROBE = "spector.ivf.nprobe";
    public static final String IVF_PQ_SUBSPACES = "spector.ivf.pq-subspaces";

    // Spectrum
    public static final String SPECTRUM_N_CENTROIDS = "spector.spectrum.n-centroids";
    public static final String SPECTRUM_N_PROBE = "spector.spectrum.n-probe";
    public static final String SPECTRUM_SHARD_THRESHOLD = "spector.spectrum.shard-threshold";
    public static final String SPECTRUM_OVERSAMPLING_FACTOR = "spector.spectrum.oversampling-factor";
    public static final String SPECTRUM_KMEANS_ITERATIONS = "spector.spectrum.kmeans-iterations";

    // Reranker
    public static final String RERANKER_ENABLED = "spector.reranker.enabled";
    public static final String RERANKER_OLLAMA_URL = "spector.reranker.ollama-url";
    public static final String RERANKER_MODEL = "spector.reranker.model";
    public static final String RERANKER_MAX_CANDIDATES = "spector.reranker.max-candidates";

    // Cluster
    public static final String CLUSTER_SHARD_COUNT = "spector.cluster.shard-count";
    public static final String CLUSTER_REPLICA_COUNT = "spector.cluster.replica-count";
    public static final String CLUSTER_SHARD_STRATEGY = "spector.cluster.shard-strategy";

    // Auth
    public static final String AUTH_ENABLED = "spector.auth.enabled";
    public static final String AUTH_API_KEY = "spector.auth.api-key";

    // Server
    public static final String SERVER_PORT = "spector.server.port";
    public static final String SERVER_DATA_DIR = "spector.server.data-dir";
}
