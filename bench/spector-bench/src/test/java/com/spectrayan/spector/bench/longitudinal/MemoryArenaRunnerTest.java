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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("MemoryArenaRunner Integration Tests")
class MemoryArenaRunnerTest {

    @Test
    @DisplayName("evaluateTrajectory computes outcome metrics for trajectory")
    void testEvaluateTrajectory() {
        SpectorMemory mockEngine = Mockito.mock(SpectorMemory.class);

        CognitiveResult result = new CognitiveResult(
                "mem-1", "database: PostgreSQL framework: Spring Boot 3.2",
                0.9f, 0.8f, 0.1f, 1, (byte) 0,
                MemoryType.EPISODIC, MemorySource.OBSERVED, new String[0], 1.0f, 1.0f,
                null, null, null, null, Map.of()
        );

        when(mockEngine.remember(anyString(), any(MemoryType.class), any(MemorySource.class)))
                .thenReturn(CompletableFuture.completedFuture("mem-1"));
        when(mockEngine.recall(anyString(), any(CognitiveProfile.class))).thenReturn(List.of(result));

        LongitudinalSession session = new LongitudinalSession(
                "session-01", 0, "Query prompt", "intent",
                Map.of("database", "PostgreSQL"), List.of("MySQL")
        );

        LongitudinalTrajectory trajectory = new LongitudinalTrajectory(
                "traj-1", "coding-agent", "desc", 1, List.of(session)
        );

        MemoryArenaRunner runner = new MemoryArenaRunner();
        LongitudinalMetrics metrics = runner.evaluateTrajectory(
                trajectory, mockEngine, "spector-full", CognitiveProfile.BALANCED
        );

        assertThat(metrics).isNotNull();
        assertThat(metrics.condition()).isEqualTo("spector-full");
        assertThat(metrics.totalSessionsEvaluated()).isEqualTo(1);
        assertThat(metrics.taskCompletionRate()).isEqualTo(1.0);
    }
}
