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
package com.spectrayan.spector.memory.graph.hebbian;
import com.spectrayan.spector.kernel.store.HebbianEdge;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;

import com.spectrayan.spector.memory.graph.GraphHealthMetrics;

import java.nio.file.Path;
import java.util.List;

/**
 * Common interface for Hebbian graph implementations — both the legacy fixed-width
 * layout ({@code HebbianGraph}, V2) and the sparse CSR layout ({@link HebbianGraphMemory}, V3).
 *
 * <h3>Biological Analog</h3>
 * <p>In the cortex, neurons form association networks where activating one memory
 * spreads activation to connected memories. This graph models those co-recall
 * edges with bounded degree and decaying weights.</p>
 *
 * <p>All implementations are thread-safe for concurrent reads. Structural mutations
 * (strengthen, decay) are synchronized via {@link java.util.concurrent.locks.ReentrantLock}.</p>
 *
 * @see HebbianGraphMemory The CSR V3 sparse implementation (preferred)
 */
public interface HebbianGraphBase extends com.spectrayan.spector.kernel.store.HebbianGraphBase {

    default void setDecayModulator(DecayModulator modulator) {
        setDecayModulator((com.spectrayan.spector.kernel.store.DecayModulator) modulator);
    }

    int decayEdges(float decayFactor, GraphHealthMetrics metrics);

    long memoryUsageBytes();
}
