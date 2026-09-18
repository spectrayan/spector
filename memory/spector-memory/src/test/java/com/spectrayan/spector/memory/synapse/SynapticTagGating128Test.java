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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.core.cognitive.SynapticTag128;
import com.spectrayan.spector.core.cognitive.SynapticTagMath;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.HeaderBits;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.scan.ScanFilter;
import com.spectrayan.spector.kernel.scan.SlabScanner;
import com.spectrayan.spector.kernel.scan.SlotVisitor;
import com.spectrayan.spector.kernel.score.RecordGates;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.synapse.scan.CognitiveScoreVisitor;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification test suite for 128-bit synaptic tag gating, scoring, and storage
 * resolving GitHub Issue #795 across the entire memory pipeline.
 */
class SynapticTagGating128Test {

    private static final int DIMS = 16;
    private static final EngramLayout LAYOUT = new EngramLayout(DIMS);

    @Test
    @DisplayName("Issue #795: SlabScanner correctly passes 128-bit tags (lo and hi) and RecordGates.isTagGated128 filters high bits")
    void testSlabScanner128BitGating() {
        final int recordCount = 4;
        final long nowMs = 1_720_000_000_000L;
        final long byteSize = (long) recordCount * LAYOUT.recordStride();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(byteSize);

            // Record 0: Tag only in low bits (bit 1)
            LAYOUT.writeHeader(segment, 0 * LAYOUT.recordStride(),
                    new EncodingHeader(nowMs - 1000L, 0x02L, 0x00L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0, (byte) 50, 1.0f));

            // Record 1: Tag only in high bits (bit 127 = 0x8000_0000_0000_0000L in hi)
            LAYOUT.writeHeader(segment, 1 * LAYOUT.recordStride(),
                    new EncodingHeader(nowMs - 1000L, 0x00L, 0x8000_0000_0000_0000L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0, (byte) 50, 1.0f));

            // Record 2: Tags in both low and high bits
            LAYOUT.writeHeader(segment, 2 * LAYOUT.recordStride(),
                    new EncodingHeader(nowMs - 1000L, 0x02L, 0x8000_0000_0000_0000L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0, (byte) 50, 1.0f));

            // Record 3: No tags (0L, 0L)
            LAYOUT.writeHeader(segment, 3 * LAYOUT.recordStride(),
                    new EncodingHeader(nowMs - 1000L, 0x00L, 0x00L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0, (byte) 50, 1.0f));

            // Query filtering ONLY for high bit 127 (lo = 0L, hi = 0x8000_0000_0000_0000L)
            ScanFilter filter = new ScanFilter(
                    0L, 0x8000_0000_0000_0000L, 0L, 0L,
                    0L, Long.MAX_VALUE, nowMs, false,
                    (byte) -128, (byte) 127,
                    0.0f, (byte) 0, (byte) 0, false, false,
                    RecordGates.DEFAULT_STALE_BUCKET_THRESHOLD,
                    RecordGates.DEFAULT_WEAK_MASS_THRESHOLD
            );

            List<Integer> survivors = new ArrayList<>();
            List<SynapticTag128> receivedTags = new ArrayList<>();

            SlotVisitor visitor = new SlotVisitor() {
                @Override
                public void accept(int slot, int partition, long offset, long headerBits, float rawScore) {
                    survivors.add(slot);
                }

                @Override
                public void accept(int slot, int partition, long offset, long headerBits, float rawScore, long timestampMs, long tagsLo, long tagsHi) {
                    survivors.add(slot);
                    receivedTags.add(new SynapticTag128(tagsLo, tagsHi));
                }
            };

            float[] queryVector = new float[DIMS];
            SlabScanner.scan(
                    segment, recordCount, LAYOUT, queryVector,
                    null, null, filter, null, MemoryType.SEMANTIC,
                    0L, 0, visitor
            );

            // Record 1 and Record 2 have the matching high bit; Record 0 and 3 do not.
            assertThat(survivors).containsExactly(1, 2);
            assertThat(receivedTags.get(0)).isEqualTo(new SynapticTag128(0x00L, 0x8000_0000_0000_0000L));
            assertThat(receivedTags.get(1)).isEqualTo(new SynapticTag128(0x02L, 0x8000_0000_0000_0000L));
        }
    }

    @Test
    @DisplayName("Issue #795: Hyperfocus gating in SlabScanner with 128-bit mask")
    void testHyperfocus128BitGating() {
        final int recordCount = 3;
        final long nowMs = 1_720_000_000_000L;
        final long byteSize = (long) recordCount * LAYOUT.recordStride();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(byteSize);

            // Record 0: missing required hyperfocus bit in hi
            LAYOUT.writeHeader(segment, 0 * LAYOUT.recordStride(),
                    new EncodingHeader(nowMs - 1000L, 0x05L, 0x10L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0, (byte) 50, 1.0f));

            // Record 1: satisfies both lo (0x05) and hi (0x30 contains 0x20)
            LAYOUT.writeHeader(segment, 1 * LAYOUT.recordStride(),
                    new EncodingHeader(nowMs - 1000L, 0x07L, 0x30L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0, (byte) 50, 1.0f));

            // Record 2: satisfies hi (0x20) but missing lo (0x01 missing 0x04)
            LAYOUT.writeHeader(segment, 2 * LAYOUT.recordStride(),
                    new EncodingHeader(nowMs - 1000L, 0x01L, 0x20L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0, (byte) 50, 1.0f));

            // Hyperfocus requirement: lo must have 0x05, hi must have 0x20
            ScanFilter filter = new ScanFilter(
                    0L, 0L, 0x05L, 0x20L,
                    0L, Long.MAX_VALUE, nowMs, false,
                    (byte) -128, (byte) 127,
                    0.0f, (byte) 0, (byte) 0, false, false,
                    RecordGates.DEFAULT_STALE_BUCKET_THRESHOLD,
                    RecordGates.DEFAULT_WEAK_MASS_THRESHOLD
            );

            List<Integer> survivors = new ArrayList<>();
            SlotVisitor visitor = (slot, partition, offset, headerBits, rawScore) -> survivors.add(slot);

            float[] queryVector = new float[DIMS];
            SlabScanner.scan(
                    segment, recordCount, LAYOUT, queryVector,
                    null, null, filter, null, MemoryType.SEMANTIC,
                    0L, 0, visitor
            );

            // Only Record 1 satisfies both lo and hi hyperfocus requirements
            assertThat(survivors).containsExactly(1);
        }
    }

    @Test
    @DisplayName("Issue #795: CognitiveScoreVisitor processes 128-bit tags with lateral tag overlap")
    void testCognitiveScoreVisitor128BitLateralOverlap() {
        final long nowMs = 1_720_000_000_000L;
        final float rawDistance = 0.1f;
        final float importance = 5.0f;
        final byte arousal = (byte) 100;
        final float storageStrength = 1.0f;
        final int activationCount = 1;
        final byte flags = 0;
        final byte valence = 0;

        long headerBits = HeaderBits.pack(
                flags, valence, arousal, activationCount,
                importance, storageStrength, MemoryType.SEMANTIC.ordinal()
        );

        // Query tag mask with high bits:
        long queryLo = 0x00L;
        long queryHi = 0x00FF_0000_0000_0000L;

        RecallOptions options = RecallOptions.builder()
                .topK(5)
                .lateralMode(true)
                .synapticTagMask(queryLo, queryHi)
                .build();

        CognitiveScoreVisitor visitor = new CognitiveScoreVisitor(options, nowMs, null, null);

        // Record with matching high tag bits
        visitor.accept(0, 0, 0L, headerBits, rawDistance, nowMs - 1000L, 0x00L, 0x000F_0000_0000_0000L);

        // Record with non-matching tag bits
        visitor.accept(1, 0, 100L, headerBits, rawDistance, nowMs - 1000L, 0x00L, 0x0000_0000_0000_0001L);

        List<CognitiveScorer.ScoredRecord> results = visitor.drain();
        assertThat(results).hasSize(2);

        // Record 0 should score higher due to lateral tag overlap in high 64 bits
        assertThat(results.get(0).index()).isEqualTo(0);
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    @DisplayName("Issue #795: End-to-end SpectorMemory recall with 128-bit synaptic tag filtering")
    void testEndToEndRecall128Bit() throws Exception {
        MemoryProperties props = new MemoryProperties()
                .setDimensions(DIMS)
                .setWorkingCapacity(10)
                .setEpisodicPartitionCapacity(50)
                .setSemanticCapacity(50)
                .setProceduralCapacity(50);

        try (SpectorMemory memory = DefaultSpectorMemory.builder(props)
                .embeddingProvider(new MockEmbeddingProvider(DIMS))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {

            // Store memories with tags
            memory.remember("mem-alpha", "Alpha neural pathway configuration",
                    MemoryType.EPISODIC, MemorySource.OBSERVED, "neural", "synapse");
            memory.remember("mem-beta", "Beta cognitive memory indexing",
                    MemoryType.EPISODIC, MemorySource.OBSERVED, "indexing", "database");
            memory.remember("mem-gamma", "Gamma system telemetry",
                    MemoryType.EPISODIC, MemorySource.OBSERVED, "telemetry", "system");

            // Calculate 128-bit tag hash for "synapse"
            SynapticTag128 synapseTag = SynapticTagMath.encodeTag128("synapse");
            assertThat(synapseTag.popcount()).isGreaterThan(0);

            // Recall querying specifically for the 128-bit mask of "synapse"
            RecallOptions filterOptions = RecallOptions.builder()
                    .synapticTagMask(synapseTag.lo(), synapseTag.hi())
                    .build();

            List<CognitiveResult> filteredResults = memory.recall("neural cognitive system", filterOptions);
            assertThat(filteredResults).isNotEmpty();
            // mem-alpha contains "synapse", so it must be present
            assertThat(filteredResults.stream().anyMatch(r -> r.id().equals("mem-alpha"))).isTrue();

            // All returned memories must contain the "synapse" tag
            for (CognitiveResult r : filteredResults) {
                assertThat(Arrays.asList(r.synapticTags())).contains("synapse");
            }

            // Also verify backward compatibility: 64-bit synapticFilter()
            RecallOptions legacyOptions = RecallOptions.builder()
                    .synapticTagMask(synapseTag.lo())
                    .build();
            assertThat(legacyOptions.synapticTagMaskHi()).isEqualTo(0L);
            assertThat(legacyOptions.synapticTagMask()).isEqualTo(synapseTag.lo());
        }
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(int dims) {
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            Random rng = new Random(text.hashCode());
            float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                vector[i] = (rng.nextFloat() - 0.5f) * 2.0f;
            }
            float norm = 0f;
            for (float v : vector) norm += v * v;
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
