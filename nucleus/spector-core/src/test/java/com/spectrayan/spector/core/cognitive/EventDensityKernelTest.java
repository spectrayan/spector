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

class EventDensityKernelTest {

    @Test
    @DisplayName("Event density is exact linear combination")
    void eventDensityLinearCombination() {
        float density = EventDensityKernel.computeEventDensity(1.0f, 2.0f, 3.0f, 0.5f, 0.3f, 0.2f);
        // 0.5 * 1.0 + 0.3 * 2.0 + 0.2 * 3.0 = 0.5 + 0.6 + 0.6 = 1.7f
        assertThat(density).isCloseTo(1.7f, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    @DisplayName("Dynamic sampling rate at threshold is midpoint between min and max")
    void samplingRateMidpointAtThreshold() {
        float rate = EventDensityKernel.computeDynamicSamplingRate(0.5f, 0.5f, 0.15f, 1.0f, 10.0f);
        // sigmoid(0) = 0.5 -> 1.0 + 9.0 * 0.5 = 5.5f
        assertThat(rate).isCloseTo(5.5f, org.assertj.core.data.Offset.offset(1e-5f));
    }

    @Property
    void dynamicSamplingRateAlwaysWithinBounds(
            @ForAll @FloatRange(min = -10.0f, max = 10.0f) float density,
            @ForAll @FloatRange(min = 0.1f, max = 10.0f) float minHz,
            @ForAll @FloatRange(min = 10.0f, max = 100.0f) float maxHz) {
        float rate = EventDensityKernel.computeDynamicSamplingRate(density, 0.5f, 0.15f, minHz, maxHz);
        assertThat(rate).isBetween(minHz, maxHz);
    }
}
