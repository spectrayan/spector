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
import java.util.List;

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

    // Concurrency & Core System Properties
    public static final String CONCURRENCY_STRUCTURED = "spector.concurrency.structured";
    public static final boolean DEFAULT_CONCURRENCY_STRUCTURED = true;

    public static final String EVENTS_ASYNC = "spector.events.async";
    public static final boolean DEFAULT_EVENTS_ASYNC = false;

    public static final String EMBEDDING_SEQUENTIAL = "spector.embedding.sequential";
    public static final boolean DEFAULT_EMBEDDING_SEQUENTIAL = false;

    public static final String GRAPH_EXPANSION_THRESHOLD_PROPERTY = "spector.memory.graphExpansionThreshold";
    public static final String GRAPH_EXPANSION_THRESHOLD_BENCH_ALIAS = "spector.benchmark.graphExpansionThreshold";
    public static final String GRAPH_EXPANSION_MODE_PROPERTY = "spector.memory.graphExpansionMode";

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

    public static final String PROVIDER_EMBEDDING_MODEL_PATH = "spector.provider.embedding.model-path";
    public static final String DEFAULT_PROVIDER_EMBEDDING_MODEL_PATH = "";

    public static final String PROVIDER_EMBEDDING_EXECUTION_PROVIDER = "spector.provider.embedding.execution-provider";
    public static final String DEFAULT_PROVIDER_EMBEDDING_EXECUTION_PROVIDER = "CPU";

    public static final String PROVIDER_EMBEDDING_INTRA_OP_THREADS = "spector.provider.embedding.intra-op-threads";
    public static final int DEFAULT_PROVIDER_EMBEDDING_INTRA_OP_THREADS = 0;

    public static final String PROVIDER_EMBEDDING_VOCAB_PATH = "spector.provider.embedding.vocab-path";
    public static final String DEFAULT_PROVIDER_EMBEDDING_VOCAB_PATH = "";

    // Provider — Generation
    public static final String PROVIDER_GENERATION_TYPE = "spector.provider.generation.type";
    public static final String DEFAULT_PROVIDER_GENERATION_TYPE = "ollama";

    public static final String PROVIDER_GENERATION_MODEL = "spector.provider.generation.model";
    public static final String DEFAULT_PROVIDER_GENERATION_MODEL = "llama3.2";

    public static final String PROVIDER_GENERATION_API_KEY = "spector.provider.generation.api-key";
    public static final String DEFAULT_PROVIDER_GENERATION_API_KEY = "";

    public static final String PROVIDER_GENERATION_BASE_URL = "spector.provider.generation.base-url";
    public static final String DEFAULT_PROVIDER_GENERATION_BASE_URL = "http://localhost:11434";

    public static final String PROVIDER_GENERATION_TIMEOUT = "spector.provider.generation.timeout";
    public static final Duration DEFAULT_PROVIDER_GENERATION_TIMEOUT = Duration.ofSeconds(60);

    public static final String PROVIDER_GENERATION_FALLBACK_MODEL = "spector.provider.generation.fallback-model";
    public static final String DEFAULT_PROVIDER_GENERATION_FALLBACK_MODEL = "qwen3:0.6b";

    // Chunking Subsystem (Commons & Ingestion)
    public static final String CHUNKING_TEXT_SIZE = "spector.chunking.text.size";
    public static final int DEFAULT_CHUNKING_TEXT_SIZE = 512;

    public static final String CHUNKING_TEXT_OVERLAP = "spector.chunking.text.overlap";
    public static final int DEFAULT_CHUNKING_TEXT_OVERLAP = 64;

    public static final String CHUNKING_TOKEN_LIMIT = "spector.chunking.token.limit";
    public static final int DEFAULT_CHUNKING_TOKEN_LIMIT = 128;

    public static final String CHUNKING_TOKEN_OVERLAP = "spector.chunking.token.overlap";
    public static final int DEFAULT_CHUNKING_TOKEN_OVERLAP = 16;

    public static final String CHUNKING_DOCUMENT_MAX_SIZE = "spector.chunking.document.max-size";
    public static final long DEFAULT_CHUNKING_DOCUMENT_MAX_SIZE = 100L * 1024 * 1024; // 100MB

    // HDC Hyperdimensional Vector Subsystem
    public static final String HDC_DIMENSIONS = "spector.hdc.dimensions";
    public static final int DEFAULT_HDC_DIMENSIONS = 10_000;

    public static final String HDC_NGRAM_SIZE = "spector.hdc.ngram-size";
    public static final int DEFAULT_HDC_NGRAM_SIZE = 3;

    // SVASQ Quantization Subsystem
    public static final String QUANTIZATION_SVASQ_SEED = "spector.quantization.svasq.seed";
    public static final long DEFAULT_QUANTIZATION_SVASQ_SEED = 42L;

    public static final String QUANTIZATION_SVASQ_CLIP_PERCENTILE = "spector.quantization.svasq.clip-percentile";
    public static final float DEFAULT_QUANTIZATION_SVASQ_CLIP_PERCENTILE = 0.001f;

    public static final String QUANTIZATION_SVASQ_CLIP_SIGMAS = "spector.quantization.svasq.clip-sigmas";
    public static final float DEFAULT_QUANTIZATION_SVASQ_CLIP_SIGMAS = 3.0f;

    public static final String QUANTIZATION_SVASQ_CLIP_SIGMAS_4BIT = "spector.quantization.svasq.clip-sigmas-4bit";
    public static final float DEFAULT_QUANTIZATION_SVASQ_CLIP_SIGMAS_4BIT = 2.5f;

    public static final String QUANTIZATION_SVASQ_MAX_SAMPLE_SIZE = "spector.quantization.svasq.max-sample-size";
    public static final int DEFAULT_QUANTIZATION_SVASQ_MAX_SAMPLE_SIZE = 10_000;

    public static final String QUANTIZATION_SVASQ_MIN_STD = "spector.quantization.svasq.min-std";
    public static final float DEFAULT_QUANTIZATION_SVASQ_MIN_STD = 1e-6f;

    // Memory — Core Capacities & Options
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

    public static final String MEMORY_WORKING_CAPACITY = "spector.memory.working-capacity";
    public static final int DEFAULT_MEMORY_WORKING_CAPACITY = 100;

    public static final String MEMORY_EPISODIC_PARTITION_CAPACITY = "spector.memory.episodic-partition-capacity";
    public static final int DEFAULT_MEMORY_EPISODIC_PARTITION_CAPACITY = 1_000;

    public static final String MEMORY_SEMANTIC_CAPACITY = "spector.memory.semantic-capacity";
    public static final int DEFAULT_MEMORY_SEMANTIC_CAPACITY = 10_000;

    public static final String MEMORY_PROCEDURAL_CAPACITY = "spector.memory.procedural-capacity";
    public static final int DEFAULT_MEMORY_PROCEDURAL_CAPACITY = 1_000;

    public static final String MEMORY_ENTITY_GRAPH_CAPACITY = "spector.memory.entity-graph-capacity";
    public static final int DEFAULT_MEMORY_ENTITY_GRAPH_CAPACITY = 50_000;

    public static final String MEMORY_PINNED_QUOTA = "spector.memory.pinned-quota";
    public static final int DEFAULT_MEMORY_PINNED_QUOTA = 10_000;

    public static final String MEMORY_CHECKPOINT_INTERVAL_SECONDS = "spector.memory.checkpoint-interval-seconds";
    public static final int DEFAULT_MEMORY_CHECKPOINT_INTERVAL_SECONDS = 30;

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

    // LLM Sampling Parameters
    public static final String MEMORY_LLM_TEMPERATURE = "spector.memory.llm.temperature";
    public static final float DEFAULT_MEMORY_LLM_TEMPERATURE = 0.3f;

    public static final String MEMORY_LLM_MAX_TOKENS = "spector.memory.llm.max-tokens";
    public static final int DEFAULT_MEMORY_LLM_MAX_TOKENS = 1024;

    public static final String MEMORY_LLM_TOP_P = "spector.memory.llm.top-p";
    public static final float DEFAULT_MEMORY_LLM_TOP_P = 0.95f;

    public static final String MEMORY_LLM_ENTITY_MODEL = "spector.memory.llm.entity-model";
    public static final String DEFAULT_MEMORY_LLM_ENTITY_MODEL = "";

    // Memory — Cognitive & Biological Hyperparameters
    public static final String MEMORY_SURPRISE_WARMUP = "spector.memory.surprise-warmup";
    public static final int DEFAULT_MEMORY_SURPRISE_WARMUP = 10;

    public static final String MEMORY_FLASHBULB_THRESHOLD = "spector.memory.flashbulb-threshold";
    public static final float DEFAULT_MEMORY_FLASHBULB_THRESHOLD = 3.0f;

    public static final String MEMORY_VALENCE_LEARNING_RATE = "spector.memory.valence-learning-rate";
    public static final float DEFAULT_MEMORY_VALENCE_LEARNING_RATE = 0.3f;

    public static final String MEMORY_DEDUPLICATION_RADIUS = "spector.memory.deduplication-radius";
    public static final float DEFAULT_MEMORY_DEDUPLICATION_RADIUS = 0.05f;

    public static final String MEMORY_INHIBITION_TTL_MS = "spector.memory.inhibition-ttl-ms";
    public static final long DEFAULT_MEMORY_INHIBITION_TTL_MS = 300_000L;

    public static final String MEMORY_INHIBITION_FLOOR = "spector.memory.inhibition-floor";
    public static final float DEFAULT_MEMORY_INHIBITION_FLOOR = 0.1f;

    public static final String MEMORY_HABITUATION_DECAY_RATE = "spector.memory.habituation-decay-rate";
    public static final float DEFAULT_MEMORY_HABITUATION_DECAY_RATE = 0.2f;

    public static final String MEMORY_LTP_COOLDOWN_MS = "spector.memory.ltp-cooldown-ms";
    public static final long DEFAULT_MEMORY_LTP_COOLDOWN_MS = 300_000L;

    // Memory — Header & Strength Region Layout
    public static final String MEMORY_HEADER_VERSION = "spector.memory.header.version";
    public static final int DEFAULT_MEMORY_HEADER_VERSION = 2;

    public static final String MEMORY_STRENGTH_ENABLED = "spector.memory.strength.enabled";
    public static final boolean DEFAULT_MEMORY_STRENGTH_ENABLED = true;
    @Deprecated
    public static final String MEMORY_AUDIT_ENABLED = MEMORY_STRENGTH_ENABLED;
    @Deprecated
    public static final boolean DEFAULT_MEMORY_AUDIT_ENABLED = DEFAULT_MEMORY_STRENGTH_ENABLED;

    public static final String MEMORY_STRENGTH_STRIDE_BYTES = "spector.memory.strength.stride-bytes";
    public static final int DEFAULT_MEMORY_STRENGTH_STRIDE_BYTES = 96;
    @Deprecated
    public static final String MEMORY_AUDIT_STRIDE_BYTES = MEMORY_STRENGTH_STRIDE_BYTES;
    @Deprecated
    public static final int DEFAULT_MEMORY_AUDIT_STRIDE_BYTES = DEFAULT_MEMORY_STRENGTH_STRIDE_BYTES;

    public static final String MEMORY_ACTR_RING_BUFFER_SLOTS = "spector.memory.actr.ring-buffer-slots";
    public static final int DEFAULT_MEMORY_ACTR_RING_BUFFER_SLOTS = 8;

    public static final String MEMORY_STRENGTH_AUTO_LTP_COOLDOWN_MS = "spector.memory.strength.auto-ltp-cooldown-ms";
    public static final long DEFAULT_MEMORY_STRENGTH_AUTO_LTP_COOLDOWN_MS = 300_000L;
    @Deprecated
    public static final String MEMORY_AUDIT_AUTO_LTP_COOLDOWN_MS = MEMORY_STRENGTH_AUTO_LTP_COOLDOWN_MS;
    @Deprecated
    public static final long DEFAULT_MEMORY_AUDIT_AUTO_LTP_COOLDOWN_MS = DEFAULT_MEMORY_STRENGTH_AUTO_LTP_COOLDOWN_MS;

    public static final String MEMORY_AUTO_LTP_STORAGE_INCREMENT = "spector.memory.strength.auto-ltp-storage-increment";
    public static final String MEMORY_STRENGTH_AUTO_LTP_STORAGE_INCREMENT = MEMORY_AUTO_LTP_STORAGE_INCREMENT;
    public static final float DEFAULT_MEMORY_AUTO_LTP_STORAGE_INCREMENT = 0.05f;

    public static final String MEMORY_STRENGTH_RESERVED_BYTES = "spector.memory.strength.reserved-bytes";
    public static final int DEFAULT_MEMORY_STRENGTH_RESERVED_BYTES = 16;
    @Deprecated
    public static final String MEMORY_AUDIT_RESERVED_BYTES = MEMORY_STRENGTH_RESERVED_BYTES;
    @Deprecated
    public static final int DEFAULT_MEMORY_AUDIT_RESERVED_BYTES = DEFAULT_MEMORY_STRENGTH_RESERVED_BYTES;

    public static final String MEMORY_HEADER_V2_RESERVED_BYTES = "spector.memory.header.v2-reserved-bytes";
    public static final int DEFAULT_MEMORY_HEADER_V2_RESERVED_BYTES = 12;

    public static final String MEMORY_DECAY_EXPONENT = "spector.memory.decay.exponent";
    public static final float DEFAULT_MEMORY_DECAY_EXPONENT = 0.15f;

    public static final String MEMORY_DECAY_FLOOR = "spector.memory.decay.floor";
    public static final float DEFAULT_MEMORY_DECAY_FLOOR = 0.10f;

    public static final String MEMORY_TWOFACTOR_S_GAIN = "spector.memory.twofactor.s-gain";
    public static final float DEFAULT_MEMORY_TWOFACTOR_S_GAIN = 0.1f;

    public static final String MEMORY_TWOFACTOR_S_MIN = "spector.memory.twofactor.s-min";
    public static final float DEFAULT_MEMORY_TWOFACTOR_S_MIN = 0.01f;

    public static final String MEMORY_TWOFACTOR_S_MAX = "spector.memory.twofactor.s-max";
    public static final float DEFAULT_MEMORY_TWOFACTOR_S_MAX = 5.0f;

    public static final String MEMORY_TWOFACTOR_INITIAL_STORAGE_STRENGTH = "spector.memory.twofactor.initial-storage-strength";
    public static final float DEFAULT_MEMORY_TWOFACTOR_INITIAL_STORAGE_STRENGTH = 1.0f;

    public static final String MEMORY_TWOFACTOR_S_EXPONENT = "spector.memory.twofactor.s-exponent";
    public static final float DEFAULT_MEMORY_TWOFACTOR_S_EXPONENT = 0.3f;

    public static final String MEMORY_ENTITY_LTP_REINFORCEMENT = "spector.memory.entity.ltp-reinforcement";
    public static final float DEFAULT_MEMORY_ENTITY_LTP_REINFORCEMENT = 0.2f;

    // Hebbian & Graphs
    public static final String MEMORY_HEBBIAN_MAX_DEGREE = "spector.memory.hebbian.max-degree";
    public static final int DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE = 24;

    public static final String MEMORY_HEBBIAN_SESSION_BOUNDARY_MS = "spector.memory.hebbian.session-boundary-ms";
    public static final long DEFAULT_MEMORY_HEBBIAN_SESSION_BOUNDARY_MS = 300_000L;

    public static final String MEMORY_STDP_A_PLUS = "spector.memory.stdp.a-plus";
    public static final float DEFAULT_MEMORY_STDP_A_PLUS = 0.1f;

    public static final String MEMORY_STDP_A_MINUS = "spector.memory.stdp.a-minus";
    public static final float DEFAULT_MEMORY_STDP_A_MINUS = 0.05f;

    public static final String MEMORY_STDP_TAU_PLUS = "spector.memory.stdp.tau-plus";
    public static final float DEFAULT_MEMORY_STDP_TAU_PLUS = 30_000f;

    public static final String MEMORY_STDP_TAU_MINUS = "spector.memory.stdp.tau-minus";
    public static final float DEFAULT_MEMORY_STDP_TAU_MINUS = 30_000f;

    public static final String MEMORY_HEBBIAN_DECAY_FLOOR = "spector.memory.hebbian.decay-floor";
    public static final float DEFAULT_MEMORY_HEBBIAN_DECAY_FLOOR = 0.10f;

    public static final String MEMORY_HEBBIAN_ACTIVATION_CUTOFF = "spector.memory.hebbian.activation-cutoff";
    public static final float DEFAULT_MEMORY_HEBBIAN_ACTIVATION_CUTOFF = 0.01f;

    public static final String MEMORY_HEBBIAN_HOP_ATTENUATION = "spector.memory.hebbian.hop-attenuation";
    public static final float DEFAULT_MEMORY_HEBBIAN_HOP_ATTENUATION = 0.50f;

    public static final String MEMORY_HEBBIAN_DEFAULT_WEIGHT_DELTA = "spector.memory.hebbian.default-weight-delta";
    public static final float DEFAULT_MEMORY_HEBBIAN_DEFAULT_WEIGHT_DELTA = 1.0f;

    public static final String MEMORY_HEBBIAN_NEUTRAL_BRIDGE_SCORE = "spector.memory.hebbian.neutral-bridge-score";
    public static final int DEFAULT_MEMORY_HEBBIAN_NEUTRAL_BRIDGE_SCORE = 128;

    // ── CoActivation ──

    public static final String MEMORY_COACTIVATION_CAPACITY = "spector.memory.coactivation.capacity";
    public static final int DEFAULT_MEMORY_COACTIVATION_CAPACITY = 10_000;

    public static final String MEMORY_COACTIVATION_MIN_TABLE_CAPACITY = "spector.memory.coactivation.min-table-capacity";
    public static final int DEFAULT_MEMORY_COACTIVATION_MIN_TABLE_CAPACITY = 64;

    public static final String MEMORY_CROSS_CAPTURE_FAN_EXPONENT = "spector.memory.cross-capture.fan-exponent";
    public static final float DEFAULT_MEMORY_CROSS_CAPTURE_FAN_EXPONENT = 0.50f;

    // ── Cross-Capture Graph (ADR-0009) ──

    /** Attenuation factor applied to Cross-Capture Graph candidates during recall expansion. */
    public static final String MEMORY_CROSS_CAPTURE_ATTENUATION = "spector.memory.cross-capture.attenuation";
    public static final float DEFAULT_MEMORY_CROSS_CAPTURE_ATTENUATION = 0.25f;

    /** Maximum number of co-occurring tags to explore per query tag during traversal. */
    public static final String MEMORY_CROSS_CAPTURE_MAX_TAG_NEIGHBORS = "spector.memory.cross-capture.max-tag-neighbors";
    public static final int DEFAULT_MEMORY_CROSS_CAPTURE_MAX_TAG_NEIGHBORS = 5;

    /** Maximum number of memories to retrieve per related tag during traversal. */
    public static final String MEMORY_CROSS_CAPTURE_MAX_MEMORIES_PER_TAG = "spector.memory.cross-capture.max-memories-per-tag";
    public static final int DEFAULT_MEMORY_CROSS_CAPTURE_MAX_MEMORIES_PER_TAG = 10;

    /** Ignored tag prefixes for cross-capture expansion (e.g. conversational structural tags). */
    public static final String MEMORY_CROSS_CAPTURE_IGNORED_TAG_PREFIXES = "spector.memory.cross-capture.ignored-tag-prefixes";
    public static final List<String> DEFAULT_MEMORY_CROSS_CAPTURE_IGNORED_TAG_PREFIXES = List.of("conv_", "sess_");

    /** Minimum tag length to qualify for cross-capture expansion. */
    public static final String MEMORY_CROSS_CAPTURE_MIN_TAG_LENGTH = "spector.memory.cross-capture.min-tag-length";
    public static final int DEFAULT_MEMORY_CROSS_CAPTURE_MIN_TAG_LENGTH = 3;

    // ── Spectral Sparsification (#416) ──

    /** Whether Tier 1 (actual pruning) is enabled. When false, operates in shadow mode (Tier 0). */
    public static final String MEMORY_SPARSIFICATION_ENABLED = "spector.memory.sparsification.enabled";
    public static final boolean DEFAULT_MEMORY_SPARSIFICATION_ENABLED = false;

    /** Leverage keep floor: edges with leverage below this fraction are DROP candidates. */
    public static final String MEMORY_SPARSIFICATION_KEEP_FLOOR = "spector.memory.sparsification.keep-floor";
    public static final float DEFAULT_MEMORY_SPARSIFICATION_KEEP_FLOOR = 0.15f;

    /** Bridge protection threshold [0,255]: edges at or above are never sparsified. */
    public static final String MEMORY_SPARSIFICATION_BRIDGE_THRESHOLD = "spector.memory.sparsification.bridge-threshold";
    public static final int DEFAULT_MEMORY_SPARSIFICATION_BRIDGE_THRESHOLD = 224;

    public static final String MEMORY_BRIDGE_SAMPLE_COUNT = "spector.memory.bridge.sample-count";
    public static final int DEFAULT_MEMORY_BRIDGE_SAMPLE_COUNT = 15;

    public static final String MEMORY_BRIDGE_BUDGET_MS = "spector.memory.bridge.budget-ms";
    public static final long DEFAULT_MEMORY_BRIDGE_BUDGET_MS = 500L;

    public static final String MEMORY_ENTITY_MAX_DEGREE = "spector.memory.entity.max-degree";
    public static final int DEFAULT_MEMORY_ENTITY_MAX_DEGREE = 16;

    public static final String MEMORY_ENTITY_MAX_PER_MEM = "spector.memory.entity.max-per-memory";
    public static final int DEFAULT_MEMORY_ENTITY_MAX_PER_MEM = 10;

    public static final String MEMORY_RELATION_MAX_PER_MEM = "spector.memory.relation.max-per-memory";
    public static final int DEFAULT_MEMORY_RELATION_MAX_PER_MEM = 20;

    public static final String MEMORY_ENTITY_COSINE_THRESHOLD = "spector.memory.entity.cosine-threshold";
    public static final float DEFAULT_MEMORY_ENTITY_COSINE_THRESHOLD = 0.85f;

    public static final String MEMORY_ENTITY_RETENTION_DAYS = "spector.memory.entity.retention-days";
    public static final int DEFAULT_MEMORY_ENTITY_RETENTION_DAYS = 7;

    // Graph Scoring & Recall Defaults
    public static final String MEMORY_GRAPH_CAUSAL_BOOST = "spector.memory.graph.causal-boost";
    public static final float DEFAULT_MEMORY_GRAPH_CAUSAL_BOOST = 0.3f;

    public static final String MEMORY_GRAPH_HEBBIAN_BOOST = "spector.memory.graph.hebbian-boost";
    public static final float DEFAULT_MEMORY_GRAPH_HEBBIAN_BOOST = 0.3f;

    public static final String MEMORY_GRAPH_TEMPORAL_FWD = "spector.memory.graph.temporal-forward";
    public static final float DEFAULT_MEMORY_GRAPH_TEMPORAL_FWD = 0.8f;

    public static final String MEMORY_GRAPH_TEMPORAL_BWD = "spector.memory.graph.temporal-backward";
    public static final float DEFAULT_MEMORY_GRAPH_TEMPORAL_BWD = 0.7f;

    public static final String MEMORY_GRAPH_ENTITY_ATTENUATION = "spector.memory.graph.entity-attenuation";
    public static final float DEFAULT_MEMORY_GRAPH_ENTITY_ATTENUATION = 0.25f;

    public static final String MEMORY_GRAPH_EXPANSION_THRESHOLD = "spector.memory.graph.expansion-threshold";
    public static final String MEMORY_GRAPH_EXPANSION_THRESHOLD_CAMEL = "spector.memory.graphExpansionThreshold";
    public static final float DEFAULT_MEMORY_GRAPH_EXPANSION_THRESHOLD = 0.40f;

    public static final String MEMORY_RETRIEVAL_ENABLE_MMR = "spector.memory.retrieval.enable-mmr";
    public static final boolean DEFAULT_MEMORY_RETRIEVAL_ENABLE_MMR = true;

    public static final String MEMORY_RETRIEVAL_MMR_LAMBDA = "spector.memory.retrieval.mmr-lambda";
    public static final float DEFAULT_MEMORY_RETRIEVAL_MMR_LAMBDA = 0.70f;

    public static final String MEMORY_SCHEDULER_ENABLED = "spector.memory.scheduler.enabled";
    public static final boolean DEFAULT_MEMORY_SCHEDULER_ENABLED = true;

    public static final String MEMORY_WANDER_ENABLED = "spector.memory.wander.enabled";
    public static final boolean DEFAULT_MEMORY_WANDER_ENABLED = false;

    // Circadian & Reflection
    public static final String MEMORY_CIRCADIAN_ENABLED = "spector.memory.circadian.enabled";
    public static final boolean DEFAULT_MEMORY_CIRCADIAN_ENABLED = true;

    public static final String MEMORY_CIRCADIAN_VOLUME_TRIGGER = "spector.memory.circadian.volume-trigger";
    public static final int DEFAULT_MEMORY_CIRCADIAN_VOLUME_TRIGGER = 100;

    public static final String MEMORY_CIRCADIAN_TIME_TRIGGER = "spector.memory.circadian.time-trigger";
    public static final Duration DEFAULT_MEMORY_CIRCADIAN_TIME_TRIGGER = Duration.ofHours(1);

    public static final String MEMORY_CIRCADIAN_TOMBSTONE_THRESHOLD = "spector.memory.circadian.tombstone-threshold";
    public static final float DEFAULT_MEMORY_CIRCADIAN_TOMBSTONE_THRESHOLD = 0.30f;

    public static final String MEMORY_CIRCADIAN_DECAY_PRUNE_THRESHOLD = "spector.memory.circadian.decay-prune-threshold";
    public static final float DEFAULT_MEMORY_CIRCADIAN_DECAY_PRUNE_THRESHOLD = 0.05f;

    public static final String CONSOLIDATION_SOUL_DRIFT_REFUSION_ENABLED = "spector.consolidation.soul-drift-refusion.enabled";
    public static final boolean DEFAULT_CONSOLIDATION_SOUL_DRIFT_REFUSION_ENABLED = true;

    public static final String CONSOLIDATION_SOUL_DRIFT_REFUSION_BATCH_SIZE = "spector.consolidation.soul-drift-refusion.batch-size";
    public static final int DEFAULT_CONSOLIDATION_SOUL_DRIFT_REFUSION_BATCH_SIZE = 100;

    public static final String CONSOLIDATION_REFLECTION_TEMPERATURE = "spector.consolidation.reflection.temperature";
    public static final float DEFAULT_CONSOLIDATION_REFLECTION_TEMPERATURE = 0.1f;

    public static final String CONSOLIDATION_REFLECTION_MAX_TOKENS = "spector.consolidation.reflection.max-tokens";
    public static final int DEFAULT_CONSOLIDATION_REFLECTION_MAX_TOKENS = 2048;

    public static final String CONSOLIDATION_REFLECTION_TOP_P = "spector.consolidation.reflection.top-p";
    public static final float DEFAULT_CONSOLIDATION_REFLECTION_TOP_P = 0.95f;

    public static final String CONSOLIDATION_REFLECTION_MAX_PRIOR_CONTEXT_TURNS = "spector.consolidation.reflection.max-prior-context-turns";
    public static final int DEFAULT_CONSOLIDATION_REFLECTION_MAX_PRIOR_CONTEXT_TURNS = 10;

    public static final String MEMORY_REFLECT_MIN_CLUSTER_SIZE = "spector.memory.reflect.min-cluster-size";
    public static final int DEFAULT_MEMORY_REFLECT_MIN_CLUSTER_SIZE = 5;

    public static final String MEMORY_HEBBIAN_PROMOTION_MIN_WEIGHT = "spector.memory.hebbian.promotion-min-weight";
    public static final float DEFAULT_MEMORY_HEBBIAN_PROMOTION_MIN_WEIGHT = 3.0f;

    public static final String MEMORY_HEBBIAN_DECAY_FACTOR = "spector.memory.hebbian.decay-factor";
    public static final float DEFAULT_MEMORY_HEBBIAN_DECAY_FACTOR = 0.9f;

    public static final String MEMORY_ENTITY_DECAY_FACTOR = "spector.memory.entity.decay-factor";
    public static final float DEFAULT_MEMORY_ENTITY_DECAY_FACTOR = 0.95f;

    public static final String MEMORY_ENTITY_PRUNE_THRESHOLD = "spector.memory.entity.prune-threshold";
    public static final float DEFAULT_MEMORY_ENTITY_PRUNE_THRESHOLD = 0.5f;

    // Active Inference Self-Model Engine (AISME)
    public static final String MEMORY_AISME_ENABLED = "spector.memory.aisme.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_ENABLED = false;

    public static final String MEMORY_AISME_HOMEOSTASIS_ENABLED = "spector.memory.aisme.homeostasis.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_HOMEOSTASIS_ENABLED = true;

    public static final String MEMORY_AISME_FREE_ENERGY_ENABLED = "spector.memory.aisme.free-energy.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_FREE_ENERGY_ENABLED = true;

    public static final String MEMORY_AISME_HOPFIELD_ENABLED = "spector.memory.aisme.hopfield.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_HOPFIELD_ENABLED = true;

    public static final String MEMORY_AISME_MANIFOLD_ENABLED = "spector.memory.aisme.manifold.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_MANIFOLD_ENABLED = true;

    public static final String MEMORY_AISME_PREDICTIVE_CODING_ENABLED = "spector.memory.aisme.predictive-coding.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_PREDICTIVE_CODING_ENABLED = true;

    public static final String MEMORY_AISME_CONSCIOUSNESS_CONTINUITY_ENABLED = "spector.memory.aisme.consciousness-continuity.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_CONSCIOUSNESS_CONTINUITY_ENABLED = true;

    public static final String MEMORY_AISME_GLOBAL_WORKSPACE_ENABLED = "spector.memory.aisme.global-workspace.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_ENABLED = true;

    public static final String MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY = "spector.memory.aisme.global-workspace.capacity";
    public static final int DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY = 7;

    public static final String MEMORY_AISME_HOPFIELD_TEMPERATURE = "spector.memory.aisme.hopfield.temperature";
    public static final float DEFAULT_MEMORY_AISME_HOPFIELD_TEMPERATURE = 4.0f;

    public static final String MEMORY_AISME_MANIFOLD_SIGMA = "spector.memory.aisme.manifold.sigma";
    public static final float DEFAULT_MEMORY_AISME_MANIFOLD_SIGMA = 1.0f;

    public static final String MEMORY_AISME_PHI_COHESION_THRESHOLD = "spector.memory.aisme.phi.cohesion-threshold";
    public static final float DEFAULT_MEMORY_AISME_PHI_COHESION_THRESHOLD = 0.05f;

    public static final String MEMORY_AISME_DMN_ENABLED = "spector.memory.aisme.dmn.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_DMN_ENABLED = true;

    public static final String MEMORY_AISME_DMN_IDLE_SECONDS = "spector.memory.aisme.dmn.idle-seconds";
    public static final int DEFAULT_MEMORY_AISME_DMN_IDLE_SECONDS = 60;

    public static final String MEMORY_AISME_LONGITUDINAL_CONTINUITY_ENABLED = "spector.memory.aisme.longitudinal-continuity.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_LONGITUDINAL_CONTINUITY_ENABLED = true;

    public static final String MEMORY_AISME_LONGITUDINAL_SNAPSHOT_INTERVAL_MINUTES = "spector.memory.aisme.longitudinal-snapshot.interval-minutes";
    public static final int DEFAULT_MEMORY_AISME_LONGITUDINAL_SNAPSHOT_INTERVAL_MINUTES = 60;

    // AISME — Expected Free Energy (G) Policy Engine
    public static final String MEMORY_AISME_EFE_ENABLED = "spector.memory.aisme.efe.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_EFE_ENABLED = true;

    public static final String MEMORY_AISME_EFE_POLICY_PRECISION = "spector.memory.aisme.efe.policy-precision";
    public static final float DEFAULT_MEMORY_AISME_EFE_POLICY_PRECISION = 1.0f;

    public static final String MEMORY_AISME_EFE_EPISTEMIC_WEIGHT = "spector.memory.aisme.efe.epistemic-weight";
    public static final float DEFAULT_MEMORY_AISME_EFE_EPISTEMIC_WEIGHT = 1.0f;

    public static final String MEMORY_AISME_EFE_PRAGMATIC_WEIGHT = "spector.memory.aisme.efe.pragmatic-weight";
    public static final float DEFAULT_MEMORY_AISME_EFE_PRAGMATIC_WEIGHT = 1.0f;

    public static final String MEMORY_AISME_EFE_SOUL_WEIGHT_AGENT = "spector.memory.aisme.efe.soul-weight.agent";
    public static final float DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_AGENT = 0.40f;

    public static final String MEMORY_AISME_EFE_SOUL_WEIGHT_USER = "spector.memory.aisme.efe.soul-weight.user";
    public static final float DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_USER = 0.35f;

    public static final String MEMORY_AISME_EFE_SOUL_WEIGHT_TENANT = "spector.memory.aisme.efe.soul-weight.tenant";
    public static final float DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_TENANT = 0.15f;

    public static final String MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT = "spector.memory.aisme.efe.soul-weight.org-unit";
    public static final float DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT = 0.10f;

    // AISME — Constructive Memory Persistence
    public static final String MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_THRESHOLD = "spector.memory.aisme.constructive.persistence-threshold";
    public static final float DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_THRESHOLD = 0.70f;

    public static final String MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_ENABLED = "spector.memory.aisme.constructive.persistence-enabled";
    public static final boolean DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_ENABLED = true;

    // AISME — Background Homeostatic Decay
    public static final String MEMORY_AISME_BACKGROUND_DECAY_ENABLED = "spector.memory.aisme.background-decay.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_ENABLED = true;

    public static final String MEMORY_AISME_BACKGROUND_DECAY_FACTOR = "spector.memory.aisme.background-decay.factor";
    public static final float DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_FACTOR = 0.02f;

    public static final String MEMORY_AISME_BACKGROUND_DECAY_INTERVAL_SECONDS = "spector.memory.aisme.background-decay.interval-seconds";
    public static final int DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_INTERVAL_SECONDS = 300;

    // AISME — Soft Identity Anchor & Lyapunov Stability
    public static final String MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED = "spector.memory.aisme.soft-identity-anchor.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED = true;

    public static final String MEMORY_AISME_IDENTITY_ANCHOR_ETA = "spector.memory.aisme.identity-anchor.eta";
    public static final float DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA = 0.0001f;

    public static final String MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD = "spector.memory.aisme.identity-lyapunov.threshold";
    public static final float DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD = 0.15f;

    public static final String MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS = "spector.memory.aisme.identity-core-snapshot.epochs";
    public static final int DEFAULT_MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS = 50;

    // AISME — Event Density Gating & Dynamic Epistemic Compression
    public static final String MEMORY_AISME_EVENT_DENSITY_ENABLED = "spector.memory.aisme.event-density.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_EVENT_DENSITY_ENABLED = true;

    public static final String MEMORY_AISME_EVENT_DENSITY_THRESHOLD = "spector.memory.aisme.event-density.threshold";
    public static final float DEFAULT_MEMORY_AISME_EVENT_DENSITY_THRESHOLD = 0.50f;

    public static final String MEMORY_AISME_EVENT_DENSITY_ALPHA_KL = "spector.memory.aisme.event-density.alpha-kl";
    public static final float DEFAULT_MEMORY_AISME_EVENT_DENSITY_ALPHA_KL = 0.40f;

    public static final String MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT = "spector.memory.aisme.event-density.beta-gradient";
    public static final float DEFAULT_MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT = 0.30f;

    public static final String MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE = "spector.memory.aisme.event-density.gamma-surprise";
    public static final float DEFAULT_MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE = 0.30f;

    public static final String MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ = "spector.memory.aisme.event-density.sampling-min-hz";
    public static final float DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ = 0.10f;

    public static final String MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ = "spector.memory.aisme.event-density.sampling-max-hz";
    public static final float DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ = 30.0f;

    public static final String MEMORY_AISME_BOCPD_ENABLED = "spector.memory.aisme.bocpd.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_BOCPD_ENABLED = true;

    public static final String MEMORY_AISME_BOCPD_HAZARD_LAMBDA = "spector.memory.aisme.bocpd.hazard-lambda";
    public static final float DEFAULT_MEMORY_AISME_BOCPD_HAZARD_LAMBDA = 100.0f;

    public static final String MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD = "spector.memory.aisme.bocpd.change-point-threshold";
    public static final float DEFAULT_MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD = 0.65f;

    public static final String MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD = "spector.memory.aisme.bocpd.surprisal-cut-threshold";
    public static final float DEFAULT_MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD = 1.50f;

    public static final String MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES = "spector.memory.aisme.bocpd.max-episode-frames";
    public static final int DEFAULT_MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES = 200;

    public static final String MEMORY_AISME_BOCPD_MAX_RUN_LENGTH = "spector.memory.aisme.bocpd.max-run-length";
    public static final int DEFAULT_MEMORY_AISME_BOCPD_MAX_RUN_LENGTH = 150;

    // Differential Privacy & Edge Local Anonymization
    public static final String MEMORY_AISME_PRIVACY_ENABLED = "spector.memory.aisme.privacy.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_PRIVACY_ENABLED = false;

    public static final String MEMORY_AISME_PRIVACY_EPSILON = "spector.memory.aisme.privacy.epsilon";
    public static final float DEFAULT_MEMORY_AISME_PRIVACY_EPSILON = 2.0f;

    public static final String MEMORY_AISME_PRIVACY_DELTA = "spector.memory.aisme.privacy.delta";
    public static final float DEFAULT_MEMORY_AISME_PRIVACY_DELTA = 1e-5f;

    public static final String MEMORY_AISME_PRIVACY_CLIPPING_NORM = "spector.memory.aisme.privacy.clipping-norm";
    public static final float DEFAULT_MEMORY_AISME_PRIVACY_CLIPPING_NORM = 1.0f;

    public static final String MEMORY_AISME_PRIVACY_ANONYMIZE_PII = "spector.memory.aisme.privacy.anonymize-pii";
    public static final boolean DEFAULT_MEMORY_AISME_PRIVACY_ANONYMIZE_PII = true;

    public static final String MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT = "spector.memory.aisme.privacy.pseudonymization-salt";
    public static final String DEFAULT_MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT = "spector-privacy-salt";

    // AISME — Multimodal Composite Importance Scoring I(o_t)
    public static final String MEMORY_AISME_IMPORTANCE_ENABLED = "spector.memory.aisme.importance.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_IMPORTANCE_ENABLED = true;

    public static final String MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE = "spector.memory.aisme.importance.weight-surprise";
    public static final float DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE = 0.20f;

    public static final String MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT = "spector.memory.aisme.importance.weight-affect";
    public static final float DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT = 0.20f;

    public static final String MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL = "spector.memory.aisme.importance.weight-goal";
    public static final float DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL = 0.20f;

    public static final String MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL = "spector.memory.aisme.importance.weight-social";
    public static final float DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL = 0.20f;

    public static final String MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY = "spector.memory.aisme.importance.weight-novelty";
    public static final float DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY = 0.20f;

    public static final String MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD = "spector.memory.aisme.importance.flashbulb-threshold";
    public static final float DEFAULT_MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD = 0.85f;

    // AISME — Lifespan-Adaptive Forgetting & Retention Threshold \tau(t)
    public static final String MEMORY_AISME_LIFESPAN_ENABLED = "spector.memory.aisme.lifespan.enabled";
    public static final boolean DEFAULT_MEMORY_AISME_LIFESPAN_ENABLED = true;

    public static final String MEMORY_AISME_LIFESPAN_TAU_0 = "spector.memory.aisme.lifespan.tau-0";
    public static final float DEFAULT_MEMORY_AISME_LIFESPAN_TAU_0 = 0.30f;

    public static final String MEMORY_AISME_LIFESPAN_K = "spector.memory.aisme.lifespan.k";
    public static final float DEFAULT_MEMORY_AISME_LIFESPAN_K = 0.15f;

    public static final String MEMORY_AISME_LIFESPAN_T0_EPOCHS = "spector.memory.aisme.lifespan.t0-epochs";
    public static final long DEFAULT_MEMORY_AISME_LIFESPAN_T0_EPOCHS = 365L;

    public static final String MEMORY_AISME_LIFESPAN_V_TARGET = "spector.memory.aisme.lifespan.v-target";
    public static final long DEFAULT_MEMORY_AISME_LIFESPAN_V_TARGET = 100000L;

    public static final String MEMORY_AISME_LIFESPAN_GAMMA = "spector.memory.aisme.lifespan.gamma";
    public static final float DEFAULT_MEMORY_AISME_LIFESPAN_GAMMA = 1.2f;

    public static final String MEMORY_AISME_LIFESPAN_FLASHBULB_PROTECT = "spector.memory.aisme.lifespan.flashbulb-protect";
    public static final boolean DEFAULT_MEMORY_AISME_LIFESPAN_FLASHBULB_PROTECT = true;

    // DreamPathway & Generative Cognition Hyperparameters (ADR Issue #679)
    public static final String MEMORY_DREAM_ENABLED = "spector.memory.dream.enabled";
    public static final boolean DEFAULT_MEMORY_DREAM_ENABLED = false;

    public static final String MEMORY_DREAM_NOISE_SCALE = "spector.memory.dream.noise-scale";
    public static final float DEFAULT_MEMORY_DREAM_NOISE_SCALE = 0.15f;

    public static final String MEMORY_DREAM_TEMPERATURE_REM = "spector.memory.dream.temperature.rem";
    public static final float DEFAULT_MEMORY_DREAM_TEMPERATURE_REM = 2.0f;

    public static final String MEMORY_DREAM_TEMPERATURE_DAYDREAM = "spector.memory.dream.temperature.daydream";
    public static final float DEFAULT_MEMORY_DREAM_TEMPERATURE_DAYDREAM = 1.0f;

    public static final String MEMORY_DREAM_TEMPERATURE_THOUGHT = "spector.memory.dream.temperature.thought";
    public static final float DEFAULT_MEMORY_DREAM_TEMPERATURE_THOUGHT = 0.5f;

    public static final String MEMORY_DREAM_MAX_DREAMS_PER_CYCLE = "spector.memory.dream.max-dreams-per-cycle";
    public static final int DEFAULT_MEMORY_DREAM_MAX_DREAMS_PER_CYCLE = 5;

    public static final String MEMORY_DREAM_MAX_COUNTERFACTUALS_PER_SEED = "spector.memory.dream.max-counterfactuals-per-seed";
    public static final int DEFAULT_MEMORY_DREAM_MAX_COUNTERFACTUALS_PER_SEED = 3;

    public static final String MEMORY_DREAM_PERSISTENCE_THRESHOLD = "spector.memory.dream.persistence-threshold";
    public static final float DEFAULT_MEMORY_DREAM_PERSISTENCE_THRESHOLD = 0.50f;

    public static final String MEMORY_DREAM_LANGEVIN_STEP_SIZE = "spector.memory.dream.langevin.step-size";
    public static final float DEFAULT_MEMORY_DREAM_LANGEVIN_STEP_SIZE = 0.01f;

    public static final String MEMORY_DREAM_LANGEVIN_STEPS = "spector.memory.dream.langevin.steps";
    public static final int DEFAULT_MEMORY_DREAM_LANGEVIN_STEPS = 100;

    public static final String MEMORY_DREAM_NOVELTY_RADIUS = "spector.memory.dream.novelty-radius";
    public static final float DEFAULT_MEMORY_DREAM_NOVELTY_RADIUS = 1.5f;

    public static final String MEMORY_DREAM_HEBBIAN_INHIBITION_DELTA = "spector.memory.dream.hebbian.inhibition-delta";
    public static final float DEFAULT_MEMORY_DREAM_HEBBIAN_INHIBITION_DELTA = -0.05f;

    public static final String MEMORY_DREAM_JOURNAL_ENABLED = "spector.memory.dream.journal-enabled";
    public static final boolean DEFAULT_MEMORY_DREAM_JOURNAL_ENABLED = true;

    public static final String MEMORY_DREAM_CYCLE_FREQUENCY = "spector.memory.dream.cycle-frequency";
    public static final int DEFAULT_MEMORY_DREAM_CYCLE_FREQUENCY = 3;

    // Soul-Conditioned & Salience-Modulated Dreaming Hyperparameters (ADR Issue #681)
    public static final String MEMORY_DREAM_SEED_WEIGHT_RECENCY = "spector.memory.dream.seed.weight-recency";
    public static final float DEFAULT_MEMORY_DREAM_SEED_WEIGHT_RECENCY = 0.30f;

    public static final String MEMORY_DREAM_SEED_WEIGHT_NOVELTY = "spector.memory.dream.seed.weight-novelty";
    public static final float DEFAULT_MEMORY_DREAM_SEED_WEIGHT_NOVELTY = 0.20f;

    public static final String MEMORY_DREAM_SEED_WEIGHT_SOUL = "spector.memory.dream.seed.weight-soul";
    public static final float DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SOUL = 0.30f;

    public static final String MEMORY_DREAM_SEED_WEIGHT_SALIENCE = "spector.memory.dream.seed.weight-salience";
    public static final float DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SALIENCE = 0.20f;

    public static final String MEMORY_DREAM_IDENTITY_RESONANCE_THRESHOLD = "spector.memory.dream.identity-resonance-threshold";
    public static final float DEFAULT_MEMORY_DREAM_IDENTITY_RESONANCE_THRESHOLD = 0.75f;

    public static final String MEMORY_DREAM_ETHICAL_VIOLATION_THRESHOLD = "spector.memory.dream.ethical-violation-threshold";
    public static final float DEFAULT_MEMORY_DREAM_ETHICAL_VIOLATION_THRESHOLD = 0.80f;

    public static final String MEMORY_DREAM_LANGEVIN_SOUL_ATTRACTOR_LAMBDA = "spector.memory.dream.langevin.soul-attractor-lambda";
    public static final float DEFAULT_MEMORY_DREAM_LANGEVIN_SOUL_ATTRACTOR_LAMBDA = 0.15f;

    public static final String MEMORY_DREAM_HARTMANN_OPENNESS_MULTIPLIER = "spector.memory.dream.hartmann.openness-multiplier";
    public static final float DEFAULT_MEMORY_DREAM_HARTMANN_OPENNESS_MULTIPLIER = 1.35f;

    public static final String MEMORY_DREAM_HARTMANN_VIGILANCE_MULTIPLIER = "spector.memory.dream.hartmann.vigilance-multiplier";
    public static final float DEFAULT_MEMORY_DREAM_HARTMANN_VIGILANCE_MULTIPLIER = 0.75f;

    // Session, Sync, WAL & Subsystems
    public static final String MEMORY_WAL_MAX_CHUNK_BYTES = "spector.memory.wal.max-chunk-bytes";
    public static final long DEFAULT_MEMORY_WAL_MAX_CHUNK_BYTES = 8L * 1024 * 1024;

    public static final String MEMORY_VACUUM_THRESHOLD = "spector.memory.vacuum.threshold";
    public static final float DEFAULT_MEMORY_VACUUM_THRESHOLD = 0.20f;
    public static final float DEFAULT_MEMORY_VACUUM_DEFAULT_THRESHOLD = DEFAULT_MEMORY_VACUUM_THRESHOLD;

    public static final String MEMORY_SESSION_BUFFER_SIZE = "spector.memory.session.buffer-size";
    public static final int DEFAULT_MEMORY_SESSION_BUFFER_SIZE = 64;
    public static final int DEFAULT_MEMORY_SESSION_BUFFER_MAX_SIZE = DEFAULT_MEMORY_SESSION_BUFFER_SIZE;

    public static final String MEMORY_SESSION_BUFFER_TTL_MS = "spector.memory.session.buffer-ttl-ms";
    public static final long DEFAULT_MEMORY_SESSION_BUFFER_TTL_MS = 5000L;

    public static final String MEMORY_EAGER_CONSOLIDATION_QUEUE_CAPACITY = "spector.memory.eager-consolidation.queue-capacity";
    public static final int DEFAULT_MEMORY_EAGER_CONSOLIDATION_QUEUE_CAPACITY = 256;

    public static final String MEMORY_NAMESPACE_MAX_ID_LENGTH = "spector.memory.namespace.max-id-length";
    public static final int DEFAULT_MEMORY_NAMESPACE_MAX_ID_LENGTH = 63;

    public static final String MEMORY_NAMESPACE_SOFT_WARNING_THRESHOLD = "spector.memory.namespace.soft-warning-threshold";
    public static final float DEFAULT_MEMORY_NAMESPACE_SOFT_WARNING_THRESHOLD = 0.70f;

    public static final String MEMORY_MAX_NAMESPACES = "spector.memory.max-namespaces";
    public static final int DEFAULT_MEMORY_MAX_NAMESPACES = 100;

    public static final String MEMORY_NAMESPACE_ID = "spector.memory.namespace-id";
    public static final String DEFAULT_MEMORY_NAMESPACE_ID = "default";

    public static final String MEMORY_PERSISTENCE_MODE_FLAG = "spector.memory.persistence-mode";
    public static final String DEFAULT_MEMORY_PERSISTENCE_MODE_NAME = "DISK";

    public static final String MEMORY_PERSIST_WORKING_MEMORY = "spector.memory.persist-working-memory";
    public static final boolean DEFAULT_MEMORY_PERSIST_WORKING_MEMORY = false;

    public static final String MEMORY_PIN_SOURCE_EPISODES = "spector.memory.pin-source-episodes";
    public static final boolean DEFAULT_MEMORY_PIN_SOURCE_EPISODES = false;

    public static final String MEMORY_ENTITY_EXTRACTION_MODE = "spector.memory.entity.extraction-mode";
    public static final String DEFAULT_MEMORY_ENTITY_EXTRACTION_MODE = "NONE";

    public static final String MEMORY_ENTITY_RESOLUTION_ENABLED = "spector.memory.entity.resolution-enabled";
    public static final boolean DEFAULT_MEMORY_ENTITY_RESOLUTION_ENABLED = false;

    public static final String MEMORY_ENTITY_SHADOW_MODE = "spector.memory.entity.shadow-mode";
    public static final boolean DEFAULT_MEMORY_ENTITY_SHADOW_MODE = true;

    public static final String MEMORY_ENTITY_EXTRACTION_PARALLELISM = "spector.memory.entity-extraction.parallelism";
    public static final int DEFAULT_MEMORY_ENTITY_EXTRACTION_PARALLELISM = 1;

    public static final String MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY = "spector.memory.entity-extraction.queue-capacity";
    public static final int DEFAULT_MEMORY_ENTITY_EXTRACTION_QUEUE_CAPACITY = 1000;

    public static final String MEMORY_TASKQUEUE_POLL_TIMEOUT_MS = "spector.memory.taskqueue.poll-timeout-ms";
    public static final long DEFAULT_MEMORY_TASKQUEUE_POLL_TIMEOUT_MS = 500L;

    public static final String MEMORY_TASKQUEUE_DRAIN_TIMEOUT_MS = "spector.memory.taskqueue.drain-timeout-ms";
    public static final long DEFAULT_MEMORY_TASKQUEUE_DRAIN_TIMEOUT_MS = 5000L;

    public static final String MEMORY_TASKQUEUE_MAX_RETRIES = "spector.memory.taskqueue.max-retries";
    public static final int DEFAULT_MEMORY_TASKQUEUE_MAX_RETRIES = 2;

    public static final String MEMORY_TASKQUEUE_RETRY_BACKOFF_MS = "spector.memory.taskqueue.retry-backoff-ms";
    public static final long DEFAULT_MEMORY_TASKQUEUE_RETRY_BACKOFF_MS = 500L;

    public static final String MEMORY_TASKQUEUE_BACKPRESSURE_POLICY = "spector.memory.taskqueue.backpressure-policy";
    public static final String DEFAULT_MEMORY_TASKQUEUE_BACKPRESSURE_POLICY = "REJECT_FAST";

    public static final String MEMORY_CONSOLIDATION_PARALLELISM = "spector.memory.consolidation.parallelism";
    public static final int DEFAULT_MEMORY_CONSOLIDATION_PARALLELISM = 1;

    public static final String MEMORY_CONSOLIDATION_QUEUE_CAPACITY = "spector.memory.consolidation.queue-capacity";
    public static final int DEFAULT_MEMORY_CONSOLIDATION_QUEUE_CAPACITY = 1000;

    public static final String MEMORY_EDGE_IMPORTANCE = "spector.memory.edge-importance";
    public static final String DEFAULT_MEMORY_EDGE_IMPORTANCE = "DEFAULT";

    public static final String MEMORY_ID_STRATEGY = "spector.memory.id-strategy";
    public static final String DEFAULT_MEMORY_ID_STRATEGY = "TSID";

    public static final String MEMORY_GRAPH_EXPANSION_MODE = "spector.memory.graph.expansion-mode";
    public static final String DEFAULT_MEMORY_GRAPH_EXPANSION_MODE = "GATED";

    public static final String MEMORY_ENTITY_ADJ_DECAY_FACTOR = "spector.memory.entity.adj-decay-factor";
    public static final float DEFAULT_MEMORY_ENTITY_ADJ_DECAY_FACTOR = 0.95f;

    public static final String MEMORY_ENTITY_ADJ_PRUNE_THRESHOLD = "spector.memory.entity.adj-prune-threshold";
    public static final float DEFAULT_MEMORY_ENTITY_ADJ_PRUNE_THRESHOLD = 0.2f;

    public static final String MEMORY_ENTITY_MERGE_DISTANCE = "spector.memory.entity.merge-distance";
    public static final int DEFAULT_MEMORY_ENTITY_MERGE_DISTANCE = 2;

    public static final String MEMORY_CROSS_CAPTURE_MIN_WEIGHT = "spector.memory.cross-capture.min-weight";
    public static final float DEFAULT_MEMORY_CROSS_CAPTURE_MIN_WEIGHT = 2.0f;

    public static final String MEMORY_CROSS_CAPTURE_SCALE_FACTOR = "spector.memory.cross-capture.scale-factor";
    public static final float DEFAULT_MEMORY_CROSS_CAPTURE_SCALE_FACTOR = 0.05f;

    public static final String MEMORY_CIRCADIAN_INTERFERENCE_THRESHOLD = "spector.memory.circadian.interference-threshold";
    public static final float DEFAULT_MEMORY_CIRCADIAN_INTERFERENCE_THRESHOLD = 0.12f;

    public static final String MEMORY_CIRCADIAN_INTERFERENCE_DECAY_FACTOR = "spector.memory.circadian.interference-decay-factor";
    public static final float DEFAULT_MEMORY_CIRCADIAN_INTERFERENCE_DECAY_FACTOR = 0.7f;

    public static final String MEMORY_HYPERFOCUS_TTL_MS = "spector.memory.hyperfocus.ttl-ms";
    public static final long DEFAULT_MEMORY_HYPERFOCUS_TTL_MS = 1800_000L;

    public static final String MEMORY_ICNU_THRESHOLD = "spector.memory.icnu.threshold";
    public static final float DEFAULT_MEMORY_ICNU_THRESHOLD = 0.2f;

    public static final String MEMORY_ICNU_STEEPNESS = "spector.memory.icnu.steepness";
    public static final float DEFAULT_MEMORY_ICNU_STEEPNESS = 8.0f;

    public static final String MEMORY_ICNU_WEIGHT_INTEREST = "spector.memory.icnu.weight-interest";
    public static final float DEFAULT_MEMORY_ICNU_WEIGHT_INTEREST = 0.30f;

    public static final String MEMORY_ICNU_WEIGHT_CHALLENGE = "spector.memory.icnu.weight-challenge";
    public static final float DEFAULT_MEMORY_ICNU_WEIGHT_CHALLENGE = 0.10f;

    public static final String MEMORY_ICNU_WEIGHT_NOVELTY = "spector.memory.icnu.weight-novelty";
    public static final float DEFAULT_MEMORY_ICNU_WEIGHT_NOVELTY = 0.40f;

    public static final String MEMORY_ICNU_WEIGHT_URGENCY = "spector.memory.icnu.weight-urgency";
    public static final float DEFAULT_MEMORY_ICNU_WEIGHT_URGENCY = 0.20f;

    public static final String MEMORY_COACTIVATION_PAIR_CAPACITY = "spector.memory.coactivation-pair-capacity";
    public static final int DEFAULT_MEMORY_COACTIVATION_PAIR_CAPACITY = 10_000;

    public static final String MEMORY_COACTIVATION_EDGE_CAPACITY = "spector.memory.coactivation-edge-capacity";
    public static final int DEFAULT_MEMORY_COACTIVATION_EDGE_CAPACITY = 20_000;

    public static final String MEMORY_TEMPORAL_FACTS_INITIAL_SIZE = "spector.memory.temporal-facts-initial-size";
    public static final long DEFAULT_MEMORY_TEMPORAL_FACTS_INITIAL_SIZE = 16L * 1024 * 1024;

    public static final String MEMORY_INDEX_MIDX_CAPACITY = "spector.memory.index-midx-capacity";
    public static final int DEFAULT_MEMORY_INDEX_MIDX_CAPACITY = 100_000;

    public static final String MEMORY_INDEX_IDPL_SIZE = "spector.memory.index-idpl-size";
    public static final long DEFAULT_MEMORY_INDEX_IDPL_SIZE = 16L * 1024 * 1024;

    public static final String MEMORY_TYPE_REGISTRY_CAPACITY = "spector.memory.type-registry-capacity";
    public static final int DEFAULT_MEMORY_TYPE_REGISTRY_CAPACITY = 1024;

    public static final String MEMORY_TYPE_REGISTRY_SIZE = "spector.memory.type-registry-size";
    public static final long DEFAULT_MEMORY_TYPE_REGISTRY_SIZE = 1L * 1024 * 1024;

    public static final String MEMORY_INSULA_SIZE = "spector.memory.insula-size";
    public static final long DEFAULT_MEMORY_INSULA_SIZE = 1024L * 1024;

    // Recall & Search Pipeline Flags (RecallOptions)
    public static final String RECALL_TEXT_SEARCH_ENABLED = "spector.recall.text-search.enabled";
    public static final boolean DEFAULT_RECALL_TEXT_SEARCH_ENABLED = true;

    public static final String RECALL_TEXT_SEARCH_MODE = "spector.recall.text-search.mode";
    public static final String DEFAULT_RECALL_TEXT_SEARCH_MODE = "HYBRID";

    public static final String RECALL_SCORING_MODE = "spector.recall.scoring-mode";
    public static final String DEFAULT_RECALL_SCORING_MODE = "COGNITIVE";

    public static final String RECALL_SCORE_FUSION_MODE = "spector.recall.score-fusion-mode";
    public static final String DEFAULT_RECALL_SCORE_FUSION_MODE = "MULTIPLICATIVE";

    public static final String RECALL_TRACE_ENABLED = "spector.recall.trace.enabled";
    public static final boolean DEFAULT_RECALL_TRACE_ENABLED = false;

    public static final String RECALL_RERANKER_ENABLED = "spector.recall.reranker.enabled";
    public static final boolean DEFAULT_RECALL_RERANKER_ENABLED = false;

    public static final String RECALL_RERANKER_DEPTH = "spector.recall.reranker.depth";
    public static final int DEFAULT_RECALL_RERANKER_DEPTH = 50;

    public static final String RECALL_MMR_ENABLED = "spector.recall.mmr.enabled";
    public static final boolean DEFAULT_RECALL_MMR_ENABLED = true;

    public static final String RECALL_MMR_LAMBDA = "spector.recall.mmr.lambda";
    public static final float DEFAULT_RECALL_MMR_LAMBDA = 0.5f;

    public static final String RECALL_AUTO_PROFILE_ENABLED = "spector.recall.auto-profile.enabled";
    public static final boolean DEFAULT_RECALL_AUTO_PROFILE_ENABLED = false;

    public static final String RECALL_INCLUDE_CONTRADICTIONS = "spector.recall.include-contradictions";
    public static final boolean DEFAULT_RECALL_INCLUDE_CONTRADICTIONS = false;

    public static final String RECALL_LATERAL_ENABLED = "spector.recall.lateral.enabled";
    public static final boolean DEFAULT_RECALL_LATERAL_ENABLED = false;

    public static final String RECALL_LATERAL_DISTANCE_THRESHOLD = "spector.recall.lateral.distance-threshold";
    public static final float DEFAULT_RECALL_LATERAL_DISTANCE_THRESHOLD = 1.2f;

    public static final String RECALL_LATERAL_MIN_TAG_OVERLAP = "spector.recall.lateral.min-tag-overlap";
    public static final float DEFAULT_RECALL_LATERAL_MIN_TAG_OVERLAP = 0.5f;

    // Lateral Inhibition & Interference Resolution (MR-04)
    public static final String RECALL_LATERAL_INHIBITION_ENABLED = "spector.memory.recall.lateral-inhibition.enabled";
    public static final boolean DEFAULT_RECALL_LATERAL_INHIBITION_ENABLED = false;

    public static final String RECALL_LATERAL_INHIBITION_OVERLAP_THRESHOLD = "spector.memory.recall.lateral-inhibition.overlap-threshold";
    public static final float DEFAULT_RECALL_LATERAL_INHIBITION_OVERLAP_THRESHOLD = 0.88f;

    public static final String RECALL_LATERAL_INHIBITION_OVERSCAN_FACTOR = "spector.memory.recall.lateral-inhibition.overscan-factor";
    public static final int DEFAULT_RECALL_LATERAL_INHIBITION_OVERSCAN_FACTOR = 3;

    public static final String RECALL_LATERAL_INHIBITION_MAX_CLUSTER_CANDIDATES = "spector.memory.recall.lateral-inhibition.max-cluster-candidates";
    public static final int DEFAULT_RECALL_LATERAL_INHIBITION_MAX_CLUSTER_CANDIDATES = 64;

    public static final String RECALL_LATERAL_INHIBITION_SOFT_KAPPA = "spector.memory.recall.lateral-inhibition.soft-kappa";
    public static final float DEFAULT_RECALL_LATERAL_INHIBITION_SOFT_KAPPA = 0.15f;

    public static final String RECALL_LATERAL_INHIBITION_HARD_KAPPA = "spector.memory.recall.lateral-inhibition.hard-kappa";
    public static final float DEFAULT_RECALL_LATERAL_INHIBITION_HARD_KAPPA = 0.40f;

    public static final String RECALL_LATERAL_INHIBITION_CONTRADICTION_HEURISTIC_ENABLED = "spector.memory.recall.lateral-inhibition.contradiction-heuristic-enabled";
    public static final boolean DEFAULT_RECALL_LATERAL_INHIBITION_CONTRADICTION_HEURISTIC_ENABLED = false;

    public static final String RECALL_LATERAL_INHIBITION_RIF_ENABLED = "spector.memory.recall.lateral-inhibition.rif-enabled";
    public static final boolean DEFAULT_RECALL_LATERAL_INHIBITION_RIF_ENABLED = false;

    public static final String RECALL_STRICTNESS_COEFFICIENT = "spector.recall.strictness-coefficient";
    public static final float DEFAULT_RECALL_STRICTNESS_COEFFICIENT = 1.0f;

    public static final String RECALL_VALENCE_ALIGNMENT_ENABLED = "spector.recall.valence-alignment.enabled";
    public static final boolean DEFAULT_RECALL_VALENCE_ALIGNMENT_ENABLED = false;

    public static final String RECALL_MODE = "spector.recall.mode";
    public static final String DEFAULT_RECALL_MODE = "LEARN";

    public static final String RECALL_MAX_REPLAY_EVENTS = "spector.recall.max-replay-events";
    public static final int DEFAULT_RECALL_MAX_REPLAY_EVENTS = 100_000;

    public static final String RECALL_ADAPTIVE_TEMPERATURE_ENABLED = "spector.recall.adaptive-temperature.enabled";
    public static final boolean DEFAULT_RECALL_ADAPTIVE_TEMPERATURE_ENABLED = false;

    public static final String RECALL_BASE_TEMPERATURE = "spector.recall.base-temperature";
    public static final float DEFAULT_RECALL_BASE_TEMPERATURE = 1.0f;

    public static final String RECALL_TEMPERATURE_SURPRISE_COEFFICIENT = "spector.recall.temperature-surprise-coefficient";
    public static final float DEFAULT_RECALL_TEMPERATURE_SURPRISE_COEFFICIENT = 0.15f;

    public static final String RECALL_MIN_TEMPERATURE = "spector.recall.min-temperature";
    public static final float DEFAULT_RECALL_MIN_TEMPERATURE = 0.1f;

    public static final String RECALL_MAX_TEMPERATURE = "spector.recall.max-temperature";
    public static final float DEFAULT_RECALL_MAX_TEMPERATURE = 5.0f;

    // Early Associative Prior (MR-06)
    public static final String RECALL_ASSOCIATIVE_PRIOR_ENABLED = "spector.memory.recall.associative-prior.enabled";
    public static final boolean DEFAULT_RECALL_ASSOCIATIVE_PRIOR_ENABLED = false;

    public static final String RECALL_ASSOCIATIVE_PRIOR_DELTA = "spector.memory.recall.associative-prior.delta";
    public static final float DEFAULT_RECALL_ASSOCIATIVE_PRIOR_DELTA = 0.15f;

    public static final String RECALL_ASSOCIATIVE_PRIOR_STDP_WEIGHT = "spector.memory.recall.associative-prior.stdp-weight";
    public static final float DEFAULT_RECALL_ASSOCIATIVE_PRIOR_STDP_WEIGHT = 0.7f;

    public static final String RECALL_ASSOCIATIVE_PRIOR_HUB_WEIGHT = "spector.memory.recall.associative-prior.hub-weight";
    public static final float DEFAULT_RECALL_ASSOCIATIVE_PRIOR_HUB_WEIGHT = 0.3f;

    public static final String RECALL_ASSOCIATIVE_PRIOR_CACHE_SIZE = "spector.memory.recall.associative-prior.cache-size";
    public static final int DEFAULT_RECALL_ASSOCIATIVE_PRIOR_CACHE_SIZE = 1024;

    // Graph Compaction & Telemetry (MR-08)
    public static final String GRAPH_COMPACTION_FRAGMENTATION_THRESHOLD = "spector.consolidation.graph-compaction.fragmentation-threshold";
    public static final float DEFAULT_GRAPH_COMPACTION_FRAGMENTATION_THRESHOLD = 0.25f;

    public static final String GRAPH_COMPACTION_LOAD_FACTOR_THRESHOLD = "spector.consolidation.graph-compaction.load-factor-threshold";
    public static final float DEFAULT_GRAPH_COMPACTION_LOAD_FACTOR_THRESHOLD = 0.70f;

    public static final String GRAPH_COMPACTION_MAX_PASSES_PER_CYCLE = "spector.consolidation.graph-compaction.max-passes-per-cycle";
    public static final int DEFAULT_GRAPH_COMPACTION_MAX_PASSES_PER_CYCLE = 2;

    public static final String GRAPH_COMPACTION_MODE = "spector.consolidation.graph-compaction.mode";
    public static final String DEFAULT_GRAPH_COMPACTION_MODE = "ADAPTIVE";

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

    // Query & Ranking Subsystem
    public static final String QUERY_DEFAULT_TOP_K = "spector.query.default-top-k";
    public static final int DEFAULT_QUERY_DEFAULT_TOP_K = 10;

    public static final String QUERY_RRF_K = "spector.query.rrf-k";
    public static final int DEFAULT_QUERY_RRF_K = 60;

    public static final String QUERY_RERANKER_MAX_CANDIDATES = "spector.query.reranker.max-candidates";
    public static final int DEFAULT_QUERY_RERANKER_MAX_CANDIDATES = 20;

    public static final String QUERY_RERANKER_CONNECT_TIMEOUT = "spector.query.reranker.connect-timeout";
    public static final Duration DEFAULT_QUERY_RERANKER_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    public static final String QUERY_RERANKER_REQUEST_TIMEOUT = "spector.query.reranker.request-timeout";
    public static final Duration DEFAULT_QUERY_RERANKER_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public static final String QUERY_RERANKER_DOC_SNIPPET_LENGTH = "spector.query.reranker.doc-snippet-length";
    public static final int DEFAULT_QUERY_RERANKER_DOC_SNIPPET_LENGTH = 500;

    public static final String QUERY_HYBRID_FANOUT_MULTIPLIER = "spector.query.hybrid.fanout-multiplier";
    public static final int DEFAULT_QUERY_HYBRID_FANOUT_MULTIPLIER = 2;

    public static final String QUERY_HYBRID_MIN_RETRIEVAL_K = "spector.query.hybrid.min-retrieval-k";
    public static final int DEFAULT_QUERY_HYBRID_MIN_RETRIEVAL_K = 50;

    // GPU Vector Search & Memory
    public static final String GPU_BATCH_MIN_WINDOW_MS = "spector.gpu.batch.min-window-ms";
    public static final long DEFAULT_GPU_BATCH_MIN_WINDOW_MS = 1L;

    public static final String GPU_BATCH_MAX_WINDOW_MS = "spector.gpu.batch.max-window-ms";
    public static final long DEFAULT_GPU_BATCH_MAX_WINDOW_MS = 100L;

    public static final String GPU_BATCH_DEFAULT_WINDOW = "spector.gpu.batch.default-window";
    public static final Duration DEFAULT_GPU_BATCH_DEFAULT_WINDOW = Duration.ofMillis(10);

    public static final String GPU_BATCH_DEFAULT_MAX_BATCH = "spector.gpu.batch.default-max-batch";
    public static final int DEFAULT_GPU_BATCH_DEFAULT_MAX_BATCH = 1024;

    public static final String GPU_MEMORY_MIN_BUDGET_BYTES = "spector.gpu.memory.min-budget-bytes";
    public static final long DEFAULT_GPU_MEMORY_MIN_BUDGET_BYTES = 256L * 1024 * 1024; // 256MB

    public static final String GPU_LEAK_DETECTOR_THRESHOLD = "spector.gpu.leak-detector.threshold";
    public static final Duration DEFAULT_GPU_LEAK_DETECTOR_THRESHOLD = Duration.ofSeconds(300);

    // HNSW
    public static final String HNSW_M = "spector.hnsw.m";
    public static final int DEFAULT_HNSW_M = 16;

    public static final String HNSW_EF_CONSTRUCTION = "spector.hnsw.ef-construction";
    public static final int DEFAULT_HNSW_EF_CONSTRUCTION = 200;

    public static final String HNSW_EF_SEARCH = "spector.hnsw.ef-search";
    public static final int DEFAULT_HNSW_EF_SEARCH = 50;

    public static final String INDEX_HNSW_PARALLEL_THRESHOLD = "spector.index.hnsw.parallel-threshold";
    public static final int DEFAULT_INDEX_HNSW_PARALLEL_THRESHOLD = 10_000;

    public static final String INDEX_HNSW_CALIBRATION_SAMPLE_SIZE = "spector.index.hnsw.calibration-sample-size";
    public static final int DEFAULT_INDEX_HNSW_CALIBRATION_SAMPLE_SIZE = 10_000;

    // IVF & PQ
    public static final String IVF_NLIST = "spector.ivf.nlist";
    public static final int DEFAULT_IVF_NLIST = 0;

    public static final String IVF_NPROBE = "spector.ivf.nprobe";
    public static final int DEFAULT_IVF_NPROBE = 0;

    public static final String IVF_PQ_SUBSPACES = "spector.ivf.pq-subspaces";
    public static final int DEFAULT_IVF_PQ_SUBSPACES = 0;

    public static final String INDEX_IVF_KMEANS_MAX_ITERS = "spector.index.ivf.kmeans-max-iters";
    public static final int DEFAULT_INDEX_IVF_KMEANS_MAX_ITERS = 25;

    public static final String INDEX_IVF_KMEANS_SEED = "spector.index.ivf.kmeans-seed";
    public static final long DEFAULT_INDEX_IVF_KMEANS_SEED = 42L;

    public static final String INDEX_PQ_KSUB = "spector.index.pq.ksub";
    public static final int DEFAULT_INDEX_PQ_KSUB = 256;

    // Text & Keyword Indexing (BM25, SPLADE, ColBERT)
    public static final String INDEX_BM25_K1 = "spector.index.bm25.k1";
    public static final float DEFAULT_INDEX_BM25_K1 = 1.2f;

    public static final String INDEX_BM25_B = "spector.index.bm25.b";
    public static final float DEFAULT_INDEX_BM25_B = 0.75f;

    public static final String INDEX_BM25_PARALLEL_THRESHOLD = "spector.index.bm25.parallel-threshold";
    public static final int DEFAULT_INDEX_BM25_PARALLEL_THRESHOLD = 20_000;

    public static final String INDEX_SPLADE_PARALLEL_THRESHOLD = "spector.index.splade.parallel-threshold";
    public static final int DEFAULT_INDEX_SPLADE_PARALLEL_THRESHOLD = 15_000;

    public static final String INDEX_COLBERT_CACHE_CAPACITY = "spector.index.colbert.cache-capacity";
    public static final int DEFAULT_INDEX_COLBERT_CACHE_CAPACITY = 1024;

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

    // Multimodal & Sensory Media
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

    public static final String MULTIMODAL_VIDEO_KEYFRAME_INTERVAL = "spector.multimodal.video.keyframe-interval-seconds";
    public static final int DEFAULT_MULTIMODAL_VIDEO_KEYFRAME_INTERVAL = 10;
    public static final String MULTIMODAL_VIDEO_MAX_KEYFRAMES = "spector.multimodal.video.max-keyframes";
    public static final int DEFAULT_MULTIMODAL_VIDEO_MAX_KEYFRAMES = 30;

    public static final String MULTIMODAL_AUDIO_MAX_FILE_SIZE = "spector.multimodal.audio.max-file-size";
    public static final long DEFAULT_MULTIMODAL_AUDIO_MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB
    public static final String MULTIMODAL_VISION_MAX_IMAGE_SIZE = "spector.multimodal.vision.max-image-size";
    public static final long DEFAULT_MULTIMODAL_VISION_MAX_IMAGE_SIZE = 20L * 1024 * 1024; // 20MB
    public static final String MULTIMODAL_TIKA_MAX_CONTENT_LENGTH = "spector.multimodal.tika.max-content-length";
    public static final int DEFAULT_MULTIMODAL_TIKA_MAX_CONTENT_LENGTH = 100 * 1024 * 1024; // 100MB

    // Evaluation & Test Judge
    public static final String TEST_JUDGE_MODEL = "spector.test.judge.model";
    public static final String DEFAULT_TEST_JUDGE_MODEL = "llama3.1";
    public static final String TEST_JUDGE_BASE_URL = "spector.test.judge.base-url";
    public static final String DEFAULT_TEST_JUDGE_BASE_URL = "http://localhost:11434";
    public static final String TEST_JUDGE_CONFIDENCE = "spector.test.judge.confidence";
    public static final float DEFAULT_TEST_JUDGE_CONFIDENCE = 0.6f;

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
    public static final List<String> DEFAULT_AUTH_PUBLIC_PATHS = List.of("/actuator/health", "/api/docs");

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

    // Spacetime Vector Search (ADR-0030 v1)
    public static final String RECALL_SPACETIME_ENABLED = "spector.recall.spacetime.enabled";
    public static final boolean DEFAULT_RECALL_SPACETIME_ENABLED = false;

    public static final String RECALL_SPACETIME_HARMONIC_WEIGHT = "spector.recall.spacetime.harmonic-weight";
    public static final float DEFAULT_RECALL_SPACETIME_HARMONIC_WEIGHT = 0.15f;

    public static final String RECALL_ALLOW_FUTURE = "spector.recall.allow-future";
    public static final boolean DEFAULT_RECALL_ALLOW_FUTURE = false;

    public static final String RECALL_FLASHBULB_MASS_FLOOR = "spector.recall.flashbulb.mass-floor";
    public static final float DEFAULT_RECALL_FLASHBULB_MASS_FLOOR = 0.30f;

    // Spacetime Simulation (ADR-0031)
    public static final String RECALL_SPACETIME_SIMULATION_ENABLED = "spector.recall.spacetime.simulation.enabled";
    public static final boolean DEFAULT_RECALL_SPACETIME_SIMULATION_ENABLED = true;

    public static final String RECALL_SPACETIME_WANDER_RHO_PLUS = "spector.recall.spacetime.wander.rho-plus";
    public static final float DEFAULT_RECALL_SPACETIME_WANDER_RHO_PLUS = 0.35f;

    public static final String RECALL_SPACETIME_WANDER_RHO_MINUS = "spector.recall.spacetime.wander.rho-minus";
    public static final float DEFAULT_RECALL_SPACETIME_WANDER_RHO_MINUS = 0.35f;

    public static final String RECALL_SPACETIME_WANDER_LAMBDA = "spector.recall.spacetime.wander.lambda";
    public static final float DEFAULT_RECALL_SPACETIME_WANDER_LAMBDA = 0.30f;

    public static final String RECALL_SPACETIME_DREAM_REM_LAMBDA = "spector.recall.spacetime.dream.rem.lambda";
    public static final float DEFAULT_RECALL_SPACETIME_DREAM_REM_LAMBDA = 0.30f;

    public static final String RECALL_SPACETIME_DREAM_NREM_LAMBDA = "spector.recall.spacetime.dream.nrem.lambda";
    public static final float DEFAULT_RECALL_SPACETIME_DREAM_NREM_LAMBDA = 1.00f;

    // Server
    public static final String SERVER_PORT = "spector.server.port";
    public static final int DEFAULT_SERVER_PORT = 7070;

    public static final String SERVER_DATA_DIR = "spector.server.data-dir";
    public static final String DEFAULT_SERVER_DATA_DIR = "./spector-data";
}

