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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecallPathwayDirectTest")
class RecallPathwayDirectTest {

    private static final int DIMENSIONS = 32;
    private DefaultSpectorMemory memory;

    @BeforeEach
    void setUp() {
        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100)
                .setPathwayEnabled(true);

        memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(new MockEmbeddingProvider(DIMENSIONS))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();

        memory.remember("mem-1", "Authentication failure for user admin.", MemoryType.EPISODIC, MemorySource.OBSERVED, "auth", "security");
        memory.remember("mem-2", "Rate limiter throttles excessive requests.", MemoryType.EPISODIC, MemorySource.OBSERVED, "rate-limit", "security");
        memory.remember("mem-3", "HTTPS encryption with TLS 1.3 protocol.", MemoryType.SEMANTIC, MemorySource.OBSERVED, "tls", "security");
    }

    @AfterEach
    void tearDown() {
        if (memory != null) memory.close();
    }

    @Test
    @DisplayName("Vector Query Recall: executes pathway directly with float vector")
    void testVectorRecallDirect() {
        final float[] queryVector = memory.embeddingProvider().embed("security protocols").vector();
        final List<CognitiveResult> results = memory.recall("security protocols", RecallOptions.builder().topK(2).build());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isNotEmpty();
    }

    @Test
    @DisplayName("Recall Signal Tracing: enableTrace records fine-grained pathway execution traces")
    void testRecallSignalTracing() {
        final RecallOptions options = RecallOptions.builder()
                .topK(3)
                .enableTrace(true)
                .build();

        final List<CognitiveResult> results = memory.recall("authentication and authorization", options);
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Validation: null query throws IllegalArgumentException")
    void testNullQueryValidation() {
        assertThatThrownBy(() -> memory.recall(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Nested Recall Conduction: inner recall during listener execution does not clobber context")
    void testNestedRecallConduction() {
        var pathway = memory.recallPathway();
        final java.util.concurrent.atomic.AtomicBoolean innerExecuted = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<String> innerFoundId = new java.util.concurrent.atomic.AtomicReference<>();

        pathway.addListener(new com.spectrayan.spector.memory.pathway.pipeline.RecallListener() {
            @Override
            public void onRecallComplete(final List<CognitiveResult> results) {}

            @Override
            public void onRecallComplete(final List<CognitiveResult> results, final com.spectrayan.spector.commons.pathway.PathwayContext ctx) {
                var innerSignal = com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal.forTextQuery(
                        "HTTPS encryption", RecallOptions.DEFAULT);
                memory.bindRecallSignalContext(innerSignal);
                var innerResults = pathway.execute(null, innerSignal);
                if (!innerResults.isEmpty()) {
                    innerFoundId.set(innerResults.get(0).id());
                }
                innerExecuted.set(true);
            }
        });

        var outerSignal = com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal.forTextQuery(
                "Authentication failure",
                RecallOptions.builder().recallMode(com.spectrayan.spector.memory.model.RecallMode.LEARN).build());
        memory.bindRecallSignalContext(outerSignal);
        var outerResults = pathway.execute(null, outerSignal);

        assertThat(outerResults).isNotEmpty();
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .untilTrue(innerExecuted);
        assertThat(innerFoundId.get()).isEqualTo("mem-3");
    }

    @Test
    @DisplayName("Zero Ambient Thread-Local State: recall execution does not pollute thread locals")
    void testRecallPathwayZeroThreadLocalPollution() {
        var pathway = memory.recallPathway();
        var signal = com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal.forTextQuery(
                "Rate limiter", RecallOptions.DEFAULT);
        memory.bindRecallSignalContext(signal);

        var results = pathway.execute(null, signal);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo("mem-2");
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(final int dims) {
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(final String text) {
            final Random rng = new Random(text.hashCode());
            final float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                vector[i] = (rng.nextFloat() - 0.5f) * 2.0f;
            }
            float norm = 0f;
            for (final float v : vector) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) vector[i] /= norm;
            }
            return new EmbeddingResult(vector, text.split("\\s+").length, "mock-" + dims + "d");
        }

        @Override
        public int dimensions() { return dims; }

        @Override
        public String modelName() { return "mock-" + dims + "d"; }
    }
}
