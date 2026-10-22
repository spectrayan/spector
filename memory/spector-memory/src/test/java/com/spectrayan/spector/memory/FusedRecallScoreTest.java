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

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.session.SessionWriteBuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * memory_recall reports the fused ranking score on every path, and cosine separately.
 */
class FusedRecallScoreTest {

    private static final int DIMENSIONS = 32;
    private static final String HIT = "the platform standup stays at nine thirty";
    private static final String NEAR = "the platform standup moved by a few minutes";
    private static final String FAR = "orbital mechanics of a distant comet";
    private static final String QUERY = "when is the platform standup";

    private SpectorMemory memory;

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
        }
    }

    @Test
    void sessionBufferReportsTheFusedScoreAndKeepsCosineSeparate() {
        SessionWriteBuffer buffer = new SessionWriteBuffer();
        float[] vector = axis(0);
        buffer.add("m1", HIT, vector, MemoryType.SEMANTIC, 0L);
        RecallOptions options = RecallOptions.builder().topK(5).build();

        List<CognitiveResult> hits = buffer.search(HIT, vector, options);

        assertThat(hits).hasSize(1);
        CognitiveResult hit = hits.get(0);
        assertThat(hit.cosine()).isCloseTo(1.0f, within(1.0e-5f));
        float expected = CognitiveScoreFusionKernel.computeLinearBlendScore(
                1.0f, 1.0f, 1.0f, 0.0f, options.alpha(), options.beta(), options.tagRelevanceBoost());
        assertThat(hit.score()).isCloseTo(expected, within(1.0e-5f));
        assertThat(hit.score()).isLessThan(hit.cosine());
    }

    @Test
    void consecutiveRecallsStayOnTheFusedScaleAndCosineRanksAParaphraseAboveUnrelatedText() throws Exception {
        ConsolidationIntegrationTest.TestEmbeddingProvider embedder =
                new ConsolidationIntegrationTest.TestEmbeddingProvider(DIMENSIONS);
        embedder.register(HIT, axis(0));
        float[] near = new float[DIMENSIONS];
        near[0] = 0.6f;
        near[1] = 0.8f;
        embedder.register(NEAR, near);
        embedder.register(FAR, axis(1));
        embedder.register(QUERY, axis(0));
        memory = DefaultSpectorMemory.builder(new MemoryProperties()
                        .setDimensions(DIMENSIONS)
                        .setWorkingCapacity(20)
                        .setEpisodicPartitionCapacity(100)
                        .setSemanticCapacity(50)
                        .setProceduralCapacity(20))
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();

        MemoryScope.runWithScope("sess-fused", null, () -> {
            memory.remember("hit", HIT, MemoryType.SEMANTIC);
            memory.remember("near", NEAR, MemoryType.SEMANTIC);
            memory.remember("far", FAR, MemoryType.SEMANTIC);
        });

        RecallOptions options = RecallOptions.builder().topK(10).recallMode(RecallMode.OBSERVE).build();
        List<CognitiveResult> first = MemoryScope.callWithScope(
                "sess-fused", null, () -> memory.recall(QUERY, options));
        List<CognitiveResult> second = MemoryScope.callWithScope(
                "sess-fused", null, () -> memory.recall(QUERY, options));
        List<CognitiveResult> stored = memory.recall(QUERY, options);

        assertThat(first.stream().map(CognitiveResult::id).toList()).doesNotHaveDuplicates();
        assertThat(byId(first, "hit").score()).isCloseTo(byId(second, "hit").score(), within(0.02f));
        assertThat(byId(first, "near").score()).isCloseTo(byId(stored, "near").score(), within(0.02f));
        assertThat(byId(first, "near").hasCosine()).isTrue();
        assertThat(byId(first, "far").hasCosine()).isTrue();
        assertThat(byId(first, "near").cosine()).isGreaterThan(byId(first, "far").cosine());
        assertThat(byId(first, "hit").cosine()).isGreaterThan(byId(first, "near").cosine());
    }

    private static CognitiveResult byId(List<CognitiveResult> results, String id) {
        return results.stream().filter(r -> id.equals(r.id())).findFirst().orElseThrow();
    }

    private static float[] axis(int index) {
        float[] vector = new float[DIMENSIONS];
        vector[index] = 1.0f;
        return vector;
    }
}
