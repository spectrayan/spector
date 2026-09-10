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
package com.spectrayan.spector.kernel.store;

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
