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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IndexPlaneCoordinator Lifecycle and Hydration Tests")
class IndexPlaneCoordinatorTest {

    static class TestManagedIndex implements ManagedIndex {
        private final String name;
        private final IndexKind kind;
        private final Set<String> deps;
        private final List<String> executionLog;
        final AtomicBoolean hydrated = new AtomicBoolean(false);
        final AtomicBoolean checkpointed = new AtomicBoolean(false);
        final AtomicBoolean closed = new AtomicBoolean(false);

        TestManagedIndex(String name, IndexKind kind, Set<String> deps, List<String> executionLog) {
            this.name = name;
            this.kind = kind;
            this.deps = deps;
            this.executionLog = executionLog;
        }

        @Override public String name() { return name; }
        @Override public IndexKind kind() { return kind; }
        @Override public Set<String> dependsOn() { return deps; }
        @Override public void attach(IndexContext context) {}

        @Override
        public CompletionStage<Void> hydrate() {
            hydrated.set(true);
            if (executionLog != null) {
                executionLog.add(name);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void checkpoint() {
            checkpointed.set(true);
        }

        @Override
        public IndexStats stats() {
            return new IndexStats(100L, 200L, 10L, 1L, 5L);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @Test
    @DisplayName("Should hydrate derived indexes in strict topological dependency order and skip primary stores")
    void shouldHydrateInTopologicalOrder() {
        IndexPlaneCoordinator coordinator = new IndexPlaneCoordinator(null);
        List<String> hydrateOrder = new ArrayList<>();

        // Register in arbitrary order
        TestManagedIndex bm25 = new TestManagedIndex("BM25", IndexKind.DERIVED_EXPENSIVE, Set.of("MemoryIndex", "EntityReverseIndex"), hydrateOrder);
        TestManagedIndex reverse = new TestManagedIndex("EntityReverseIndex", IndexKind.DERIVED_CHEAP, Set.of("EntityDirectory"), hydrateOrder);
        TestManagedIndex entityDir = new TestManagedIndex("EntityDirectory", IndexKind.PRIMARY, Set.of("TypeRegistry"), hydrateOrder);
        TestManagedIndex memoryIndex = new TestManagedIndex("MemoryIndex", IndexKind.PRIMARY, Set.of(), hydrateOrder);
        TestManagedIndex typeRegistry = new TestManagedIndex("TypeRegistry", IndexKind.PRIMARY, Set.of(), hydrateOrder);

        coordinator.register(bm25)
                .register(reverse)
                .register(entityDir)
                .register(memoryIndex)
                .register(typeRegistry);

        assertThat(coordinator.state()).isEqualTo(IndexPlaneCoordinator.State.OPEN);
        assertThat(coordinator.isReady()).isFalse();

        coordinator.hydrateAll().toCompletableFuture().join();

        assertThat(coordinator.isReady()).isTrue();
        assertThat(coordinator.state()).isEqualTo(IndexPlaneCoordinator.State.READY);

        // PRIMARY stores must be skipped during hydrateAll()
        assertThat(typeRegistry.hydrated).isFalse();
        assertThat(entityDir.hydrated).isFalse();
        assertThat(memoryIndex.hydrated).isFalse();

        // DERIVED stores must be hydrated in dependency order: EntityReverseIndex BEFORE BM25
        assertThat(reverse.hydrated).isTrue();
        assertThat(bm25.hydrated).isTrue();
        assertThat(hydrateOrder).containsExactly("EntityReverseIndex", "BM25");
    }

    @Test
    @DisplayName("Should detect circular dependencies during topological sorting")
    void shouldDetectCircularDependencies() {
        IndexPlaneCoordinator coordinator = new IndexPlaneCoordinator(null);

        TestManagedIndex a = new TestManagedIndex("A", IndexKind.DERIVED_CHEAP, Set.of("B"), null);
        TestManagedIndex b = new TestManagedIndex("B", IndexKind.DERIVED_CHEAP, Set.of("A"), null);

        coordinator.register(a).register(b);

        assertThatThrownBy(() -> coordinator.hydrateAll().toCompletableFuture().join())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circular index dependency detected");
    }

    @Test
    @DisplayName("Should checkpoint and close all registered indexes cleanly")
    void shouldCheckpointAndCloseAll() {
        IndexPlaneCoordinator coordinator = new IndexPlaneCoordinator(null);

        TestManagedIndex a = new TestManagedIndex("A", IndexKind.PRIMARY, Set.of(), null);
        TestManagedIndex b = new TestManagedIndex("B", IndexKind.DERIVED_EXPENSIVE, Set.of(), null);

        coordinator.register(a).register(b);
        coordinator.checkpointAll();

        assertThat(a.checkpointed).isTrue();
        assertThat(b.checkpointed).isTrue();

        coordinator.close();
        assertThat(coordinator.state()).isEqualTo(IndexPlaneCoordinator.State.CLOSED);
        assertThat(a.closed).isTrue();
        assertThat(b.closed).isTrue();
    }
}
