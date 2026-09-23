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

import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import com.spectrayan.spector.memory.pathway.skill.model.SkillKind;
import com.spectrayan.spector.memory.pathway.skill.model.SkillMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0086 §5.2, §5.7: SkillDedupRelay Tests")
class SkillDedupRelayTest {

    private SkillDedupRelay relay;
    private SkillBody sampleBody;

    @BeforeEach
    void setUp() {
        relay = new SkillDedupRelay();
        sampleBody = new SkillBody(
                new SkillMeta(SkillMeta.SCHEMA_V1, "git-rebase-flow", SkillKind.PLAYBOOK, 0.5f, List.of(), Map.of()),
                "# Git Rebase Flow"
        );
    }

    @Test
    @DisplayName("Candidate with cosine similarity >= 0.88 is rerouted to REINFORCE")
    void nearDuplicateReroutedToReinforce() {
        relay.registerSkillVector("skill-existing-1", new float[]{1.0f, 0.0f, 0.0f});

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .extractedBody(sampleBody)
                .build();
        signal.vector(new float[]{0.99f, 0.05f, 0.0f}); // Cosine sim ~0.998 >= 0.88

        assertThat(relay.transmit(signal)).isTrue();
        assertThat(signal.mode()).isEqualTo(SkillSignal.Mode.REINFORCE);
        assertThat(signal.duplicateOf()).isEqualTo("skill-existing-1");
        assertThat(signal.persistedSkillId()).isEqualTo("skill-existing-1");
    }

    @Test
    @DisplayName("Distinct candidate with cosine similarity < 0.88 remains COMPILE")
    void distinctCandidateRemainsCompile() {
        relay.registerSkillVector("skill-existing-1", new float[]{1.0f, 0.0f, 0.0f});

        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .extractedBody(sampleBody)
                .build();
        signal.vector(new float[]{0.0f, 1.0f, 0.0f}); // Orthogonal, sim = 0.0

        assertThat(relay.transmit(signal)).isTrue();
        assertThat(signal.mode()).isEqualTo(SkillSignal.Mode.COMPILE);
        assertThat(signal.duplicateOf()).isNull();
    }

    @Test
    @DisplayName("REINFORCE mode passes through untouched")
    void reinforceModePassesThrough() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .skillId("skill-target")
                .reward(1.0f)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
        assertThat(signal.mode()).isEqualTo(SkillSignal.Mode.REINFORCE);
    }
}
