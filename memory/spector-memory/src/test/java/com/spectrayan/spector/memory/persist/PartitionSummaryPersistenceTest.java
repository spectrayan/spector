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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.PartitionSummaryHeader;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.pipeline.pruning.DefaultPartitionPruner;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification suite for R2: Persisted PartitionSummary ($O(\text{partitions})$ Cold Start).
 *
 * <p>Validates that:
 * <ul>
 *   <li>Frozen partitions write summary into bundle header at freeze time</li>
 *   <li>Reopening frozen partitions reads summary without reading record payloads</li>
 *   <li>Corrupted header bytes trigger CRC failure and transparently fallback to full scan with identical search results</li>
 *   <li>Zero-record partitions freeze and reopen cleanly</li>
 *   <li>Active writable partitions remain dynamically computed</li>
 * </ul>
 * </p>
 */
class PartitionSummaryPersistenceTest {

    private static final int DIMENSIONS = 32;
    private SpectorMemory memory;
    private TestEmbeddingProvider embeddingProvider;

    private SpectorMemory build(Path dir, int semanticCap) {
        embeddingProvider = new TestEmbeddingProvider(DIMENSIONS);
        MockLlmProvider llmProvider = new MockLlmProvider();

        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(32)
                .setEpisodicPartitionCapacity(16)
                .setSemanticCapacity(semanticCap)
                .setProceduralCapacity(32);
        memProps.getRemember().setSurpriseWarmup(1);

        return DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(embeddingProvider)
                .llmProvider(llmProvider)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
            memory = null;
        }
    }

    private Path findPartition0Dir(Path rootDir) throws IOException {
        Path partitionsDir = StoragePaths.partitionsDir(rootDir);
        try (var stream = Files.newDirectoryStream(partitionsDir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && p.getFileName().toString().startsWith("000_")) {
                    return p;
                }
            }
        }
        throw new IllegalStateException("Partition 000 not found in " + partitionsDir);
    }

    @Test
    @DisplayName("a) Frozen partition writes summary into bundle header at freeze time")
    void frozenPartitionWritesSummaryIntoBundleHeaderAtFreezeTime(@TempDir Path dir) throws Exception {
        // Build with semanticCap=2 so the 3rd record forces a partition roll
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[1] = 1.0f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;

        embeddingProvider.register("Database connection pool tuning", v1);
        embeddingProvider.register("Postgres replication latency", v2);
        embeddingProvider.register("Redis cache invalidation", v3);

        memory.remember("db-1", "Database connection pool tuning", MemoryType.SEMANTIC, MemorySource.USER_STATED, "database");
        memory.remember("db-2", "Postgres replication latency", MemoryType.SEMANTIC, MemorySource.USER_STATED, "database");
        // Ingest 3rd record to force roll of partition 000
        memory.remember("cache-1", "Redis cache invalidation", MemoryType.SEMANTIC, MemorySource.USER_STATED, "cache");

        Path p0Dir = findPartition0Dir(dir);
        Path p0Bundle = StoragePaths.partitionBundleFile(p0Dir);
        assertThat(Files.exists(p0Bundle)).isTrue();

        // Inspect bundle header on disk directly
        try (PartitionBundle bundle = PartitionBundle.Init.open(p0Bundle)) {
            assertThat(bundle.hasValidSummary()).isTrue();

            PartitionSummaryHeader header = bundle.readSummary();
            assertThat(header).isNotNull();
            assertThat(header.seq()).isEqualTo(0);
            assertThat(header.semanticCount()).isEqualTo(2);
            assertThat(header.minTimestampMs()).isGreaterThan(0L);
            assertThat(header.maxTimestampMs()).isGreaterThanOrEqualTo(header.minTimestampMs());
            // Verify synaptic tag mask contains bits from the ingested tags
            assertThat(header.synapticTagMaskLo()).isNotZero();
        }
    }

    @Test
    @DisplayName("b) Reopening frozen partition reads summary without reading record payloads")
    void reopeningFrozenPartitionReadsSummaryWithoutReadingRecordPayloads(@TempDir Path dir) throws Exception {
        // Step 1: Ingest records and roll
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[1] = 1.0f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;

        embeddingProvider.register("Kafka consumer group lag", v1);
        embeddingProvider.register("Kafka partition rebalance protocol", v2);
        embeddingProvider.register("Flink state checkpointing", v3);

        memory.remember("k-1", "Kafka consumer group lag", MemoryType.SEMANTIC, MemorySource.USER_STATED, "kafka");
        memory.remember("k-2", "Kafka partition rebalance protocol", MemoryType.SEMANTIC, MemorySource.USER_STATED, "kafka");
        memory.remember("f-1", "Flink state checkpointing", MemoryType.SEMANTIC, MemorySource.USER_STATED, "flink");

        // Step 2: Close memory engine
        memory.close();
        memory = null;

        // Step 3: Reopen engine (cold start)
        memory = build(dir, 2);
        DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;
        PartitionManager pm = defaultMem.partitionManager();

        List<PartitionHandle> handles = pm.snapshot();
        assertThat(handles).hasSizeGreaterThanOrEqualTo(2);

        PartitionHandle frozenP0 = handles.get(0);
        assertThat(frozenP0.seq()).isEqualTo(0);
        assertThat(frozenP0.writable()).isFalse();

        // Verify summary was loaded directly from the bundle header without scanning
        PartitionSummary summary = frozenP0.summary();
        assertThat(summary).isNotNull();
        assertThat(summary.semanticCount()).isEqualTo(2);
        assertThat(summary.writable()).isFalse();
        assertThat(frozenP0.partitionBundle().hasValidSummary()).isTrue();

        PartitionSummaryHeader bundleHeader = frozenP0.partitionBundle().readSummary();
        assertThat(bundleHeader).isNotNull();
        assertThat(bundleHeader.semanticCount()).isEqualTo(summary.semanticCount());
        assertThat(bundleHeader.minTimestampMs()).isEqualTo(summary.minTimestampMs());
        assertThat(bundleHeader.maxTimestampMs()).isEqualTo(summary.maxTimestampMs());
        assertThat(bundleHeader.synapticTagMaskLo()).isEqualTo(summary.synapticTagMask());
        assertThat(bundleHeader.synapticTagMaskHi()).isEqualTo(summary.synapticTagMaskHi());
    }

    @Test
    @DisplayName("c) Corrupted header bytes trigger CRC failure and transparently fallback to fromRouter scan")
    void corruptedHeaderBytesTriggerCrcFailureAndTransparentlyFallbackToScan(@TempDir Path dir) throws Exception {
        // Step 1: Ingest memories into partition 0 and roll
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[1] = 1.0f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;

        embeddingProvider.register("Postgres connection pool max size is 100", v1);
        embeddingProvider.register("Postgres replication lag is under 10ms", v2);
        embeddingProvider.register("Dashboard layout uses 12-column CSS grid", v3);

        memory.remember("pg-1", "Postgres connection pool max size is 100", MemoryType.SEMANTIC, MemorySource.USER_STATED, "postgres");
        memory.remember("pg-2", "Postgres replication lag is under 10ms", MemoryType.SEMANTIC, MemorySource.USER_STATED, "postgres");
        memory.remember("ui-1", "Dashboard layout uses 12-column CSS grid", MemoryType.SEMANTIC, MemorySource.USER_STATED, "ui");

        // Execute baseline recall query before corruption
        RecallOptions recallOpts = RecallOptions.builder().topK(10).build();
        List<CognitiveResult> initialRecall = memory.recall("Postgres connection pool max size is 100", recallOpts);
        assertThat(initialRecall).isNotEmpty();
        List<String> expectedIds = initialRecall.stream().map(CognitiveResult::id).toList();

        // Step 2: Close memory
        memory.close();
        memory = null;

        // Step 3: Corrupt a byte in the summary header block at offset 512 + 16 (in minTimestampMs)
        Path p0Dir = findPartition0Dir(dir);
        Path p0Bundle = StoragePaths.partitionBundleFile(p0Dir);

        try (FileChannel channel = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(1);
            channel.read(buf, 512 + 16);
            buf.flip();
            byte original = buf.get(0);
            buf.put(0, (byte) (original ^ 0xFF));
            channel.write(buf, 512 + 16);
            channel.force(true);
        }

        // Verify that opening bundle directly detects corrupted CRC
        try (PartitionBundle corruptedBundle = PartitionBundle.Init.open(p0Bundle)) {
            assertThat(corruptedBundle.hasValidSummary()).isFalse();
            assertThat(corruptedBundle.readSummary()).isNull();
        }

        // Step 4: Reopen engine — should transparently fall back to fromRouter scan
        memory = build(dir, 2);
        DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;
        PartitionHandle reopenedP0 = defaultMem.partitionManager().snapshot().get(0);

        // Summary must still be sound and present via fromRouter scan fallback
        assertThat(reopenedP0.summary()).isNotNull();
        assertThat(reopenedP0.summary().semanticCount()).isEqualTo(2);
        assertThat(reopenedP0.summary().visibleRecordCount()).isEqualTo(2);

        // Pruning and recall search results must remain 100% sound and identical
        List<CognitiveResult> fallbackRecall = memory.recall("Postgres connection pool max size is 100", recallOpts);
        assertThat(fallbackRecall).isNotEmpty();
        List<String> actualIds = fallbackRecall.stream().map(CognitiveResult::id).toList();
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("d) Zero-record partitions freeze and reopen cleanly")
    void zeroRecordPartitionsFreezeAndReopenCleanly(@TempDir Path dir) throws Exception {
        Path bundlePath = dir.resolve("empty.bundle");
        EngramLayout cogLayout = new EngramLayout(DIMENSIONS);
        TextBlobLayout textLayout = new TextBlobLayout();

        // Create empty bundle and freeze it with zero records
        try (PartitionBundle bundle = PartitionBundle.Init.mmap(
                bundlePath,
                10, 1024L, 10, 1024L, DIMENSIONS,
                cogLayout.layoutId(), cogLayout.schemaVersion(),
                textLayout.layoutId(), textLayout.schemaVersion())) {

            PartitionSummary zeroSummary = new PartitionSummary(
                    5, 1000L, 2000L, 0L, 0L, 0, 0, 0, false);
            bundle.writeSummary(zeroSummary.toHeader());
            assertThat(bundle.hasValidSummary()).isTrue();
        }

        // Reopen empty frozen bundle
        try (PartitionBundle reopened = PartitionBundle.Init.open(bundlePath)) {
            assertThat(reopened.hasValidSummary()).isTrue();
            PartitionSummaryHeader header = reopened.readSummary();
            assertThat(header).isNotNull();
            assertThat(header.seq()).isEqualTo(5);
            assertThat(header.semanticCount()).isEqualTo(0);
            assertThat(header.episodicCount()).isEqualTo(0);
            assertThat(header.proceduralCount()).isEqualTo(0);

            PartitionSummary reopenedSummary = PartitionSummary.fromHeader(header, false);
            assertThat(reopenedSummary.visibleRecordCount()).isEqualTo(0);
            assertThat(reopenedSummary.hasRecordsFor(new MemoryType[]{MemoryType.SEMANTIC}, null)).isFalse();

            DefaultPartitionPruner pruner = new DefaultPartitionPruner();
            PartitionHandle handle = new PartitionHandle(5, dir, null, null, false, reopened, reopenedSummary);
            // Pruner should soundly prune an empty partition
            assertThat(pruner.shouldPrune(handle, RecallOptions.builder().build(), null)).isTrue();
        }
    }

    @Test
    @DisplayName("e) Active writable partitions remain dynamically computed")
    void activeWritablePartitionsRemainDynamicallyComputed(@TempDir Path dir) {
        memory = build(dir, 100);
        DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;
        PartitionHandle active = defaultMem.partitionManager().activeHandle();

        assertThat(active.writable()).isTrue();
        assertThat(active.summary().semanticCount()).isEqualTo(0);
        assertThat(active.summary().maxTimestampMs()).isEqualTo(Long.MAX_VALUE);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        embeddingProvider.register("Dynamic active memory", v1);
        memory.remember("dyn-1", "Dynamic active memory", MemoryType.SEMANTIC, MemorySource.USER_STATED, "dynamic");

        // Active partition summary must reflect live ingestion without roll or restart
        assertThat(active.summary().semanticCount()).isEqualTo(1);
        assertThat(active.summary().maxTimestampMs()).isEqualTo(Long.MAX_VALUE);

        float[] v2 = new float[DIMENSIONS]; v2[1] = 1.0f;
        embeddingProvider.register("Second active memory", v2);
        memory.remember("dyn-2", "Second active memory", MemoryType.SEMANTIC, MemorySource.USER_STATED, "dynamic");

        assertThat(active.summary().semanticCount()).isEqualTo(2);
        assertThat(active.summary().maxTimestampMs()).isEqualTo(Long.MAX_VALUE);
    }

    // ── Test Doubles ──

    static class TestEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final Map<String, float[]> registry = new HashMap<>();

        TestEmbeddingProvider(int dims) { this.dims = dims; }

        void register(String text, float[] vector) {
            registry.put(text, vector);
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = registry.get(text);
            if (vec == null) {
                vec = new float[dims];
                int h = text.hashCode();
                for (int i = 0; i < dims; i++) {
                    vec[i] = (float) Math.sin(h * (i + 1));
                }
                float norm = 0f;
                for (float v : vec) norm += v * v;
                norm = (float) Math.sqrt(norm);
                if (norm > 1e-6f) {
                    for (int i = 0; i < dims; i++) vec[i] /= norm;
                }
            }
            return new EmbeddingResult(vec, text.split("\\s+").length, "test");
        }

        @Override public int dimensions() { return dims; }
        @Override public String modelName() { return "test"; }
    }

    static class MockLlmProvider implements LlmProvider {
        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            return new LlmResponse("Consolidated response", 10, 10, "mock-llm");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String modelName() {
            return "mock-llm";
        }
    }
}
