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
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.hebbian.HebbianEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HebbianCompactionStressTest — CSR Scratch Segment Compaction Safety")
class HebbianCompactionStressTest {

    @Test
    @DisplayName("Self-loops are rejected cleanly and do not corrupt node degree or CSR structure")
    void testSelfLoopRejection() {
        try (var graph = new HebbianGraphMemory(50)) {
            // Self-loop: vertex 10 <-> 10
            graph.strengthen(10, 10, 1.0f);

            assertThat(graph.degree(10)).isZero();
            assertThat(graph.neighbors(10)).isEmpty();
        }
    }

    @Test
    @DisplayName("CSR compaction via save flushes overflow into CSR slabs without in-place shift corruption")
    void testCompactionSafetyUnderHighMutation(@TempDir Path tempDir) {
        int nodeCount = 100;
        int edgeCapacity = 2000;
        int maxDegree = 20;
        Path savePath = tempDir.resolve("hebb_save.hebb");

        try (var graph = new HebbianGraphMemory(nodeCount, edgeCapacity, maxDegree, EdgeImportance.DEFAULT)) {
            // 1. Insert 160 edges across 80 nodes into overflow
            for (int i = 0; i < 80; i++) {
                graph.strengthen(i, i + 1, 2.0f);
                graph.strengthen(i, (i + 5) % nodeCount, 1.5f);
            }

            // Verify pre-compaction
            assertThat(graph.degree(0)).isGreaterThanOrEqualTo(2);

            // 2. Save to file (triggers compactIfNeeded with scratch segment)
            graph.save(savePath);

            // 3. Verify that all edges survived with exact weights and correct neighbor IDs in CSR
            for (int i = 0; i < 80; i++) {
                final int neighborTarget1 = i + 1;
                final int neighborTarget2 = (i + 5) % nodeCount;

                List<HebbianEdge> neighbors = graph.neighbors(i);
                boolean foundNeighbor1 = neighbors.stream()
                        .anyMatch(e -> e.neighborIndex() == neighborTarget1 && Math.abs(e.weight() - 2.0f) < 0.001f);
                assertThat(foundNeighbor1)
                        .as("Edge %d <-> %d must be preserved in CSR", i, neighborTarget1)
                        .isTrue();

                boolean foundNeighbor2 = neighbors.stream()
                        .anyMatch(e -> e.neighborIndex() == neighborTarget2 && Math.abs(e.weight() - 1.5f) < 0.001f);
                assertThat(foundNeighbor2)
                        .as("Edge %d <-> %d must be preserved in CSR", i, neighborTarget2)
                        .isTrue();
            }
        }

        // Reopen and verify persistent CSR structure
        try (var graph = HebbianGraphMemory.load(savePath, nodeCount)) {
            for (int i = 0; i < 80; i++) {
                final int neighborTarget1 = i + 1;
                final int neighborTarget2 = (i + 5) % nodeCount;

                List<HebbianEdge> neighbors = graph.neighbors(i);
                boolean foundNeighbor1 = neighbors.stream()
                        .anyMatch(e -> e.neighborIndex() == neighborTarget1 && Math.abs(e.weight() - 2.0f) < 0.001f);
                assertThat(foundNeighbor1)
                        .as("Reloaded edge %d <-> %d must match", i, neighborTarget1)
                        .isTrue();

                boolean foundNeighbor2 = neighbors.stream()
                        .anyMatch(e -> e.neighborIndex() == neighborTarget2 && Math.abs(e.weight() - 1.5f) < 0.001f);
                assertThat(foundNeighbor2)
                        .as("Reloaded edge %d <-> %d must match", i, neighborTarget2)
                        .isTrue();
            }
        }
    }
}
