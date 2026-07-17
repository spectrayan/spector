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
package com.spectrayan.spector.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorMode;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.PersistenceMode;
import com.spectrayan.spector.config.HnswParams;
import com.spectrayan.spector.index.HnswIndex;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;
import com.spectrayan.spector.embed.SparseEncodingProvider;
import com.spectrayan.spector.embed.TokenEmbeddingProvider;
import com.spectrayan.spector.embed.ollama.OllamaSparseEncodingProvider;
import com.spectrayan.spector.embed.ollama.OllamaTokenEmbeddingProvider;
import com.spectrayan.spector.config.SpectorConfig;
import com.spectrayan.spector.engine.DefaultSpectorEngine;
import com.spectrayan.spector.commons.TextChunker;
import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pipeline.LlmTagExtractor;
import com.spectrayan.spector.memory.pipeline.TagExtractor;

/**
 * Composition root for a Spector instance.
 *
 * <p><strong>This is the single entry point for all Spector consumers.</strong>
 * It creates, wires, and exposes the subsystem services. No business logic
 * lives here — each service owns its domain.</p>
 *
 * <h3>Services</h3>
 * <ul>
 *   <li>{@link #search()} — mode-aware search (engine or memory)</li>
 *   <li>{@link #ingestion()} — mode-aware ingestion (text, file, directory)</li>
 *   <li>{@link #memoryHandler()} — cognitive memory operations (remember, recall, suppress, etc.)</li>
 * </ul>
 *
 * <h3>Direct Subsystem Access</h3>
 * <ul>
 *   <li>{@link #engine()} — vector search engine (always available)</li>
 *   <li>{@link #memory()} — cognitive memory (null if not enabled)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   try (SpectorRuntime runtime = SpectorRuntime.from(props, embedder)) {
 *       runtime.ingestion().ingest("doc1", "some text");
 *       runtime.ingestion().ingest(Path.of("/docs"), "**\/*.md", 800, 100, ".git");
 *       var results = runtime.search().query("something", 10);
 *   }
 * }</pre>
 */
public final class SpectorRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SpectorRuntime.class);

    private final SpectorEngine engine;
    private final SpectorMemory memory;  // nullable
    private final SpectorProperties properties;
    private final SpectorMode mode;
    private final TextChunker sharedChunker;  // nullable — shared by memory + ingestion pipeline

    // Shared provider singletons (nullable) — reused across all tenants in Enterprise
    private final SparseEncodingProvider sparseEncodingProvider;
    private final TokenEmbeddingProvider tokenEmbeddingProvider;

    // Lazily created services — double-checked locking for thread safety.
    // The volatile + ReentrantLock pattern ensures exactly-once initialization
    // even under concurrent access from virtual threads (ADR-005: no synchronized).
    private volatile SearchHandler searchService;
    private volatile IngestionHandler ingestionService;
    private volatile MemoryHandler memoryService;
    private final java.util.concurrent.locks.ReentrantLock serviceLock = new java.util.concurrent.locks.ReentrantLock();

    private SpectorRuntime(SpectorEngine engine, SpectorMemory memory,
                           SpectorProperties properties, SpectorMode mode,
                           TextChunker sharedChunker,
                           SparseEncodingProvider sparseEncodingProvider,
                           TokenEmbeddingProvider tokenEmbeddingProvider) {
        this.engine = engine;
        this.memory = memory;
        this.properties = properties;
        this.mode = mode;
        this.sharedChunker = sharedChunker;
        this.sparseEncodingProvider = sparseEncodingProvider;
        this.tokenEmbeddingProvider = tokenEmbeddingProvider;
    }

    // ─────────────── Factory ───────────────

    /**
     * Creates a runtime from configuration and embedding provider.
     *
     * @param props    hierarchical configuration
     * @param embedder embedding provider (shared by engine and memory)
     * @return initialized runtime (caller must close)
     */
    public static SpectorRuntime from(SpectorProperties props, EmbeddingProvider embedder) {
        return from(props, embedder, null);
    }

    /**
     * Creates a runtime from configuration, embedding provider, and optional
     * text generation provider for LLM-powered tag extraction.
     *
     * <p>When {@code spector.memory.tag-extractor=llm} in the config, the
     * provided {@code textGenProvider} is used to create an {@link LlmTagExtractor}
     * that extracts semantic tags during ingestion and {@code remember()} calls.
     * If {@code textGenProvider} is null and LLM mode is requested, falls back
     * to content-based extraction.</p>
     *
     * @param props           hierarchical configuration
     * @param embedder        embedding provider (shared by engine and memory)
     * @param textGenProvider text generation provider for LLM tag extraction (nullable)
     * @return initialized runtime (caller must close)
     */
    public static SpectorRuntime from(SpectorProperties props, EmbeddingProvider embedder,
                                      TextGenerationProvider textGenProvider) {
        SpectorMode mode = SpectorConfigFactory.mode(props);

        // ── Read memory config early (needed to configure engine in MEMORY mode) ──
        var memoryConfig = SpectorConfigFactory.memoryDefaults(props);
        boolean memoryEnabled = memoryConfig.enabled() || mode.memoryEnabled();

        // ── Engine ──
        SpectorConfig engineConfig = SpectorConfig.from(props);
        // Engine manages its own index/store for search workloads (document
        // indexing, RAG). Memory is fully decoupled — uses dir-level partitions
        // with its own .mem files. The two no longer share HNSW or VectorStore.
        if (mode.memoryEnabled() && memoryEnabled) {
            java.nio.file.Path indexDir = memoryConfig.persistencePath()
                    .resolveSibling("index");
            engineConfig = engineConfig
                    .withPersistence(PersistenceMode.DISK, indexDir)
                    .withCapacity(memoryConfig.capacity())
                    .withNodesPerShard(Math.min(5_000, memoryConfig.capacity()));
            log.info("[Runtime] Engine: DISK persistence at {}, capacity={}",
                     indexDir, memoryConfig.capacity());
        }
        SpectorEngine engine = new DefaultSpectorEngine(engineConfig, embedder);
        log.info("[Runtime] Engine: dims={}, index={}, persistence={}, dataDir={}, mode={}",
                engineConfig.dimensions(), engineConfig.indexType(),
                engineConfig.persistenceMode(), engineConfig.dataDirectory(),
                mode);

        // ── Memory (opt-in or auto-enabled in MEMORY mode) ──
        SpectorMemory memory = null;

        // ── Shared TextChunker — used by both memory.remember() and IngestionPipeline ──
        var ingestionConfig = SpectorConfigFactory.ingestionDefaults(props);
        var textChunker = new TextChunker(ingestionConfig.chunkSize(), ingestionConfig.chunkOverlap());
        log.info("[Runtime] TextChunker: chunkSize={}, overlap={}",
                ingestionConfig.chunkSize(), ingestionConfig.chunkOverlap());

        // ── Shared SPLADE + ColBERT provider singletons (set if memory is enabled) ──
        SparseEncodingProvider sharedSpladeProvider = null;
        TokenEmbeddingProvider sharedColbertProvider = null;

        if (memoryEnabled) {
            var memoryBuilder = DefaultSpectorMemory.builder()
                    .dimensions(engineConfig.dimensions())
                    .embeddingProvider(embedder)
                    .persistenceMode(MemoryPersistenceMode.valueOf(memoryConfig.persistenceMode()))
                    .persistence(memoryConfig.persistencePath())
                    .semanticCapacity(memoryConfig.capacity())  // from spector.memory.capacity config
                    .nodesPerPartition(memoryConfig.nodesPerPartition())
                    .hebbianGraphCapacity(memoryConfig.capacity())
                    .temporalChainCapacity(memoryConfig.capacity())
                    .chunker(textChunker);

            // ── Entity extraction (LLM when available, otherwise disabled) ──
            if (textGenProvider != null) {
                memoryBuilder.entityExtractionMode(com.spectrayan.spector.memory.graph.EntityExtractionMode.LLM)
                        .textGenerationProvider(textGenProvider);
                // Pass configured LLM generation options
                var llmConfig = memoryConfig.llm();
                if (llmConfig != null) {
                    var genOpts = com.spectrayan.spector.embed.GenerationOptions.builder()
                            .temperature(llmConfig.temperature())
                            .maxTokens(llmConfig.maxTokens())
                            .topP(llmConfig.topP())
                            .build();
                    memoryBuilder.llmGenerationOptions(genOpts);
                    log.info("[Runtime] LLM options: temperature={}, maxTokens={}, topP={}",
                            llmConfig.temperature(), llmConfig.maxTokens(), llmConfig.topP());
                }
                log.info("[Runtime] Entity extraction: LLM (model={})", textGenProvider.modelName());
            } else {
                memoryBuilder.entityExtractionMode(com.spectrayan.spector.memory.graph.EntityExtractionMode.NONE);
                log.info("[Runtime] Entity extraction: NONE (no TextGenerationProvider)");
            }

            // ── SPLADE + ColBERT providers (shared singletons) ──

            if (memoryConfig.spladeEnabled()) {
                sharedSpladeProvider = new OllamaSparseEncodingProvider(embedder);
                memoryBuilder.sparseEncodingProvider(sharedSpladeProvider);
                log.info("[Runtime] SPLADE provider: {}", sharedSpladeProvider.modelName());
            } else {
                log.info("[Runtime] SPLADE sparse indexing is disabled via configuration");
            }
            if (memoryConfig.colbertEnabled()) {
                sharedColbertProvider = new OllamaTokenEmbeddingProvider(embedder);
                memoryBuilder.tokenEmbeddingProvider(sharedColbertProvider);
                log.info("[Runtime] ColBERT provider: {}", sharedColbertProvider.modelName());
            } else {
                log.info("[Runtime] ColBERT reranking is disabled via configuration");
            }

            // ── Create HNSW index for memory's semantic recall ──
            // Without this, SemanticRecallStrategy falls back to header-only scoring
            // which cannot compute vector similarity (VECTOR_ONLY search is broken).
            var hnswConfig = SpectorConfigFactory.hnswDefaults(props);
            var hnswParams = new HnswParams(hnswConfig.m(), hnswConfig.efConstruction(), hnswConfig.efSearch());
            var memoryHnsw = new HnswIndex(
                    engineConfig.dimensions(), memoryConfig.capacity(),
                    SimilarityFunction.COSINE, hnswParams);
            memoryBuilder.semanticIndex(memoryHnsw);
            log.info("[Runtime] Memory HNSW: dims={}, capacity={}, M={}, efC={}, efS={}",
                    engineConfig.dimensions(), memoryConfig.capacity(),
                    hnswConfig.m(), hnswConfig.efConstruction(), hnswConfig.efSearch());

            // Memory manages its own vector storage via dir-level partitions.
            // Engine's vectorStore and HNSW index are NOT shared with memory.

            // ── Tag extractor (content, llm, or none) ──
            String tagMode = memoryConfig.tagExtractor().toLowerCase();
            switch (tagMode) {
                case "llm" -> {
                    if (textGenProvider != null) {
                        memoryBuilder.tagExtractor(new LlmTagExtractor(textGenProvider));
                        log.info("[Runtime] Tag extractor: LLM (model={})", textGenProvider.modelName());
                    } else {
                        log.warn("[Runtime] tag-extractor=llm but no TextGenerationProvider supplied, falling back to content");
                    }
                }
                case "none" -> {
                    memoryBuilder.tagExtractor(TagExtractor.NONE);
                    log.info("[Runtime] Tag extractor: NONE (Bloom filter disabled)");
                }
                default -> log.info("[Runtime] Tag extractor: content (keyword-based)");
            }

            // ── Embedding batch size from config ──
            var embedConfigDefaults = SpectorConfigFactory.embeddingDefaults(props);
            memoryBuilder.embedBatchSize(embedConfigDefaults.batchSize());
            log.info("[Runtime] Embedding batch size: {}", embedConfigDefaults.batchSize());

            memory = memoryBuilder.build();
            log.info("[Runtime] Memory: persistence={}, path={}",
                    memoryConfig.persistenceMode(), memoryConfig.persistencePath());
        }

        return new SpectorRuntime(engine, memory, props, mode, textChunker,
                sharedSpladeProvider, sharedColbertProvider);
    }

    /**
     * Creates a runtime with engine only (no memory).
     */
    public static SpectorRuntime engineOnly(SpectorEngine engine, SpectorProperties props) {
        return new SpectorRuntime(engine, null, props, SpectorMode.SEARCH, null, null, null);
    }

    // ─────────────── Service Accessors ───────────────

    /** Returns the mode-aware search service. */
    public SearchHandler search() {
        SearchHandler svc = searchService; // volatile read
        if (svc == null) {
            serviceLock.lock();
            try {
                svc = searchService;
                if (svc == null) {
                    svc = new SearchHandler(engine, memory, mode);
                    searchService = svc; // volatile write
                }
            } finally {
                serviceLock.unlock();
            }
        }
        return svc;
    }

    /** Returns the mode-aware ingestion service. */
    public IngestionHandler ingestion() {
        IngestionHandler svc = ingestionService; // volatile read
        if (svc == null) {
            serviceLock.lock();
            try {
                svc = ingestionService;
                if (svc == null) {
                    var ingestionConfig = SpectorConfigFactory.ingestionDefaults(properties);

                    // Select target based on mode
                    com.spectrayan.spector.ingestion.IngestionTarget target =
                            (mode.memoryEnabled() && memory != null)
                            ? memory.target()
                            : engine.target();

                    // Reuse the shared chunker from runtime construction;
                    // fall back to config-derived chunker for engineOnly() runtimes.
                    var chunker = sharedChunker != null
                            ? sharedChunker
                            : new com.spectrayan.spector.commons.TextChunker(
                                    ingestionConfig.chunkSize(), ingestionConfig.chunkOverlap());

                    // Build unified pipeline from config
                    var embedDefaults = SpectorConfigFactory.embeddingDefaults(properties);
                    var embedConfig = new com.spectrayan.spector.embed.EmbedConfig(
                            embedDefaults.batchSize(), embedDefaults.maxRetries());

                    var pipeline = com.spectrayan.spector.ingestion.IngestionPipeline.builder()
                            .target(target)
                            .embeddingProvider(engine.embeddingProvider())
                            .chunking(chunker)
                            .chunkThreshold(ingestionConfig.chunkSize())
                            .embedConfig(embedConfig)
                            .build();

                    svc = new IngestionHandler(pipeline, engine, memory, mode);
                    ingestionService = svc; // volatile write
                }
            } finally {
                serviceLock.unlock();
            }
        }
        return svc;
    }

    /**
     * Returns the cognitive memory handler, or empty if memory is not enabled.
     *
     * <p>Unlike {@link #search()} and {@link #ingestion()} which are mode-aware
     * (routing to engine or memory based on {@link SpectorMode}), this handler
     * always operates on the cognitive memory subsystem directly. See
     * {@link MemoryHandler} Javadoc for the design rationale.</p>
     */
    public java.util.Optional<MemoryHandler> memoryHandler() {
        if (memory == null) {
            return java.util.Optional.empty();
        }
        MemoryHandler svc = memoryService; // volatile read
        if (svc == null) {
            serviceLock.lock();
            try {
                svc = memoryService;
                if (svc == null) {
                    svc = new MemoryHandler(memory);
                    memoryService = svc; // volatile write
                }
            } finally {
                serviceLock.unlock();
            }
        }
        return java.util.Optional.of(svc);
    }

    // ─────────────── Direct Subsystem Access ───────────────

    /** Returns the search engine (never null). */
    public SpectorEngine engine() { return engine; }

    /** Returns the cognitive memory, or empty if not enabled. */
    public java.util.Optional<SpectorMemory> memory() { return java.util.Optional.ofNullable(memory); }

    /** Returns {@code true} if cognitive memory is available. */
    public boolean hasMemory() { return memory != null; }

    /** Returns the configuration properties. */
    public SpectorProperties properties() { return properties; }

    /** Returns the global operating mode. */
    public SpectorMode mode() { return mode; }

    /**
     * Returns the shared SPLADE sparse encoding provider, or empty if disabled.
     *
     * <p>Enterprise callers should reuse this singleton instead of creating
     * per-tenant instances — the provider is stateless and thread-safe.</p>
     */
    public java.util.Optional<SparseEncodingProvider> sparseEncodingProvider() {
        return java.util.Optional.ofNullable(sparseEncodingProvider);
    }

    /**
     * Returns the shared ColBERT token embedding provider, or empty if disabled.
     *
     * <p>Enterprise callers should reuse this singleton instead of creating
     * per-tenant instances — the provider is stateless and thread-safe.</p>
     */
    public java.util.Optional<TokenEmbeddingProvider> tokenEmbeddingProvider() {
        return java.util.Optional.ofNullable(tokenEmbeddingProvider);
    }

    // ─────────────── Lifecycle ───────────────

    @Override
    public void close() {
        Exception firstException = null;
        try {
            engine.close();
        } catch (Exception e) {
            log.error("[Runtime] Error closing engine: {}", e.getMessage(), e);
            firstException = e;
        }
        if (memory != null) {
            try {
                memory.close();
            } catch (Exception e) {
                log.error("[Runtime] Error closing memory: {}", e.getMessage(), e);
                if (firstException != null) {
                    firstException.addSuppressed(e);
                } else {
                    firstException = e;
                }
            }
        }
        log.info("[Runtime] Shutdown complete (mode={})", mode);
        if (firstException != null) {
            throw new RuntimeException("[Runtime] Shutdown failed", firstException);
        }
    }

}
