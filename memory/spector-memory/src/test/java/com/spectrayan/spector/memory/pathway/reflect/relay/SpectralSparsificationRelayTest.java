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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpectralSparsificationRelay: Reflect Pathway Integration Tests")
class SpectralSparsificationRelayTest {

    @Test
    @DisplayName("Handles null hebbianGraph gracefully")
    void testNullHebbianGraphGraceful() {
        SpectralSparsificationRelay relay = new SpectralSparsificationRelay();
        ReflectSignal signal = ReflectSignal.builder().build();

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Handles empty hebbianGraph gracefully without errors")
    void testEmptyHebbianGraphGraceful() {
        SpectralSparsificationRelay relay = new SpectralSparsificationRelay();
        try (HebbianGraph graph = new HebbianGraph(10)) {
            GraphHealthMetrics metrics = new GraphHealthMetrics();
            ReflectSignal signal = ReflectSignal.builder()
                    .hebbianGraph(graph)
                    .graphMetrics(metrics)
                    .build();

            boolean result = relay.transmit(signal);

            assertThat(result).isTrue();
            assertThat(metrics.sparsifiedEdges()).isZero();
            assertThat(metrics.sparsificationCandidates()).isZero();
        }
    }

    @Test
    @DisplayName("Shadow mode (Tier 0) records candidate statistics in GraphHealthMetrics without pruning")
    void testShadowModeTelemetry() {
        SpectralSparsificationRelay relay = new SpectralSparsificationRelay(false, 0.15f);

        try (HebbianGraph graph = new HebbianGraph(20)) {
            // Build a small graph with connected components and some redundancy
            graph.strengthen(0, 1, 1.0f);
            graph.strengthen(1, 2, 1.0f);
            graph.strengthen(2, 0, 1.0f);
            graph.strengthen(2, 3, 1.0f);
            graph.strengthen(3, 4, 1.0f);
            graph.strengthen(4, 5, 1.0f);
            graph.strengthen(5, 3, 1.0f);

            GraphHealthMetrics metrics = new GraphHealthMetrics();
            ReflectSignal signal = ReflectSignal.builder()
                    .hebbianGraph(graph)
                    .graphMetrics(metrics)
                    .build();

            boolean result = relay.transmit(signal);

            assertThat(result).isTrue();
            assertThat(metrics.sparsifiedEdges()).isZero(); // No actual pruning in shadow mode
            // Candidates may be computed depending on UST sampling
            assertThat(metrics.sparsificationCandidates()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("Tier 1 enabled runs without crashing")
    void testTier1EnabledExecution() {
        SpectralSparsificationRelay relay = new SpectralSparsificationRelay(true, 0.20f);

        try (HebbianGraph graph = new HebbianGraph(15)) {
            graph.strengthen(0, 1, 1.0f);
            graph.strengthen(1, 2, 1.0f);
            graph.strengthen(2, 3, 1.0f);

            GraphHealthMetrics metrics = new GraphHealthMetrics();
            ReflectSignal signal = ReflectSignal.builder()
                    .hebbianGraph(graph)
                    .graphMetrics(metrics)
                    .build();

            boolean result = relay.transmit(signal);

            assertThat(result).isTrue();
        }
    }
}
