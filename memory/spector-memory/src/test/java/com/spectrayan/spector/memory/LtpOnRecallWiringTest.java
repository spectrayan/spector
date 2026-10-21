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
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies, through the real factory, that recall-based LTP reconsolidation is wired only
 * when {@code spector.memory.recall.ltp-on-recall} is enabled (#1147).
 */
class LtpOnRecallWiringTest {

    private static final int DIMENSIONS = 16;
    private static final String ID = "ltp-target";
    private static final String TEXT = "The deploy window is Tuesday at noon.";

    @Test
    @DisplayName("Default: recall does not strengthen returned memories")
    void recallDoesNotStrengthenByDefault() {
        try (SpectorMemory memory = build(false)) {
            CognitiveRecord before = rememberAndInspect(memory);

            recallTarget(memory);

            // Listeners run fire-and-forget, so hold the assertion for a while rather than check once
            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                CognitiveRecord after = memory.inspect(ID);
                assertThat(after.spectorRecallCount()).isEqualTo(before.spectorRecallCount());
                assertThat(after.storageStrength()).isEqualTo(before.storageStrength());
            });
        }
    }

    @Test
    @DisplayName("ltp-on-recall=true: a LEARN recall strengthens the returned memory once per cooldown")
    void recallStrengthensWhenEnabled() {
        try (SpectorMemory memory = build(true)) {
            CognitiveRecord before = rememberAndInspect(memory);

            recallTarget(memory);

            // Listeners run fire-and-forget after recall returns
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                CognitiveRecord after = memory.inspect(ID);
                assertThat(after.spectorRecallCount()).isEqualTo(before.spectorRecallCount() + 1);
                assertThat(after.storageStrength()).isGreaterThan(before.storageStrength());
            });

            // Second recall inside the cooldown leaves the count unchanged
            int countAfterFirst = memory.inspect(ID).spectorRecallCount();
            recallTarget(memory);
            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(memory.inspect(ID).spectorRecallCount()).isEqualTo(countAfterFirst));
        }
    }

    private static SpectorMemory build(boolean ltpOnRecall) {
        MemoryProperties props = new MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100);
        props.getRecall().setLtpOnRecall(ltpOnRecall);
        return DefaultSpectorMemory.builder(props)
                .embeddingProvider(new ConsolidationIntegrationTest.TestEmbeddingProvider(DIMENSIONS))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    private static CognitiveRecord rememberAndInspect(SpectorMemory memory) {
        memory.remember(ID, TEXT, MemoryType.SEMANTIC, MemorySource.OBSERVED, "ops");
        CognitiveRecord before = memory.inspect(ID);
        assertThat(before).isNotNull();
        return before;
    }

    private static void recallTarget(SpectorMemory memory) {
        List<CognitiveResult> results = memory.recall(TEXT, RecallOptions.builder().topK(5).build());
        assertThat(results).extracting(CognitiveResult::id).contains(ID);
    }
}
