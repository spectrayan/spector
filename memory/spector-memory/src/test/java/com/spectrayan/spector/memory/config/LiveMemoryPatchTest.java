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
package com.spectrayan.spector.memory.config;

import com.spectrayan.spector.commons.chunker.ChunkConfig;
import com.spectrayan.spector.config.model.LiveMemoryPatch;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0085: LiveMemoryPatch & Runtime Propagation Tests")
class LiveMemoryPatchTest {

    @Test
    @DisplayName("LiveMemoryPatch captures defaults and supports builder modification")
    void testLiveMemoryPatchDefaultsAndBuilder() {
        LiveMemoryPatch patch = LiveMemoryPatch.DEFAULTS;
        assertThat(patch.decayEnabled()).isTrue();
        assertThat(patch.flashbulbThreshold()).isEqualTo(3.0f);
        assertThat(patch.vacuumThreshold()).isEqualTo(0.20f);

        LiveMemoryPatch modified = patch.toBuilder()
                .flashbulbThreshold(3.8f)
                .decayEnabled(false)
                .vacuumThreshold(0.35f)
                .hebbianMaxDegree(32)
                .build();

        assertThat(modified.flashbulbThreshold()).isEqualTo(3.8f);
        assertThat(modified.decayEnabled()).isFalse();
        assertThat(modified.vacuumThreshold()).isEqualTo(0.35f);
        assertThat(modified.hebbianMaxDegree()).isEqualTo(32);
        assertThat(modified.surpriseWarmup()).isEqualTo(patch.surpriseWarmup());
    }

    @Test
    @DisplayName("LiveMemoryPatch projects cleanly from MemoryProperties")
    void testLiveMemoryPatchFromMemoryProperties() {
        MemoryProperties props = new MemoryProperties();
        props.getRemember().setFlashbulbThreshold(4.2f);
        props.getRemember().setSurpriseWarmup(25);
        props.getVacuum().setThreshold(0.40f);
        props.getGraph().getHebbian().setMaxDegree(48);

        LiveMemoryPatch patch = LiveMemoryPatch.from(props);
        assertThat(patch.flashbulbThreshold()).isEqualTo(4.2f);
        assertThat(patch.surpriseWarmup()).isEqualTo(25);
        assertThat(patch.vacuumThreshold()).isEqualTo(0.40f);
        assertThat(patch.hebbianMaxDegree()).isEqualTo(48);
    }

    @Test
    @DisplayName("DefaultSpectorMemory hot-swaps LiveMemoryPatch, RecallOptions, and ChunkConfig dynamically")
    void testDefaultSpectorMemoryDynamicUpdates(@TempDir Path tempDir) {
        MemoryProperties memProps = new MemoryProperties(100, 384);
        try (SpectorMemory memory = SpectorMemory.builder()
                .fromProperties(memProps)
                .persistence(tempDir)
                .embeddingProvider(new com.spectrayan.spector.memory.test.FakeEmbeddingProvider())
                .build()) {

            assertThat(memory).isInstanceOf(DefaultSpectorMemory.class);
            DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;

            // 1. Live Memory Patch
            LiveMemoryPatch newPatch = LiveMemoryPatch.DEFAULTS.toBuilder()
                    .flashbulbThreshold(4.5f)
                    .decayEnabled(false)
                    .hebbianMaxDegree(16)
                    .build();
            memory.applyLiveMemoryPatch(newPatch);
            assertThat(defaultMem.liveMemoryPatch()).isEqualTo(newPatch);

            // 2. Recall Options
            RecallOptions newOptions = RecallOptions.builder()
                    .profile(CognitiveProfile.EXPLORING)
                    .topK(20)
                    .build();
            memory.updateRecallOptions(newOptions);
            assertThat(defaultMem.defaultRecallOptions().profile()).isEqualTo(CognitiveProfile.EXPLORING);
            assertThat(defaultMem.defaultRecallOptions().topK()).isEqualTo(20);

            // 3. Chunk Config
            ChunkConfig newChunk = new ChunkConfig(1200, 150, "text/markdown", null, true, true, false, false);
            memory.updateChunkConfig(newChunk);

            // 4. HNSW efSearch
            memory.updateHnswEfSearch(80);
        }
    }
}
