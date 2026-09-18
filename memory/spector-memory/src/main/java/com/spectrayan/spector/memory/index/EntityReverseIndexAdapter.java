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

import com.spectrayan.spector.kernel.graph.EntityDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Adapter managing the on-heap {@code memoryToEntities} reverse index view in {@link EntityDirectory} (ADR-0082).
 *
 * <p>Implements {@link ManagedIndex} as {@link IndexKind#DERIVED_CHEAP}, scanning the authoritative
 * forward adjacency segment on hydration to guarantee cold-start entity-projection parity.</p>
 *
 * @since 1.1.0
 */
public final class EntityReverseIndexAdapter implements ManagedIndex {

    private static final Logger log = LoggerFactory.getLogger(EntityReverseIndexAdapter.class);
    public static final String NAME = "EntityReverseIndex";

    private final EntityDirectory entityDirectory;
    private volatile IndexContext context;
    private volatile long lastHydrateMs = 0L;
    private volatile long generation = 0L;

    public EntityReverseIndexAdapter(EntityDirectory entityDirectory) {
        this.entityDirectory = Objects.requireNonNull(entityDirectory, "entityDirectory");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public IndexKind kind() {
        return IndexKind.DERIVED_CHEAP;
    }

    @Override
    public Set<String> dependsOn() {
        return Set.of("EntityDirectory");
    }

    @Override
    public void attach(IndexContext context) {
        this.context = context;
    }

    @Override
    public CompletionStage<Void> hydrate() {
        long start = System.currentTimeMillis();
        try {
            entityDirectory.rebuildReverseIndex();
            this.generation = computeGeneration();
            this.lastHydrateMs = System.currentTimeMillis() - start;
            log.info("Hydrated {} reverse index: {} indexed memory slots ({} ms, gen={})",
                    NAME, entityDirectory.reverseIndexSize(), lastHydrateMs, generation);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to hydrate {} reverse index: {}", NAME, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void checkpoint() {
        this.generation = computeGeneration();
    }

    @Override
    public CompletionStage<Void> rebuild() {
        return hydrate();
    }

    @Override
    public IndexStats stats() {
        int slots = entityDirectory.reverseIndexSize();
        // Approximate on-heap footprint: ~64 bytes per memory slot (ConcurrentHashMap node + KeySet overhead)
        long estimatedHeap = (long) slots * 64L;
        return new IndexStats(estimatedHeap, 0L, slots, generation, lastHydrateMs);
    }

    public long computeGeneration() {
        long hwm = entityDirectory.adjHighWaterMark();
        long count = entityDirectory.entityCount();
        return (hwm << 32) | (count & 0xFFFFFFFFL);
    }

    public EntityDirectory entityDirectory() {
        return entityDirectory;
    }
}
