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
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for STC Cross-Capture: {@link HyperEntityGraphMemory#boostHyperedgeWeight} and
 * {@link GraphHealthMetrics#recordCrossCapture}.
 *
 * <p>Verifies that the Synaptic Tagging and Capture mechanism correctly
 * propagates Hebbian co-activation strength to existing hyperedges
 * without creating new relations.</p>
 */
class CrossCaptureTest {

    private HyperEntityGraphMemory graph;

    @BeforeEach
    void setUp() {
        graph = new HyperEntityGraphMemory(100, 500);
    }

    @AfterEach
    void tearDown() {
        graph.close();
    }

    @Test
    void boostHyperedgeWeight_boostsExistingEdge() {
        int alice = 1;
        int bob = 2;
        graph.addHyperedge(new int[]{alice, bob}, new int[]{1, 2}, 1, 1.0f, 0, System.currentTimeMillis());

        boolean boosted = graph.boostHyperedgeWeight(alice, bob, 0.2f);
        assertThat(boosted).isTrue();

        var edges = graph.findHyperedgesForEntity(alice);
        assertThat(edges).hasSize(1);
        assertThat(edges.getFirst().weight()).isEqualTo(1.2f);
    }

    @Test
    void boostHyperedgeWeight_doesNotCreateNewEdges() {
        int alice = 1;
        int bob = 2;

        boolean boosted = graph.boostHyperedgeWeight(alice, bob, 0.5f);
        assertThat(boosted).isFalse();

        var edges = graph.findHyperedgesForEntity(alice);
        assertThat(edges).isEmpty();
    }

    @Test
    void boostHyperedgeWeight_rejectsInvalidInputs() {
        int alice = 1;
        int bob = 2;
        graph.addHyperedge(new int[]{alice, bob}, new int[]{1, 2}, 1, 1.0f, 0, System.currentTimeMillis());

        // Negative boost
        assertThat(graph.boostHyperedgeWeight(alice, bob, -0.5f)).isFalse();

        // Zero boost
        assertThat(graph.boostHyperedgeWeight(alice, bob, 0.0f)).isFalse();

        // Self-loop
        assertThat(graph.boostHyperedgeWeight(alice, alice, 0.5f)).isFalse();

        // Out-of-range entity IDs
        assertThat(graph.boostHyperedgeWeight(-1, bob, 0.5f)).isFalse();
        assertThat(graph.boostHyperedgeWeight(alice, 9999, 0.5f)).isFalse();
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
}
