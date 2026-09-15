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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates Requirement I1.1: pre-computed vector overload stores vector verbatim
 * with zero calls to the embedding provider.
 */
class SpectorMemoryPrecomputedVectorTest {

    private static final int DIMENSIONS = 8;
    private SpectorMemory memory;
    private CountingEmbeddingProvider countingEmbedder;

    @BeforeEach
    void setUp() {
        countingEmbedder = new CountingEmbeddingProvider(DIMENSIONS);
        memory = DefaultSpectorMemory.builder(new MemoryProperties()
                        .setDimensions(DIMENSIONS)
                        .setWorkingCapacity(10)
                        .setEpisodicPartitionCapacity(50)
                        .setSemanticCapacity(50)
                        .setProceduralCapacity(50))
                .embeddingProvider(countingEmbedder)
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
    void rememberWithPrecomputedVectorDoesNotCallEmbeddingProvider() {
        float[] precomputed = new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f};

        memory.remember("doc-precomputed-1", "This text is already embedded", precomputed,
                MemoryType.SEMANTIC, MemorySource.OBSERVED);

        // Verify zero embedding calls occurred
        assertThat(countingEmbedder.callCount.get()).isZero();
        assertThat(memory.totalMemories()).isEqualTo(1);
    }

    @Test
    void rememberWithoutVectorCallsEmbeddingProvider() {
        memory.remember("doc-auto-embed-1", "This text needs embedding",
                MemoryType.SEMANTIC, MemorySource.OBSERVED);

        // Verify embedding provider was invoked exactly once
        assertThat(countingEmbedder.callCount.get()).isEqualTo(1);
        assertThat(memory.totalMemories()).isEqualTo(1);
    }

    private static final class CountingEmbeddingProvider implements EmbeddingProvider {
        private final int dimensions;
        private final AtomicInteger callCount = new AtomicInteger();

        CountingEmbeddingProvider(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public EmbeddingResult embed(String text) {
            callCount.incrementAndGet();
            float[] vec = new float[dimensions];
            vec[0] = 1.0f;
            return EmbeddingResult.of(vec, "counting-model");
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        @Override
        public String modelName() {
            return "counting-model";
        }
    }
}
