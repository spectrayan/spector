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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0082 Index Plane Restart Parity Integration Tests")
class IndexRestartParityTest {

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dimensions;
        private final Random random = new Random(42);

        MockEmbeddingProvider(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vec[i] = random.nextFloat();
            }
            return new EmbeddingResult(vec, 1, "mock-embed");
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        @Override
        public String modelName() {
            return "mock-embed";
        }
    }

    @Test
    @DisplayName("Should hydrate reverse index deterministically on cold restart and preserve entity projections")
    void shouldPreserveReverseIndexAcrossRestart(@TempDir Path tmp) {
        MemoryProperties props = new MemoryProperties();
        props.setDimensions(32);
        props.getGraph().getEntity().setExtractionMode("NONE");

        int aliceId;
        int acmeId;

        // 1. First run: Start engine, intern entities, link to memories, close
        try (SpectorMemory memory = SpectorMemory.builder(props)
                .embeddingProvider(new MockEmbeddingProvider(32))
                .persistence(tmp)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .build()) {

            DefaultSpectorMemory dsm = (DefaultSpectorMemory) memory;
            IndexPlaneCoordinator coordinator = dsm.indexPlaneCoordinator();
            assertThat(coordinator).isNotNull();
            assertThat(coordinator.isReady()).isTrue();

            var entityDir = dsm.entityDirectory();
            aliceId = entityDir.intern("Alice", "PERSON");
            acmeId = entityDir.intern("Acme Corp", "ORGANIZATION");

            entityDir.linkEntityToMemory(aliceId, 42);
            entityDir.linkEntityToMemory(acmeId, 42);
            entityDir.linkEntityToMemory(aliceId, 99);

            assertThat(entityDir.reverseIndexSize()).isEqualTo(2);
            assertThat(entityDir.entitiesForMemory(42)).containsKeys(aliceId, acmeId);
        }

        // 2. Cold Restart: Reopen from same bundle directory
        MemoryProperties reopenProps = new MemoryProperties();
        reopenProps.setDimensions(32);
        reopenProps.getGraph().getEntity().setExtractionMode("NONE");

        try (SpectorMemory reopened = SpectorMemory.builder(reopenProps)
                .embeddingProvider(new MockEmbeddingProvider(32))
                .persistence(tmp)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .build()) {

            DefaultSpectorMemory dsm = (DefaultSpectorMemory) reopened;
            IndexPlaneCoordinator coordinator = dsm.indexPlaneCoordinator();
            assertThat(coordinator).isNotNull();
            assertThat(coordinator.isReady()).isTrue();

            var entityDir = dsm.entityDirectory();
            assertThat(entityDir.entityCount()).isEqualTo(2);

            // Reverse index must be deterministically hydrated on cold start (ADR-0082 §5.2)
            assertThat(entityDir.reverseIndexSize()).isEqualTo(2);

            Map<Integer, String> slot42 = entityDir.entitiesForMemory(42);
            assertThat(slot42).hasSize(2)
                    .containsEntry(aliceId, "alice")
                    .containsEntry(acmeId, "acme corp");

            Map<Integer, String> slot99 = entityDir.entitiesForMemory(99);
            assertThat(slot99).hasSize(1)
                    .containsEntry(aliceId, "alice");

            var reverseAdapterOpt = coordinator.get(EntityReverseIndexAdapter.NAME);
            assertThat(reverseAdapterOpt).isPresent();
            IndexStats stats = reverseAdapterOpt.get().stats();
            assertThat(stats.entries()).isEqualTo(2);
            assertThat(stats.generation()).isGreaterThan(0L);
            assertThat(stats.hydrateMs()).isLessThan(50L); // Well within budget
        }
    }
}
