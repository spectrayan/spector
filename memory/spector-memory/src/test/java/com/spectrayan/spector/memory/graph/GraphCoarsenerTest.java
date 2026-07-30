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
package com.spectrayan.spector.memory.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GraphCoarsenerTest {

    @Test
    @DisplayName("Kron reduction preserves clusters, CSR dimensions, and effective resistance error bounds")
    void coarsen_validGraph_preservesEffectiveResistanceAndClusters() {
        int nodeCount = 10;
        int[] src = new int[]{0, 0, 1, 1, 2, 3, 4, 5, 6, 7};
        int[] dst = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 8};
        float[] weights = new float[]{1.0f, 2.0f, 1.5f, 0.8f, 2.5f, 1.2f, 0.5f, 1.8f, 0.9f, 1.1f};
        float[] nodeWeights = new float[]{10.0f, 8.0f, 6.0f, 5.0f, 2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};

        CoarsenedGraph coarsened = GraphCoarsener.coarsen(nodeCount, src, dst, weights, nodeWeights, 0.4f);

        assertThat(coarsened).isNotNull();
        assertThat(coarsened.originalNodeCount()).isEqualTo(10);
        assertThat(coarsened.reducedNodeCount()).isEqualTo(4);
        assertThat(coarsened.clusters()).hasSize(4);
        assertThat(coarsened.maxEffectiveResistanceError()).isLessThanOrEqualTo(0.05);

        // Verify CSR arrays
        assertThat(coarsened.rowPointers()).hasSize(5); // C + 1
        assertThat(coarsened.columnIndices().length).isEqualTo(coarsened.values().length);

        // Verify all 10 nodes are accounted for across clusters
        int totalMembers = coarsened.clusters().values().stream()
            .mapToInt(c -> c.memberEntityIds().length)
            .sum();
        assertThat(totalMembers).isEqualTo(10);
    }

    @Test
    @DisplayName("Handles empty and single node graphs gracefully")
    void coarsen_singleNodeAndEmptyGraph_handlesGracefully() {
        CoarsenedGraph empty = GraphCoarsener.coarsen(0, new int[0], new int[0], new float[0], null, 0.5f);
        assertThat(empty.originalNodeCount()).isEqualTo(0);
        assertThat(empty.reducedNodeCount()).isEqualTo(0);
        assertThat(empty.clusters()).isEmpty();

        CoarsenedGraph single = GraphCoarsener.coarsen(1, new int[0], new int[0], new float[0], null, 0.5f);
        assertThat(single.originalNodeCount()).isEqualTo(1);
        assertThat(single.reducedNodeCount()).isEqualTo(1);
        assertThat(single.clusters()).hasSize(1);
    }

    @Test
    @DisplayName("Scale benchmark: 10,000-node graph coarsening completes in < 100ms")
    void coarsen_scale10KNodes_completesUnder100Ms() {
        int nodeCount = 10_000;
        int edgeCount = 30_000;
        int[] src = new int[edgeCount];
        int[] dst = new int[edgeCount];
        float[] weights = new float[edgeCount];
        float[] nodeWeights = new float[nodeCount];

        Random rng = new Random(42);
        for (int i = 0; i < nodeCount; i++) {
            nodeWeights[i] = 1.0f + rng.nextFloat() * 10.0f;
        }
        for (int i = 0; i < edgeCount; i++) {
            src[i] = rng.nextInt(nodeCount);
            dst[i] = rng.nextInt(nodeCount);
            weights[i] = 0.1f + rng.nextFloat() * 5.0f;
        }

        long start = System.nanoTime();
        CoarsenedGraph coarsened = GraphCoarsener.coarsen(nodeCount, src, dst, weights, nodeWeights, 0.2f);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(coarsened.reducedNodeCount()).isEqualTo(2000);
        assertThat(elapsedMs).isLessThan(100L);
    }

    @Test
    @DisplayName("EntityGraphMemory coarsen integration test")
    void entityGraphMemory_coarsenIntegration_succeeds() {
        EntityGraphMemory graph = new EntityGraphMemory(100, 500, 32, EdgeImportance.DEFAULT);
        int e0 = graph.addEntity("Alpha", "CONCEPT");
        int e1 = graph.addEntity("Beta", "CONCEPT");
        int e2 = graph.addEntity("Gamma", "CONCEPT");

        graph.addRelation(e0, e1, "ASSOCIATED_WITH");
        graph.addRelation(e1, e2, "DEPENDS_ON");

        CoarsenedGraph coarsened = graph.coarsen(0.66f);

        assertThat(coarsened).isNotNull();
        assertThat(coarsened.originalNodeCount()).isEqualTo(3);
        assertThat(coarsened.reducedNodeCount()).isEqualTo(2);
    }
}

