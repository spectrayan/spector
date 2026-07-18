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

import com.spectrayan.spector.memory.graph.HyperEntityGraph.HyperEdge;
import com.spectrayan.spector.memory.graph.HyperEntityGraph.HyperEdgeVertex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HyperEntityGraph} — hyperedge-based entity graph.
 */
class HyperEntityGraphTest {

    private static final int ENTITY_CAP = 100;
    private static final int HEDGE_CAP = 50;

    @Test
    @DisplayName("add and retrieve a 3-vertex hyperedge")
    void addAndRetrieveHyperedge() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            int edgeId = g.addHyperedge(
                    new int[]{0, 1, 2},
                    new int[]{HyperEntityGraph.ROLE_SUBJECT, HyperEntityGraph.ROLE_OBJECT, HyperEntityGraph.ROLE_CONTEXT},
                    42, 5.0f, 100, System.currentTimeMillis());

            assertThat(edgeId).isEqualTo(0);
            assertThat(g.totalHyperedges()).isEqualTo(1);

            HyperEdge edge = g.getHyperedge(edgeId);
            assertThat(edge).isNotNull();
            assertThat(edge.type()).isEqualTo(42);
            assertThat(edge.weight()).isEqualTo(5.0f);
            assertThat(edge.memoryIdx()).isEqualTo(100);
            assertThat(edge.vertices()).hasSize(3);

            assertThat(edge.vertices().get(0).entityId()).isEqualTo(0);
            assertThat(edge.vertices().get(0).roleId()).isEqualTo(HyperEntityGraph.ROLE_SUBJECT);
            assertThat(edge.vertices().get(1).entityId()).isEqualTo(1);
            assertThat(edge.vertices().get(1).roleId()).isEqualTo(HyperEntityGraph.ROLE_OBJECT);
            assertThat(edge.vertices().get(2).entityId()).isEqualTo(2);
            assertThat(edge.vertices().get(2).roleId()).isEqualTo(HyperEntityGraph.ROLE_CONTEXT);
        }
    }

    @Test
    @DisplayName("findHyperedgesForEntity returns sorted by weight")
    void findHyperedgesForEntity() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            g.addHyperedge(new int[]{0, 1}, new int[]{1, 2}, 1, 2.0f, 1, 0);
            g.addHyperedge(new int[]{0, 2}, new int[]{1, 2}, 1, 5.0f, 2, 0);
            g.addHyperedge(new int[]{0, 3}, new int[]{1, 2}, 1, 3.0f, 3, 0);

            List<HyperEdge> edges = g.findHyperedgesForEntity(0);
            assertThat(edges).hasSize(3);
            // Sorted by descending weight
            assertThat(edges.get(0).weight()).isEqualTo(5.0f);
            assertThat(edges.get(1).weight()).isEqualTo(3.0f);
            assertThat(edges.get(2).weight()).isEqualTo(2.0f);
        }
    }

    @Test
    @DisplayName("findCoOccurringEntities returns all related entities")
    void findCoOccurringEntities() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            // Entity 0 appears with 1, 2 in one hyperedge, and with 3 in another
            g.addHyperedge(new int[]{0, 1, 2}, new int[]{1, 2, 3}, 1, 1.0f, 1, 0);
            g.addHyperedge(new int[]{0, 3}, new int[]{1, 2}, 1, 1.0f, 2, 0);

            Set<Integer> coOccurring = g.findCoOccurringEntities(0);
            assertThat(coOccurring).containsExactlyInAnyOrder(1, 2, 3);
        }
    }

    @Test
    @DisplayName("strengthen increases hyperedge weight")
    void strengthenHyperedge() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            int edgeId = g.addHyperedge(new int[]{0, 1}, new int[]{1, 2}, 1, 1.0f, 1, 0);
            g.strengthen(edgeId, 2.5f);

            HyperEdge edge = g.getHyperedge(edgeId);
            assertThat(edge.weight()).isEqualTo(3.5f);
        }
    }

    @Test
    @DisplayName("decay removes weak hyperedges")
    void decayRemovesWeakEdges() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            g.addHyperedge(new int[]{0, 1}, new int[]{1, 2}, 1, 1.0f, 1, 0);   // weak
            g.addHyperedge(new int[]{0, 2}, new int[]{1, 2}, 1, 10.0f, 2, 0);  // strong

            int evicted = g.decayHyperedges(0.05f, 0.1f);

            assertThat(evicted).isEqualTo(1);
            assertThat(g.totalHyperedges()).isEqualTo(1);

            // Weak edge should be gone
            assertThat(g.getHyperedge(0)).isNull();
            // Strong edge should survive (10 * 0.05 = 0.5 > 0.1)
            assertThat(g.getHyperedge(1)).isNotNull();
        }
    }

    @Test
    @DisplayName("binary edge is stored as 2-vertex hyperedge")
    void binaryEdgeAsTwoVertexHyperedge() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            int edgeId = g.addHyperedge(
                    new int[]{5, 10},
                    new int[]{HyperEntityGraph.ROLE_SUBJECT, HyperEntityGraph.ROLE_OBJECT},
                    99, 7.0f, 42, 0);

            HyperEdge edge = g.getHyperedge(edgeId);
            assertThat(edge.vertices()).hasSize(2);
            assertThat(edge.vertices().get(0).entityId()).isEqualTo(5);
            assertThat(edge.vertices().get(1).entityId()).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("rejects hyperedge with < 2 or > 8 vertices")
    void rejectsInvalidVertexCount() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            // Too few
            int id1 = g.addHyperedge(new int[]{0}, new int[]{1}, 1, 1.0f, 1, 0);
            assertThat(id1).isEqualTo(-1);

            // Too many (9 vertices)
            int id2 = g.addHyperedge(
                    new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8},
                    new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9},
                    1, 1.0f, 1, 0);
            assertThat(id2).isEqualTo(-1);

            assertThat(g.totalHyperedges()).isZero();
        }
    }

    @Test
    @DisplayName("persistence round-trip saves and loads")
    void persistenceRoundTrip(@TempDir Path tmpDir) {
        Path filePath = tmpDir.resolve("hyperentity.hyeg");

        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            g.addHyperedge(new int[]{0, 1, 2}, new int[]{1, 2, 3}, 10, 5.0f, 100, 12345L);
            g.addHyperedge(new int[]{3, 4}, new int[]{1, 2}, 20, 3.0f, 200, 67890L);
            g.save(filePath);
        }

        try (var g = HyperEntityGraph.load(filePath, ENTITY_CAP, HEDGE_CAP)) {
            assertThat(g.totalHyperedges()).isEqualTo(2);

            HyperEdge e0 = g.getHyperedge(0);
            assertThat(e0).isNotNull();
            assertThat(e0.type()).isEqualTo(10);
            assertThat(e0.weight()).isEqualTo(5.0f);
            assertThat(e0.vertices()).hasSize(3);
            assertThat(e0.timestamp()).isEqualTo(12345L);

            HyperEdge e1 = g.getHyperedge(1);
            assertThat(e1).isNotNull();
            assertThat(e1.type()).isEqualTo(20);
            assertThat(e1.weight()).isEqualTo(3.0f);
            assertThat(e1.vertices()).hasSize(2);

            // Incidence should be rebuilt
            assertThat(g.findHyperedgesForEntity(0)).hasSize(1);
            assertThat(g.findHyperedgesForEntity(3)).hasSize(1);
        }
    }

    @Test
    @DisplayName("incidence rebuilt correctly after load")
    void incidenceRebuiltAfterLoad(@TempDir Path tmpDir) {
        Path filePath = tmpDir.resolve("hyperentity2.hyeg");

        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            g.addHyperedge(new int[]{0, 1}, new int[]{1, 2}, 1, 1.0f, 1, 0);
            g.addHyperedge(new int[]{0, 2}, new int[]{1, 2}, 1, 2.0f, 2, 0);
            g.addHyperedge(new int[]{0, 1, 3}, new int[]{1, 2, 3}, 1, 3.0f, 3, 0);
            g.save(filePath);
        }

        try (var g = HyperEntityGraph.load(filePath, ENTITY_CAP, HEDGE_CAP)) {
            // Entity 0 participates in all 3 hyperedges
            assertThat(g.findHyperedgesForEntity(0)).hasSize(3);
            // Entity 1 participates in 2 hyperedges
            assertThat(g.findHyperedgesForEntity(1)).hasSize(2);
            // Entity 2 participates in 1 hyperedge
            assertThat(g.findHyperedgesForEntity(2)).hasSize(1);
            // Entity 3 participates in 1 hyperedge
            assertThat(g.findHyperedgesForEntity(3)).hasSize(1);

            // Co-occurring entities for entity 0
            Set<Integer> coOccurring = g.findCoOccurringEntities(0);
            assertThat(coOccurring).containsExactlyInAnyOrder(1, 2, 3);
        }
    }

    @Test
    @DisplayName("entity graph compression ratio — hyperedge vs binary")
    void compressionRatio() {
        // Scenario: 10 3-entity relationships
        int relationships = 10;

        // Binary: 10 relationships × 3 edges = 30 edges
        int binaryEdges = relationships * 3;

        // Hyper: 10 relationships × 1 hyperedge = 10 hyperedges
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            for (int i = 0; i < relationships; i++) {
                g.addHyperedge(
                        new int[]{i * 3, i * 3 + 1, i * 3 + 2},
                        new int[]{1, 2, 3},
                        1, 1.0f, i, 0);
            }

            assertThat(g.totalHyperedges()).isEqualTo(relationships);

            float reductionPct = (1.0f - (float) g.totalHyperedges() / binaryEdges) * 100f;
            assertThat(reductionPct)
                    .as("Hypergraph should reduce edge count by at least 50%%")
                    .isGreaterThan(50.0f);
        }
    }

    @Test
    @DisplayName("out-of-range entity IDs handled gracefully")
    void outOfRangeEntityIds() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            assertThat(g.findHyperedgesForEntity(-1)).isEmpty();
            assertThat(g.findHyperedgesForEntity(999)).isEmpty();
            assertThat(g.findCoOccurringEntities(-1)).isEmpty();
        }
    }

    @Test
    @DisplayName("getHyperedge returns null for invalid/deleted edges")
    void getHyperedgeInvalidReturnsNull() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            assertThat(g.getHyperedge(-1)).isNull();
            assertThat(g.getHyperedge(999)).isNull();
        }
    }

    @Test
    @DisplayName("decay and find work correctly together")
    void decayAndFindIntegration() {
        try (var g = new HyperEntityGraph(ENTITY_CAP, HEDGE_CAP)) {
            g.addHyperedge(new int[]{0, 1}, new int[]{1, 2}, 1, 1.0f, 1, 0);   // weak
            g.addHyperedge(new int[]{0, 2, 3}, new int[]{1, 2, 3}, 2, 10.0f, 2, 0); // strong
            g.addHyperedge(new int[]{0, 4}, new int[]{1, 2}, 3, 0.5f, 3, 0);    // very weak

            // Before decay: entity 0 has 3 hyperedges
            assertThat(g.findHyperedgesForEntity(0)).hasSize(3);

            // Decay aggressively
            g.decayHyperedges(0.05f, 0.1f);

            // After decay: only the strong one survives (10 * 0.05 = 0.5 > 0.1)
            assertThat(g.findHyperedgesForEntity(0)).hasSize(1);
            assertThat(g.findCoOccurringEntities(0)).containsExactlyInAnyOrder(2, 3);
        }
    }
}
