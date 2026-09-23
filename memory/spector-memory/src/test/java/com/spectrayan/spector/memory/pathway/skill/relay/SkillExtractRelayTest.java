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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0086 §5.2, §5.4: SkillExtractRelay Tests")
class SkillExtractRelayTest {

    private SkillExtractRelay relay;

    @BeforeEach
    void setUp() {
        relay = new SkillExtractRelay();
    }

    @Test
    @DisplayName("REINFORCE mode bypasses extraction")
    void reinforceBypassesExtraction() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.REINFORCE)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
        assertThat(signal.extractedBody()).isNull();
    }

    @Test
    @DisplayName("Already extracted body is preserved")
    void alreadyExtractedBodyPreserved() {
        SkillBody body = new SkillBody(
                new SkillMeta(SkillMeta.SCHEMA_V1, "existing", SkillKind.PLAYBOOK, 0.9f, List.of(), Map.of()),
                "# Existing markdown"
        );
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .extractedBody(body)
                .build();

        assertThat(relay.transmit(signal)).isTrue();
        assertThat(signal.extractedBody()).isSameAs(body);
    }

    @Test
    @DisplayName("Synthesizes PLAYBOOK for episodic cluster with steps")
    void synthesizesPlaybookForEpisodicCluster() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .cue("Deploy service to production")
                .parent("ep-1", MemoryType.EPISODIC)
                .parent("ep-2", MemoryType.EPISODIC)
                .parentText("Checked git status and pulled main")
                .parentText("Executed build and ran smoke tests")
                .build();

        assertThat(relay.transmit(signal)).isTrue();
        SkillBody body = signal.extractedBody();
        assertThat(body).isNotNull();
        assertThat(body.hasMeta()).isTrue();
        assertThat(body.meta().name()).isEqualTo("deploy-service-to-production");
        assertThat(body.meta().kind()).isEqualTo(SkillKind.PLAYBOOK);
        assertThat(body.meta().confidence()).isEqualTo(0.35f);
        assertThat(body.meta().parents()).containsKey("episodic");
        assertThat(body.meta().parents().get("episodic")).containsExactly("ep-1", "ep-2");
        assertThat(body.body()).contains("## Execution Steps");
        assertThat(body.body()).contains("1. Checked git status and pulled main");
        assertThat(body.body()).contains("2. Executed build and ran smoke tests");
    }

    @Test
    @DisplayName("Synthesizes HEURISTIC for semantic cluster")
    void synthesizesHeuristicForSemanticCluster() {
        SkillSignal signal = SkillSignal.builder()
                .mode(SkillSignal.Mode.COMPILE)
                .cue("Fix NPE on startup")
                .parent("sem-1", MemoryType.SEMANTIC)
                .parent("sem-2", MemoryType.SEMANTIC)
                .parent("sem-3", MemoryType.SEMANTIC)
                .parentText("Config file might be missing default values")
                .build();

        assertThat(relay.transmit(signal)).isTrue();
        SkillBody body = signal.extractedBody();
        assertThat(body).isNotNull();
        assertThat(body.meta().kind()).isEqualTo(SkillKind.HEURISTIC);
        assertThat(body.meta().name()).isEqualTo("fix-npe-on-startup");
        assertThat(body.body()).contains("## Heuristic Rule");
        assertThat(body.body()).contains("Config file might be missing default values");
    }
}
