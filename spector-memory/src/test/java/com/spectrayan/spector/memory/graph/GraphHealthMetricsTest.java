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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GraphHealthMetrics} observability collector.
 *
 * <p>Verifies metric accumulation, quartile bucketing, average computation,
 * fragmentation ratio, and toString representation.</p>
 */
class GraphHealthMetricsTest {

    private GraphHealthMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new GraphHealthMetrics();
    }

    @Nested
    @DisplayName("Hebbian Metrics")
    class HebbianMetrics {

        @Test
        @DisplayName("records decay events and survivor counts")
        void recordsDecayAndSurvivors() {
            metrics.recordHebbianDecay();
            metrics.recordHebbianDecay();
            metrics.recordHebbianSurvivor(100, 5);
            metrics.recordHebbianSurvivor(200, 10);
            metrics.recordHebbianSurvivor(50, 3);

            assertEquals(2, metrics.hebbianEdgesDecayed());
            assertEquals(3, metrics.hebbianEdgesSurviving());
        }

        @Test
        @DisplayName("records arousal modulation events")
        void recordsArousalModulation() {
            metrics.recordHebbianArousalModulation();
            metrics.recordHebbianArousalModulation();

            assertEquals(2, metrics.hebbianArousalModulated());
        }

        @Test
        @DisplayName("records bridge protection events")
        void recordsBridgeProtection() {
            metrics.recordHebbianBridgeProtection();

            assertEquals(1, metrics.hebbianBridgeProtected());
        }
    }

    @Nested
    @DisplayName("Entity Metrics")
    class EntityMetrics {

        @Test
        @DisplayName("records entity decay and survivor counts")
        void recordsDecayAndSurvivors() {
            metrics.recordEntityDecay();
            metrics.recordEntitySurvivor(128);
            metrics.recordEntitySurvivor(200);

            assertEquals(1, metrics.entityEdgesDecayed());
            assertEquals(2, metrics.entityEdgesSurviving());
        }
    }

    @Nested
    @DisplayName("Bridge Score Distribution")
    class BridgeDistribution {

        @Test
        @DisplayName("buckets bridge scores into quartiles")
        void bucketsCorrectly() {
            // Q1: 0-63
            metrics.recordHebbianSurvivor(0, 1);
            metrics.recordHebbianSurvivor(63, 1);
            // Q2: 64-127
            metrics.recordHebbianSurvivor(64, 1);
            metrics.recordHebbianSurvivor(127, 1);
            // Q3: 128-191
            metrics.recordHebbianSurvivor(128, 1);
            metrics.recordEntitySurvivor(191);
            // Q4: 192-255
            metrics.recordEntitySurvivor(192);
            metrics.recordEntitySurvivor(255);

            assertEquals(2, metrics.bridgeQ1());
            assertEquals(2, metrics.bridgeQ2());
            assertEquals(2, metrics.bridgeQ3());
            assertEquals(2, metrics.bridgeQ4());
        }
    }

    @Nested
    @DisplayName("Edge Age Metrics")
    class EdgeAge {

        @Test
        @DisplayName("computes average edge age")
        void computesAverageAge() {
            // Ages: 10, 20, 30 → avg = 20
            metrics.recordHebbianSurvivor(0, 10);
            metrics.recordHebbianSurvivor(0, 20);
            metrics.recordHebbianSurvivor(0, 30);

            assertEquals(20.0f, metrics.averageEdgeAge(), 0.01f);
        }

        @Test
        @DisplayName("tracks maximum edge age")
        void tracksMaxAge() {
            metrics.recordHebbianSurvivor(0, 5);
            metrics.recordHebbianSurvivor(0, 100);
            metrics.recordHebbianSurvivor(0, 50);

            assertEquals(100, metrics.maxEdgeAge());
        }

        @Test
        @DisplayName("returns 0 when no edges recorded")
        void returnsZeroWhenEmpty() {
            assertEquals(0f, metrics.averageEdgeAge());
            assertEquals(0, metrics.maxEdgeAge());
        }
    }

    @Nested
    @DisplayName("Importance Score Metrics")
    class ImportanceScore {

        @Test
        @DisplayName("computes average importance score")
        void computesAverage() {
            metrics.recordImportanceScore(0.5f);
            metrics.recordImportanceScore(0.8f);
            metrics.recordImportanceScore(0.2f);

            assertEquals(0.5f, metrics.averageImportanceScore(), 0.01f);
        }

        @Test
        @DisplayName("returns 0 when no scores recorded")
        void returnsZeroWhenEmpty() {
            assertEquals(0f, metrics.averageImportanceScore());
        }
    }

    @Nested
    @DisplayName("Fragmentation")
    class Fragmentation {

        @Test
        @DisplayName("computes fragmentation ratio")
        void computesRatio() {
            // 5 components among 100 active nodes = 0.05 fragmentation
            metrics.setHebbianFragmentation(5, 100);

            assertEquals(0.05f, metrics.fragmentationRatio(), 0.001f);
            assertEquals(5, metrics.hebbianComponents());
            assertEquals(100, metrics.hebbianActiveNodes());
        }

        @Test
        @DisplayName("returns 0 for no active nodes")
        void returnsZeroForNoNodes() {
            metrics.setHebbianFragmentation(0, 0);

            assertEquals(0f, metrics.fragmentationRatio());
        }
    }

    @Nested
    @DisplayName("Aggregate Totals")
    class Totals {

        @Test
        @DisplayName("totals combine hebbian and entity metrics")
        void totalsCombine() {
            metrics.recordHebbianDecay();
            metrics.recordHebbianDecay();
            metrics.recordEntityDecay();
            metrics.recordHebbianSurvivor(50, 1);
            metrics.recordEntitySurvivor(200);
            metrics.recordHebbianBridgeProtection();
            metrics.recordEntityBridgeProtection();

            assertEquals(3, metrics.totalEdgesDecayed());
            assertEquals(2, metrics.totalEdgesSurviving());
            assertEquals(2, metrics.totalBridgeProtected());
        }
    }

    @Test
    @DisplayName("toString produces readable summary")
    void toStringReadable() {
        metrics.recordHebbianDecay();
        metrics.recordHebbianSurvivor(200, 10);
        metrics.recordEntitySurvivor(50);

        String s = metrics.toString();
        assertNotNull(s);
        assertTrue(s.contains("hebbian["));
        assertTrue(s.contains("entity["));
        assertTrue(s.contains("bridge["));
        assertTrue(s.contains("avgAge="));
        assertTrue(s.contains("fragmentation="));
    }
}
