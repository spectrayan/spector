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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.kernel.store.ContinuityMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WanderGatesTest {

    @Test
    void isIdleGateEvaluation() {
        long now = System.currentTimeMillis();
        WanderSignal activeSignal = WanderSignal.builder()
                .lastActivityTimestampMs(now)
                .idleThresholdSeconds(60)
                .build();
        assertThat(WanderGates.IS_IDLE.isSatisfiedBy(activeSignal)).isFalse();

        WanderSignal idleSignal = WanderSignal.builder()
                .lastActivityTimestampMs(now - 120_000L)
                .idleThresholdSeconds(60)
                .build();
        assertThat(WanderGates.IS_IDLE.isSatisfiedBy(idleSignal)).isTrue();
    }

    @Test
    void dmnAndManifoldGatesEvaluation() {
        AismeProperties disabledConfig = AismeProperties.disabled();
        WanderSignal disabledSignal = WanderSignal.builder()
                .aismeConfig(disabledConfig)
                .build();
        assertThat(WanderGates.DMN_ENABLED.isSatisfiedBy(disabledSignal)).isFalse();
        assertThat(WanderGates.MANIFOLD_ENABLED.isSatisfiedBy(disabledSignal)).isFalse();

        AismeProperties enabledConfig = AismeProperties.defaultConfig();
        WanderSignal enabledSignal = WanderSignal.builder()
                .aismeConfig(enabledConfig)
                .build();
        assertThat(WanderGates.DMN_ENABLED.isSatisfiedBy(enabledSignal)).isTrue();
        assertThat(WanderGates.MANIFOLD_ENABLED.isSatisfiedBy(enabledSignal)).isTrue();
    }

    @Test
    void continuityGateEvaluation() {
        WanderSignal noMemorySignal = WanderSignal.builder()
                .aismeConfig(AismeProperties.defaultConfig())
                .build();
        assertThat(WanderGates.CONTINUITY_ENABLED.isSatisfiedBy(noMemorySignal)).isFalse();

        try (ContinuityMemory memory = ContinuityMemory.heap(10)) {
            WanderSignal memorySignal = WanderSignal.builder()
                    .aismeConfig(AismeProperties.defaultConfig())
                    .continuityMemory(memory)
                    .build();
            assertThat(WanderGates.CONTINUITY_ENABLED.isSatisfiedBy(memorySignal)).isTrue();
        }
    }
}
