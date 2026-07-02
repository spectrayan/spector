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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for STC Cross-Capture: {@link EntityGraph#boostEdgeWeight} and
 * {@link GraphHealthMetrics#recordCrossCapture}.
 *
 * <p>Verifies that the Synaptic Tagging and Capture mechanism correctly
 * propagates Hebbian co-activation strength to existing entity edges
 * without creating new relations.</p>
 */
class CrossCaptureTest {

    private EntityGraph graph;

    @BeforeEach
    void setUp() {
        graph = new EntityGraph(100, 500);
    }

    @AfterEach
    void tearDown() {
        graph.close();
    }

    @Test
    void boostEdgeWeight_boostsExistingEntityEdge() {
        // Setup: two entities with a relation
        int alice = graph.addEntity("Alice", "PERSON");
        int bob = graph.addEntity("Bob", "PERSON");
        graph.addRelation(alice, bob, "WORKS_WITH");

        // Read initial weight (should be 1.0)
        var edges = graph.edges(alice);
        assertThat(edges).hasSize(1);
        float initialWeight = edges.getFirst().weight();
        assertThat(initialWeight).isEqualTo(1.0f);

        // Cross-capture boost
        boolean boosted = graph.boostEdgeWeight(alice, bob, 0.2f);
        assertThat(boosted).isTrue();

        // Verify weight increased
        edges = graph.edges(alice);
        assertThat(edges.getFirst().weight()).isEqualTo(1.2f);
    }

    @Test
    void boostEdgeWeight_doesNotCreateNewEdges() {
        // Setup: two entities WITHOUT a relation
        int alice = graph.addEntity("Alice", "PERSON");
        int bob = graph.addEntity("Bob", "PERSON");

        // Attempt cross-capture boost — should return false
        boolean boosted = graph.boostEdgeWeight(alice, bob, 0.5f);
        assertThat(boosted).isFalse();

        // Verify no edges were created
        var edges = graph.edges(alice);
        assertThat(edges).isEmpty();
    }

    @Test
    void boostEdgeWeight_cappedAtMaxWeight() {
        // Setup: two entities with a relation
        int alice = graph.addEntity("Alice", "PERSON");
        int bob = graph.addEntity("Bob", "PERSON");
        graph.addRelation(alice, bob, "WORKS_WITH");

        // Boost repeatedly — should cap at MAX_EDGE_WEIGHT
        for (int i = 0; i < 100; i++) {
            graph.boostEdgeWeight(alice, bob, 1.0f);
        }

        var edges = graph.edges(alice);
        assertThat(edges.getFirst().weight()).isEqualTo(EntityGraph.MAX_EDGE_WEIGHT);
    }

    @Test
    void boostEdgeWeight_rejectsInvalidInputs() {
        int alice = graph.addEntity("Alice", "PERSON");
        int bob = graph.addEntity("Bob", "PERSON");
        graph.addRelation(alice, bob, "WORKS_WITH");

        // Negative boost
        assertThat(graph.boostEdgeWeight(alice, bob, -0.5f)).isFalse();

        // Zero boost
        assertThat(graph.boostEdgeWeight(alice, bob, 0.0f)).isFalse();

        // Self-loop
        assertThat(graph.boostEdgeWeight(alice, alice, 0.5f)).isFalse();

        // Out-of-range entity IDs
        assertThat(graph.boostEdgeWeight(-1, bob, 0.5f)).isFalse();
        assertThat(graph.boostEdgeWeight(alice, 9999, 0.5f)).isFalse();
    }

    @Test
    void crossCaptureMetrics_recordedCorrectly() {
        var metrics = new GraphHealthMetrics();

        assertThat(metrics.crossCapturedEdges()).isEqualTo(0);

        metrics.recordCrossCapture();
        metrics.recordCrossCapture();
        metrics.recordCrossCapture();

        assertThat(metrics.crossCapturedEdges()).isEqualTo(3);
    }

    @Test
    void boostEdgeWeight_updatesRecency() {
        // Setup: two entities with a relation
        int alice = graph.addEntity("Alice", "PERSON");
        int bob = graph.addEntity("Bob", "PERSON");
        graph.addRelation(alice, bob, "WORKS_WITH");

        // Run a decay cycle to advance currentCycle
        var metrics = new GraphHealthMetrics();
        graph.decayEdges(0.95f, 0.5f, metrics);

        // Now boost — the lastCycle should be updated to currentCycle
        boolean boosted = graph.boostEdgeWeight(alice, bob, 0.1f);
        assertThat(boosted).isTrue();

        // Edge should still exist after another decay (recency refreshed)
        graph.decayEdges(0.95f, 0.5f, null);
        var edges = graph.edges(alice);
        assertThat(edges).isNotEmpty();
    }
}
