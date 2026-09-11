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
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit and property-based tests for {@link WelfordAccumulator}.
 */
class WelfordAccumulatorTest {

    @Test
    void emptyAccumulatorHasNeutralDefaults() {
        WelfordAccumulator acc = WelfordAccumulator.EMPTY;
        assertThat(acc.count()).isEqualTo(0L);
        assertThat(acc.mean()).isEqualTo(0.0);
        assertThat(acc.m2()).isEqualTo(0.0);
        assertThat(acc.variance()).isEqualTo(0.0);
        assertThat(acc.stdDev()).isEqualTo(0.0);
        assertThat(acc.zScore(10.0)).isEqualTo(0.0);
        assertThat(acc.isWarm(1)).isFalse();
    }

    @Test
    void singleObservationHasZeroVariance() {
        WelfordAccumulator acc = WelfordAccumulator.EMPTY.update(42.0);
        assertThat(acc.count()).isEqualTo(1L);
        assertThat(acc.mean()).isEqualTo(42.0);
        assertThat(acc.m2()).isEqualTo(0.0);
        assertThat(acc.variance()).isEqualTo(0.0);
        assertThat(acc.stdDev()).isEqualTo(0.0);
        assertThat(acc.zScore(42.0)).isEqualTo(0.0);
        assertThat(acc.isWarm(1)).isTrue();
        assertThat(acc.isWarm(2)).isFalse();
    }

    @Test
    void knownDatasetMatchesPopulationStatistics() {
        // Data: [2, 4, 4, 4, 5, 5, 7, 9] -> N=8, Mean=5.0, Population Var = 4.0, StdDev = 2.0
        double[] data = {2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0};
        WelfordAccumulator acc = WelfordAccumulator.EMPTY;
        for (double d : data) {
            acc = acc.update(d);
        }

        assertThat(acc.count()).isEqualTo(8L);
        assertThat(acc.mean()).isCloseTo(5.0, within(1e-9));
        assertThat(acc.variance()).isCloseTo(4.0, within(1e-9));
        assertThat(acc.stdDev()).isCloseTo(2.0, within(1e-9));
        assertThat(acc.zScore(7.0)).isCloseTo(1.0, within(1e-9));
        assertThat(acc.zScore(1.0)).isCloseTo(-2.0, within(1e-9));
        assertThat(acc.isWarm(8)).isTrue();
        assertThat(acc.isWarm(9)).isFalse();
    }

    @Test
    void immutabilityGuaranteed() {
        WelfordAccumulator empty = WelfordAccumulator.EMPTY;
        WelfordAccumulator one = empty.update(10.0);

        assertThat(empty.count()).isEqualTo(0L);
        assertThat(one.count()).isEqualTo(1L);
    }

    @Property(tries = 100)
    void meanMatchesTwoPassSum(
            @ForAll @Size(min = 1, max = 50) List<@DoubleRange(min = -1e4, max = 1e4) Double> samples
    ) {
        WelfordAccumulator acc = WelfordAccumulator.EMPTY;
        double sum = 0.0;
        for (double s : samples) {
            acc = acc.update(s);
            sum += s;
        }

        double expectedMean = sum / samples.size();
        assertThat(acc.count()).isEqualTo(samples.size());
        assertThat(acc.mean()).isCloseTo(expectedMean, within(1e-5));
        assertThat(acc.variance()).isGreaterThanOrEqualTo(0.0);
    }
}
