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
package com.spectrayan.spector.memory.aisme.fegr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MentalStatePosterior}.
 */
class MentalStatePosteriorTest {

    @Test
    void validConstruction_defensiveCopyAndAccessors() {
        float[] mean = {0.5f, -0.5f};
        float[] prec = {2.0f, 3.0f};

        MentalStatePosterior posterior = new MentalStatePosterior(mean, prec, 1000L, 1);

        assertThat(posterior.dimensions()).isEqualTo(2);
        assertThat(posterior.timestampMs()).isEqualTo(1000L);
        assertThat(posterior.version()).isEqualTo(1);
        assertThat(posterior.mean()).containsExactly(0.5f, -0.5f);
        assertThat(posterior.precision()).containsExactly(2.0f, 3.0f);

        // Mutating external array does not alter record
        mean[0] = 99.0f;
        assertThat(posterior.mean()[0]).isEqualTo(0.5f);
    }

    @Test
    void invalidPrecisions_throwValidationException() {
        float[] mean = {0.0f};
        float[] zeroPrec = {0.0f};
        float[] negPrec = {-1.0f};
        float[] nanPrec = {Float.NaN};

        assertThatThrownBy(() -> new MentalStatePosterior(mean, zeroPrec, 0L, 0))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new MentalStatePosterior(mean, negPrec, 0L, 0))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new MentalStatePosterior(mean, nanPrec, 0L, 0))
                .isInstanceOf(SpectorValidationException.class);
    }

    @Test
    void withEvidence_updatesMeanAndPrecision() {
        float[] mean = {0.0f};
        float[] prec = {2.0f};
        MentalStatePosterior prior = new MentalStatePosterior(mean, prec, 1000L, 1);

        float[] evidenceMean = {4.0f};
        float[] evidencePrec = {2.0f};

        MentalStatePosterior updated = prior.withEvidence(evidenceMean, evidencePrec, 2000L, 2);

        // Fused prec = 2+2=4. Fused mean = (2*0 + 2*4)/4 = 2.0
        assertThat(updated.precision()[0]).isEqualTo(4.0f);
        assertThat(updated.mean()[0]).isEqualTo(2.0f);
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.timestampMs()).isEqualTo(2000L);
    }

    @Test
    void decayTowards_blendsMeanAndPrecisionBackToPrior() {
        float[] mean = {4.0f};
        float[] prec = {8.0f};
        MentalStatePosterior current = new MentalStatePosterior(mean, prec, 1000L, 1);

        float[] priorMean = {0.0f};
        float[] priorPrec = {2.0f};

        MentalStatePosterior decayed = current.decayTowards(priorMean, priorPrec, 0.5f, 2000L, 2);

        // mean = 0.5 * 4 + 0.5 * 0 = 2.0
        // prec = 0.5 * 8 + 0.5 * 2 = 5.0
        assertThat(decayed.mean()[0]).isCloseTo(2.0f, within(1e-5f));
        assertThat(decayed.precision()[0]).isCloseTo(5.0f, within(1e-5f));
    }
}
