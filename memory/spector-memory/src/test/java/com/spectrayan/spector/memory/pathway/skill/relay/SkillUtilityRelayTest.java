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
package com.spectrayan.spector.memory.pathway.skill.relay;

import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@DisplayName("ADR-0086 §5.8, §8: SkillUtilityRelay Tests")
class SkillUtilityRelayTest {

    @Test
    @DisplayName("Without an outcome signal (reward == 0, duplicateOf == null), REINFORCE is an intentional no-op")
    void noOpWithoutOutcome() {
        SkillUtilityRelay relay = new SkillUtilityRelay(0.1f);

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .skillId("skill-target-1")
                .reward(0.0f)
                .build();

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(relay.utilityFor("skill-target-1")).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("Outcome reward triggers delta update scaled by utility-alpha")
    void deltaUpdateWithPositiveReward() {
        SkillUtilityRelay relay = new SkillUtilityRelay(0.2f);

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .skillId("skill-target-1")
                .reward(0.8f)
                .build();

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(relay.utilityFor("skill-target-1")).isCloseTo(0.16f, within(0.0001f));
    }

    @Test
    @DisplayName("Negative reward outcome decreases utility score")
    void negativeRewardDecreasesUtility() {
        SkillUtilityRelay relay = new SkillUtilityRelay(0.1f);

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .skillId("skill-target-1")
                .reward(-0.5f)
                .build();

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(relay.utilityFor("skill-target-1")).isCloseTo(-0.05f, within(0.0001f));
    }

    @Test
    @DisplayName("Near-duplicate reroute without explicit reward is a no-op (outcome gate)")
    void duplicateRerouteWithoutRewardIsNoOp() {
        SkillUtilityRelay relay = new SkillUtilityRelay(0.15f);

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .build();
        signal.duplicateOf("skill-existing-99");

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(relay.utilityFor("skill-existing-99")).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("Near-duplicate reroute with outcome reward updates duplicate utility")
    void duplicateRerouteWithRewardUpdatesUtility() {
        SkillUtilityRelay relay = new SkillUtilityRelay(0.15f);

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .reward(1.0f)
                .build();
        signal.duplicateOf("skill-existing-99");

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(relay.utilityFor("skill-existing-99")).isCloseTo(0.15f, within(0.0001f));
    }

    @Test
    @DisplayName("COMPILE mode passes through untouched without mutating utility")
    void compileModePassesThrough() {
        SkillUtilityRelay relay = new SkillUtilityRelay(0.2f);

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .skillId("skill-compile-1")
                .reward(1.0f)
                .build();

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(relay.utilityFor("skill-compile-1")).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("Updates storageStrength and agent recall count in StrengthMemory when present")
    void updatesStrengthMemoryWhenPresent() {
        StrengthMemory strength = StrengthMemory.heap(10, 10, 10);
        strength.initializeDefault(MemoryType.PROCEDURAL, 2, 0.5f);
        assertThat(strength.readStorageStrength(MemoryType.PROCEDURAL, 2)).isEqualTo(1.0f);

        MemoryIndex mockIndex = Mockito.mock(MemoryIndex.class);
        when(mockIndex.locate("skill-proc-42")).thenReturn(new MemoryLocation(MemoryType.PROCEDURAL, 0L, 2));

        SkillUtilityRelay relay = new SkillUtilityRelay(0.25f, strength);

        var ctx = DefaultPathwayContext.builder()
                .bind(MemoryIndex.class, mockIndex)
                .bind(StrengthMemory.class, strength)
                .build();

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .skillId("skill-proc-42")
                .reward(1.0f)
                .build();
        signal.bind(ctx);

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(relay.utilityFor("skill-proc-42")).isCloseTo(0.25f, within(0.0001f));
        assertThat(strength.readAgentRecallCount(MemoryType.PROCEDURAL, 2)).isEqualTo(1);
        assertThat(strength.readStorageStrength(MemoryType.PROCEDURAL, 2)).isCloseTo(1.25f, within(0.0001f));
    }
}
