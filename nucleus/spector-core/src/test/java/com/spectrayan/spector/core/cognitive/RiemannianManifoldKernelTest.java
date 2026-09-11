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
package com.spectrayan.spector.core.cognitive;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiemannianManifoldKernelTest {

    @Test
    @DisplayName("Diagonal metric increases along active perturbation dimension")
    void updateDiagonalMetricIncreases() {
        float[] diag = {1.0f, 1.0f};
        float[][] diffs = {{2.0f, 0.0f}};
        RiemannianManifoldKernel.updateDiagonalMetric(diag, diffs, 0.1f);

        // diag[0] += 0.1 * 4.0 = 0.4 -> 1.4f
        assertThat(diag[0]).isCloseTo(1.4f, org.assertj.core.data.Offset.offset(1e-5f));
        // diag[1] unchanged -> 1.0f
        assertThat(diag[1]).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-5f));
    }

    @Test
    @DisplayName("Update low-rank components adds new component when below maxRank")
    void updateLowRankAddsComponent() {
        float[][] existing = new float[0][];
        float[] diff = {1.0f, 0.0f, 0.0f};
        float[][] updated = RiemannianManifoldKernel.updateLowRankComponents(existing, diff, 0.04f, 4);

        assertThat(updated).hasDimensions(1, 3);
        // invNorm = sqrt(0.04)/1 = 0.2
        assertThat(updated[0][0]).isCloseTo(0.2f, org.assertj.core.data.Offset.offset(1e-5f));
    }

    @Property
    void diagonalMetricNeverDropsBelowMinimum(
            @ForAll @FloatRange(min = 0.01f, max = 5.0f) float initial,
            @ForAll @FloatRange(min = -10.0f, max = 10.0f) float diff) {
        float[] diag = {initial};
        float[][] diffs = {{diff}};
        RiemannianManifoldKernel.updateDiagonalMetric(diag, diffs, 0.05f);
        assertThat(diag[0]).isGreaterThanOrEqualTo(RiemannianManifoldKernel.MIN_DIAGONAL_SCALE);
    }
}
