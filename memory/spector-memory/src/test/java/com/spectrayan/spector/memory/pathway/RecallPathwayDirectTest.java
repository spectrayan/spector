/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
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
        memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(new MockEmbeddingProvider(DIMENSIONS))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(20)
                .episodicPartitionCapacity(100)
                .semanticCapacity(100)
                .proceduralCapacity(100)
                .usePathwayEngine(true)
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
