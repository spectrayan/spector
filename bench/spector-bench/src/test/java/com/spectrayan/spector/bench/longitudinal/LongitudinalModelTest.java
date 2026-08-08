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
package com.spectrayan.spector.bench.longitudinal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Longitudinal Data Models Unit Tests")
class LongitudinalModelTest {

    @Test
    @DisplayName("LongitudinalSession enforces immutability and non-null guarantees")
    void testLongitudinalSessionImmutability() {
        LongitudinalSession session = new LongitudinalSession(
                "session-01",
                0,
                "Prompt text",
                "intent",
                Map.of("key", "val"),
                List.of("negative")
        );

        assertThat(session.sessionId()).isEqualTo("session-01");
        assertThat(session.dayOffset()).isEqualTo(0);
        assertThat(session.groundTruthContext()).containsEntry("key", "val");
        assertThat(session.negativeConstraints()).containsExactly("negative");

        assertThatThrownBy(() -> session.groundTruthContext().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("LongitudinalMetrics enforces valid score boundaries [0.0 - 1.0]")
    void testLongitudinalMetricsBoundaries() {
        LongitudinalMetrics metrics = new LongitudinalMetrics(
                "spector-full",
                0.95,
                0.90,
                1.0,
                0.95,
                0.90,
                20
        );

        assertThat(metrics.condition()).isEqualTo("spector-full");
        assertThat(metrics.taskCompletionRate()).isEqualTo(0.95);

        assertThatThrownBy(() -> new LongitudinalMetrics(
                "invalid", 1.5, 0.9, 1.0, 0.9, 0.9, 10
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
