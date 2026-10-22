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

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.store.AbstractEngramMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The governed release gate filters real recall candidates (#1167).
 */
class GovernedRecallFilterTest {

    private static final int DIMENSIONS = 32;
    private static final String KEEP = "The platform standup stays at 9:30.";
    private static final String DROP = "The platform standup moved to 10:00.";

    private ConsolidationIntegrationTest.TestEmbeddingProvider embedder;
    private SpectorMemory memory;

    @BeforeEach
    void setUp() {
        embedder = new ConsolidationIntegrationTest.TestEmbeddingProvider(DIMENSIONS);
        float[] vector = new float[DIMENSIONS];
        vector[0] = 1.0f;
        embedder.register(KEEP, vector);
        embedder.register(DROP, vector);
        memory = DefaultSpectorMemory.builder(new com.spectrayan.spector.config.properties.MemoryProperties()
                        .setDimensions(DIMENSIONS)
                        .setWorkingCapacity(20)
                        .setEpisodicPartitionCapacity(100)
                        .setSemanticCapacity(50)
                        .setProceduralCapacity(20))
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
        memory.remember("keep", KEEP, MemoryType.SEMANTIC, "standup");
        memory.remember("drop", DROP, MemoryType.SEMANTIC, "standup");
    }

    @AfterEach
    void tearDown() {
        memory.close();
    }

    @Test
    void retractedRecordIsDroppedFromRecall() {
        DefaultSpectorMemory dm = (DefaultSpectorMemory) memory;
        var loc = dm.index().locate("drop");
        dm.partitionManager().routerFor(loc.colocatedPartition()).markRetracted(loc);

        assertThat(recalledIds(RecallOptions.builder().topK(10).build()))
                .contains("keep")
                .doesNotContain("drop");
    }

    @Test
    void restrictedRecordWithoutPersonaIsDroppedFromRecall() {
        setConsolidationFlags("drop", EncodingHeaderFields.FLAG_RESTRICTED);

        assertThat(recalledIds(RecallOptions.builder().topK(10).build()))
                .contains("keep")
                .doesNotContain("drop");
    }

    @Test
    void unverifiedRecordBelowMinTrustIsDroppedFromRecall() {
        setConsolidationFlags("drop", EncodingHeaderFields.FLAG_UNVERIFIED);

        assertThat(recalledIds(RecallOptions.builder().topK(10).minTrustScore(1000f).build()))
                .contains("keep")
                .doesNotContain("drop");
    }

    private List<String> recalledIds(RecallOptions options) {
        return memory.recall(KEEP, options).stream().map(CognitiveResult::id).toList();
    }

    private void setConsolidationFlags(String id, byte flags) {
        DefaultSpectorMemory dm = (DefaultSpectorMemory) memory;
        var loc = dm.index().locate(id);
        var region = dm.partitionManager().routerFor(loc.colocatedPartition()).get(loc.type());
        if (!(region instanceof AbstractEngramMemory<?> engram)) {
            throw new AssertionError("expected an engram region for " + id);
        }
        engram.layout().headerLayout().writeConsolidationFlags(engram.segment(), loc.offset(), flags);
    }
}
