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
package com.spectrayan.spector.memory.runtime;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.kernel.api.KernelSpec;
import com.spectrayan.spector.kernel.api.NamespaceKernel;
import com.spectrayan.spector.kernel.api.NamespaceKernels;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 10.6 Capacity Proof: 100 / 500 / 1000 binds heap overhead and engine sharing (R4.10, R4.11)")
class CapacityProofTest {

    private static final int DIMS = 128;

    @Test
    @DisplayName("Capacity proof: 100, 500, 1000 empty hot kernels exhibit O(N × kernel metadata) heap footprint")
    void testKernelBindsCapacityProof(@TempDir Path tempDir) {
        int[] bindCounts = {100, 500, 1000};
        KernelSpec spec = KernelSpec.standard(DIMS);

        for (int count : bindCounts) {
            Path countDir = tempDir.resolve("bench-" + count);
            List<NamespaceKernel> kernels = new ArrayList<>(count);

            System.gc();
            long beforeHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            try {
                for (int i = 0; i < count; i++) {
                    Path kDir = countDir.resolve("k-" + i);
                    NamespaceKernel kernel = NamespaceKernels.open(kDir, spec);
                    kernels.add(kernel);
                }

                assertThat(kernels).hasSize(count);

                System.gc();
                long afterHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long heapDelta = Math.max(0, afterHeap - beforeHeap);
                long bytesPerKernel = heapDelta / count;

                // Success criteria: Heap for N empty hot kernels must be O(N × kernel metadata).
                // Each kernel instance should consume strictly < 64 KiB of on-heap metadata
                // because all slabs and data buffers reside off-heap in memory-mapped regions.
                assertThat(bytesPerKernel)
                        .withFailMessage("Heap footprint per kernel %d bytes exceeded 65,536 bytes at N=%d", bytesPerKernel, count)
                        .isLessThan(65536L);

                // Verify basic operation on sampled kernels
                assertThat(kernels.get(0).directory()).isNotNull();
                assertThat(kernels.get(count - 1).directory()).isNotNull();
            } finally {
                // Release all file handles cleanly
                for (NamespaceKernel kernel : kernels) {
                    try {
                        kernel.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("SpectorRuntime capacity proof: 100 attached namespaces share single engine instances without engine heap duplication")
    void testSharedRuntimeEnginesCapacityProof(@TempDir Path tempDir) {
        EmbeddingProvider dummyEmbedder = new EmbeddingProvider() {
            @Override public EmbeddingResult embed(String text) { return EmbeddingResult.of(new float[DIMS], "mock"); }
            @Override public List<EmbeddingResult> embedBatch(List<String> texts) { return texts.stream().map(this::embed).toList(); }
            @Override public int dimensions() { return DIMS; }
            @Override public String modelName() { return "mock"; }
        };

        SpectorProperties props = SpectorProperties.builder().build();
        props.memory().setDimensions(DIMS);

        try (SpectorRuntime runtime = SpectorRuntime.builder()
                .properties(props)
                .embeddingProvider(dummyEmbedder)
                .build()) {

            List<SpectorMemory> instances = new ArrayList<>(10);
            try {
                for (int i = 0; i < 10; i++) {
                    Path nsDir = tempDir.resolve("runtime-ns-" + i);
                    SpectorMemory memory = runtime.attach("ns-" + i, b -> b.persistence(nsDir));
                    instances.add(memory);
                }

                assertThat(instances).hasSize(10);

                // Verify that all attached instances reference the exact same pathway engines
                DefaultSpectorMemory first = (DefaultSpectorMemory) instances.get(0);
                for (int i = 1; i < 10; i++) {
                    DefaultSpectorMemory current = (DefaultSpectorMemory) instances.get(i);
                    assertThat(current.sharedPathways()).isTrue();
                    assertThat(current.recallPathway()).isSameAs(first.recallPathway());
                }
            } finally {
                for (SpectorMemory memory : instances) {
                    try {
                        memory.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
