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
package com.spectrayan.spector.memory.aisme.segmentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

/**
 * Unit tests for {@link BayesianOnlineChangePointDetector}.
 */
class BayesianOnlineChangePointDetectorTest {

    private static final int DIMENSIONS = 8;

    @Test
    @DisplayName("update: Stationary sensory stream maintains low change-point probability")
    void stationaryStream_maintainsLowChangePointProbability() {
        float[] prior = new float[DIMENSIONS];
        Arrays.fill(prior, 0.5f);
        float[] prec = new float[DIMENSIONS];
        Arrays.fill(prec, 2.0f);

        BayesianOnlineChangePointDetector bocpd = new BayesianOnlineChangePointDetector(DIMENSIONS, 100.0f, 100, prior, prec);
        Random rng = new Random(42L);

        float lastCp = 0.0f;
        for (int t = 0; t < 50; t++) {
            float[] obs = new float[DIMENSIONS];
            for (int d = 0; d < DIMENSIONS; d++) {
                obs[d] = 0.5f + (float) (rng.nextGaussian() * 0.02);
            }
            lastCp = bocpd.update(obs);
        }

        assertThat(lastCp).isLessThan(0.30f);
        assertThat(bocpd.stepCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("update: Abrupt distribution shift triggers change-point probability spike")
    void abruptShift_triggersChangePointSpike() {
        float[] prior = new float[DIMENSIONS];
        Arrays.fill(prior, 0.0f);
        float[] prec = new float[DIMENSIONS];
        Arrays.fill(prec, 4.0f);

        BayesianOnlineChangePointDetector bocpd = new BayesianOnlineChangePointDetector(DIMENSIONS, 50.0f, 100, prior, prec);
        Random rng = new Random(1337L);

        // Train on regime 1 (mean = 0.0)
        for (int t = 0; t < 30; t++) {
            float[] obs = new float[DIMENSIONS];
            for (int d = 0; d < DIMENSIONS; d++) {
                obs[d] = (float) (rng.nextGaussian() * 0.05);
            }
            bocpd.update(obs);
        }

        // Pre-shift MAP run length should be high (~30)
        assertThat(bocpd.mapRunLength()).isEqualTo(30);

        // Sudden shift to regime 2 (mean = 4.0)
        float[] shiftObs = new float[DIMENSIONS];
        Arrays.fill(shiftObs, 4.0f);
        bocpd.update(shiftObs);

        // Frame 2 in new regime confirms regime collapse
        bocpd.update(shiftObs);

        // MAP run length should now be localized in the new regime (<= 2)
        assertThat(bocpd.mapRunLength()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("reset: Clears state and restores initial run-length distribution")
    void reset_clearsHistory() {
        float[] prior = new float[DIMENSIONS];
        BayesianOnlineChangePointDetector bocpd = new BayesianOnlineChangePointDetector(DIMENSIONS, 100.0f, 50, prior, null);

        bocpd.update(new float[DIMENSIONS]);
        bocpd.update(new float[DIMENSIONS]);
        assertThat(bocpd.stepCount()).isEqualTo(2);

        bocpd.reset();
        assertThat(bocpd.stepCount()).isZero();
        assertThat(bocpd.currentChangePointProbability()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("Validation: Rejects invalid parameters")
    void validation_rejectsInvalidConfig() {
        assertThatThrownBy(() -> new BayesianOnlineChangePointDetector(0, 100.0f, 50, null, null))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> new BayesianOnlineChangePointDetector(DIMENSIONS, 0.0f, 50, null, null))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> new BayesianOnlineChangePointDetector(DIMENSIONS, 100.0f, 0, null, null))
                .isInstanceOf(SpectorValidationException.class);
    }
}
