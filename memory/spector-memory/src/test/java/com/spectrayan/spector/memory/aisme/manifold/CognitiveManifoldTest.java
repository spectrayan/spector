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
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CognitiveManifold}.
 */
class CognitiveManifoldTest {

    private CognitiveManifold manifold;

    @BeforeEach
    void setUp() {
        manifold = new CognitiveManifold(2);
    }

    @Test
    void initialTensor_isIdentity() {
        PersonalMetricTensor tensor = manifold.currentTensor();
        assertThat(tensor.dimensions()).isEqualTo(2);
        assertThat(tensor.version()).isZero();
        assertThat(tensor.diagonalScaling()).containsExactly(1.0f, 1.0f);
    }

    @Test
    void distanceAndSimilarity_computedAccurately() {
        float[] a = {1.0f, 0.0f};
        float[] b = {1.0f, 3.0f};

        float dist = manifold.distance(a, b);
        float sim = manifold.similarity(a, b, 3.0f);

        assertThat(dist).isCloseTo(3.0f, within(1e-5f));
        // exp(-9 / (2*9)) = exp(-0.5) ≈ 0.6065
        assertThat(sim).isCloseTo(0.6065f, within(1e-3f));
    }

    @Test
    void updateTensor_modifiesDistanceMetric() {
        float[] diag = {4.0f, 1.0f};
        PersonalMetricTensor custom = new PersonalMetricTensor(diag, new float[0][], 1, 1000L);

        manifold.updateTensor(custom);

        float[] a = {0.0f, 0.0f};
        float[] b = {1.0f, 0.0f};

        // In X direction, scaling is 4.0 -> sqDist = 4*(1)^2 = 4 -> dist = 2.0
        float dist = manifold.distance(a, b);
        assertThat(dist).isCloseTo(2.0f, within(1e-5f));
    }

    @Test
    void reset_reinitializesToIdentity() {
        PersonalMetricTensor custom = new PersonalMetricTensor(new float[]{5.0f, 5.0f}, new float[0][], 2, 2000L);
        manifold.updateTensor(custom);
        manifold.reset();

        assertThat(manifold.currentTensor().version()).isZero();
        assertThat(manifold.currentTensor().diagonalScaling()).containsExactly(1.0f, 1.0f);
    }
}
