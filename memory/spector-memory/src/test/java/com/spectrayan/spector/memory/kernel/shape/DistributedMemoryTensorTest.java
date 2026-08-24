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
package com.spectrayan.spector.memory.kernel.shape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.memory.kernel.MemoryShape;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for off-heap {@link DistributedMemoryTensor}.
 */
class DistributedMemoryTensorTest {

    @Test
    void lifecycle_initializesAndAccumulatesMemories() {
        try (DistributedMemoryTensor tensor = new DistributedMemoryTensor(8, 256, 42L)) {
            assertThat(tensor.shape()).isEqualTo(MemoryShape.HOLOGRAPHIC);
            assertThat(tensor.inputDimension()).isEqualTo(8);
            assertThat(tensor.featureDimension()).isEqualTo(256);
            assertThat(tensor.patternCount()).isZero();

            float[] mem1 = {1.0f, 0.0f, 0.5f, -0.5f, 0.2f, -0.1f, 0.8f, -0.3f};
            float[] mem2 = {0.0f, 1.0f, -0.5f, 0.5f, -0.2f, 0.1f, -0.8f, 0.3f};

            tensor.accumulate(mem1, 1.0f);
            assertThat(tensor.patternCount()).isEqualTo(1L);

            tensor.accumulate(mem2, 1.0f);
            assertThat(tensor.patternCount()).isEqualTo(2L);

            // Energy of query close to mem1 should be lower than distant random query
            float energy1 = tensor.evaluateEnergy(mem1, 1.0f);
            assertThat(Float.isFinite(energy1)).isTrue();

            // Retract mem2
            tensor.retract(mem2, 1.0f);
            assertThat(tensor.patternCount()).isEqualTo(1L);

            // Snapshot tensor has featureDimension elements
            float[] snapshot = tensor.snapshotTensor();
            assertThat(snapshot.length).isEqualTo(256);

            // Decay
            float beforeDecay = snapshot[0];
            tensor.decay(0.5f);
            float[] decayedSnapshot = tensor.snapshotTensor();
            assertThat(decayedSnapshot[0]).isCloseTo(beforeDecay * 0.5f, within(1e-5f));
        }
    }
}
