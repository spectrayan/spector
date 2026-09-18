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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
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
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("Should persist and restore BM25 lexical index across cold restart")
    void shouldRestoreBM25AcrossRestart(@TempDir Path tmp) {
        MemoryProperties props = new MemoryProperties();
        props.setDimensions(32);
        props.getGraph().getEntity().setExtractionMode("NONE");

        String mem1;
        String mem2;

        try (SpectorMemory memory = SpectorMemory.builder(props)
                .embeddingProvider(new MockEmbeddingProvider(32))
                .persistence(tmp)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .build()) {

            DefaultSpectorMemory dsm = (DefaultSpectorMemory) memory;
            IndexPlaneCoordinator coordinator = dsm.indexPlaneCoordinator();
            mem1 = memory.remember("Quantum computing algorithmic advances and qubits", MemoryType.SEMANTIC, MemorySource.USER_STATED);
            mem2 = memory.remember("Cognitive memory architecture and active inference free energy", MemoryType.SEMANTIC, MemorySource.USER_STATED);

            MemoryBM25Index bm25 = (MemoryBM25Index) coordinator.get("BM25").orElseThrow();
            assertThat(bm25.totalDocuments()).isGreaterThanOrEqualTo(2);
            var results = bm25.search("quantum", 5);
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).id()).isEqualTo(mem1);
        }

        // Reopen from disk
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
            var coordinator = dsm.indexPlaneCoordinator();
            MemoryBM25Index bm25 = (MemoryBM25Index) coordinator.get("BM25").orElseThrow();
            assertThat(bm25.totalDocuments()).isGreaterThanOrEqualTo(2);

            var quantumResults = bm25.search("quantum", 5);
            assertThat(quantumResults).isNotEmpty();
            assertThat(quantumResults.get(0).id()).isEqualTo(mem1);

            var cognitiveResults = bm25.search("cognitive", 5);
            assertThat(cognitiveResults).isNotEmpty();
            assertThat(cognitiveResults.get(0).id()).isEqualTo(mem2);

            IndexStats stats = bm25.stats();
            assertThat(stats.entries()).isGreaterThanOrEqualTo(2);
            assertThat(stats.generation()).isGreaterThan(0L);
        }
    }

    @Test
    @DisplayName("IndexPlaneCoordinator close should be strictly non-owning for primary kernel stores")
    void coordinatorCloseShouldBeNonOwning() {
        IndexContext ctx = mock(IndexContext.class);
        IndexPlaneCoordinator coordinator = new IndexPlaneCoordinator(ctx);

        AutoCloseable kernelStore = mock(AutoCloseable.class);
        GraphStoreAdapter primaryAdapter = new GraphStoreAdapter("KernelStore", kernelStore, java.util.Set.of());
        coordinator.register(primaryAdapter);

        // Closing coordinator must NOT close primary kernel store (DefaultSpectorMemory owns it)
        coordinator.close();
        assertThat(coordinator.state()).isEqualTo(IndexPlaneCoordinator.State.CLOSED);

        try {
            verify(kernelStore, never()).close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("BM25 hydrate should detect generation mismatch and rebuild from MemoryIndex texts")
    void shouldForceRebuildOnGenerationMismatch() {
        com.spectrayan.spector.memory.cortex.index.MemoryIndex memIndex =
                mock(com.spectrayan.spector.memory.cortex.index.MemoryIndex.class);
        com.spectrayan.spector.kernel.api.MemoryLocation loc =
                mock(com.spectrayan.spector.kernel.api.MemoryLocation.class);

        java.util.concurrent.ConcurrentHashMap<String, com.spectrayan.spector.kernel.api.MemoryLocation> map =
                new java.util.concurrent.ConcurrentHashMap<>();
        map.put("doc-rebuild", loc);

        when(memIndex.size()).thenReturn(1);
        when(memIndex.locationMap()).thenReturn(map);
        when(memIndex.text("doc-rebuild")).thenReturn("Rebuilt text content from primary memory");

        IndexContext ctx = new IndexContext(null, null, memIndex, null);
        com.spectrayan.spector.memory.cortex.MemoryBM25Index bm25 =
                new com.spectrayan.spector.memory.cortex.MemoryBM25Index(1);
        bm25.attach(ctx);

        // Initially hydrate when bundle has no region -> rebuilds from memoryIndex
        bm25.hydrate().toCompletableFuture().join();

        assertThat(bm25.totalDocuments()).isEqualTo(1);
        assertThat(bm25.contains("doc-rebuild")).isTrue();
        assertThat(bm25.search("rebuilt", 1)).isNotEmpty();
        assertThat(bm25.stats().generation()).isGreaterThan(0L);
    }
}
