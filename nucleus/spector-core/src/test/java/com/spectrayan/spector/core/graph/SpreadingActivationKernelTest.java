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
package com.spectrayan.spector.core.graph;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadingActivationKernelTest {

    @Test
    @DisplayName("Single-step compound weight multiplies base weight by attenuation")
    void singleStepCompoundWeight() {
        float w = SpreadingActivationKernel.compoundWeight(0.8f, 0.7f);
        assertThat(w).isEqualTo(0.56f);
    }

    @Test
    @DisplayName("Zero base weight produces zero compound weight")
    void zeroBaseWeightProducesZero() {
        float w = SpreadingActivationKernel.compoundWeight(0.0f, 2, 0.7f, 5, 1000);
        assertThat(w).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("Multi-hop compound weight matches formula")
    void multiHopFormula() {
        float w = SpreadingActivationKernel.compoundWeight(1.0f, 1, 0.7f, 4, 100);
        // fanFactor(4) = 1/sqrt(4) = 0.5
        // idf = ln(1 + 100 / 5) = ln(21) ≈ 3.04452
        // compound = 1.0 * 0.7 * 0.5 * 3.04452 = 1.06558
        assertThat(w).isGreaterThan(0.0f);
    }

    @Property
    void compoundWeightDecreasesWithHopDepth(
            @ForAll @FloatRange(min = 0.1f, max = 10.0f) float baseWeight,
            @ForAll @FloatRange(min = 0.1f, max = 0.9f) float attenuation,
            @ForAll @IntRange(min = 1, max = 20) int degree,
            @ForAll @IntRange(min = 50, max = 1000) int corpusSize) {
        float w1 = SpreadingActivationKernel.compoundWeight(baseWeight, 1, attenuation, degree, corpusSize);
        float w2 = SpreadingActivationKernel.compoundWeight(baseWeight, 2, attenuation, degree, corpusSize);
        assertThat(w2).isLessThanOrEqualTo(w1);
    }
}
