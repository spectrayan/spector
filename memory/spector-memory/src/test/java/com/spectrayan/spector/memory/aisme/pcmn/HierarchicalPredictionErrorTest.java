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
 * Unit tests for {@link HierarchicalPredictionError}.
 */
class HierarchicalPredictionErrorTest {

    @Test
    void validConstruction_accessorsAndDefensiveCopy() {
        float[][] errors = {
                {0.5f, -0.5f},
                {1.0f, -1.0f}
        };
        float[] energies = {0.25f, 1.0f};

        HierarchicalPredictionError hpe = new HierarchicalPredictionError(errors, energies, 1.25f, 1000L);

        assertThat(hpe.tierCount()).isEqualTo(2);
        assertThat(hpe.totalEnergy()).isEqualTo(1.25f);
        assertThat(hpe.timestampMs()).isEqualTo(1000L);
        assertThat(hpe.tierEnergies()).containsExactly(0.25f, 1.0f);

        errors[0][0] = 999.0f;
        assertThat(hpe.weightedErrorVectors()[0][0]).isEqualTo(0.5f);
    }

    @Test
    void invalidArguments_throwValidationException() {
        float[][] errors = {{1.0f}};
        float[] energies = {1.0f, 2.0f};

        assertThatThrownBy(() -> new HierarchicalPredictionError(null, energies, 0f, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new HierarchicalPredictionError(errors, null, 0f, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new HierarchicalPredictionError(errors, energies, 0f, 0L))
                .isInstanceOf(SpectorValidationException.class);
    }
}
