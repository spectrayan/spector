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
package com.spectrayan.spector.memory.hebbian;

/**
 * Per-node decay rate modifier for arousal-modulated Hebbian edge decay.
 *
 * <p>Implementations return a multiplier in [0.5, 2.0] applied to the decay factor
 * per node. Values &gt; 1.0 = slower decay (high importance), &lt; 1.0 = faster decay
 * (low importance). A null modulator means uniform decay (no modulation).</p>
 *
 * <p>Typical usage: the cortex layer reads synaptic header importance/arousal
 * values and provides a modulator that maps node indices to decay multipliers.</p>
 *
 * @see SynapticDecayModulator
 * @see HebbianGraphBase#setDecayModulator(DecayModulator)
 */
@FunctionalInterface
public interface DecayModulator {
    /**
     * Returns a decay rate modifier for the given memory node.
     *
     * @param nodeIndex memory slot index
     * @return multiplier applied to decay factor (e.g., 1.2 = 20% slower decay)
     */
    float modulateDecay(int nodeIndex);
}
