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

/**
 * A weighted edge in the Hebbian associative graph connecting two memory nodes.
 *
 * @param neighborIndex index of the connected memory node
 * @param weight        association strength (higher = stronger co-activation)
 * @param bridgeScore   structural importance score (0-255, unsigned byte range)
 *
 * @see HebbianGraphBase#neighbors(int)
 * @see HebbianGraphMemory
 */
public record HebbianEdge(int neighborIndex, float weight, int bridgeScore) {

    /** Backward-compatible constructor (bridgeScore defaults to 0). */
    public HebbianEdge(int neighborIndex, float weight) {
        this(neighborIndex, weight, 0);
    }
}
