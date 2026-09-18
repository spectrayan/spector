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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.config.properties.DreamProperties;
import com.spectrayan.spector.kernel.shape.DistributedMemoryTensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DreamGatesTest {

    @Test
    void testDreamingEnabledGate() {
        DreamProperties enabledConfig = DreamProperties.defaultConfig();
        DreamSignal signalEnabled = DreamSignal.builder()
                .config(enabledConfig)
                .build();
        assertThat(DreamGates.DREAMING_ENABLED.isSatisfiedBy(signalEnabled)).isTrue();

        DreamProperties disabledConfig = DreamProperties.disabled();
        DreamSignal signalDisabled = DreamSignal.builder()
                .config(disabledConfig)
                .build();
        assertThat(DreamGates.DREAMING_ENABLED.isSatisfiedBy(signalDisabled)).isFalse();
    }

    @Test
    void testHasSeedsAndFragmentsGate() {
        DreamSignal signal = DreamSignal.builder()
                .config(DreamProperties.defaultConfig())
                .seedMemoryIds(List.of("seed-1", "seed-2"))
                .build();

        assertThat(DreamGates.HAS_SEEDS.isSatisfiedBy(signal)).isTrue();
        assertThat(DreamGates.HAS_FRAGMENTS.isSatisfiedBy(signal)).isFalse();

        signal.addFragment(new SceneFragment("seed-1", 1, "Agent", FragmentRole.AGENT, new float[]{0.1f}, (byte) 10, 20));
        assertThat(DreamGates.HAS_FRAGMENTS.isSatisfiedBy(signal)).isTrue();
    }

    @Test
    void testLangevinEnabledGate() {
        DreamSignal signalWithoutDmt = DreamSignal.builder()
                .config(DreamProperties.defaultConfig())
                .build();
        assertThat(DreamGates.LANGEVIN_ENABLED.isSatisfiedBy(signalWithoutDmt)).isFalse();

        try (DistributedMemoryTensor dmt = new DistributedMemoryTensor(8)) {
            DreamSignal signalWithDmt = DreamSignal.builder()
                    .config(DreamProperties.defaultConfig())
                    .distributedMemoryTensor(dmt)
                    .build();
            assertThat(DreamGates.LANGEVIN_ENABLED.isSatisfiedBy(signalWithDmt)).isTrue();
        }
    }
}
