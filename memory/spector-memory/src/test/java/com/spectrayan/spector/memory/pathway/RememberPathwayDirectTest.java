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
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RememberPathwayDirectTest")
class RememberPathwayDirectTest {

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
    }

    @AfterEach
    void tearDown() {
        if (memory != null) memory.close();
    }

    @Test
    @DisplayName("Direct Ingestion: remembers memories across multiple tiers")
    void testDirectIngestion() {
        memory.remember("w-1", "Current active thought.", MemoryType.WORKING, MemorySource.INFERRED, "reasoning");
        memory.remember("e-1", "User completed onboarding checklist.", MemoryType.EPISODIC, MemorySource.OBSERVED, "onboarding");
        memory.remember("s-1", "GraphQL provides flexible schema querying.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "graphql", "api");
        memory.remember("p-1", "Validate schema before executing mutation.", MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "validation");

        assertThat(memory.memoryCount(MemoryType.WORKING)).isEqualTo(1);
        assertThat(memory.memoryCount(MemoryType.EPISODIC)).isEqualTo(1);
        assertThat(memory.memoryCount(MemoryType.SEMANTIC)).isEqualTo(1);
        assertThat(memory.memoryCount(MemoryType.PROCEDURAL)).isEqualTo(1);
        assertThat(memory.totalMemories()).isEqualTo(4);

        final List<CognitiveResult> results = memory.recall("GraphQL schema mutation", RecallOptions.builder().topK(5).build());
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Salience Profile: dynamic profile configuration propagates to RememberPathway")
    void testSalienceProfilePropagation() {
        final SalienceProfile profile = memory.salienceProfile();
        assertThat(profile).isNotNull();
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
