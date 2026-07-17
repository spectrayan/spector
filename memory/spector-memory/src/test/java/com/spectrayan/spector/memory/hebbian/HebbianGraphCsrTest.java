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
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.hebbian.HebbianGraph.HebbianEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HebbianGraphCsr} — CSR-based Hebbian graph.
 */
class HebbianGraphCsrTest {

    @Test
    @DisplayName("basic strengthen + neighbors round trip")
    void strengthenAndNeighbors() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 1.0f);
            g.strengthen(0, 2, 2.0f);

            List<HebbianEdge> n = g.neighbors(0);
            assertThat(n).hasSize(2);
            // Sorted by descending weight
            assertThat(n.getFirst().weight()).isEqualTo(2.0f);
            assertThat(n.getFirst().neighborIndex()).isEqualTo(2);
            assertThat(n.getLast().weight()).isEqualTo(1.0f);
            assertThat(n.getLast().neighborIndex()).isEqualTo(1);

            // Bidirectional
            assertThat(g.neighbors(1)).hasSize(1);
            assertThat(g.neighbors(2)).hasSize(1);
        }
    }

    @Test
    @DisplayName("degree counts CSR + overflow edges")
    void degreeIncludesOverflow() {
        try (var g = new HebbianGraphCsr(100)) {
            assertThat(g.degree(0)).isZero();

            g.strengthen(0, 1, 1.0f);
            assertThat(g.degree(0)).isEqualTo(1);

            g.strengthen(0, 2, 1.0f);
            assertThat(g.degree(0)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("totalEdges counts all edges")
    void totalEdgesAccurate() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 1.0f);   // 2 directed edges
            g.strengthen(2, 3, 1.0f);   // 2 more
            assertThat(g.totalEdges()).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("strengthen existing edge increases weight")
    void strengthenExistingEdge() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 1.0f);
            g.strengthen(0, 1, 0.5f);

            List<HebbianEdge> n = g.neighbors(0);
            assertThat(n).hasSize(1);
            assertThat(n.getFirst().weight()).isEqualTo(1.5f);
        }
    }

    @Test
    @DisplayName("decay removes weak edges and compacts CSR")
    void decayCompactsCsr() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 1.0f);    // weak
            g.strengthen(0, 2, 10.0f);   // strong

            // Decay with aggressive factor
            int removed = g.decayEdges(0.05f);

            // Weak edge (0.05) should be removed, strong (0.5) survives
            assertThat(removed).isGreaterThan(0);
            assertThat(g.neighbors(0).size()).isLessThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("decay collects health metrics")
    void decayCollectsMetrics() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 5.0f);
            g.strengthen(0, 2, 5.0f);

            GraphHealthMetrics metrics = new GraphHealthMetrics();
            g.decayEdges(0.9f, metrics);

            assertThat(metrics.hebbianEdgesSurviving()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("spreading activation traverses graph")
    void spreadingActivation() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 1.0f);
            g.strengthen(1, 2, 1.0f);
            g.strengthen(2, 3, 1.0f);

            // Depth 1: only direct neighbors
            List<HebbianEdge> d1 = g.activateNeighbors(0, 1);
            assertThat(d1).hasSize(1);
            assertThat(d1.getFirst().neighborIndex()).isEqualTo(1);

            // Depth 2: neighbors of neighbors
            List<HebbianEdge> d2 = g.activateNeighbors(0, 2);
            assertThat(d2).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("persistence round-trip saves and loads CSR")
    void persistenceRoundTrip(@TempDir Path tmpDir) {
        Path filePath = tmpDir.resolve("hebbian.csr");

        // Save
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 3.0f);
            g.strengthen(0, 2, 5.0f);
            g.strengthen(2, 3, 7.0f);
            g.save(filePath);
        }

        // Load
        try (var g = HebbianGraphCsr.load(filePath, 100)) {
            assertThat(g.totalEdges()).isEqualTo(6); // 3 bidirectional = 6 directed
            assertThat(g.neighbors(0)).hasSize(2);

            HebbianEdge strongest = g.neighbors(0).getFirst();
            assertThat(strongest.neighborIndex()).isEqualTo(2);
            assertThat(strongest.weight()).isEqualTo(5.0f);
        }
    }

    @Test
    @DisplayName("V2 migration creates CSR from legacy file")
    void v2Migration(@TempDir Path tmpDir) {
        Path filePath = tmpDir.resolve("hebbian.hgph");

        // Create a legacy V2 graph
        HebbianGraph legacy = new HebbianGraph(100);
        legacy.strengthen(0, 1, 2.0f);
        legacy.strengthen(1, 2, 4.0f);
        legacy.strengthen(2, 3, 6.0f);
        legacy.save(filePath);
        legacy.close();

        // Load via CSR — should auto-migrate
        try (var csr = HebbianGraphCsr.load(filePath, 100)) {
            assertThat(csr.totalEdges()).isEqualTo(6); // 3 bidirectional = 6
            assertThat(csr.neighbors(0)).hasSize(1);
            assertThat(csr.neighbors(0).getFirst().neighborIndex()).isEqualTo(1);
            assertThat(csr.neighbors(1)).hasSize(2); // connected to 0 and 2

            // Verify V2 backup exists
            Path backup = tmpDir.resolve("hebbian.hgph.v2.bak");
            assertThat(backup).exists();
        }
    }

    @Test
    @DisplayName("memory footprint is significantly smaller than fixed-width")
    void memoryFootprintReduction() {
        int cap = 10_000;
        int avgDegree = 2;

        try (var g = new HebbianGraphCsr(cap, cap * avgDegree, HebbianGraph.DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT)) {
            // Add sparse edges (avg degree 2)
            for (int i = 0; i < cap - 1; i++) {
                g.strengthen(i, i + 1, 1.0f);
            }

            long csrBytes = g.memoryUsageBytes();
            long fixedWidthBytes = (long) cap * (4 + HebbianGraph.DEFAULT_MAX_DEGREE * 12);

            float reductionPct = (1.0f - (float) csrBytes / fixedWidthBytes) * 100f;

            // Should be at least 80% smaller
            assertThat(reductionPct)
                    .as("CSR should use at least 80%% less memory than fixed-width")
                    .isGreaterThan(80.0f);
        }
    }

    @Test
    @DisplayName("reset clears all edges")
    void resetClearsGraph() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(0, 1, 1.0f);
            g.strengthen(0, 2, 1.0f);

            int cleared = g.reset();
            assertThat(cleared).isEqualTo(4); // 2 bidirectional = 4
            assertThat(g.totalEdges()).isZero();
            assertThat(g.neighbors(0)).isEmpty();
        }
    }

    @Test
    @DisplayName("self-loops are rejected")
    void selfLoopsRejected() {
        try (var g = new HebbianGraphCsr(100)) {
            g.strengthen(5, 5, 1.0f);
            assertThat(g.degree(5)).isZero();
        }
    }

    @Test
    @DisplayName("out-of-bounds nodes are handled gracefully")
    void outOfBoundsNodes() {
        try (var g = new HebbianGraphCsr(10)) {
            g.strengthen(-1, 5, 1.0f); // negative
            g.strengthen(5, 100, 1.0f); // above capacity
            assertThat(g.totalEdges()).isZero();

            assertThat(g.neighbors(-1)).isEmpty();
            assertThat(g.neighbors(100)).isEmpty();
            assertThat(g.degree(-1)).isZero();
        }
    }
}
