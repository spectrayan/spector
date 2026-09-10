/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.runtime;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.api.NamespaceKernel;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.pathway.decide.DecidePathway;
import com.spectrayan.spector.memory.pathway.dream.DreamPathway;
import com.spectrayan.spector.memory.pathway.express.ExpressPathway;
import com.spectrayan.spector.memory.pathway.recall.RecallPathway;
import com.spectrayan.spector.memory.pathway.reflect.ReflectPathway;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.wander.WanderPathway;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.provider.generation.LlmProvider;

/**
 * Process-wide composition root and runtime orchestrator for Spector Cognitive Memory (ADR-0029 §8.1).
 *
 * <p>Owns the shared, stateless or process-level pathway engines (Recall, Remember, Reflect, Express,
 * Dream, Decide, Wander), the process embedding pipeline, and the hot map of {@code namespaceId → NamespaceKernel}.</p>
 *
 * <p>Namespaces attach to this runtime via {@link #attach(String, Consumer)} to receive a
 * {@link SpectorMemory} instance wired to process-wide shared pathway engines without duplicating
 * engine metadata or leaking tenant context.</p>
 *
 * @since 1.2.0
 */
public class SpectorRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SpectorRuntime.class);

    private final SpectorProperties properties;
    private final EmbeddingProvider embeddingProvider;
    private final ParallelEmbeddingPipeline parallelEmbeddingPipeline;
    private final LlmProvider llmProvider;
    private final ScalarQuantizer quantizer;

    // ── Process-Wide Shared Pathway Engines (ADR-0029 §8.1, Task 10.4) ──
    private final Object engineLock = new Object();
    private volatile RememberPathway rememberPathway;
    private volatile RecallPathway recallPathway;
    private volatile ReflectPathway reflectPathway;
    private final ExpressPathway expressPathway;
    private volatile DreamPathway dreamPathway;
    private final DecidePathway decidePathway;
    private volatile WanderPathway wanderPathway;

    // ── Hot Map of Bound Kernels ─────────────────────────────────
    private final ConcurrentHashMap<String, NamespaceKernel> hotKernels = new ConcurrentHashMap<>();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    protected SpectorRuntime(Builder builder) {
        this.properties = builder.properties;
        this.embeddingProvider = builder.embeddingProvider;
        this.parallelEmbeddingPipeline = builder.parallelEmbeddingPipeline != null
                ? builder.parallelEmbeddingPipeline
                : (this.embeddingProvider != null
                        ? new ParallelEmbeddingPipeline(this.embeddingProvider, false)
                        : null);
        this.llmProvider = builder.llmProvider;
        this.quantizer = builder.quantizer;

        this.rememberPathway = builder.rememberPathway;
        this.recallPathway = builder.recallPathway;
        this.reflectPathway = builder.reflectPathway;
        this.expressPathway = builder.expressPathway != null
                ? builder.expressPathway
                : ExpressPathway.builder().build();
        this.dreamPathway = builder.dreamPathway;
        this.decidePathway = builder.decidePathway != null
                ? builder.decidePathway
                : DecidePathway.builder().build();
        this.wanderPathway = builder.wanderPathway;
    }

    /**
     * Attaches a namespace to this runtime and returns an active {@link SpectorMemory} instance.
     *
     * @param namespaceId the unique namespace identifier
     * @param customizer  optional callback to customize the memory builder (persistence, cache, etc.)
     * @return the assembled SpectorMemory instance wired to shared pathway engines
     */
    public SpectorMemory attach(String namespaceId, Consumer<SpectorMemoryBuilder> customizer) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        checkNotClosed();

        SpectorMemoryBuilder builder = SpectorMemoryBuilder.createEmpty();
        if (properties != null) {
            builder.fromProperties(properties);
        }
        builder.namespaceId(namespaceId);
        if (embeddingProvider != null) {
            builder.embeddingProvider(embeddingProvider);
        }
        if (parallelEmbeddingPipeline != null) {
            builder.parallelEmbeddingPipeline(parallelEmbeddingPipeline);
        }
        if (llmProvider != null) {
            builder.llmProvider(llmProvider);
        }
        if (quantizer != null) {
            builder.quantizer(quantizer);
        }

        // Wire shared engines
        synchronized (engineLock) {
            if (rememberPathway != null) {
                builder.rememberPathway(rememberPathway);
            }
            if (recallPathway != null) {
                builder.recallPathway(recallPathway);
            }
            if (reflectPathway != null) {
                builder.reflectPathway(reflectPathway);
            }
            if (dreamPathway != null) {
                builder.dreamPathway(dreamPathway);
            }
            if (wanderPathway != null) {
                builder.wanderPathway(wanderPathway);
            }
        }
        builder.expressPathway(expressPathway);
        builder.decidePathway(decidePathway);
        builder.sharedPathways(true);

        if (customizer != null) {
            customizer.accept(builder);
        }

        SpectorMemory memory = builder.build();

        // Capture shared recall pathway from the first attached instance if not explicitly passed to builder
        if (recallPathway == null) {
            synchronized (engineLock) {
                if (memory instanceof DefaultSpectorMemory dsm) {
                    if (recallPathway == null) {
                        recallPathway = dsm.recallPathway();
                    }
                    if (reflectPathway == null) {
                        reflectPathway = dsm.reflectPathway();
                    }
                    if (dreamPathway == null) {
                        dreamPathway = dsm.dreamPathway();
                    }
                    if (wanderPathway == null) {
                        wanderPathway = dsm.wanderPathway();
                    }
                }
            }
        }

        return memory;
    }

    /**
     * Attaches a namespace to this runtime with default configuration.
     *
     * @param namespaceId the unique namespace identifier
     * @return the assembled SpectorMemory instance
     */
    public SpectorMemory attach(String namespaceId) {
        return attach(namespaceId, null);
    }

    /**
     * Registers an open {@link NamespaceKernel} into the hot kernel map.
     *
     * @param namespaceId the namespace identifier
     * @param kernel      the kernel instance
     */
    public void bindKernel(String namespaceId, NamespaceKernel kernel) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(kernel, "kernel must not be null");
        hotKernels.put(namespaceId, kernel);
    }

    /**
     * Unregisters a kernel from the hot kernel map.
     *
     * @param namespaceId the namespace identifier
     * @return the removed kernel, or null if not bound
     */
    public NamespaceKernel unbindKernel(String namespaceId) {
        if (namespaceId == null) return null;
        return hotKernels.remove(namespaceId);
    }

    /**
     * Gets a bound kernel from the hot map.
     *
     * @param namespaceId the namespace identifier
     * @return the kernel instance or null
     */
    public NamespaceKernel getKernel(String namespaceId) {
        if (namespaceId == null) return null;
        return hotKernels.get(namespaceId);
    }

    /**
     * Checks if a kernel is currently bound and hot.
     *
     * @param namespaceId the namespace identifier
     * @return true if currently bound
     */
    public boolean isKernelBound(String namespaceId) {
        if (namespaceId == null) return false;
        return hotKernels.containsKey(namespaceId);
    }

    /**
     * @return the count of currently hot bound kernels
     */
    public int hotKernelCount() {
        return hotKernels.size();
    }

    /**
     * @return an unmodifiable view of hot kernels
     */
    public Map<String, NamespaceKernel> hotKernels() {
        return Collections.unmodifiableMap(hotKernels);
    }

    // ── Getters for Shared Engines and Resources ────────────────

    public SpectorProperties properties() { return properties; }
    public EmbeddingProvider embeddingProvider() { return embeddingProvider; }
    public ParallelEmbeddingPipeline parallelEmbeddingPipeline() { return parallelEmbeddingPipeline; }
    public LlmProvider llmProvider() { return llmProvider; }
    public ScalarQuantizer quantizer() { return quantizer; }
    public RememberPathway rememberPathway() { return rememberPathway; }
    public RecallPathway recallPathway() { return recallPathway; }
    public ReflectPathway reflectPathway() { return reflectPathway; }
    public ExpressPathway expressPathway() { return expressPathway; }
    public DreamPathway dreamPathway() { return dreamPathway; }
    public DecidePathway decidePathway() { return decidePathway; }
    public WanderPathway wanderPathway() { return wanderPathway; }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        log.info("[SpectorRuntime] closing process-wide shared runtime engines and hot kernels...");

        for (Map.Entry<String, NamespaceKernel> entry : hotKernels.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("[SpectorRuntime] failed to close hot kernel for namespace '{}': {}",
                        entry.getKey(), e.getMessage());
            }
        }
        hotKernels.clear();

        if (rememberPathway != null) {
            try {
                rememberPathway.close();
            } catch (Exception e) {
                log.warn("[SpectorRuntime] failed to close RememberPathway", e);
            }
        }
        if (reflectPathway != null) {
            try {
                reflectPathway.close();
            } catch (Exception e) {
                log.warn("[SpectorRuntime] failed to close ReflectPathway", e);
            }
        }
        if (wanderPathway != null) {
            try {
                wanderPathway.close();
            } catch (Exception e) {
                log.warn("[SpectorRuntime] failed to close WanderPathway", e);
            }
        }
        if (decidePathway != null) {
            try {
                decidePathway.close();
            } catch (Exception e) {
                log.warn("[SpectorRuntime] failed to close DecidePathway", e);
            }
        }
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("SpectorRuntime is closed");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SpectorProperties properties;
        private EmbeddingProvider embeddingProvider;
        private ParallelEmbeddingPipeline parallelEmbeddingPipeline;
        private LlmProvider llmProvider;
        private ScalarQuantizer quantizer;
        private RememberPathway rememberPathway;
        private RecallPathway recallPathway;
        private ReflectPathway reflectPathway;
        private ExpressPathway expressPathway;
        private DreamPathway dreamPathway;
        private DecidePathway decidePathway;
        private WanderPathway wanderPathway;

        public Builder properties(SpectorProperties properties) {
            this.properties = properties;
            return this;
        }

        public Builder embeddingProvider(EmbeddingProvider embeddingProvider) {
            this.embeddingProvider = embeddingProvider;
            return this;
        }

        public Builder parallelEmbeddingPipeline(ParallelEmbeddingPipeline parallelEmbeddingPipeline) {
            this.parallelEmbeddingPipeline = parallelEmbeddingPipeline;
            return this;
        }

        public Builder llmProvider(LlmProvider llmProvider) {
            this.llmProvider = llmProvider;
            return this;
        }

        public Builder quantizer(ScalarQuantizer quantizer) {
            this.quantizer = quantizer;
            return this;
        }

        public Builder rememberPathway(RememberPathway rememberPathway) {
            this.rememberPathway = rememberPathway;
            return this;
        }

        public Builder recallPathway(RecallPathway recallPathway) {
            this.recallPathway = recallPathway;
            return this;
        }

        public Builder reflectPathway(ReflectPathway reflectPathway) {
            this.reflectPathway = reflectPathway;
            return this;
        }

        public Builder expressPathway(ExpressPathway expressPathway) {
            this.expressPathway = expressPathway;
            return this;
        }

        public Builder dreamPathway(DreamPathway dreamPathway) {
            this.dreamPathway = dreamPathway;
            return this;
        }

        public Builder decidePathway(DecidePathway decidePathway) {
            this.decidePathway = decidePathway;
            return this;
        }

        public Builder wanderPathway(WanderPathway wanderPathway) {
            this.wanderPathway = wanderPathway;
            return this;
        }

        public SpectorRuntime build() {
            return new SpectorRuntime(this);
        }
    }
}
