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
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeImportanceKernelTest {

    @Test
    @DisplayName("Protected nodes increase importance score by protection boost")
    void protectionBoostIncreasesScore() {
        float scoreUnprotected = EdgeImportanceKernel.score(
                3.0f, 10, 10, 128, 2,
                5.0f, 5.0f, (byte) 100, (byte) 100, (byte) 0, (byte) 0,
                2.0f, 2.0f, false, false, null);

        float scoreProtected = EdgeImportanceKernel.score(
                3.0f, 10, 10, 128, 2,
                5.0f, 5.0f, (byte) 100, (byte) 100, (byte) 0, (byte) 0,
                2.0f, 2.0f, true, false, null);

        assertThat(scoreProtected).isGreaterThan(scoreUnprotected);
    }

    @Test
    @DisplayName("Structural score produces normalized weight sum")
    void structuralScoreProducesPositiveValue() {
        float score = EdgeImportanceKernel.scoreStructural(3.0f, 10, 10, 128, 2, null);
        assertThat(score).isGreaterThan(0.0f).isLessThanOrEqualTo(1.0f);
    }

    @Property
    void edgeScoreIsNonNegative(
            @ForAll @FloatRange(min = 0.0f, max = 20.0f) float weight,
            @ForAll @IntRange(min = 0, max = 100) int cycleDelta,
            @ForAll @IntRange(min = 0, max = 255) int bridgeScore,
            @ForAll @IntRange(min = 0, max = 50) int sharedNeighbors) {
        float score = EdgeImportanceKernel.scoreStructural(weight, cycleDelta, 0, bridgeScore, sharedNeighbors, null);
        assertThat(score).isGreaterThanOrEqualTo(0.0f);
    }
}
