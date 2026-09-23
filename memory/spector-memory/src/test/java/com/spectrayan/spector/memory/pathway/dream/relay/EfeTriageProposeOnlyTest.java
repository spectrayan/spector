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

import com.spectrayan.spector.config.properties.DreamProperties;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.TriageOutcome;
import com.spectrayan.spector.memory.pathway.FakeRememberPathway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0086 §5.6, §7 Phase 5: Dream EfeTriage PRAGMATIC Propose-Only Tests")
class EfeTriageProposeOnlyTest {

    private static DreamSignal.DreamScene scene(final String id, final float quality, final TriageOutcome outcome) {
        return new DreamSignal.DreamScene(
                id,
                "narrative for " + id,
                "insight for " + id,
                new float[]{0.1f, 0.2f, 0.3f},
                List.of("seed-1"),
                quality,
                outcome);
    }

    @Test
    @DisplayName("PRAGMATIC scene is propose-only and not persisted when skillAutoCommit is false")
    void pragmaticSceneNotPersistedWhenAutoCommitFalse() throws Exception {
        final FakeRememberPathway fakeRemember = new FakeRememberPathway();

        final DreamProperties config = new DreamProperties();
        config.setEnabled(true);
        config.setPersistenceThreshold(0.5f);
        config.setSkillAutoCommit(false); // Default propose-only (ADR-0086 §5.6)

        final DreamSignal signal = DreamSignal.builder()
                .mode(DreamMode.REM)
                .config(config)
                .build();
        signal.survivingScenes().addAll(List.of(
                scene("pragmatic-scene-1", 0.9f, TriageOutcome.PRAGMATIC),
                scene("epistemic-scene-2", 0.85f, TriageOutcome.EPISTEMIC)
        ));
        signal.bind(fakeRemember.inContext("dream-triage-test"));

        final boolean transmitted = new DreamIngestionRelay().transmit(signal);

        assertThat(transmitted).isTrue();
        // Only the EPISTEMIC scene should have been persisted, PRAGMATIC is propose-only
        assertThat(fakeRemember.invocationCount())
                .as("PRAGMATIC scene was skipped while EPISTEMIC was persisted")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("PRAGMATIC scene is persisted when skillAutoCommit is explicitly enabled")
    void pragmaticScenePersistedWhenAutoCommitTrue() throws Exception {
        final FakeRememberPathway fakeRemember = new FakeRememberPathway();

        final DreamProperties config = new DreamProperties();
        config.setEnabled(true);
        config.setPersistenceThreshold(0.5f);
        config.setSkillAutoCommit(true);

        final DreamSignal signal = DreamSignal.builder()
                .mode(DreamMode.REM)
                .config(config)
                .build();
        signal.survivingScenes().add(scene("pragmatic-scene-1", 0.9f, TriageOutcome.PRAGMATIC));
        signal.bind(fakeRemember.inContext("dream-triage-test"));

        final boolean transmitted = new DreamIngestionRelay().transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(fakeRemember.invocationCount()).isEqualTo(1);
    }
}
