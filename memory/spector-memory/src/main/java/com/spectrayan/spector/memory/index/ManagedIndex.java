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

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Unified lifecycle contract for memory, graph, and derived retrieval indexes in Spector (ADR-0082).
 *
 * <p>Implemented by retrieval indexes and lifecycle adapters wrapping kernel graph stores.
 * Orchestrated by {@link IndexPlaneCoordinator} during startup, checkpoint, and shutdown.</p>
 *
 * @since 1.1.0
 */
public interface ManagedIndex extends AutoCloseable {

    /** Logical unique name of the index or store (e.g. "HebbianGraph", "EntityReverseIndex", "BM25"). */
    String name();

    /** Classification defining hydration and persistence cost characteristics. */
    IndexKind kind();

    /** Set of logical index names that must be attached/hydrated prior to this index. */
    Set<String> dependsOn();

    /** Attaches the execution context prior to hydration. */
    void attach(IndexContext context);

    /** Hydrates or rebuilds the index asynchronously. */
    CompletionStage<Void> hydrate();

    /**
     * Executes an administrative rebuild from primary memory using copy-on-write
     * partition swapping, updating the generation token and checkpointing to the bundle region.
     */
    default CompletionStage<Void> rebuild() {
        return hydrate();
    }

    /** Flushes dirty state or snapshots the index to its bundle region. */
    void checkpoint();

    /** Returns current sizing and generation telemetry. */
    IndexStats stats();

    /** Returns the current index generation stamp. */
    default long generation() {
        return stats().generation();
    }

    @Override
    default void close() throws Exception {}
}
