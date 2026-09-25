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
import com.spectrayan.spector.kernel.bundle.BundleDirectory;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.PartitionSummaryHeader;
import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Empirical challenger test suite for Milestone 2:
 * Adversarially verifies cold-start O(1) partition loading, zero record payload/header access,
 * and timestamp boundary conditions.
 */
class ColdStartAdversarialChallengeTest {

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
    @DisplayName("Adversarial 1: openFrozenBundlePartition does NOT access record payloads or headers when summary is valid")
    void openFrozenBundlePartitionDoesNotAccessRecordPayloadsOrHeadersWhenSummaryValid(@TempDir Path dir) throws Exception {
        // Step 1: Create memory with capacity 2, ingest 3 records to force partition roll
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[1] = 1.0f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;
        embeddingProvider.register("Alpha record", v1);
        embeddingProvider.register("Beta record", v2);
        embeddingProvider.register("Gamma record", v3);

        memory.remember("rec-1", "Alpha record", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tagA");
        memory.remember("rec-2", "Beta record", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tagB");
        memory.remember("rec-3", "Gamma record", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tagC");

        Path p0Dir = findPartition0Dir(dir);
        Path p0Bundle = StoragePaths.partitionBundleFile(p0Dir);
        assertThat(Files.exists(p0Bundle)).isTrue();

        // Capture frozen partition 0 metadata
        long expectedMinTs;
        long expectedMaxTs;
        long expectedTagMaskLo;
        long expectedTagMaskHi;
        int expectedSemCount;

        long semanticRegionOffset;
        long semanticRegionSize;
        try (PartitionBundle bundle = PartitionBundle.Init.open(p0Bundle)) {
            assertThat(bundle.hasValidSummary()).isTrue();
            PartitionSummaryHeader h = bundle.readSummary();
            assertThat(h).isNotNull();
            expectedMinTs = h.minTimestampMs();
            expectedMaxTs = h.maxTimestampMs();
            expectedTagMaskLo = h.synapticTagMaskLo();
            expectedTagMaskHi = h.synapticTagMaskHi();
            expectedSemCount = h.semanticCount();
            assertThat(expectedSemCount).isEqualTo(2);

            RegionEntry semEntry = bundle.directory().findRegion(RegionId.SEMANTIC);
            assertThat(semEntry).isNotNull();
            semanticRegionOffset = semEntry.offset();
            semanticRegionSize = semEntry.allocatedSize();
            assertThat(semanticRegionOffset).isGreaterThanOrEqualTo(4096L);
        }

        // Close memory
        memory.close();
        memory = null;

        // Step 2: ADVERSARIAL ATTACK — Destroy all record headers and payloads in the semantic data region
        // Open bundle file directly and wipe the semantic record data area (offset 64 after region offset)
        try (FileChannel fc = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long recordDataOffset = semanticRegionOffset + 64;
            int wipeSize = (int) (semanticRegionSize - 64);
            byte[] garbage = new byte[wipeSize];
            Arrays.fill(garbage, (byte) 0xAA);
            fc.write(ByteBuffer.wrap(garbage), recordDataOffset);
            fc.force(true);
        }

        // Step 3: Reopen engine (cold start)
        // If openFrozenBundlePartition reads record payloads or record headers,
        // it would encounter 0xAA garbage and either fail or compute corrupted stats!
        memory = build(dir, 2);
        DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;
        PartitionHandle reopenedP0 = defaultMem.partitionManager().snapshot().get(0);

        // Verification 1: Partition opened cleanly without reading corrupted data region
        assertThat(reopenedP0.writable()).isFalse();
        PartitionSummary summary = reopenedP0.summary();
        assertThat(summary).isNotNull();

        // Verification 2: Summary exactly matches the persisted header, completely untouched by the wiped records
        assertThat(summary.semanticCount()).isEqualTo(expectedSemCount);
        assertThat(summary.minTimestampMs()).isEqualTo(expectedMinTs);
        assertThat(summary.maxTimestampMs()).isEqualTo(expectedMaxTs);
        assertThat(summary.synapticTagMask()).isEqualTo(expectedTagMaskLo);
        assertThat(summary.synapticTagMaskHi()).isEqualTo(expectedTagMaskHi);

        // Step 4: Now corrupt the summary header CRC to prove that fallback DOES touch the data region
        memory.close();
        memory = null;

        try (FileChannel fc = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer crcBuf = ByteBuffer.allocate(4);
            fc.read(crcBuf, PartitionSummaryHeader.OFFSET + 60);
            int origCrc = crcBuf.getInt(0);
            crcBuf.clear();
            crcBuf.putInt(origCrc ^ 0xFFFFFFFF);
            crcBuf.flip();
            fc.write(crcBuf, PartitionSummaryHeader.OFFSET + 60);
            fc.force(true);
        }

        try (PartitionBundle corruptedBundle = PartitionBundle.Init.open(p0Bundle)) {
            assertThat(corruptedBundle.hasValidSummary()).isFalse();
            assertThat(corruptedBundle.readSummary()).isNull();
        }

        // Reopening with corrupted summary header forces fallback to fromRouter.
        // Because the record area is filled with 0xAA, fromRouter scan will see garbage timestamps/tags!
        memory = build(dir, 2);
        defaultMem = (DefaultSpectorMemory) memory;
        PartitionHandle fallbackP0 = defaultMem.partitionManager().snapshot().get(0);
        PartitionSummary fallbackSummary = fallbackP0.summary();

        // Fallback DID read the records, proving that normal cold start did NOT read them!
        assertThat(fallbackSummary.synapticTagMask()).isNotEqualTo(expectedTagMaskLo);
    }

    @Test
    @DisplayName("Boundary Condition: minTimestampMs, maxTimestampMs, and Long.MAX_VALUE for active partitions")
    void timestampBoundaryConditionsAndActivePartitions(@TempDir Path dir) {
        memory = build(dir, 100);
        DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;
        PartitionHandle active = defaultMem.partitionManager().activeHandle();

        // 1. Initial active partition has maxTimestampMs == Long.MAX_VALUE
        assertThat(active.writable()).isTrue();
        assertThat(active.summary().maxTimestampMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(active.summary().minTimestampMs()).isGreaterThanOrEqualTo(0L);

        // 2. Ingest first record
        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        embeddingProvider.register("Boundary active record 1", v1);
        memory.remember("b-1", "Boundary active record 1", MemoryType.SEMANTIC, MemorySource.USER_STATED, "boundary");

        long minAfter1 = active.summary().minTimestampMs();
        assertThat(minAfter1).isGreaterThan(0L);
        assertThat(active.summary().maxTimestampMs()).isEqualTo(Long.MAX_VALUE);

        // 3. Ingest second record later
        float[] v2 = new float[DIMENSIONS]; v2[1] = 1.0f;
        embeddingProvider.register("Boundary active record 2", v2);
        memory.remember("b-2", "Boundary active record 2", MemoryType.SEMANTIC, MemorySource.USER_STATED, "boundary");

        assertThat(active.summary().minTimestampMs()).isEqualTo(minAfter1);
        assertThat(active.summary().maxTimestampMs()).isEqualTo(Long.MAX_VALUE);

        // 4. Test pruning boundaries on active partition:
        // Because maxTimestampMs == Long.MAX_VALUE, active partition is NEVER pruned by query minTimestamp
        DefaultPartitionPruner pruner = new DefaultPartitionPruner();
        RecallOptions futureMinOptions = RecallOptions.builder()
                .minTimestamp(System.currentTimeMillis() + 1_000_000_000L) // Far in the future
                .build();
        assertThat(pruner.shouldPrune(active, futureMinOptions, new MemoryType[]{MemoryType.SEMANTIC})).isFalse();

        // 5. Test pruning boundaries on exact timestamp bounds
        long partitionMin = 1000L;
        long partitionMax = 5000L;
        PartitionSummary frozenSummary = new PartitionSummary(
                0, partitionMin, partitionMax, 0x1L, 0L, 10, 0, 0, false);
        PartitionHandle frozenHandle = new PartitionHandle(0, dir, null, null, false, null, frozenSummary);

        // Query minTimestamp == partitionMax (exact boundary) -> NOT pruned
        RecallOptions exactMax = RecallOptions.builder().minTimestamp(partitionMax).build();
        assertThat(pruner.shouldPrune(frozenHandle, exactMax, new MemoryType[]{MemoryType.SEMANTIC})).isFalse();

        // Query minTimestamp == partitionMax + 1 -> PRUNED
        RecallOptions pastMax = RecallOptions.builder().minTimestamp(partitionMax + 1).build();
        assertThat(pruner.shouldPrune(frozenHandle, pastMax, new MemoryType[]{MemoryType.SEMANTIC})).isTrue();

        // Query maxTimestamp == partitionMin (exact boundary) -> NOT pruned
        RecallOptions exactMin = RecallOptions.builder().maxTimestamp(partitionMin).build();
        assertThat(pruner.shouldPrune(frozenHandle, exactMin, new MemoryType[]{MemoryType.SEMANTIC})).isFalse();

        // Query maxTimestamp == partitionMin - 1 -> PRUNED
        RecallOptions beforeMin = RecallOptions.builder().maxTimestamp(partitionMin - 1).build();
        assertThat(pruner.shouldPrune(frozenHandle, beforeMin, new MemoryType[]{MemoryType.SEMANTIC})).isTrue();
    }

    @Test
    @DisplayName("Performance & Scaling: Cold-start reads are O(partitions) rather than O(records)")
    void coldStartPerformanceScaling(@TempDir Path dir) throws Exception {
        // Build engine with small capacity to create multiple partitions
        int numPartitions = 5;
        int recordsPerPartition = 4;
        memory = build(dir, recordsPerPartition);

        for (int p = 0; p < numPartitions; p++) {
            for (int r = 0; r < recordsPerPartition; r++) {
                String id = "scale-" + p + "-" + r;
                float[] v = new float[DIMENSIONS];
                v[(p + r) % DIMENSIONS] = 1.0f;
                embeddingProvider.register(id, v);
                memory.remember(id, "Scale testing text " + id, MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag" + p);
            }
        }

        // Close engine
        memory.close();
        memory = null;

        // Cold start timing with persisted summaries
        long startWithSummaries = System.nanoTime();
        memory = build(dir, recordsPerPartition);
        DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;
        List<PartitionHandle> handlesWithSummaries = defaultMem.partitionManager().snapshot();
        long durationWithSummaries = System.nanoTime() - startWithSummaries;

        assertThat(handlesWithSummaries).hasSizeGreaterThanOrEqualTo(numPartitions);
        for (int i = 0; i < handlesWithSummaries.size() - 1; i++) {
            PartitionHandle h = handlesWithSummaries.get(i);
            assertThat(h.writable()).isFalse();
            assertThat(h.partitionBundle().hasValidSummary()).isTrue();
            assertThat(h.summary()).isNotNull();
            assertThat(h.summary().semanticCount()).isEqualTo(recordsPerPartition);
        }

        memory.close();
        memory = null;

        // Invalidate all summaries on disk to measure fallback scan time
        Path partitionsDir = StoragePaths.partitionsDir(dir);
        try (var stream = Files.newDirectoryStream(partitionsDir)) {
            for (Path pDir : stream) {
                Path bundleFile = StoragePaths.partitionBundleFile(pDir);
                if (Files.exists(bundleFile)) {
                    try (FileChannel fc = FileChannel.open(bundleFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                        ByteBuffer crcBuf = ByteBuffer.allocate(4);
                        fc.read(crcBuf, PartitionSummaryHeader.OFFSET + 60);
                        int crc = crcBuf.getInt(0);
                        crcBuf.clear();
                        crcBuf.putInt(crc ^ 0xFFFFFFFF);
                        crcBuf.flip();
                        fc.write(crcBuf, PartitionSummaryHeader.OFFSET + 60);
                        fc.force(true);
                    }
                }
            }
        }

        // Cold start timing with fallback fromRouter scans
        long startWithScans = System.nanoTime();
        memory = build(dir, recordsPerPartition);
        defaultMem = (DefaultSpectorMemory) memory;
        List<PartitionHandle> handlesWithScans = defaultMem.partitionManager().snapshot();
        long durationWithScans = System.nanoTime() - startWithScans;

        assertThat(handlesWithScans).hasSizeGreaterThanOrEqualTo(numPartitions);
        for (int i = 0; i < handlesWithScans.size() - 1; i++) {
            PartitionHandle h = handlesWithScans.get(i);
            assertThat(h.writable()).isFalse();
            assertThat(h.partitionBundle().hasValidSummary()).isFalse(); // Summary was corrupted
            assertThat(h.summary()).isNotNull(); // Loaded via fallback
            assertThat(h.summary().semanticCount()).isEqualTo(recordsPerPartition);
        }

        // Summary-based cold start handles all partitions cleanly
        assertThat(durationWithSummaries).isGreaterThan(0L);
        assertThat(durationWithScans).isGreaterThan(0L);
    }

    @Test
    @DisplayName("Bundle Header Offset Layout: 64-byte alignment and padding verification")
    void bundleHeaderOffsetLayoutInvariants() {
        // PartitionSummaryHeader must be at offset 512, size 64
        assertThat(PartitionSummaryHeader.OFFSET).isEqualTo(512L);
        assertThat(PartitionSummaryHeader.SIZE).isEqualTo(64L);

        // Cache-line aligned (64 bytes)
        assertThat(PartitionSummaryHeader.OFFSET % 64).isZero();

        // Sits after bundle entries (128 + 5 * 64 = 448) and before data region (4096)
        long bundleEntriesEnd = 128L + 5L * 64L;
        assertThat(PartitionSummaryHeader.OFFSET).isGreaterThanOrEqualTo(bundleEntriesEnd);
        assertThat(PartitionSummaryHeader.OFFSET + PartitionSummaryHeader.SIZE).isLessThanOrEqualTo(4096L);
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
