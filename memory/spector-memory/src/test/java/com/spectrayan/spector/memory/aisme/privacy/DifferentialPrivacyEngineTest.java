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
package com.spectrayan.spector.memory.aisme.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Unit tests for {@link DifferentialPrivacyEngine}.
 */
class DifferentialPrivacyEngineTest {

    @Test
    @DisplayName("perturbVector perturbs embedding and increments consumed budget")
    void perturbVector_perturbsAndTracksBudget() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(true)
                .privacyEpsilon(2.0f)
                .privacyDelta(1e-5f)
                .privacyClippingNorm(1.0f)
                .build();

        DifferentialPrivacyEngine engine = new DifferentialPrivacyEngine(config, new Random(42L));

        float[] original = {0.5f, 0.5f, 0.5f, 0.5f};
        float[] perturbed = engine.perturbVector(original);

        assertThat(perturbed).isNotNull();
        assertThat(perturbed).isNotEqualTo(original);
        assertThat(engine.perturbationCount()).isEqualTo(1L);
        assertThat(engine.consumedEpsilon()).isCloseTo(2.0, within(1e-4));

        // Perturb again
        engine.perturbVector(original);
        assertThat(engine.perturbationCount()).isEqualTo(2L);
        assertThat(engine.consumedEpsilon()).isCloseTo(4.0, within(1e-4));
    }

    @Test
    @DisplayName("Disabled privacy returns original vector clone with zero budget consumption")
    void disabledPrivacy_returnsOriginalVector() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(false)
                .build();

        DifferentialPrivacyEngine engine = new DifferentialPrivacyEngine(config);

        float[] original = {1.0f, 2.0f, 3.0f};
        float[] perturbed = engine.perturbVector(original);

        assertThat(perturbed).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(engine.consumedEpsilon()).isEqualTo(0.0);
        assertThat(engine.perturbationCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("resetBudget clears budget counters")
    void resetBudget_clearsCounters() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(true)
                .privacyEpsilon(1.5f)
                .build();

        DifferentialPrivacyEngine engine = new DifferentialPrivacyEngine(config);
        engine.perturbVector(new float[]{1.0f, 0.0f});
        assertThat(engine.consumedEpsilon()).isGreaterThan(0.0);

        engine.resetBudget();
        assertThat(engine.consumedEpsilon()).isEqualTo(0.0);
        assertThat(engine.perturbationCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("perturbScalar applies Laplace noise to scalar metric")
    void perturbScalar_appliesLaplaceNoise() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(true)
                .privacyEpsilon(2.0f)
                .build();

        DifferentialPrivacyEngine engine = new DifferentialPrivacyEngine(config, new Random(100L));
        float scalar = 0.85f;
        float perturbed = engine.perturbScalar(scalar, 1.0f);

        assertThat(perturbed).isNotEqualTo(scalar);
    }
}
