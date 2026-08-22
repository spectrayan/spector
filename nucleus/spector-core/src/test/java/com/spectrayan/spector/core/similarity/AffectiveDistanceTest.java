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
package com.spectrayan.spector.core.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link AffectiveDistance} SIMD kernel.
 */
class AffectiveDistanceTest {

    @Test
    void identicalStates_returnsOne() {
        float[] state = {0.5f, -0.3f, 0.8f};
        assertThat(AffectiveDistance.compute(state, state, 0.5f))
                .isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    void orthogonalStates_returnsLessThanOne() {
        float[] stateA = {1.0f, 0.0f, 0.0f};
        float[] stateB = {0.0f, 1.0f, 0.0f};
        float result = AffectiveDistance.compute(stateA, stateB, 0.5f);
        // exp(-2 / (2 * 0.25)) = exp(-4) ≈ 0.0183
        assertThat(result).isGreaterThan(0.0f).isLessThan(0.5f);
    }

    @Test
    void largerSigma_broadensKernel() {
        float[] stateA = {1.0f, 0.0f};
        float[] stateB = {0.0f, 1.0f};
        float narrowResult = AffectiveDistance.compute(stateA, stateB, 0.3f);
        float broadResult = AffectiveDistance.compute(stateA, stateB, 2.0f);
        // Broader sigma should yield higher resonance for same distance
        assertThat(broadResult).isGreaterThan(narrowResult);
    }

    @Test
    void sliceCompute_matchesFullCompute() {
        float[] a = {0.0f, 0.5f, -0.3f, 0.8f, 0.0f};
        float[] b = {0.0f, 0.2f, 0.1f, -0.4f, 0.0f};
        float full = AffectiveDistance.compute(
                new float[]{0.5f, -0.3f, 0.8f},
                new float[]{0.2f, 0.1f, -0.4f}, 0.5f);
        float slice = AffectiveDistance.compute(a, 1, b, 1, 3, 0.5f);
        assertThat(slice).isCloseTo(full, within(1e-6f));
    }

    @Test
    void computeBatch_returnsCorrectLength() {
        float[] current = {0.5f, -0.2f, 0.3f};
        float[][] candidates = {
                {0.5f, -0.2f, 0.3f},
                {0.0f, 0.0f, 0.0f},
                {-1.0f, 1.0f, -1.0f}
        };
        float[] results = AffectiveDistance.computeBatch(current, candidates, 0.5f);
        assertThat(results).hasSize(3);
        // First should be ~1.0 (identical)
        assertThat(results[0]).isCloseTo(1.0f, within(1e-5f));
        // Others should be less
        assertThat(results[1]).isLessThan(results[0]);
        assertThat(results[2]).isLessThan(results[1]);
    }

    @Test
    void mismatchedLengths_throwsException() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f, 2.0f, 3.0f};
        assertThatThrownBy(() -> AffectiveDistance.compute(a, b, 0.5f))
                .isInstanceOf(SpectorValidationException.class);
    }

    @Test
    void singleDimension_worksCorrectly() {
        float[] a = {0.8f};
        float[] b = {-0.8f};
        float result = AffectiveDistance.compute(a, b, 1.0f);
        // exp(-(1.6)^2 / (2*1)) = exp(-1.28) ≈ 0.278
        assertThat(result).isCloseTo(0.278f, within(0.01f));
    }

    @Test
    void highDimensional_handlesSimdTailCorrectly() {
        // Test with non-SIMD-aligned dimension count (e.g. 13)
        float[] a = new float[13];
        float[] b = new float[13];
        for (int i = 0; i < 13; i++) {
            a[i] = i * 0.1f;
            b[i] = i * 0.1f;
        }
        assertThat(AffectiveDistance.compute(a, b, 0.5f))
                .isCloseTo(1.0f, within(1e-5f));
    }
}
