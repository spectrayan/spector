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
package com.spectrayan.spector.memory.hippocampus;

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.cortex.EpisodicPartitionedMemory;
import com.spectrayan.spector.memory.cortex.EpisodicPartitionedMemory.EpisodicPartition;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for IVF centroid clustering in ReflectDaemon (V3.1).
 */
class ReflectDaemonClusteringTest {

    private static final int DIMS = 16;
    private static final int VEC_BYTES = DIMS; // INT8 quantization
    private static final int CAPACITY = 200;

    @TempDir
    Path tempDir;

    private Path storePath;
    private CentroidRouter centroidRouter;
    private MockEmbeddingProvider embeddingProvider;
    private CognitiveIngestionTarget mockIngestionTarget;
    private AtomicInteger ingestCount;

    @BeforeEach
    void setUp() {
        storePath = tempDir.resolve("episodic");
        centroidRouter = new CentroidRouter(DIMS);
        embeddingProvider = new MockEmbeddingProvider(DIMS);
        ingestCount = new AtomicInteger(0);
        mockIngestionTarget = createMockIngestionTarget();
    }

    /**
     * Creates a mock CognitiveIngestionTarget that counts ingest calls
     * and provides a no-op quantizer for vector decode.
     */
    private CognitiveIngestionTarget createMockIngestionTarget() {
        CognitiveIngestionTarget target = mock(CognitiveIngestionTarget.class);

        // Mock quantizer — decode returns a zero vector (sufficient for test)
        ScalarQuantizer mockQuantizer = mock(ScalarQuantizer.class);
        when(mockQuantizer.decode(any(byte[].class))).thenReturn(new float[DIMS]);
        when(target.quantizer()).thenReturn(mockQuantizer);

        // Count ingestCognitiveWithHeader calls
        doAnswer(invocation -> {
            ingestCount.incrementAndGet();
            return null;
        }).when(target).ingestCognitiveWithHeader(
                anyString(), any(), any(float[].class),
                any(MemoryType.class), any(String[].class),
                any(), any(CognitiveHeader.class));

        return target;
    }

    //  V3.1: Centroid-Based Clustering 

    @Test
    void clustersBycentroidIdAndPromotes() {
        try (EpisodicPartitionedMemory episodicStore = new EpisodicPartitionedMemory(storePath, VEC_BYTES, CAPACITY)) {

            // Create 20 memories across 3 centroids (ids: 1, 2, 3)
            // Centroid 1: 10 records, Centroid 2: 5 records, Centroid 3: 3 records, Centroid 0: 2 records
            int[] centroidAssignments = {
                    1, 1, 1, 2, 2,
                    1, 1, 3, 3, 1,
                    2, 2, 1, 1, 2,
                    3, 0, 0, 1, 1
            };

            for (int i = 0; i < 20; i++) {
                CognitiveHeader header = new CognitiveHeader(
                        System.currentTimeMillis(),
                        (long) (i + 1) * 7, // synaptic tags
                        1.0f,                // exactNorm
                        2.0f,                // importance (> 1.0 so V1 fallback would also promote)
                        0,                   // agentRecallCount
                        (short) centroidAssignments[i],  // centroid ID
                        (byte) 0,
                        SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal())
                );
                episodicStore.append(header, makeVec(i));
            }

            assertThat(episodicStore.totalRecords()).isEqualTo(20);

            // Run reflection with centroid router (V3.1 path)
            ReflectDaemon daemon = new ReflectDaemon(
                    CircadianPolicy.DEFAULT, centroidRouter, null, embeddingProvider);

            ReflectReport report = daemon.runCycle(episodicStore, mockIngestionTarget);

            // Should promote 2 clusters (centroid 1 and 2, both  >=  5 records)
            // Centroid 3 has only 3 records  --  below threshold
            // Centroid 0 has only 2 records  --  below threshold
            assertThat(report.consolidatedCount()).isEqualTo(2);
            assertThat(ingestCount.get()).isEqualTo(2);
        }
    }

    @Test
    void withLlmProviderSynthesizes() {
        try (EpisodicPartitionedMemory episodicStore = new EpisodicPartitionedMemory(storePath, VEC_BYTES, CAPACITY)) {

            // Create 6 memories in the same centroid
            for (int i = 0; i < 6; i++) {
                CognitiveHeader header = new CognitiveHeader(
                        System.currentTimeMillis(),
                        0xFFL, 1.0f, 1.5f,
                        0, // agentRecallCount
                        (short) 5, // centroid 5
                        (byte) 0,
                        SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal())
                );
                episodicStore.append(header, makeVec(i));
            }

            // Mock LlmProvider
            MockLlmProvider mockLlm = new MockLlmProvider();

            // Text lookup function
            Function<Long, String> textLookup = offset -> "Memory text for offset " + offset;

            ReflectDaemon daemon = new ReflectDaemon(
                    CircadianPolicy.DEFAULT, centroidRouter, mockLlm, embeddingProvider);

            ReflectReport report = daemon.runCycle(episodicStore, mockIngestionTarget, textLookup);

            // Should promote 1 cluster via LLM synthesis
            assertThat(report.consolidatedCount()).isEqualTo(1);
            assertThat(ingestCount.get()).isEqualTo(1);

            // LLM should have been called
            assertThat(mockLlm.callCount).isGreaterThan(0);
        }
    }

    @Test
    void withoutCentroidRouterUsesV1Fallback() {
        try (EpisodicPartitionedMemory episodicStore = new EpisodicPartitionedMemory(storePath, VEC_BYTES, CAPACITY)) {

            // Create 5 memories with importance  >=  1.0
            for (int i = 0; i < 5; i++) {
                CognitiveHeader header = new CognitiveHeader(
                        System.currentTimeMillis(),
                        0L, 1.0f, 2.0f, // importance = 2.0 (above threshold)
                        0, (short) 0, (byte) 0,
                        SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal())
                );
                episodicStore.append(header, makeVec(i));
            }

            // V1 mode  --  no centroid router
            ReflectDaemon daemon = new ReflectDaemon(CircadianPolicy.DEFAULT);

            ReflectReport report = daemon.runCycle(episodicStore, mockIngestionTarget);

            // V1 should promote 1 (the highest-importance record)
            assertThat(report.consolidatedCount()).isEqualTo(1);
            assertThat(ingestCount.get()).isEqualTo(1);
        }
    }

    @Test
    void marksClusterMembersAsConsolidated() {
        try (EpisodicPartitionedMemory episodicStore = new EpisodicPartitionedMemory(storePath, VEC_BYTES, CAPACITY)) {

            // Create 6 memories in centroid 1
            for (int i = 0; i < 6; i++) {
                CognitiveHeader header = new CognitiveHeader(
                        System.currentTimeMillis(),
                        0L, 1.0f, 1.0f,
                        0, (short) 1, (byte) 0,
                        SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal())
                );
                episodicStore.append(header, makeVec(i));
            }

            ReflectDaemon daemon = new ReflectDaemon(
                    CircadianPolicy.DEFAULT, centroidRouter, null, embeddingProvider);

            daemon.runCycle(episodicStore, mockIngestionTarget);

            // All 6 should be marked as consolidated
            EpisodicPartition partition = episodicStore.partitions().getFirst();
            var layout = partition.layout();
            var segment = partition.segment();

            for (int i = 0; i < 6; i++) {
                long offset = partition.recordOffset(i);
                byte flags = layout.readFlags(segment, offset);
                assertThat(SynapticHeaderConstants.isConsolidated(flags))
                        .as("Record %d should be consolidated", i)
                        .isTrue();
            }
        }
    }

    @Test
    void secondReflectDoesNotReprocessConsolidated() {
        try (EpisodicPartitionedMemory episodicStore = new EpisodicPartitionedMemory(storePath, VEC_BYTES, CAPACITY)) {

            // 6 memories in centroid 1
            for (int i = 0; i < 6; i++) {
                CognitiveHeader header = new CognitiveHeader(
                        System.currentTimeMillis(),
                        0L, 1.0f, 1.0f,
                        0, (short) 1, (byte) 0,
                        SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal())
                );
                episodicStore.append(header, makeVec(i));
            }

            ReflectDaemon daemon = new ReflectDaemon(
                    CircadianPolicy.DEFAULT, centroidRouter, null, embeddingProvider);

            ReflectReport report1 = daemon.runCycle(episodicStore, mockIngestionTarget);
            assertThat(report1.consolidatedCount()).isEqualTo(1);

            // Second reflect  --  records are already consolidated, nothing new
            ReflectReport report2 = daemon.runCycle(episodicStore, mockIngestionTarget);
            assertThat(report2.consolidatedCount()).isEqualTo(0);
        }
    }

    //  Mock Providers 

    static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(int dims) { this.dims = dims; }

        @Override
        public EmbeddingResult embed(String text) {
            Random rng = new Random(text.hashCode());
            float[] vec = new float[dims];
            float norm = 0f;
            for (int i = 0; i < dims; i++) {
                vec[i] = (rng.nextFloat() - 0.5f) * 2.0f;
                norm += vec[i] * vec[i];
            }
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) vec[i] /= norm;
            }
            return new EmbeddingResult(vec, text.split("\\s+").length, "mock-" + dims + "d");
        }

        @Override public int dimensions() { return dims; }
        @Override public String modelName() { return "mock-" + dims + "d"; }
    }

    static class MockLlmProvider implements LlmProvider {
        int callCount = 0;

        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            callCount++;
            String result = "Synthesized fact from " + callCount + " call(s).";
            return new LlmResponse(result, 0, 0, "mock-llm");
        }

        @Override public String modelName() { return "mock-llm"; }
        @Override public boolean isAvailable() { return true; }
    }

    //  Helpers 

    private byte[] makeVec(int seed) {
        byte[] vec = new byte[VEC_BYTES];
        for (int i = 0; i < VEC_BYTES; i++) {
            vec[i] = (byte) ((seed + i) % 127);
        }
        return vec;
    }
}
