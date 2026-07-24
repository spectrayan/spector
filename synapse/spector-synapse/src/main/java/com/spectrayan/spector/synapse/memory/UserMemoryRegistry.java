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
package com.spectrayan.spector.synapse.memory;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedSparseProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedTokenProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * Resolves the {@link SpectorMemory} instance for the current request, isolating each authenticated
 * user's memory into its own lazily-built, cached instance rooted at a deterministic sharded
 * directory.
 *
 * <h3>Resolution rules</h3>
 * <ul>
 *   <li>When {@code spector.auth.enabled=false} or the principal is anonymous
 *       ({@code userId == "default"}), the single shared {@link SpectorMemory} bean is returned —
 *       byte-for-byte the legacy single-user behavior.</li>
 *   <li>When authenticated, exactly one cached {@link SpectorMemory} is returned per {@code userId},
 *       lazily built rooted at {@link StorageLayout#namespaceDirSharded(Path, String)} with
 *       {@code namespaceId == userId} (the TSID itself — no {@code user-} prefix). The returned
 *       instance is a <strong>pure function of the authenticated {@code userId}</strong>;
 *       client-supplied {@code namespace}/{@code workspace_id}/{@code agent_id} never change which
 *       user's memory is returned.</li>
 * </ul>
 *
 * <h3>Lifecycle and bounds</h3>
 * <p>Per-user instances are built with the same builder settings the shared bean uses today
 * (dimensions, embedder, persistence mode, capacities, batch size, entity extraction, salience,
 * SPLADE/ColBERT). A configurable LRU cap ({@code spector.auth.memory.max-instances}, default 512)
 * bounds off-heap usage: when caching a new instance would exceed the cap, the cached per-user
 * instance with the oldest last-resolution time is evicted and closed <em>before</em> the new
 * instance is cached. The single shared instance is never cached here and is therefore never
 * evicted or closed by this registry.</p>
 *
 * <p>Resolution and lazy construction <strong>fail closed</strong>: any construction error is
 * propagated to the caller, no partially-constructed instance is cached, and neither the shared
 * instance nor another user's instance is returned as a fallback.</p>
 *
 * <p>This component is {@link AutoCloseable}; {@link #close()} closes every cached per-user instance
 * exactly once (never the shared instance) and does not return until all are closed.</p>
 *
 * <p>Concurrency uses {@link ConcurrentHashMap} plus a {@link ReentrantLock} guarding the cold
 * (build/evict/close) path — never {@code synchronized}. Cache hits are lock-free.</p>
 */
@Component
public final class UserMemoryRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UserMemoryRegistry.class);

    /** Literal user id used when the request is anonymous or auth is disabled. */
    private static final String DEFAULT_USER_ID = "default";

    private final ObjectProvider<SpectorMemory> sharedProvider;
    private final SynapseProperties synapseProps;
    private final SpectorConfigProperties spectorProps;
    private final ObjectProvider<EmbeddingProvider> embedderProvider;
    private final ObjectProvider<LlmProvider> textGenProvider;
    private final ObjectProvider<SalienceProfileProvider> salienceProvider;

    /** Maximum number of concurrently-cached per-user instances (LRU cap). */
    private final int maxInstances;

    /** Per-user instance cache. The shared instance is intentionally never stored here. */
    private final ConcurrentHashMap<String, MemoryHandle> cache = new ConcurrentHashMap<>();

    /** Guards the cold path: lazy build, LRU eviction, and shutdown close. */
    private final ReentrantLock coldPathLock = new ReentrantLock();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public UserMemoryRegistry(
            ObjectProvider<SpectorMemory> sharedProvider,
            SynapseProperties synapseProps,
            SpectorConfigProperties spectorProps,
            ObjectProvider<EmbeddingProvider> embedderProvider,
            ObjectProvider<LlmProvider> textGenProvider,
            ObjectProvider<SalienceProfileProvider> salienceProvider,
            @Value("${spector.auth.memory.max-instances:512}") int maxInstances) {
        this.sharedProvider = sharedProvider;
        this.synapseProps = synapseProps;
        this.spectorProps = spectorProps;
        this.embedderProvider = embedderProvider;
        this.textGenProvider = textGenProvider;
        this.salienceProvider = salienceProvider;
        this.maxInstances = Math.max(1, maxInstances);
        log.info("[UserMemoryRegistry] initialized: authEnabled={}, maxInstances={}",
                synapseProps.auth().enabled(), this.maxInstances);
    }

    /**
     * Resolves memory for the current request by reading the {@code SecurityContextHolder} on the
     * calling (request/servlet) thread. Never call this from an asynchronous task — the caller must
     * resolve on the request thread and close over the returned reference.
     *
     * @return the single shared instance when auth is disabled or the principal is anonymous;
     *         otherwise the cached per-user instance for the authenticated principal
     */
    public SpectorMemory resolveForCurrentRequest() {
        if (!synapseProps.auth().enabled()) {
            return sharedMemory();
        }
        String userId = SecurityUtils.getUserId();
        if (DEFAULT_USER_ID.equals(userId)) {
            return sharedMemory();
        }
        return resolveFor(userId);
    }

    /**
     * Explicit resolution by principal id, for tests and non-servlet callers.
     *
     * @param userId the authenticated principal TSID; {@code "default"}, {@code null}, or blank
     *               resolves to the single shared instance
     * @return the shared instance for the anonymous/default principal, otherwise the cached per-user
     *         instance built rooted at the user's sharded namespace directory
     */
    public SpectorMemory resolveFor(String userId) {
        if (userId == null || userId.isBlank() || DEFAULT_USER_ID.equals(userId)) {
            return sharedMemory();
        }

        // Fast path: lock-free cache hit.
        MemoryHandle handle = cache.get(userId);
        if (handle != null) {
            handle.touch();
            return handle.memory;
        }

        MemoryHandle evicted = null;
        try {
            // Cold path: build + LRU eviction serialized by the lock. Cache hits stay lock-free.
            coldPathLock.lock();
            try {
                // computeIfAbsent guarantees the instance is built exactly once. Eviction happens
                // BEFORE insertion so the cap is never exceeded by a freshly-cached instance. If the
                // build throws, computeIfAbsent leaves nothing cached (fail-closed).
                if (!cache.containsKey(userId) && cache.size() >= maxInstances) {
                    evicted = evictOldestLocked();
                }
                handle = cache.computeIfAbsent(userId, id -> new MemoryHandle(buildInstance(id)));
            } finally {
                coldPathLock.unlock();
            }
        } finally {
            // Close evicted instance outside the lock — guaranteed even if build throws.
            if (evicted != null) {
                closeQuietly(evicted.memory);
            }
        }
        handle.touch();
        return handle.memory;
    }

    /**
     * Closes every cached per-user instance exactly once. The shared instance is never touched.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        coldPathLock.lock();
        try {
            for (MemoryHandle handle : cache.values()) {
                closeQuietly(handle.memory);
            }
            cache.clear();
        } finally {
            coldPathLock.unlock();
        }
        log.info("[UserMemoryRegistry] closed all cached per-user memory instances");
    }

    /** @return the number of currently-cached per-user instances (test/observability helper). */
    public int cachedInstanceCount() {
        return cache.size();
    }

    /** Returns a snapshot of all currently cached SpectorMemory instances for batch operations. */
    public java.util.List<SpectorMemory> cachedInstances() {
        return cache.values().stream()
            .map(h -> h.memory)
            .toList();
    }

    // ══════════════════════════════════════════════════════════════
    // Internals
    // ══════════════════════════════════════════════════════════════

    private SpectorMemory sharedMemory() {
        return sharedProvider.getIfAvailable();
    }

    /**
     * Evicts the cached per-user instance with the oldest last-resolution time. Must be
     * called while holding {@link #coldPathLock}. The caller should close the returned instance
     * after releasing the lock.
     * @return the evicted MemoryHandle, or null if nothing was evicted
     */
    private MemoryHandle evictOldestLocked() {
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (Map.Entry<String, MemoryHandle> entry : cache.entrySet()) {
            long access = entry.getValue().lastAccessNanos;
            if (access < oldestAccess) {
                oldestAccess = access;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            MemoryHandle evicted = cache.remove(oldestKey);
            if (evicted != null) {
                log.debug("[UserMemoryRegistry] evicting LRU per-user memory instance (cap={})", maxInstances);
                return evicted;
            }
        }
        return null;
    }

    /**
     * Lazily builds a per-user {@link SpectorMemory} rooted at the user's sharded namespace
     * directory, using the same builder settings the shared bean uses today. Propagates any error
     * (invalid namespace id, missing embedder, construction failure) so resolution fails closed.
     */
    private SpectorMemory buildInstance(String userId) {
        // namespaceId is the userId (TSID) itself — no "user-" prefix. namespaceDirSharded
        // validates the identifier and throws IllegalArgumentException on unsafe input before any
        // path is resolved (fail-closed).
        Path dir = StorageLayout.namespaceDirSharded(basePath(), userId);

        EmbeddingProvider embedder = embedderProvider.getIfAvailable();
        if (embedder == null) {
            throw new IllegalStateException(
                    "Cannot build per-user memory: no EmbeddingProvider bean available");
        }

        SpectorConfigProperties.Memory memory = spectorProps.getMemory();

        var builder = DefaultSpectorMemory.builder()
                .dimensions(memory.getDimensions())
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.valueOf(memory.getPersistenceMode()))
                .semanticCapacity(memory.getCapacity())
                .hebbianGraphCapacity(memory.getCapacity())
                .temporalChainCapacity(memory.getCapacity())
                .entityGraphCapacity(memory.getCapacity())
                .embedBatchSize(spectorProps.getEmbedding().getBatchSize())
                .persistence(dir);

        // Entity extraction (LLM if a LlmProvider is present) — mirrors SpectorAutoConfiguration.
        LlmProvider textGen = textGenProvider.getIfAvailable();
        if (textGen != null) {
            builder.entityExtractionMode(EntityExtractionMode.LLM);
            builder.LlmProvider(textGen);
        } else {
            builder.entityExtractionMode(EntityExtractionMode.NONE);
        }

        // Salience profile provider (user-driven importance modulation).
        SalienceProfileProvider salience = salienceProvider.getIfAvailable();
        if (salience != null) {
            builder.salienceProfileProvider(salience);
        }

        // SPLADE + ColBERT providers (auto-derived from the dense embedding provider).
        if (memory.isSpladeEnabled()) {
            builder.SparseEmbeddingProvider(new DenseDerivedSparseProvider(embedder));
        }
        if (memory.isColbertEnabled()) {
            builder.tokenEmbeddingProvider(new DenseDerivedTokenProvider(embedder));
        }

        SpectorMemory built = builder.build();
        log.info("[UserMemoryRegistry] built per-user memory instance (dims={}, persistenceMode={})",
                memory.getDimensions(), memory.getPersistenceMode());
        return built;
    }

    /**
     * Resolves the base persistence root: {@code spector.memory.persistence-path} when set,
     * otherwise the Synapse {@code dataDir}.
     */
    private Path basePath() {
        String path = spectorProps.getMemory().getPersistencePath();
        if (path == null || path.isBlank()) {
            path = synapseProps.dataDir();
        }
        return Path.of(path);
    }

    private static void closeQuietly(SpectorMemory memory) {
        if (memory == null) {
            return;
        }
        try {
            memory.close();
        } catch (RuntimeException e) {
            // Never log the raw identifier at higher levels; DEBUG only, no payload.
            log.warn("[UserMemoryRegistry] error closing a per-user memory instance: {}", e.getMessage());
            log.debug("[UserMemoryRegistry] close failure for user (id withheld)", e);
        }
    }

    /** Cache entry pairing a per-user instance with its last-resolution time (for LRU eviction). */
    private static final class MemoryHandle {
        private final SpectorMemory memory;
        private volatile long lastAccessNanos;

        MemoryHandle(SpectorMemory memory) {
            this.memory = memory;
            this.lastAccessNanos = System.nanoTime();
        }

        void touch() {
            this.lastAccessNanos = System.nanoTime();
        }
    }
}
