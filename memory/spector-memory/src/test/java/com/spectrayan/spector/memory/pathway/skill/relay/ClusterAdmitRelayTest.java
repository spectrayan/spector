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

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import com.spectrayan.spector.memory.pathway.skill.model.SkillKind;
import com.spectrayan.spector.memory.pathway.skill.model.SkillMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0086 §5.2, §5.6: ClusterAdmitRelay Tests")
class ClusterAdmitRelayTest {

    private ClusterAdmitRelay relay;

    @BeforeEach
    void setUp() {
        relay = new ClusterAdmitRelay();
    }

    @Test
    @DisplayName("REINFORCE mode bypasses cluster admission criteria")
    void reinforceBypassesAdmission() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .skillId("skill-123")
                .reward(1.0f)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
    }

    @Test
    @DisplayName("Episodic cluster with < 2 turns is rejected")
    void episodicClusterInsufficientTurnsRejected() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .parent("ep-1", MemoryType.EPISODIC)
                .build();

        assertThat(relay.transmit(signal)).isFalse();
    }

    @Test
    @DisplayName("Episodic cluster with >= 2 turns is admitted")
    void episodicClusterSufficientTurnsAdmitted() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .parent("ep-1", MemoryType.EPISODIC)
                .parent("ep-2", MemoryType.EPISODIC)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
    }

    @Test
    @DisplayName("Semantic cluster with < 3 facts is rejected")
    void semanticClusterInsufficientFactsRejected() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .parent("sem-1", MemoryType.SEMANTIC)
                .parent("sem-2", MemoryType.SEMANTIC)
                .build();

        assertThat(relay.transmit(signal)).isFalse();
    }

    @Test
    @DisplayName("Semantic cluster with >= 3 facts is admitted")
    void semanticClusterSufficientFactsAdmitted() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .parent("sem-1", MemoryType.SEMANTIC)
                .parent("sem-2", MemoryType.SEMANTIC)
                .parent("sem-3", MemoryType.SEMANTIC)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
    }

    @Test
    @DisplayName("Mixed cluster with >= 1 episodic turn and >= 1 semantic fact is admitted")
    void mixedClusterAdmitted() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .parent("ep-1", MemoryType.EPISODIC)
                .parent("sem-1", MemoryType.SEMANTIC)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
    }

    @Test
    @DisplayName("Pre-extracted body with parent reference is admitted")
    void preExtractedBodyAdmitted() {
        SkillBody body = new SkillBody(
                new SkillMeta(SkillMeta.SCHEMA_V1, "test-skill", SkillKind.HEURISTIC, 0.5f, List.of(), java.util.Map.of()),
                "# Test Body"
        );
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .parent("ep-1", MemoryType.EPISODIC)
                .extractedBody(body)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
    }
}
