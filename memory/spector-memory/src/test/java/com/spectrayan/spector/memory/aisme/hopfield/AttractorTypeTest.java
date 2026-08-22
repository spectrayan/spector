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
package com.spectrayan.spector.memory.aisme.hopfield;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AttractorType} classification.
 */
class AttractorTypeTest {

    @Test
    void classify_dominantWeight_fixedPoint() {
        float[] weights = {0.85f, 0.10f, 0.05f};
        assertThat(AttractorType.classify(weights)).isEqualTo(AttractorType.FIXED_POINT);
    }

    @Test
    void classify_superposition_metastable() {
        float[] weights = {0.50f, 0.35f, 0.15f};
        assertThat(AttractorType.classify(weights)).isEqualTo(AttractorType.METASTABLE);
    }

    @Test
    void classify_uniformWeights_diffuse() {
        float[] weights = {0.25f, 0.25f, 0.25f, 0.25f};
        assertThat(AttractorType.classify(weights)).isEqualTo(AttractorType.DIFFUSE);
    }

    @Test
    void classify_emptyOrNull_returnsDiffuse() {
        assertThat(AttractorType.classify(null)).isEqualTo(AttractorType.DIFFUSE);
        assertThat(AttractorType.classify(new float[0])).isEqualTo(AttractorType.DIFFUSE);
    }
}
