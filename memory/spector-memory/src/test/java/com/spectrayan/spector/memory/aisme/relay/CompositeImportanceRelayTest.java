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
package com.spectrayan.spector.memory.aisme.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.aisme.importance.CompositeImportanceScorer;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompositeImportanceRelay}.
 */
class CompositeImportanceRelayTest {

    private AismeProperties config;
    private CompositeImportanceScorer scorer;
    private CompositeImportanceRelay relay;

    @BeforeEach
    void setUp() {
        config = AismeProperties.builder()
                .enabled(true)
                .enableImportance(true)
                .importanceFlashbulbThreshold(0.85f)
                .build();
        scorer = new CompositeImportanceScorer(config);
        relay = new CompositeImportanceRelay(config, scorer);
    }

    @Test
    void relayName_returnsCompositeImportance() {
        assertThat(relay.relayName()).isEqualTo(RelayNames.COMPOSITE_IMPORTANCE);
    }

    @Test
    void transmit_computesImportanceAndTagsFlashbulb() {
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-1",
                "Critical database error during emergency conversation with @lead: panic!",
                new float[]{1.0f, 0.0f, 0.0f},
                MemoryType.EPISODIC,
                new String[]{"user:lead", "critical"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.eventDensityMetrics(new EventDensityMetrics(2.0f, 0.9f, 0.95f, 0.90f, true, 30.0f));
        signal.nearestDist(1.8f);

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(signal.importance()).isGreaterThanOrEqualTo(0.85f);
        assertThat(signal.isFlashbulb()).isTrue();
    }

    @Test
    void transmit_lowSalience_doesNotTagFlashbulb() {
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-2",
                "routine log statement with normal context",
                new float[]{0.1f, 0.1f, 0.1f},
                MemoryType.EPISODIC,
                new String[]{"logs"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.nearestDist(0.1f);

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(signal.importance()).isLessThan(0.85f);
        assertThat(signal.isFlashbulb()).isFalse();
    }

    @Test
    void transmit_disabledImportance_leavesOriginalImportance() {
        AismeProperties disabledConfig = AismeProperties.builder()
                .enabled(true)
                .enableImportance(false)
                .build();
        CompositeImportanceScorer disabledScorer = new CompositeImportanceScorer(disabledConfig);
        CompositeImportanceRelay disabledRelay = new CompositeImportanceRelay(disabledConfig, disabledScorer);

        RememberSignal signal = RememberSignal.forCognitive(
                "mem-3",
                "sample memory",
                new float[]{0.5f, 0.5f},
                MemoryType.EPISODIC,
                new String[]{"tag"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.importance(0.65f);

        boolean transmitted = disabledRelay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(signal.importance()).isEqualTo(0.65f);
        assertThat(signal.isFlashbulb()).isFalse();
    }
}
