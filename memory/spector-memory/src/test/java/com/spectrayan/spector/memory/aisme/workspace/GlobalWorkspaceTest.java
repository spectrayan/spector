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
package com.spectrayan.spector.memory.aisme.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.kernel.api.MemoryType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link GlobalWorkspace}.
 */
class GlobalWorkspaceTest {

    @Test
    void filterForBroadcast_truncatesToCapacityAndSortsByScore() {
        GlobalWorkspace workspace = new GlobalWorkspace(3);

        List<CognitiveResult> candidates = new ArrayList<>();
        candidates.add(createResult("m1", 0.2f));
        candidates.add(createResult("m2", 0.9f));
        candidates.add(createResult("m3", 0.5f));
        candidates.add(createResult("m4", 0.8f));

        List<CognitiveResult> broadcast = workspace.filterForBroadcast(candidates);

        assertThat(broadcast).hasSize(3);
        assertThat(broadcast.get(0).id()).isEqualTo("m2"); // 0.9
        assertThat(broadcast.get(1).id()).isEqualTo("m4"); // 0.8
        assertThat(broadcast.get(2).id()).isEqualTo("m3"); // 0.5
    }

    @Test
    void updateAttentionSchema_updatesActiveSchema() {
        GlobalWorkspace workspace = new GlobalWorkspace();
        AttentionSchema custom = new AttentionSchema("EMOTION", 2.5f, 1000L, "High arousal context");

        workspace.updateAttentionSchema(custom);
        assertThat(workspace.activeSchema()).isEqualTo(custom);
    }

    @Test
    void invalidCapacity_throwsValidationException() {
        assertThatThrownBy(() -> new GlobalWorkspace(0))
                .isInstanceOf(SpectorValidationException.class);
    }

    private static CognitiveResult createResult(String id, float score) {
        return new CognitiveResult(
                id,
                "text " + id,
                score,
                1.0f,
                0.1f,
                0,
                (byte) 0,
                MemoryType.EPISODIC,
                MemorySource.USER_STATED,
                new String[0],
                1.0f,
                1.0f,
                CognitiveResult.RetrievalMode.STANDARD,
                null,
                null,
                null,
                Map.of()
        );
    }
}
