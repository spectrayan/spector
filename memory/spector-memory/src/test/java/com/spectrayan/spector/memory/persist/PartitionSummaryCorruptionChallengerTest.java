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
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
 * Empirical Adversarial Challenger Test Suite for Milestone 2:
 * Partition Summary Header Error Injection, Corruption Resistance,
 * Soundness Verification under Pruning/Filters, and Zero-Record Lifecycles.
 */
class PartitionSummaryCorruptionChallengerTest {

    private static final int DIMENSIONS = 32;
    private SpectorMemory memory;
    private ChallengerEmbeddingProvider embeddingProvider;

    private final Map<String, float[]> embeddingRegistry = new HashMap<>();

    private SpectorMemory build(Path dir, int semanticCap) {
        embeddingProvider = new ChallengerEmbeddingProvider(DIMENSIONS, embeddingRegistry);
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
        embeddingRegistry.clear();
    }

    private Path findPartitionBundle(Path rootDir, int seq) throws IOException {
        Path partitionsDir = StoragePaths.partitionsDir(rootDir);
        String prefix = String.format("%03d_", seq);
        try (var stream = Files.newDirectoryStream(partitionsDir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && p.getFileName().toString().startsWith(prefix)) {
                    return StoragePaths.partitionBundleFile(p);
                }
            }
        }
        throw new IllegalStateException("Partition " + prefix + " not found in " + partitionsDir);
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 61, 62, 63})
    @DisplayName("Challenge 1a: Corrupting any CRC byte [60..63] invalidates header and triggers sound fallback")
    void corruptingAnyCrcByteTriggersSoundFallback(int crcByteOffset, @TempDir Path dir) throws Exception {
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[0] = 0.8f; v2[1] = 0.6f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;

        embeddingProvider.register("Vector indexing with HNSW primary", v1);
        embeddingProvider.register("Vector indexing with HNSW secondary", v2);
        embeddingProvider.register("Unrelated background document", v3);

        memory.remember("vec-1", "Vector indexing with HNSW primary", MemoryType.SEMANTIC, MemorySource.USER_STATED, "index");
        memory.remember("vec-2", "Vector indexing with HNSW secondary", MemoryType.SEMANTIC, MemorySource.USER_STATED, "index");
        // Force roll of partition 0 into partition 1
        memory.remember("vec-3", "Unrelated background document", MemoryType.SEMANTIC, MemorySource.USER_STATED, "other");

        memory.close();
        memory = null;

        // Step 1: Reopen normal uncorrupted engine to capture oracle baseline
        memory = build(dir, 2);
        RecallOptions recallOpts = RecallOptions.builder().topK(5).build();
        List<CognitiveResult> normalRecall = memory.recall("Vector indexing with HNSW primary", recallOpts);
        assertThat(normalRecall).isNotEmpty();
        List<String> expectedIds = normalRecall.stream().map(CognitiveResult::id).toList();
        memory.close();
        memory = null;

        // Step 2: Corrupt specified CRC byte in partition 0 bundle (offset 512 + crcByteOffset)
        Path p0Bundle = findPartitionBundle(dir, 0);
        try (FileChannel channel = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(1);
            channel.read(buf, PartitionSummaryHeader.OFFSET + crcByteOffset);
            buf.flip();
            byte original = buf.get(0);
            buf.put(0, (byte) (original ^ 0xAA));
            channel.write(buf, PartitionSummaryHeader.OFFSET + crcByteOffset);
            channel.force(true);
        }

        // Direct bundle inspection: summary must be invalid and read null
        try (PartitionBundle bundle = PartitionBundle.Init.open(p0Bundle)) {
            assertThat(bundle.hasValidSummary()).isFalse();
            assertThat(bundle.readSummary()).isNull();
        }

        // Step 3: Reopen engine with corrupted CRC — must transparently recover via fromRouter
        memory = build(dir, 2);
        DefaultSpectorMemory reopened = (DefaultSpectorMemory) memory;
        PartitionHandle handle0 = reopened.partitionManager().snapshot().get(0);

        assertThat(handle0.summary()).isNotNull();
        assertThat(handle0.summary().semanticCount()).isEqualTo(2);
        assertThat(handle0.summary().visibleRecordCount()).isEqualTo(2);

        // Verify 100% sound search results matching uncorrupted oracle baseline
        List<CognitiveResult> fallbackRecall = memory.recall("Vector indexing with HNSW primary", recallOpts);
        List<String> actualIds = fallbackRecall.stream().map(CognitiveResult::id).toList();
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
    }

    @ParameterizedTest
    @ValueSource(ints = {
            0,  // magic byte 0
            4,  // version byte 0
            8,  // seq byte 0
            16, // minTimestampMs byte 0
            24, // maxTimestampMs byte 0
            32, // synapticTagMaskLo byte 0
            40, // synapticTagMaskHi byte 0
            48, // semanticCount byte 0
            52, // episodicCount byte 0
            56, // proceduralCount byte 0
            59  // proceduralCount byte 3 (last payload byte)
    })
    @DisplayName("Challenge 1b: Flipping any payload byte in [0..59] invalidates header and triggers sound fallback")
    void flippingPayloadBytesTriggersSoundFallback(int payloadByteOffset, @TempDir Path dir) throws Exception {
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[0] = 0.8f; v2[1] = 0.6f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;

        embeddingProvider.register("Neural engram consolidation primary", v1);
        embeddingProvider.register("Neural engram consolidation secondary", v2);
        embeddingProvider.register("Unrelated synaptic trace", v3);

        memory.remember("neuro-1", "Neural engram consolidation primary", MemoryType.SEMANTIC, MemorySource.USER_STATED, "neuro");
        memory.remember("neuro-2", "Neural engram consolidation secondary", MemoryType.SEMANTIC, MemorySource.USER_STATED, "neuro");
        memory.remember("neuro-3", "Unrelated synaptic trace", MemoryType.SEMANTIC, MemorySource.USER_STATED, "other");

        memory.close();
        memory = null;

        // Step 1: Capture uncorrupted oracle baseline
        memory = build(dir, 2);
        RecallOptions recallOpts = RecallOptions.builder().topK(5).build();
        List<CognitiveResult> normalRecall = memory.recall("Neural engram consolidation primary", recallOpts);
        assertThat(normalRecall).isNotEmpty();
        List<String> expectedIds = normalRecall.stream().map(CognitiveResult::id).toList();
        memory.close();
        memory = null;

        // Step 2: Corrupt payload byte at specified offset
        Path p0Bundle = findPartitionBundle(dir, 0);
        try (FileChannel channel = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(1);
            channel.read(buf, PartitionSummaryHeader.OFFSET + payloadByteOffset);
            buf.flip();
            byte original = buf.get(0);
            buf.put(0, (byte) (original ^ 0x55));
            channel.write(buf, PartitionSummaryHeader.OFFSET + payloadByteOffset);
            channel.force(true);
        }

        try (PartitionBundle bundle = PartitionBundle.Init.open(p0Bundle)) {
            assertThat(bundle.hasValidSummary()).isFalse();
            assertThat(bundle.readSummary()).isNull();
        }

        // Step 3: Reopen engine and verify fromRouter fallback soundness
        memory = build(dir, 2);
        DefaultSpectorMemory reopened = (DefaultSpectorMemory) memory;
        PartitionHandle handle0 = reopened.partitionManager().snapshot().get(0);

        assertThat(handle0.summary()).isNotNull();
        assertThat(handle0.summary().semanticCount()).isEqualTo(2);

        List<CognitiveResult> fallbackRecall = memory.recall("Neural engram consolidation primary", recallOpts);
        List<String> actualIds = fallbackRecall.stream().map(CognitiveResult::id).toList();
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("Challenge 1c: Legacy bundle with magic 0x00000000 triggers fromRouter fallback with 100% soundness")
    void legacyBundleWithZeroMagicTriggersSoundFallback(@TempDir Path dir) throws Exception {
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[0] = 0.8f; v2[1] = 0.6f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;

        embeddingProvider.register("PostgreSQL database connection pooling", v1);
        embeddingProvider.register("PostgreSQL replication streaming slot", v2);
        embeddingProvider.register("React user interface virtual DOM", v3);

        memory.remember("pg-1", "PostgreSQL database connection pooling", MemoryType.SEMANTIC, MemorySource.USER_STATED, "db");
        memory.remember("pg-2", "PostgreSQL replication streaming slot", MemoryType.SEMANTIC, MemorySource.USER_STATED, "db");
        memory.remember("ui-1", "React user interface virtual DOM", MemoryType.SEMANTIC, MemorySource.USER_STATED, "ui");

        memory.close();
        memory = null;

        // Capture oracle baseline
        memory = build(dir, 2);
        RecallOptions recallOpts = RecallOptions.builder().topK(5).build();
        List<CognitiveResult> normalRecall = memory.recall("PostgreSQL database connection pooling", recallOpts);
        assertThat(normalRecall).isNotEmpty();
        List<String> expectedIds = normalRecall.stream().map(CognitiveResult::id).toList();
        memory.close();
        memory = null;

        // Zero out entire 64-byte summary region (offset 512..575) to simulate legacy bundle created without summary
        Path p0Bundle = findPartitionBundle(dir, 0);
        try (FileChannel channel = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer zeros = ByteBuffer.allocate(64);
            channel.write(zeros, PartitionSummaryHeader.OFFSET);
            channel.force(true);
        }

        try (PartitionBundle bundle = PartitionBundle.Init.open(p0Bundle)) {
            assertThat(bundle.hasValidSummary()).isFalse();
            assertThat(bundle.readSummary()).isNull();
        }

        // Reopen engine — should recognize magic 0x00000000 as absent and fall back to fromRouter
        memory = build(dir, 2);
        DefaultSpectorMemory reopened = (DefaultSpectorMemory) memory;
        PartitionHandle handle0 = reopened.partitionManager().snapshot().get(0);

        assertThat(handle0.summary()).isNotNull();
        assertThat(handle0.summary().semanticCount()).isEqualTo(2);

        List<CognitiveResult> fallbackRecall = memory.recall("PostgreSQL database connection pooling", recallOpts);
        List<String> actualIds = fallbackRecall.stream().map(CognitiveResult::id).toList();
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("Challenge 1d: Mixed multi-partition corruption maintains full namespace search precision and recall")
    void mixedMultiPartitionCorruptionMaintainsFullPrecisionAndRecall(@TempDir Path dir) throws Exception {
        // Create 4 partitions: 3 frozen, 1 active (semanticCap = 1 so each item forces roll)
        memory = build(dir, 1);

        String[] topics = {
                "Quantum computing entanglement protocol",
                "Distributed consensus Raft cluster",
                "Graph neural network message passing",
                "Compiler abstract syntax tree lowering"
        };

        for (int i = 0; i < 4; i++) {
            float[] vec = new float[DIMENSIONS];
            vec[i] = 1.0f;
            embeddingProvider.register(topics[i], vec);
            memory.remember("doc-" + i, topics[i], MemoryType.SEMANTIC, MemorySource.USER_STATED, "topic-" + i);
        }

        memory.close();
        memory = null;

        // Capture uncorrupted oracle baseline
        memory = build(dir, 1);
        RecallOptions recallOpts = RecallOptions.builder().topK(10).build();
        List<CognitiveResult> normalRecall = memory.recall("Quantum computing entanglement protocol", recallOpts);
        List<String> expectedIds = normalRecall.stream().map(CognitiveResult::id).toList();
        memory.close();
        memory = null;

        // Partition 0: corrupt CRC byte 60
        Path p0Bundle = findPartitionBundle(dir, 0);
        try (FileChannel ch = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer b = ByteBuffer.allocate(1);
            ch.read(b, PartitionSummaryHeader.OFFSET + 60);
            b.flip();
            b.put(0, (byte) (b.get(0) ^ 0xFF));
            ch.write(b, PartitionSummaryHeader.OFFSET + 60);
        }

        // Partition 1: zero out magic (legacy bundle)
        Path p1Bundle = findPartitionBundle(dir, 1);
        try (FileChannel ch = FileChannel.open(p1Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer zeros = ByteBuffer.allocate(64);
            ch.write(zeros, PartitionSummaryHeader.OFFSET);
        }

        // Partition 2: corrupt maxTimestampMs payload byte 24
        Path p2Bundle = findPartitionBundle(dir, 2);
        try (FileChannel ch = FileChannel.open(p2Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer b = ByteBuffer.allocate(1);
            ch.read(b, PartitionSummaryHeader.OFFSET + 24);
            b.flip();
            b.put(0, (byte) (b.get(0) ^ 0x7F));
            ch.write(b, PartitionSummaryHeader.OFFSET + 24);
        }

        // Reopen engine with mixed corruptions
        memory = build(dir, 1);
        DefaultSpectorMemory reopened = (DefaultSpectorMemory) memory;
        List<PartitionHandle> handles = reopened.partitionManager().snapshot();
        assertThat(handles).hasSize(4);

        // Partition 0, 1, 2 must have valid summaries via fallback; Partition 3 is active
        for (int i = 0; i < 3; i++) {
            assertThat(handles.get(i).summary()).isNotNull();
            assertThat(handles.get(i).summary().semanticCount()).isEqualTo(1);
        }

        // Search recall across entire namespace must match oracle baseline in elements and top match
        List<CognitiveResult> fallbackRecall = memory.recall("Quantum computing entanglement protocol", recallOpts);
        List<String> actualIds = fallbackRecall.stream().map(CognitiveResult::id).toList();
        assertThat(actualIds).containsExactlyInAnyOrderElementsOf(expectedIds);
        assertThat(actualIds.get(0)).isEqualTo("doc-0"); // Top match is deterministic and sound
    }

    @Test
    @DisplayName("Challenge 2a: Zero-record partition freeze and reopen under full engine lifecycle")
    void zeroRecordPartitionFreezeAndReopenUnderEngineLifecycle(@TempDir Path dir) throws Exception {
        memory = build(dir, 10);
        DefaultSpectorMemory defaultMem = (DefaultSpectorMemory) memory;

        // Force roll while partition 0 has 0 records
        defaultMem.partitionManager().rollPartition();

        List<PartitionHandle> snapshot = defaultMem.partitionManager().snapshot();
        assertThat(snapshot).hasSize(2);

        PartitionHandle frozenP0 = snapshot.get(0);
        assertThat(frozenP0.writable()).isFalse();
        assertThat(frozenP0.summary().visibleRecordCount()).isEqualTo(0);

        // Add a record to partition 1
        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        embeddingProvider.register("Post-empty-partition memory", v1);
        memory.remember("post-1", "Post-empty-partition memory", MemoryType.SEMANTIC, MemorySource.USER_STATED, "test");

        // Verify recall prunes empty partition soundly and finds record in active partition
        List<CognitiveResult> recall1 = memory.recall("Post-empty-partition memory", RecallOptions.builder().topK(5).build());
        assertThat(recall1).hasSize(1);
        assertThat(recall1.get(0).id()).isEqualTo("post-1");

        // Restart engine to verify cold start on empty frozen partition
        memory.close();
        memory = null;

        memory = build(dir, 10);
        DefaultSpectorMemory restartedMem = (DefaultSpectorMemory) memory;
        List<PartitionHandle> restartedSnapshot = restartedMem.partitionManager().snapshot();
        assertThat(restartedSnapshot).hasSizeGreaterThanOrEqualTo(2);

        PartitionHandle restartedP0 = restartedSnapshot.get(0);
        assertThat(restartedP0.writable()).isFalse();
        assertThat(restartedP0.summary()).isNotNull();
        assertThat(restartedP0.summary().visibleRecordCount()).isEqualTo(0);

        // Empty partition must be soundly pruned by pruner
        DefaultPartitionPruner pruner = new DefaultPartitionPruner();
        assertThat(pruner.shouldPrune(restartedP0, RecallOptions.builder().build(), null)).isTrue();

        // Active partition record must be cleanly recalled
        List<CognitiveResult> recall2 = memory.recall("Post-empty-partition memory", RecallOptions.builder().topK(5).build());
        assertThat(recall2).hasSize(1);
        assertThat(recall2.get(0).id()).isEqualTo("post-1");
    }

    @Test
    @DisplayName("Challenge 2b: Fallback summary correctly preserves tag and temporal pruning soundness")
    void fallbackSummaryPreservesPruningSoundness(@TempDir Path dir) throws Exception {
        memory = build(dir, 2);

        float[] v1 = new float[DIMENSIONS]; v1[0] = 1.0f;
        float[] v2 = new float[DIMENSIONS]; v2[1] = 1.0f;
        float[] v3 = new float[DIMENSIONS]; v3[2] = 1.0f;

        embeddingProvider.register("Corrupt tag test alpha", v1);
        embeddingProvider.register("Corrupt tag test beta", v2);
        embeddingProvider.register("Active tag test gamma", v3);

        memory.remember("tag-a", "Corrupt tag test alpha", MemoryType.SEMANTIC, MemorySource.USER_STATED, "security");
        memory.remember("tag-b", "Corrupt tag test beta", MemoryType.SEMANTIC, MemorySource.USER_STATED, "crypto");
        memory.remember("tag-c", "Active tag test gamma", MemoryType.SEMANTIC, MemorySource.USER_STATED, "networking");

        memory.close();
        memory = null;

        // Corrupt summary in partition 0
        Path p0Bundle = findPartitionBundle(dir, 0);
        try (FileChannel ch = FileChannel.open(p0Bundle, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer b = ByteBuffer.allocate(1);
            ch.read(b, PartitionSummaryHeader.OFFSET + 60);
            b.flip();
            b.put(0, (byte) (b.get(0) ^ 0xFF));
            ch.write(b, PartitionSummaryHeader.OFFSET + 60);
        }

        memory = build(dir, 2);
        DefaultSpectorMemory reopened = (DefaultSpectorMemory) memory;
        PartitionHandle handle0 = reopened.partitionManager().snapshot().get(0);

        // Fallback summary must have accurate synapticTagMask matching the ingested tags
        PartitionSummary fallbackSummary = handle0.summary();
        assertThat(fallbackSummary).isNotNull();
        assertThat(fallbackSummary.synapticTagMask()).isNotZero();

        // Query with non-matching tag filter: partition 0 MUST be pruned (zero false positives on irrelevance)
        DefaultPartitionPruner pruner = new DefaultPartitionPruner();
        RecallOptions nonMatchingFilter = RecallOptions.builder()
                .synapticTagMask(0x8000_0000_0000_0000L) // bit not present
                .build();
        assertThat(pruner.shouldPrune(handle0, nonMatchingFilter, null)).isTrue();

        // Query with matching tag filter: partition 0 must NOT be pruned (zero false negatives)
        RecallOptions matchingFilter = RecallOptions.builder()
                .synapticTagMask(fallbackSummary.synapticTagMask())
                .build();
        assertThat(pruner.shouldPrune(handle0, matchingFilter, null)).isFalse();
    }

    // ── Test Doubles ──

    static class ChallengerEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final Map<String, float[]> registry;

        ChallengerEmbeddingProvider(int dims, Map<String, float[]> registry) {
            this.dims = dims;
            this.registry = registry;
        }

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
