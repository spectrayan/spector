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

import com.spectrayan.spector.config.model.*;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Central registry of configuration property keys and default values across Spector namespaces.
 *
 * <p>Avoids string literal and default value duplication and typos when reading properties in
 * factories, Spring auto-configuration, CLI, or runtime classes.</p>
 */
public final class SpectorPropertyConstants {

    private SpectorPropertyConstants() {}

    // Global
    public static final String MODE = "spector.mode";
    public static final String DEFAULT_MODE = "MEMORY";

    // Provider — Embedding
    public static final String PROVIDER_EMBEDDING_TYPE = "spector.provider.embedding.type";
    public static final String DEFAULT_PROVIDER_EMBEDDING_TYPE = "ollama";

    public static final String PROVIDER_EMBEDDING_MODEL = "spector.provider.embedding.model";
    public static final String DEFAULT_PROVIDER_EMBEDDING_MODEL = "nomic-embed-text";

    public static final String PROVIDER_EMBEDDING_API_KEY = "spector.provider.embedding.api-key";
    public static final String DEFAULT_PROVIDER_EMBEDDING_API_KEY = "";

    public static final String PROVIDER_EMBEDDING_BASE_URL = "spector.provider.embedding.base-url";
    public static final String DEFAULT_PROVIDER_EMBEDDING_BASE_URL = "http://localhost:11434";

    public static final String PROVIDER_EMBEDDING_DIMENSIONS = "spector.provider.embedding.dimensions";
    public static final int DEFAULT_PROVIDER_EMBEDDING_DIMENSIONS = 768;

    public static final String PROVIDER_EMBEDDING_BATCH_SIZE = "spector.provider.embedding.batch-size";
    public static final int DEFAULT_PROVIDER_EMBEDDING_BATCH_SIZE = 32;

    public static final String PROVIDER_EMBEDDING_MAX_RETRIES = "spector.provider.embedding.max-retries";
    public static final int DEFAULT_PROVIDER_EMBEDDING_MAX_RETRIES = 3;

    public static final String PROVIDER_EMBEDDING_MAX_CONCURRENT = "spector.provider.embedding.max-concurrent";
    public static final int DEFAULT_PROVIDER_EMBEDDING_MAX_CONCURRENT = 0;

    public static final String PROVIDER_EMBEDDING_TIMEOUT = "spector.provider.embedding.timeout";
    public static final Duration DEFAULT_PROVIDER_EMBEDDING_TIMEOUT = Duration.ofSeconds(30);

    public static final String PROVIDER_EMBEDDING_CACHE_ENABLED = "spector.provider.embedding.cache.enabled";
    public static final boolean DEFAULT_PROVIDER_EMBEDDING_CACHE_ENABLED = true;

    public static final String PROVIDER_EMBEDDING_CACHE_MAX_SIZE = "spector.provider.embedding.cache.max-size";
    public static final int DEFAULT_PROVIDER_EMBEDDING_CACHE_MAX_SIZE = 1000;

    public static final String PROVIDER_EMBEDDING_CACHE_TTL = "spector.provider.embedding.cache.ttl";
    public static final Duration DEFAULT_PROVIDER_EMBEDDING_CACHE_TTL = Duration.ofMinutes(60);

    public static final String PROVIDER_EMBEDDING_CACHE_STATS_LOG_INTERVAL = "spector.provider.embedding.cache.stats-log-interval";
    public static final Duration DEFAULT_PROVIDER_EMBEDDING_CACHE_STATS_LOG_INTERVAL = Duration.ofMinutes(5);

    // Provider — Generation
    public static final String PROVIDER_GENERATION_TYPE = "spector.provider.generation.type";
    public static final String DEFAULT_PROVIDER_GENERATION_TYPE = "ollama";

    public static final String PROVIDER_GENERATION_MODEL = "spector.provider.generation.model";
    public static final String DEFAULT_PROVIDER_GENERATION_MODEL = "llama3.2";

    public static final String PROVIDER_GENERATION_API_KEY = "spector.provider.generation.api-key";
    public static final String DEFAULT_PROVIDER_GENERATION_API_KEY = "";

    public static final String PROVIDER_GENERATION_BASE_URL = "spector.provider.generation.base-url";
    public static final String DEFAULT_PROVIDER_GENERATION_BASE_URL = "http://localhost:11434";

    // Memory
    public static final String MEMORY_ENABLED = "spector.memory.enabled";
    public static final boolean DEFAULT_MEMORY_ENABLED = false;

    public static final String MEMORY_PERSISTENCE_MODE = "spector.memory.persistence-mode";
    public static final PersistenceMode DEFAULT_MEMORY_PERSISTENCE_MODE = PersistenceMode.DISK;

    public static final String MEMORY_PERSISTENCE_PATH = "spector.memory.persistence-path";
    public static final Path DEFAULT_MEMORY_PERSISTENCE_PATH = Path.of(".spector", "memory");

    public static final String MEMORY_DIMENSIONS = "spector.memory.dimensions";
    public static final int DEFAULT_MEMORY_DIMENSIONS = 768;

    public static final String MEMORY_CAPACITY = "spector.memory.capacity";
    public static final int DEFAULT_MEMORY_CAPACITY = 100_000;

    public static final String MEMORY_NODES_PER_PARTITION = "spector.memory.nodes-per-partition";
    public static final int DEFAULT_MEMORY_NODES_PER_PARTITION = 10_000;

    public static final String MEMORY_DECAY_ENABLED = "spector.memory.decay-enabled";
    public static final boolean DEFAULT_MEMORY_DECAY_ENABLED = true;

    public static final String MEMORY_CONSOLIDATION_INTERVAL = "spector.memory.consolidation-interval";
    public static final Duration DEFAULT_MEMORY_CONSOLIDATION_INTERVAL = Duration.ofSeconds(60);

    public static final String MEMORY_DEFAULT_INGESTION_TIER = "spector.memory.default-ingestion-tier";
    public static final IngestionTierMode DEFAULT_MEMORY_DEFAULT_INGESTION_TIER = IngestionTierMode.SEMANTIC;

    public static final String MEMORY_HNSW_PREFILTER = "spector.memory.hnsw-prefilter";
    public static final HnswPrefilterMode DEFAULT_MEMORY_HNSW_PREFILTER = HnswPrefilterMode.AUTO;

    public static final String MEMORY_TAG_EXTRACTOR = "spector.memory.tag-extractor";
    public static final TagExtractorMode DEFAULT_MEMORY_TAG_EXTRACTOR = TagExtractorMode.CONTENT;

    public static final String MEMORY_TAG_EXTRACTOR_MODEL = "spector.memory.tag-extractor-model";
    public static final String DEFAULT_MEMORY_TAG_EXTRACTOR_MODEL = "";

    public static final String MEMORY_TEXT_SEARCH_MODE = "spector.memory.text-search-mode";
    public static final TextSearchMode DEFAULT_MEMORY_TEXT_SEARCH_MODE = TextSearchMode.HYBRID;

    public static final String MEMORY_SPLADE_ENABLED = "spector.memory.splade-enabled";
    public static final boolean DEFAULT_MEMORY_SPLADE_ENABLED = true;

    public static final String MEMORY_COLBERT_ENABLED = "spector.memory.colbert-enabled";
    public static final boolean DEFAULT_MEMORY_COLBERT_ENABLED = true;

    public static final String MEMORY_BM25_ENABLED = "spector.memory.bm25-enabled";
    public static final boolean DEFAULT_MEMORY_BM25_ENABLED = true;

    public static final String MEMORY_LLM_TEMPERATURE = "spector.memory.llm.temperature";
    public static final float DEFAULT_MEMORY_LLM_TEMPERATURE = 0.3f;

    public static final String MEMORY_LLM_MAX_TOKENS = "spector.memory.llm.max-tokens";
    public static final int DEFAULT_MEMORY_LLM_MAX_TOKENS = 1024;

    public static final String MEMORY_LLM_TOP_P = "spector.memory.llm.top-p";
    public static final float DEFAULT_MEMORY_LLM_TOP_P = 0.95f;

    public static final String MEMORY_LLM_ENTITY_MODEL = "spector.memory.llm.entity-model";
    public static final String DEFAULT_MEMORY_LLM_ENTITY_MODEL = "";

    // Ingestion
    public static final String INGESTION_ROOT_DIRECTORY = "spector.ingestion.root-directory";
    public static final Path DEFAULT_INGESTION_ROOT_DIRECTORY = Path.of(".");

    public static final String INGESTION_FILE_PATTERN = "spector.ingestion.file-pattern";
    public static final String DEFAULT_INGESTION_FILE_PATTERN = "**/*.md";

    public static final String INGESTION_SKIP_DIRS = "spector.ingestion.skip-dirs";
    public static final String DEFAULT_INGESTION_SKIP_DIRS = ".git,.idea,.mvn,target,node_modules,.github";

    public static final String INGESTION_CHUNK_SIZE = "spector.ingestion.chunk-size";
    public static final int DEFAULT_INGESTION_CHUNK_SIZE = 2500;

    public static final String INGESTION_CHUNK_OVERLAP = "spector.ingestion.chunk-overlap";
    public static final int DEFAULT_INGESTION_CHUNK_OVERLAP = 200;

    public static final String INGESTION_PARALLELISM = "spector.ingestion.parallelism";
    public static final int DEFAULT_INGESTION_PARALLELISM = 4;

    public static final String INGESTION_MAX_RETRIES = "spector.ingestion.max-retries";
    public static final int DEFAULT_INGESTION_MAX_RETRIES = 3;

    public static final String INGESTION_RETRY_DELAY_MS = "spector.ingestion.retry-delay-ms";
    public static final int DEFAULT_INGESTION_RETRY_DELAY_MS = 2000;

    // HNSW
    public static final String HNSW_M = "spector.hnsw.m";
    public static final int DEFAULT_HNSW_M = 16;

    public static final String HNSW_EF_CONSTRUCTION = "spector.hnsw.ef-construction";
    public static final int DEFAULT_HNSW_EF_CONSTRUCTION = 200;

    public static final String HNSW_EF_SEARCH = "spector.hnsw.ef-search";
    public static final int DEFAULT_HNSW_EF_SEARCH = 50;

    // IVF
    public static final String IVF_NLIST = "spector.ivf.nlist";
    public static final int DEFAULT_IVF_NLIST = 0;

    public static final String IVF_NPROBE = "spector.ivf.nprobe";
    public static final int DEFAULT_IVF_NPROBE = 0;

    public static final String IVF_PQ_SUBSPACES = "spector.ivf.pq-subspaces";
    public static final int DEFAULT_IVF_PQ_SUBSPACES = 0;

    // Spectrum
    public static final String SPECTRUM_N_CENTROIDS = "spector.spectrum.n-centroids";
    public static final int DEFAULT_SPECTRUM_N_CENTROIDS = 256;

    public static final String SPECTRUM_N_PROBE = "spector.spectrum.n-probe";
    public static final int DEFAULT_SPECTRUM_N_PROBE = 16;

    public static final String SPECTRUM_SHARD_THRESHOLD = "spector.spectrum.shard-threshold";
    public static final int DEFAULT_SPECTRUM_SHARD_THRESHOLD = 20_000;

    public static final String SPECTRUM_OVERSAMPLING_FACTOR = "spector.spectrum.oversampling-factor";
    public static final int DEFAULT_SPECTRUM_OVERSAMPLING_FACTOR = 3;

    public static final String SPECTRUM_KMEANS_ITERATIONS = "spector.spectrum.kmeans-iterations";
    public static final int DEFAULT_SPECTRUM_KMEANS_ITERATIONS = 25;

    // Decay
    public static final String DECAY_ENABLED = "spector.memory.decay.enabled";
    public static final boolean DEFAULT_DECAY_ENABLED = true;
    public static final String DECAY_HALF_LIFE_DAYS = "spector.memory.decay.half-life-days";
    public static final double DEFAULT_DECAY_HALF_LIFE_DAYS = 30.0;
    public static final String DECAY_MIN_IMPORTANCE = "spector.memory.decay.min-importance";
    public static final double DEFAULT_DECAY_MIN_IMPORTANCE = 0.1;

    // Consolidation
    public static final String CONSOLIDATION_ENABLED = "spector.memory.consolidation.enabled";
    public static final boolean DEFAULT_CONSOLIDATION_ENABLED = true;
    public static final String CONSOLIDATION_INTERVAL = "spector.memory.consolidation.interval";
    public static final Duration DEFAULT_CONSOLIDATION_INTERVAL = Duration.ofHours(24);
    public static final String CONSOLIDATION_SIMILARITY_THRESHOLD = "spector.memory.consolidation.similarity-threshold";
    public static final double DEFAULT_CONSOLIDATION_SIMILARITY_THRESHOLD = 0.85;

    // Telemetry
    public static final String TELEMETRY_ENABLED = "spector.telemetry.enabled";
    public static final boolean DEFAULT_TELEMETRY_ENABLED = true;
    public static final String TELEMETRY_INTERVAL_MS = "spector.telemetry.interval-ms";
    public static final long DEFAULT_TELEMETRY_INTERVAL_MS = 2000L;
    public static final String TELEMETRY_PER_QUERY_ENABLED = "spector.telemetry.per-query-enabled";
    public static final boolean DEFAULT_TELEMETRY_PER_QUERY_ENABLED = true;
    public static final String TELEMETRY_QUERY_SAMPLE_RATE = "spector.telemetry.query-sample-rate";
    public static final double DEFAULT_TELEMETRY_QUERY_SAMPLE_RATE = 1.0;
    public static final String TELEMETRY_SIMD_ENABLED = "spector.telemetry.simd-enabled";
    public static final boolean DEFAULT_TELEMETRY_SIMD_ENABLED = true;
    public static final String TELEMETRY_GRAPH_ENABLED = "spector.telemetry.graph-enabled";
    public static final boolean DEFAULT_TELEMETRY_GRAPH_ENABLED = true;

    // Multimodal
    public static final String MULTIMODAL_ENABLED = "spector.multimodal.enabled";
    public static final boolean DEFAULT_MULTIMODAL_ENABLED = false;
    public static final String MULTIMODAL_VISION_MODEL = "spector.multimodal.vision.model";
    public static final String DEFAULT_MULTIMODAL_VISION_MODEL = "moondream";
    public static final String MULTIMODAL_VISION_BASE_URL = "spector.multimodal.vision.base-url";
    public static final String DEFAULT_MULTIMODAL_VISION_BASE_URL = "http://localhost:11434";
    public static final String MULTIMODAL_VISION_TIMEOUT = "spector.multimodal.vision.timeout";
    public static final int DEFAULT_MULTIMODAL_VISION_TIMEOUT = 120;
    public static final String MULTIMODAL_AUDIO_MODEL = "spector.multimodal.audio.model";
    public static final String DEFAULT_MULTIMODAL_AUDIO_MODEL = "gemma4";
    public static final String MULTIMODAL_AUDIO_TIMEOUT = "spector.multimodal.audio.timeout";
    public static final int DEFAULT_MULTIMODAL_AUDIO_TIMEOUT = 180;
    public static final String MULTIMODAL_ASSET_STORE_TYPE = "spector.multimodal.asset-store.type";
    public static final String DEFAULT_MULTIMODAL_ASSET_STORE_TYPE = "local";
    public static final String MULTIMODAL_ASSET_BASE_PATH = "spector.multimodal.asset-store.base-path";
    public static final Path DEFAULT_MULTIMODAL_ASSET_BASE_PATH = Path.of(".spector", "assets");

    // Auth
    public static final String AUTH_ENABLED = "spector.auth.enabled";
    public static final boolean DEFAULT_AUTH_ENABLED = false;

    public static final String AUTH_API_KEY = "spector.auth.api-key";
    public static final String DEFAULT_AUTH_API_KEY = "";

    public static final Duration DEFAULT_AUTH_JWT_TTL = Duration.ofHours(1);
    public static final Duration DEFAULT_AUTH_REFRESH_TTL = Duration.ofDays(30);
    public static final String DEFAULT_AUTH_OIDC_JWKS_URL = "";
    public static final String DEFAULT_AUTH_OIDC_ISSUER = "";
    public static final int DEFAULT_AUTH_PBKDF2_ITERATIONS = 310_000;
    public static final int DEFAULT_AUTH_LOCKOUT_MAX_ATTEMPTS = 5;
    public static final int DEFAULT_AUTH_LOCKOUT_MINUTES = 15;
    public static final java.util.List<String> DEFAULT_AUTH_PUBLIC_PATHS = java.util.List.of("/actuator/health", "/api/docs");

    // CORS
    public static final String CORS_ALLOWED_ORIGINS = "spector.cors.allowed-origins";
    public static final String DEFAULT_CORS_ALLOWED_ORIGINS = "http://localhost:4200";

    // Client
    public static final String CLIENT_MAX_CONNECTIONS = "spector.client.max-connections";
    public static final int DEFAULT_CLIENT_MAX_CONNECTIONS = 10;
    public static final String CLIENT_REQUEST_TIMEOUT = "spector.client.request-timeout";
    public static final Duration DEFAULT_CLIENT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final String CLIENT_CONNECT_TIMEOUT = "spector.client.connect-timeout";
    public static final Duration DEFAULT_CLIENT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    // Persistence Files
    public static final String PERSISTENCE_FILES_INDEX = "spector.persistence.files.index";
    public static final String DEFAULT_PERSISTENCE_FILES_INDEX = "index.spct";
    public static final String PERSISTENCE_FILES_VECTORS = "spector.persistence.files.vectors";
    public static final String DEFAULT_PERSISTENCE_FILES_VECTORS = "vectors.mmap";
    public static final String PERSISTENCE_FILES_DOCUMENTS = "spector.persistence.files.documents";
    public static final String DEFAULT_PERSISTENCE_FILES_DOCUMENTS = "documents.dat";
    public static final String PERSISTENCE_FILES_ID_MAPPINGS = "spector.persistence.files.id-mappings";
    public static final String DEFAULT_PERSISTENCE_FILES_ID_MAPPINGS = "id-mappings.dat";
    public static final String PERSISTENCE_FILES_SHARD_DIR = "spector.persistence.files.shard-dir-name";
    public static final String DEFAULT_PERSISTENCE_FILES_SHARD_DIR = "index_shards";

    // Server
    public static final String SERVER_PORT = "spector.server.port";
    public static final int DEFAULT_SERVER_PORT = 7070;

    public static final String SERVER_DATA_DIR = "spector.server.data-dir";
    public static final String DEFAULT_SERVER_DATA_DIR = "./spector-data";
}
