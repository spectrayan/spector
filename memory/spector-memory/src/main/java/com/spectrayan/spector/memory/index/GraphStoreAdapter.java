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
package com.spectrayan.spector.memory.index;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Adapter wrapping primary kernel graph stores (Hebbian, TemporalChain, HyperEntityGraph,
 * EntityDirectory) under the {@link ManagedIndex} contract without polluting kernel types (ADR-0082).
 *
 * @since 1.1.0
 */
public final class GraphStoreAdapter implements ManagedIndex {

    private final String name;
    private final Object store;
    private final Set<String> dependencies;
    private final Runnable checkpointAction;
    private final AutoCloseable closeAction;
    private volatile IndexContext context;
    private volatile long offHeapBytes;
    private volatile long entries;

    public GraphStoreAdapter(String name, Object store, Set<String> dependencies) {
        this(name, store, dependencies, null, null);
    }

    public GraphStoreAdapter(String name, Object store, Set<String> dependencies,
                             Runnable checkpointAction, AutoCloseable closeAction) {
        this.name = Objects.requireNonNull(name, "name");
        this.store = Objects.requireNonNull(store, "store");
        this.dependencies = dependencies != null ? Set.copyOf(dependencies) : Set.of();
        this.checkpointAction = checkpointAction;
        this.closeAction = closeAction;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public IndexKind kind() {
        return IndexKind.PRIMARY;
    }

    @Override
    public Set<String> dependsOn() {
        return dependencies;
    }

    @Override
    public void attach(IndexContext context) {
        this.context = context;
    }

    @Override
    public CompletionStage<Void> hydrate() {
        // PRIMARY stores are already memory-mapped during bundle initialization.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void checkpoint() {
        if (checkpointAction != null) {
            checkpointAction.run();
        }
    }

    @Override
    public IndexStats stats() {
        long e = entries;
        long b = offHeapBytes;
        if (e == 0L) {
            if (store instanceof com.spectrayan.spector.kernel.graph.EntityDirectory ed) {
                e = ed.entityCount();
                b = (long) ed.entityCount() * 128L;
            } else if (store instanceof com.spectrayan.spector.kernel.shape.AbstractMemory<?> am) {
                e = am.visibleCount();
                b = am.layout() != null ? (long) am.layout().recordStride() * am.visibleCount() : 0L;
            } else if (store instanceof com.spectrayan.spector.memory.cortex.index.MemoryIndex mi) {
                e = mi.size();
            }
        }
        return new IndexStats(0L, b, e, 1L, 0L);
    }

    public Object store() {
        return store;
    }

    public void setMetrics(long offHeapBytes, long entries) {
        this.offHeapBytes = offHeapBytes;
        this.entries = entries;
    }

    @Override
    public void close() throws Exception {
        if (closeAction != null) {
            closeAction.close();
        }
    }
}
