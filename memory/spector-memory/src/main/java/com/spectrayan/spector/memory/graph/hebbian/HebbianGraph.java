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

import com.spectrayan.spector.kernel.store.GraphHealthSink;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;

import java.nio.file.Path;

/**
 * Backward compatibility subclass extending {@link com.spectrayan.spector.kernel.store.HebbianGraph}.
 * Contains zero {@code java.lang.foreign.*} imports — all off-heap layout and Panama FFM
 * are sealed in spector-kernel.
 *
 * @deprecated Use {@link com.spectrayan.spector.kernel.store.HebbianGraphMemory} instead.
 */
@Deprecated(since = "1.0.0", forRemoval = false)
public class HebbianGraph extends com.spectrayan.spector.kernel.store.HebbianGraph {

    public HebbianGraph(int capacity) {
        super(capacity);
    }

    public HebbianGraph(int capacity, int maxDegree) {
        super(capacity, maxDegree);
    }

    public HebbianGraph(Path filePath, int capacity) {
        super(filePath, capacity);
    }

    public HebbianGraph(Path filePath, int capacity, int maxDegree) {
        super(filePath, capacity, maxDegree);
    }

    public static HebbianGraph load(Path filePath, int defaultCapacity) {
        if (filePath == null || !java.nio.file.Files.exists(filePath)) {
            return new HebbianGraph(defaultCapacity);
        }
        return new HebbianGraph(filePath, defaultCapacity);
    }

    public static HebbianGraph load(Path filePath, int defaultCapacity, int defaultMaxDegree) {
        if (filePath == null || !java.nio.file.Files.exists(filePath)) {
            return new HebbianGraph(defaultCapacity, defaultMaxDegree);
        }
        return new HebbianGraph(filePath, defaultCapacity, defaultMaxDegree);
    }

    public int decayEdges(float decayFactor, GraphHealthMetrics metrics) {
        return super.decayEdges(decayFactor, (GraphHealthSink) metrics);
    }
}
