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
package com.spectrayan.spector.core.math;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit and property-based tests for {@link SoftmaxKernel}.
 */
class SoftmaxKernelTest {

    @Test
    void nullAndEmptyHandledGracefully() {
        SoftmaxKernel.computeProbabilities(null, 1.0f, null);
        SoftmaxKernel.computeProbabilitiesScaled(null, 1.0f, null);
        SoftmaxKernel.applySoftmaxTemperature(null, 1.0f);

        float[] empty = new float[0];
        SoftmaxKernel.computeProbabilities(empty, 1.0f, empty);
        SoftmaxKernel.computeProbabilitiesScaled(empty, 1.0f, empty);
        SoftmaxKernel.applySoftmaxTemperature(empty, 1.0f);
    }

    @Test
    void singleElementAlwaysHasProbabilityOne() {
        float[] scores = {42.0f};
        float[] probs = new float[1];
        SoftmaxKernel.computeProbabilities(scores, 0.5f, probs);
        assertThat(probs[0]).isEqualTo(1.0f);

        SoftmaxKernel.computeProbabilitiesScaled(scores, -2.0f, probs);
        assertThat(probs[0]).isEqualTo(1.0f);
    }

    @Test
    void uniformScoresYieldUniformProbabilities() {
        float[] scores = {5.0f, 5.0f, 5.0f, 5.0f};
        float[] probs = new float[4];
        SoftmaxKernel.computeProbabilities(scores, 1.0f, probs);

        for (float p : probs) {
            assertThat(p).isCloseTo(0.25f, within(1e-6f));
        }
    }

    @Test
    void largeInputsDoNotOverflowDueToMaxShiftStabilization() {
        // Raw exp(1000.0f) is Float.POSITIVE_INFINITY. Without max-shift, sum is NaN / Inf.
        float[] largeScores = {1000.0f, 1001.0f, 1002.0f};
        float[] probs = new float[3];
        SoftmaxKernel.computeProbabilities(largeScores, 1.0f, probs);

        assertThat(Float.isNaN(probs[0])).isFalse();
        assertThat(Float.isNaN(probs[1])).isFalse();
        assertThat(Float.isNaN(probs[2])).isFalse();

        // Relative differences are -2, -1, 0 -> exp(-2), exp(-1), 1
        double sum = Math.exp(-2) + Math.exp(-1) + 1.0;
        assertThat(probs[0]).isCloseTo((float) (Math.exp(-2) / sum), within(1e-5f));
        assertThat(probs[1]).isCloseTo((float) (Math.exp(-1) / sum), within(1e-5f));
        assertThat(probs[2]).isCloseTo((float) (1.0 / sum), within(1e-5f));
        assertThat(probs[0] + probs[1] + probs[2]).isCloseTo(1.0f, within(1e-6f));
    }

    @Test
    void scaledProbabilitiesHandleNegativeBetaWithoutOverflow() {
        float[] logits = {100.0f, 120.0f, 150.0f};
        float beta = -0.5f; // As used in active inference / policy evaluation
        float[] probs = new float[3];
        SoftmaxKernel.computeProbabilitiesScaled(logits, beta, probs);

        // Lower logit gives higher probability with negative beta
        assertThat(probs[0]).isGreaterThan(probs[1]);
        assertThat(probs[1]).isGreaterThan(probs[2]);
        assertThat(probs[0] + probs[1] + probs[2]).isCloseTo(1.0f, within(1e-6f));
    }

    @Test
    void applySoftmaxTemperatureModulatesAndPreservesMass() {
        float[] scores = {1.0f, 2.0f, 3.0f};
        float originalSum = 1.0f + 2.0f + 3.0f;

        // T=1.0 is identity bypass
        float[] copy = scores.clone();
        SoftmaxKernel.applySoftmaxTemperature(copy, 1.0f);
        assertThat(copy).containsExactly(1.0f, 2.0f, 3.0f);

        // T=0.5 sharpens towards highest score
        float[] sharpened = scores.clone();
        SoftmaxKernel.applySoftmaxTemperature(sharpened, 0.5f);
        float sharpSum = sharpened[0] + sharpened[1] + sharpened[2];
        assertThat(sharpSum).isCloseTo(originalSum, within(1e-5f));
        assertThat(sharpened[2]).isGreaterThan(scores[2]);
    }

    @Test
    void adaptiveTemperatureClampingAndModulation() {
        // baseline=1.0, kappa=0.5, min=0.5, max=2.0
        // zSurprise <= 0 -> effective = 1.0
        assertThat(SoftmaxKernel.adaptiveTemperature(1.0f, -0.5, 0.5f, 0.5f, 2.0f)).isEqualTo(1.0f);
        // zSurprise = 1.0 -> 1.0 * (1 + 0.5 * 1.0) = 1.5
        assertThat(SoftmaxKernel.adaptiveTemperature(1.0f, 1.0, 0.5f, 0.5f, 2.0f)).isEqualTo(1.5f);
        // zSurprise = 10.0 -> 1.0 * (1 + 0.5 * 10) = 6.0 -> clamped to max 2.0
        assertThat(SoftmaxKernel.adaptiveTemperature(1.0f, 10.0, 0.5f, 0.5f, 2.0f)).isEqualTo(2.0f);
    }

    @Property(tries = 100)
    void probabilitiesAlwaysFormValidSimplex(
            @ForAll @Size(min = 2, max = 50) List<@FloatRange(min = -500.0f, max = 500.0f) Float> inputScores,
            @ForAll @FloatRange(min = 0.01f, max = 10.0f) float temperature
    ) {
        float[] scores = new float[inputScores.size()];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = inputScores.get(i);
        }
        float[] probs = new float[scores.length];
        SoftmaxKernel.computeProbabilities(scores, temperature, probs);

        float sum = 0.0f;
        for (float p : probs) {
            assertThat(p).isGreaterThanOrEqualTo(0.0f);
            assertThat(Float.isNaN(p)).isFalse();
            sum += p;
        }
        assertThat(sum).isCloseTo(1.0f, within(1e-5f));
    }
}
