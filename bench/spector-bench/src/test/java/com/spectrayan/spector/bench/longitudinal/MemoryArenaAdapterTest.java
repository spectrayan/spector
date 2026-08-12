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

@DisplayName("MemoryArenaAdapter Unit Tests")
class MemoryArenaAdapterTest {

    @Test
    @DisplayName("remember delegates to SpectorMemory engine remember")
    void testRememberDelegatesToRemember() {
        SpectorMemory mockEngine = Mockito.mock(SpectorMemory.class);
        when(mockEngine.remember(anyString(), any(MemoryType.class), any(MemorySource.class)))
                .thenReturn("mem-1");

        MemoryArenaAdapter adapter = new MemoryArenaAdapter(mockEngine, CognitiveProfile.BALANCED);
        String id = adapter.remember("test experience", 0.8f, 0.5f);

        assertThat(id).isEqualTo("mem-1");
        assertThat(adapter.getCognitiveProfile()).isEqualTo(CognitiveProfile.BALANCED);
    }

    @Test
    @DisplayName("recall delegates to SpectorMemory engine recall with profile")
    void testRecallDelegatesToRecall() {
        SpectorMemory mockEngine = Mockito.mock(SpectorMemory.class);
        CognitiveResult dummyResult = new CognitiveResult(
                "mem-1", "test experience", 0.9f, 0.8f, 0.1f, 1, (byte) 0,
                MemoryType.EPISODIC, MemorySource.OBSERVED, new String[0], 1.0f, 1.0f,
                null, null, null, null, Map.of()
        );
        when(mockEngine.recall(anyString(), any(CognitiveProfile.class))).thenReturn(List.of(dummyResult));

        MemoryArenaAdapter adapter = new MemoryArenaAdapter(mockEngine, CognitiveProfile.BALANCED);
        List<CognitiveResult> results = adapter.recall("query", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).text()).isEqualTo("test experience");
    }
}
