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
package com.spectrayan.spector.memory.aisme.manifold;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link ManifoldConsolidator}.
 */
class ManifoldConsolidatorTest {

    private ManifoldConsolidator consolidator;

    @BeforeEach
    void setUp() {
        consolidator = new ManifoldConsolidator(0.1f, 2);
    }

    @Test
    void consolidate_emptyPairs_returnsUnmodifiedTensor() {
        PersonalMetricTensor initial = PersonalMetricTensor.identity(3);
        PersonalMetricTensor consolidated = consolidator.consolidate(initial, List.of(), 0.1f);

        assertThat(consolidated).isEqualTo(initial);
    }

    @Test
    void consolidate_coActivatedPairs_updatesDiagonalAndRank() {
        PersonalMetricTensor initial = PersonalMetricTensor.identity(2);
        List<float[]> coActivations = List.of(
                new float[]{2.0f, 0.0f},
                new float[]{1.0f, 0.0f}
        );

        PersonalMetricTensor updated = consolidator.consolidate(initial, coActivations, 0.1f);

        assertThat(updated.version()).isEqualTo(1);
        // Coordinate 0 should have increased scaling due to co-activation variance
        assertThat(updated.diagonalScaling()[0]).isGreaterThan(initial.diagonalScaling()[0]);
        // Coordinate 1 should remain base (no variance along axis 1)
        assertThat(updated.diagonalScaling()[1]).isEqualTo(initial.diagonalScaling()[1]);
        // Low rank factor was added
        assertThat(updated.rank()).isEqualTo(1);
    }
}
