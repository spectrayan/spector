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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A cold store must not score every write as importance 1.0 / novelty 0.10.
 */
class ColdStoreNoveltyTest {

    private static final int DIMENSIONS = 32;
    private static final String FIRST = "the first thing stored";
    private static final String SAME = "the first thing stored";
    private static final String OTHER = "an unrelated second memory";
    private static final String EPISODE = "episodic turn about the launch";

    private SpectorMemory memory;

    @BeforeEach
    void setUp() {
        var embedder = new ConsolidationIntegrationTest.TestEmbeddingProvider(DIMENSIONS);
        embedder.register(FIRST, axis(0));
        embedder.register(OTHER, axis(1));
        embedder.register(EPISODE, axis(2));
        memory = DefaultSpectorMemory.builder(new MemoryProperties()
                        .setDimensions(DIMENSIONS)
                        .setWorkingCapacity(20)
                        .setEpisodicPartitionCapacity(100)
                        .setSemanticCapacity(50)
                        .setProceduralCapacity(20))
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
        }
    }

    @Test
    void firstMemoryInAnEmptyStoreIsFullyNovel() {
        ImportanceResult estimate = memory.estimateImportance(FIRST);

        assertThat(estimate.breakdown().noveltyScore()).isEqualTo(1.0f);
        assertThat(estimate.importance()).isEqualTo(10.0f);
        assertThat(estimate.isFlashbulb()).isFalse();

        memory.remember("first", FIRST, MemoryType.SEMANTIC);

        assertThat(storedImportance("first")).isCloseTo(estimate.importance(), within(0.01f));
    }

    @Test
    void duplicateScoresBelowUnrelatedOnAColdStoreAndEstimateMatchesTheWrite() {
        memory.remember("first", FIRST, MemoryType.SEMANTIC);

        ImportanceResult duplicate = memory.estimateImportance(SAME);
        ImportanceResult unrelated = memory.estimateImportance(OTHER);

        assertThat(duplicate.breakdown().noveltyScore())
                .isLessThan(unrelated.breakdown().noveltyScore());
        assertThat(duplicate.importance()).isLessThan(1.0f);
        assertThat(unrelated.importance()).isGreaterThan(duplicate.importance());

        memory.remember("other", OTHER, MemoryType.SEMANTIC);

        assertThat(storedImportance("other")).isCloseTo(unrelated.importance(), within(0.01f));
    }

    @Test
    void episodicWriteIsVisibleToTheNextNoveltyScore() {
        memory.remember("turn", EPISODE, MemoryType.EPISODIC);

        ImportanceResult duplicate = memory.estimateImportance(EPISODE);
        ImportanceResult unrelated = memory.estimateImportance(OTHER);

        assertThat(duplicate.importance()).isLessThan(unrelated.importance());
    }

    private float storedImportance(String id) {
        DefaultSpectorMemory dm = (DefaultSpectorMemory) memory;
        var loc = dm.index().locate(id);
        return dm.partitionManager().routerFor(loc.colocatedPartition()).readHeader(loc).importance();
    }

    private static float[] axis(int index) {
        float[] vector = new float[DIMENSIONS];
        vector[index] = 1.0f;
        return vector;
    }
}
