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
package com.spectrayan.spector.memory.temporal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.spectrayan.spector.memory.graph.TypeRegistryMemory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the TemporalKnowledgeGraph and its query capabilities.
 */
class TemporalKnowledgeGraphTest {

    @TempDir
    Path tempDir;

    private TypeRegistryMemory predicateRegistry;
    private TemporalKnowledgeGraph tkg;

    @BeforeEach
    void setUp() {
        predicateRegistry = new TypeRegistryMemory("relation-type");
        tkg = new TemporalKnowledgeGraph(predicateRegistry);
    }

    @AfterEach
    void tearDown() throws Exception {
        tkg.close();
    }

    @Test
    void assertAndQueryCurrentState() {
        // Alice (entity 1) works at Acme (entity 2) starting from epoch 1000
        int factId = tkg.assertFact(
                1, "works_at", 2, -1L, (short) 0,
                1000L, Long.MAX_VALUE, 0.9f, false
        );

        assertThat(factId).isEqualTo(1);
        assertThat(tkg.factCount()).isEqualTo(1);

        // Query active fact at epoch 1500
        List<TemporalFact> facts = tkg.factsAbout(1)
                .validAt(Instant.ofEpochMilli(1500L))
                .excludeRetracted()
                .resolve();

        assertThat(facts).hasSize(1);
        TemporalFact fact = facts.get(0);
        assertThat(fact.factId()).isEqualTo(1);
        assertThat(fact.subjectEntityId()).isEqualTo(1);
        assertThat(fact.objectEntityId()).isEqualTo(2);
        assertThat(fact.validFrom()).isEqualTo(1000L);
        assertThat(fact.validTo()).isEqualTo(Long.MAX_VALUE);
        assertThat(fact.confidence()).isEqualTo(0.9f);
        assertThat(fact.isOngoing()).isTrue();
    }

    @Test
    void pointInTimeAndIntervalQueries() {
        // Fact valid in 2024: [2024-01-01, 2025-01-01)
        long start2024 = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
        long end2024 = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli();

        tkg.assertFact(
                1, "lives_in", -1, 100L, (short) 5, // "Paris" at textOffset 100, length 5
                start2024, end2024, 0.8f, true
        );

        // Query at 2024-06-01: should return the fact
        Instant mid2024 = Instant.parse("2024-06-01T00:00:00Z");
        List<TemporalFact> midFacts = tkg.factsAbout(1)
                .validAt(mid2024)
                .resolve();
        assertThat(midFacts).hasSize(1);
        assertThat(midFacts.get(0).isInferred()).isTrue();

        // Query at 2025-06-01: should be empty
        Instant mid2025 = Instant.parse("2025-06-01T00:00:00Z");
        assertThat(tkg.factsAbout(1).validAt(mid2025).resolve()).isEmpty();

        // Interval overlap query (Q1 2024 overlap)
        Instant q1Start = Instant.parse("2024-01-01T00:00:00Z");
        Instant q1End = Instant.parse("2024-04-01T00:00:00Z");
        List<TemporalFact> overlapFacts = tkg.factsAbout(1)
                .validDuring(q1Start, q1End)
                .resolve();
        assertThat(overlapFacts).hasSize(1);
    }

    @Test
    void retractFactExcludesFromQueries() {
        int factId = tkg.assertFact(
                1, "role", -1, 50L, (short) 8, // "Engineer"
                1000L, Long.MAX_VALUE, 0.95f, false
        );

        // Retract it
        tkg.retractFact(factId);

        // Query without retraction exclusion
        List<TemporalFact> all = tkg.factsAbout(1)
                .validAt(Instant.ofEpochMilli(1500L))
                .resolve();
        assertThat(all).hasSize(1);

        // Query with retraction exclusion
        List<TemporalFact> activeOnly = tkg.factsAbout(1)
                .validAt(Instant.ofEpochMilli(1500L))
                .excludeRetracted()
                .resolve();
        assertThat(activeOnly).isEmpty();
    }

    @Test
    void contradictionResolutionLatestWins() {
        // Assert first fact
        tkg.assertFact(
                1, "has_status", -1, 10L, (short) 4, // "Busy"
                1000L, Long.MAX_VALUE, 0.5f, false
        );

        // Let time pass a bit for a distinct txTime (sleep or pass mock/different txTime if configurable,
        // but here we just assert another fact which gets a higher txTime natively)
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}

        tkg.assertFact(
                1, "has_status", -1, 20L, (short) 9, // "Available"
                1000L, Long.MAX_VALUE, 0.9f, false
        );

        // With LatestTxWinsResolver (default), the second one wins
        List<TemporalFact> resolved = tkg.factsAbout(1)
                .validAt(Instant.ofEpochMilli(1500L))
                .resolve();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).objectTextOffset()).isEqualTo(20L); // Available
    }

    @Test
    void contradictionResolutionHighestConfidence() throws Exception {
        // Create TKG with confidence resolver
        try (TemporalKnowledgeGraph confidenceTkg = new TemporalKnowledgeGraph(predicateRegistry, new HighestConfidenceResolver())) {
            confidenceTkg.assertFact(
                    1, "has_status", -1, 10L, (short) 4, // "Busy" (high confidence)
                    1000L, Long.MAX_VALUE, 0.95f, false
            );

            try { Thread.sleep(2); } catch (InterruptedException ignored) {}

            confidenceTkg.assertFact(
                    1, "has_status", -1, 20L, (short) 9, // "Available" (low confidence)
                    1000L, Long.MAX_VALUE, 0.4f, false
            );

            List<TemporalFact> resolved = confidenceTkg.factsAbout(1)
                    .validAt(Instant.ofEpochMilli(1500L))
                    .resolve();

            assertThat(resolved).hasSize(1);
            assertThat(resolved.get(0).objectTextOffset()).isEqualTo(10L); // Busy wins due to confidence
        }
    }

    @Test
    void rebuildIndexesFromDisk() throws Exception {
        Path dbPath = tempDir.resolve("temporal-facts.tfacts");

        // Write facts using one instance
        try (TemporalKnowledgeGraph writer = new TemporalKnowledgeGraph(dbPath, 64L * 1024, predicateRegistry)) {
            writer.assertFact(
                    5, "located_in", 6, -1L, (short) 0,
                    2000L, Long.MAX_VALUE, 0.85f, false
            );
            writer.assertFact(
                    5, "status", -1, 30L, (short) 6,
                    2000L, Long.MAX_VALUE, 0.7f, false
            );
            writer.flush();
        }

        // Open a new instance on the same file and verify indexes are rebuilt
        try (TemporalKnowledgeGraph reader = new TemporalKnowledgeGraph(dbPath, 64L * 1024, predicateRegistry)) {
            assertThat(reader.factCount()).isEqualTo(2);
            assertThat(reader.entityCount()).isEqualTo(1); // just entity 5

            List<TemporalFact> facts = reader.factsAbout(5)
                    .validAt(Instant.ofEpochMilli(3000L))
                    .resolve();
            assertThat(facts).hasSize(2);
        }
    }
}
