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
package com.spectrayan.spector.memory.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Central coordinator for the Spector Index Plane (ADR-0082).
 *
 * <p>Governs lifecycle registration, dependency-ordered hydration, periodic checkpointing,
 * and readiness state for all primary and derived index structures.</p>
 *
 * @since 1.1.0
 */
public final class IndexPlaneCoordinator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IndexPlaneCoordinator.class);

    public enum State {
        OPEN,
        ATTACHING,
        HYDRATING,
        READY,
        CLOSED
    }

    private final Map<String, ManagedIndex> indexes = new LinkedHashMap<>();
    private final IndexContext context;
    private volatile State state = State.OPEN;

    public IndexPlaneCoordinator(IndexContext context) {
        this.context = context;
    }

    /**
     * Registers a managed index with the coordinator.
     */
    public synchronized IndexPlaneCoordinator register(ManagedIndex index) {
        Objects.requireNonNull(index, "index");
        if (state == State.CLOSED) {
            throw new IllegalStateException("Cannot register index on closed coordinator");
        }
        indexes.put(index.name(), index);
        if (context != null) {
            index.attach(context);
        }
        return this;
    }

    /**
     * Returns an unmodifiable collection of all registered indexes.
     */
    public synchronized Collection<ManagedIndex> registeredIndexes() {
        return List.copyOf(indexes.values());
    }

    /**
     * Returns a registered index by name.
     */
    public synchronized Optional<ManagedIndex> get(String name) {
        return Optional.ofNullable(indexes.get(name));
    }

    /**
     * Executes dependency-ordered hydration across all registered indexes.
     * Skips PRIMARY indexes (already mapped in memory) and hydrates DERIVED indexes in topological order.
     */
    public synchronized CompletionStage<Void> hydrateAll() {
        if (state == State.READY) {
            return CompletableFuture.completedFuture(null);
        }
        this.state = State.HYDRATING;
        List<ManagedIndex> sorted = computeTopologicalOrder();

        CompletableFuture<Void> stage = CompletableFuture.completedFuture(null);
        for (ManagedIndex index : sorted) {
            if (index.kind() == IndexKind.PRIMARY) {
                continue; // No-op, already memory-mapped
            }
            stage = stage.thenCompose(v -> {
                log.debug("Hydrating index: {} (kind={})", index.name(), index.kind());
                return index.hydrate();
            });
        }

        return stage.thenRun(() -> {
            this.state = State.READY;
            log.info("IndexPlaneCoordinator: All indexes hydrated. State is READY ({} indexes registered).",
                    indexes.size());
        });
    }

    /**
     * Checkpoints all registered indexes.
     */
    public synchronized void checkpointAll() {
        for (ManagedIndex index : indexes.values()) {
            try {
                index.checkpoint();
            } catch (Exception e) {
                log.warn("Error during checkpoint of index {}: {}", index.name(), e.getMessage(), e);
            }
        }
    }

    /**
     * Computes the execution order based on dependencies.
     */
    public synchronized List<ManagedIndex> computeTopologicalOrder() {
        List<ManagedIndex> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (ManagedIndex index : indexes.values()) {
            if (!visited.contains(index.name())) {
                dfs(index, visited, visiting, result);
            }
        }
        return result;
    }

    private void dfs(ManagedIndex current, Set<String> visited, Set<String> visiting, List<ManagedIndex> result) {
        visiting.add(current.name());
        for (String depName : current.dependsOn()) {
            ManagedIndex dep = indexes.get(depName);
            if (dep != null) {
                if (visiting.contains(depName)) {
                    throw new IllegalStateException("Circular index dependency detected: "
                            + current.name() + " -> " + depName);
                }
                if (!visited.contains(depName)) {
                    dfs(dep, visited, visiting, result);
                }
            }
        }
        visiting.remove(current.name());
        visited.add(current.name());
        result.add(current);
    }

    public State state() {
        return state;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    @Override
    public synchronized void close() {
        this.state = State.CLOSED;
        for (ManagedIndex index : indexes.values()) {
            try {
                index.close();
            } catch (Exception e) {
                log.warn("Error closing index {}: {}", index.name(), e.getMessage());
            }
        }
        indexes.clear();
    }
}
