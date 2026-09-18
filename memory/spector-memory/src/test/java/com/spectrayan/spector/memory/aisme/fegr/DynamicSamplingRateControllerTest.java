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
package com.spectrayan.spector.memory.aisme.fegr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DynamicSamplingRateController}.
 */
class DynamicSamplingRateControllerTest {

    @Test
    @DisplayName("computeSamplingRate: scales smoothly from min to max across event densities")
    void computeSamplingRate_scalesWithinBounds() {
        DynamicSamplingRateController controller = new DynamicSamplingRateController(0.1f, 30.0f, 0.50f, 0.10f);

        float rateZero = controller.computeSamplingRate(0.0f);
        float rateMid = controller.computeSamplingRate(0.50f);
        float rateHigh = controller.computeSamplingRate(1.5f);

        assertThat(rateZero).isGreaterThanOrEqualTo(0.1f).isLessThan(1.0f);
        assertThat(rateMid).isCloseTo(15.05f, org.assertj.core.data.Offset.offset(0.5f));
        assertThat(rateHigh).isCloseTo(30.0f, org.assertj.core.data.Offset.offset(0.5f));
    }

    @Test
    @DisplayName("Validation: rejects invalid bounds or non-positive temperatures")
    void validation_rejectsInvalidConfig() {
        assertThatThrownBy(() -> new DynamicSamplingRateController(0.0f, 30.0f, 0.5f))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> new DynamicSamplingRateController(10.0f, 5.0f, 0.5f))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> new DynamicSamplingRateController(0.1f, 30.0f, -0.1f))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> new DynamicSamplingRateController(0.1f, 30.0f, 0.5f, 0.0f))
                .isInstanceOf(SpectorValidationException.class);
    }
}
