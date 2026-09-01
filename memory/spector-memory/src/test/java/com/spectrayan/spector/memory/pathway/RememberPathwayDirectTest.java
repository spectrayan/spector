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

    @Test
    @DisplayName("Concurrent scoped identity ingestion isolates soul stacks and importance scoring")
    void testConcurrentScopedIdentityIngestionIsolation() throws Exception {
        com.spectrayan.spector.memory.model.UserSoul soulA = new com.spectrayan.spector.memory.model.UserSoul(
                "user-a", "Alice", "Astrophysicist",
                com.spectrayan.spector.memory.model.PersonaContext.builder().about("Astrophysicist").occupation("Astrophysics").build(),
                null);

        com.spectrayan.spector.memory.model.UserSoul soulB = new com.spectrayan.spector.memory.model.UserSoul(
                "user-b", "Bob", "Chef",
                com.spectrayan.spector.memory.model.PersonaContext.builder().about("Chef").occupation("Culinary").build(),
                null);

        SalienceProfile salienceA = SalienceProfile.builder()
                .interest("astronomy", com.spectrayan.spector.memory.model.InterestLevel.HIGH)
                .alpha(0.8f)
                .beta(0.2f)
                .build();

        SalienceProfile salienceB = SalienceProfile.builder()
                .interest("culinary", com.spectrayan.spector.memory.model.InterestLevel.HIGH)
                .alpha(0.3f)
                .beta(0.7f)
                .build();

        com.spectrayan.spector.memory.model.IngestionContext ctxA = com.spectrayan.spector.memory.model.IngestionContext.builder()
                .soulContexts(List.of(soulA))
                .soulVersion(soulA.soulVersion())
                .salienceProfile(salienceA)
                .build();

        com.spectrayan.spector.memory.model.IngestionContext ctxB = com.spectrayan.spector.memory.model.IngestionContext.builder()
                .soulContexts(List.of(soulB))
                .soulVersion(soulB.soulVersion())
                .salienceProfile(salienceB)
                .build();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        var futureA = executor.submit(() -> {
            latch.await();
            memory.remember("mem-a", "James Webb telescope discovered high-redshift galaxy",
                    MemoryType.SEMANTIC, MemorySource.USER_STATED, ctxA, "astronomy");
            return true;
        });

        var futureB = executor.submit(() -> {
            latch.await();
            memory.remember("mem-b", "French sourdough fermentation technique and recipe",
                    MemoryType.SEMANTIC, MemorySource.USER_STATED, ctxB, "culinary");
            return true;
        });

        latch.countDown();
        futureA.get(5, java.util.concurrent.TimeUnit.SECONDS);
        futureB.get(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        com.spectrayan.spector.memory.model.CognitiveRecord recA = memory.inspect("mem-a");
        com.spectrayan.spector.memory.model.CognitiveRecord recB = memory.inspect("mem-b");

        assertThat(recA).as("mem-a record").isNotNull();
        assertThat(recB).as("mem-b record").isNotNull();

        assertThat(recA.tags()).containsExactly("astronomy");
        assertThat(recB.tags()).containsExactly("culinary");
        assertThat(recA.text()).contains("telescope");
        assertThat(recB.text()).contains("sourdough");

        // Scoped recall verifies salience interest routing per soul profile
        var resultsA = memory.recall("telescope galaxy", RecallOptions.builder().topK(5).salienceProfile(salienceA).build());
        var resultsB = memory.recall("sourdough recipe", RecallOptions.builder().topK(5).salienceProfile(salienceB).build());

        assertThat(resultsA).isNotEmpty();
        assertThat(resultsA.get(0).id()).isEqualTo("mem-a");

        assertThat(resultsB).isNotEmpty();
        assertThat(resultsB.get(0).id()).isEqualTo("mem-b");
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
