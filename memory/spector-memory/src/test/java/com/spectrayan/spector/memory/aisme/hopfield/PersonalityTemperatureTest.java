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
package com.spectrayan.spector.memory.aisme.hopfield;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PersonalityTemperature}.
 */
class PersonalityTemperatureTest {

    @Test
    void deriveBeta_hyperfocus_sharpTemperature() {
        float beta = PersonalityTemperature.deriveBeta(CognitiveProfile.HYPERFOCUS, 0.0f);
        assertThat(beta).isEqualTo(12.0f);
    }

    @Test
    void deriveBeta_divergent_broadTemperature() {
        float beta = PersonalityTemperature.deriveBeta(CognitiveProfile.DIVERGENT, 0.0f);
        assertThat(beta).isEqualTo(1.0f);
    }

    @Test
    void deriveBeta_arousalModulation_highArousalIncreasesBeta() {
        float betaCalm = PersonalityTemperature.deriveBeta(CognitiveProfile.BALANCED, -1.0f);
        float betaNeutral = PersonalityTemperature.deriveBeta(CognitiveProfile.BALANCED, 0.0f);
        float betaAroused = PersonalityTemperature.deriveBeta(CognitiveProfile.BALANCED, 1.0f);

        assertThat(betaCalm).isLessThan(betaNeutral);
        assertThat(betaNeutral).isLessThan(betaAroused);
    }

    @Test
    void deriveBeta_withInteroceptiveState() {
        InteroceptiveState state = new InteroceptiveState(0f, 0.8f, 0f, new float[0], 0L, 0);
        float beta = PersonalityTemperature.deriveBeta(CognitiveProfile.BALANCED, state);
        assertThat(beta).isGreaterThan(4.0f);
    }
}
