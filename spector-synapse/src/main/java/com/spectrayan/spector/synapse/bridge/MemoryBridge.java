/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.bridge;

import com.spectrayan.spector.embed.EmbeddingConfig;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Bridge between the Synapse REST API and the Spector cognitive memory engine.
 *
 * <p>Constructs a {@link DefaultSpectorMemory} instance at startup using
 * configuration from {@link SynapseProperties}. The memory engine uses
 * SIMD-accelerated vector search with Panama off-heap memory.</p>
 */
@Service
public class MemoryBridge {

    private static final Logger log = LoggerFactory.getLogger(MemoryBridge.class);

    private final SynapseProperties props;
    private volatile SpectorMemory memory;
    private volatile boolean available;

    public MemoryBridge(SynapseProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        try {
            EmbeddingConfig embedConfig = EmbeddingConfig.ollama(props.ollama().embedModel())
                    .withBaseUrl(props.ollama().baseUrl());
            EmbeddingProvider embedder = new OllamaEmbeddingProvider(embedConfig);

            String dataDir = System.getenv("SPECTOR_DATA_DIR");
            if (dataDir == null || dataDir.isBlank()) {
                dataDir = System.getProperty("java.io.tmpdir") + "/spector-synapse";
            }
            Path dataPath = Path.of(dataDir, "cognitive");
            int dims = props.memory().dimensions() > 0 ? props.memory().dimensions() : 768;

            memory = DefaultSpectorMemory.builder()
                    .dimensions(dims)
                    .embeddingProvider(embedder)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .persistence(dataPath)
                    .semanticCapacity(10_000)
                    .hebbianGraphCapacity(10_000)
                    .temporalChainCapacity(10_000)
                    .build();

            available = true;
            log.info("[MemoryBridge] SpectorMemory initialized — dims={}, path={}",
                    props.memory().dimensions(), dataPath);
        } catch (Exception e) {
            available = false;
            log.warn("[MemoryBridge] SpectorMemory initialization failed — running in stub mode: {}",
                    e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        if (memory != null) {
            try {
                memory.close();
                log.info("[MemoryBridge] SpectorMemory closed");
            } catch (Exception e) {
                log.warn("[MemoryBridge] Error closing memory: {}", e.getMessage());
            }
        }
    }

    /**
     * Store a memory via the cognitive engine.
     */
    public StoreResponse store(StoreRequest request) {
        if (!available) {
            return new StoreResponse("stub-" + System.nanoTime(), request.text(),
                    "SEMANTIC", 0.0, "Memory stored (stub mode — engine not available)");
        }

        String[] tags = request.tags() != null
                ? request.tags().toArray(String[]::new)
                : new String[0];

        try {
            CompletableFuture<String> future = memory.remember(
                    request.text(), MemoryType.SEMANTIC, MemorySource.USER_STATED, tags);
            String id = future.join();
            log.info("[MemoryBridge] Stored memory id={}, tags={}", id, Arrays.toString(tags));
            return new StoreResponse(id, request.text(), "SEMANTIC", 1.0,
                    "Memory stored in cognitive engine");
        } catch (Exception e) {
            log.error("[MemoryBridge] Store failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Memory store failed: " + e.getMessage(), e);
        }
    }

    /**
     * Recall memories via the fused cognitive scoring pipeline.
     */
    public List<RecallResult> recall(RecallRequest request) {
        if (!available) {
            return List.of();
        }

        try {
            List<CognitiveResult> results = memory.recall(request.query());
            int limit = request.topK() > 0 ? request.topK() : 10;

            return results.stream()
                    .limit(limit)
                    .map(r -> new RecallResult(
                            r.id(),
                            r.text(),
                            r.memoryType().name(),
                            (double) r.score(),
                            r.memoryType().name(),
                            String.format("%.1f days", r.ageDays()),
                            Arrays.asList(r.synapticTags())
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("[MemoryBridge] Recall failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Forget (tombstone) a memory by ID.
     */
    public void forget(String id) {
        if (!available) return;
        try {
            memory.forget(id);
            log.info("[MemoryBridge] Forgot memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Forget failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Reinforce a memory via dopamine feedback.
     */
    public void reinforce(String id, float valence) {
        if (!available) return;
        try {
            byte byteValence = (byte) Math.max(-127, Math.min(127, (int) (valence * 127)));
            memory.reinforce(id, byteValence);
            log.info("[MemoryBridge] Reinforced memory id={} valence={}", id, byteValence);
        } catch (Exception e) {
            log.error("[MemoryBridge] Reinforce failed: {}", e.getMessage(), e);
        }
    }

    /** Check if the memory engine is available. */
    public boolean isAvailable() { return available; }

    /** Get the underlying SpectorMemory instance. */
    public SpectorMemory engine() { return memory; }
}
