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
package com.spectrayan.spector.memory.aisme.pcmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TierPrediction}.
 */
class TierPredictionTest {

    @Test
    void validConstruction_defensiveCopyAndAccessors() {
        float[] pred = {1.0f, 2.0f};
        float[] prec = {2.0f, 2.0f};

        TierPrediction tp = new TierPrediction(2, pred, prec, 1000L);

        assertThat(tp.tierLevel()).isEqualTo(2);
        assertThat(tp.dimensions()).isEqualTo(2);
        assertThat(tp.timestampMs()).isEqualTo(1000L);
        assertThat(tp.predictionVector()).containsExactly(1.0f, 2.0f);
        assertThat(tp.precisionWeights()).containsExactly(2.0f, 2.0f);

        pred[0] = 999.0f;
        assertThat(tp.predictionVector()[0]).isEqualTo(1.0f);
    }

    @Test
    void invalidArguments_throwValidationException() {
        float[] v1 = {1.0f};
        float[] v2 = {1.0f, 2.0f};

        assertThatThrownBy(() -> new TierPrediction(1, null, v1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new TierPrediction(1, v1, null, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new TierPrediction(1, v1, v2, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new TierPrediction(1, new float[0], new float[0], 0L))
                .isInstanceOf(SpectorValidationException.class);
    }
}
