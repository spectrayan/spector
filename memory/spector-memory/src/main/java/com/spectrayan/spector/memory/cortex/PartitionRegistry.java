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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.List;

/**
 * Read-side view of the partition registry (issue #443, Phase 1).
 *
 * <p>Implemented by {@code PartitionManager}. Consumers (the recall pipeline,
 * graph expansion, LTP listener, reinforcement handler) depend on this abstraction
 * rather than a single {@link CognitiveMemoryRouter}, so recall fans out across all
 * live partitions and point-lookups resolve to the partition a memory actually lives
 * in.</p>
 *
 * <h3>Concurrency</h3>
 * <p>{@link #snapshot()} performs a single volatile read of an immutable list. A reader
 * takes the snapshot once at the start of an operation and iterates that fixed view —
 * it can never observe a half-registered partition. Rolls publish a new immutable list
 * by a single reference assignment under the manager's roll lock.</p>
 */
public interface PartitionRegistry {

    /** Immutable snapshot of all live partitions, ordered by sequence (last = active). */
    List<PartitionHandle> snapshot();

    /** O(P) lookup of the handle for a given partition sequence, or {@code null} if unknown. */
    PartitionHandle handleFor(int seq);

    /** The router for the single active (writable) partition — used for all writes. */
    CognitiveMemoryRouter activeRouter();

    /**
     * Resolves the router for the partition a memory lives in. Falls back to the active
     * router when the sequence is unknown (defensive; keeps point-lookups total).
     */
    default CognitiveMemoryRouter routerFor(int seq) {
        PartitionHandle h = handleFor(seq);
        return h != null ? h.router() : activeRouter();
    }
}
