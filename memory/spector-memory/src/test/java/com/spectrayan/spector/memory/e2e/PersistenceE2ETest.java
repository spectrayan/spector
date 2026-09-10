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
package com.spectrayan.spector.memory.e2e;

import com.spectrayan.spector.memory.*;
import com.spectrayan.spector.memory.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.model.*;

import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static com.spectrayan.spector.memory.e2e.E2EAssertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * E2E tests for persistence: WAL integrity, reflect/consolidation, and
 * DISK mode save → close → reload round-trip.
 */
@DisplayName("🧠 E2E: Persistence, WAL & Reflect")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistenceE2ETest extends AbstractE2ETest {

    // ══════════════════════════════════════════════════════════════
    // WAL INTEGRITY
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("WAL contains at least one event per ingested memory")
    void walContainsEvents() {
        int walSize = memory.admin().wal().size();
        log.info("WAL size: {} events (seed memories: {})", walSize, seedMemories.size());

        assertThat(walSize)
                .as("WAL should have at least one event per ingested memory")
                .isGreaterThanOrEqualTo(seedMemories.size());
    }

    // ══════════════════════════════════════════════════════════════
    // REFLECT (SLEEP CONSOLIDATION)
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("Reflect cycle completes with valid report")
    void reflectCycleCompletes() {
        ReflectReport report = memory.reflect();

        log.info("Reflect report: {}", report);
        assertThat(report).as("Reflect should return a report").isNotNull();
        assertThat(report.duration()).as("Report should have a duration").isNotNull();
        assertThat(report.duration().toNanos()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(11)
    @DisplayName("Memories are still accessible after reflect")
    void memoriesAccessibleAfterReflect() {
        List<CognitiveResult> results = memory.recall("database optimization",
                RecallOptions.builder().topK(5).build());

        assertThat(results)
                .as("Memories should be accessible after reflect cycle")
                .isNotEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // DISK PERSISTENCE ROUND-TRIP
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("DISK persistence: save → close → reload → recall")
    void diskPersistenceRoundTrip() throws Exception {
        Path testDataDir = Path.of(".test-data", "e2e-persistence-" + System.currentTimeMillis());
        Files.createDirectories(testDataDir);

        try {
            // 1. Create a DISK-mode memory and ingest a few memories
            var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                    .setDimensions(embeddingProvider.dimensions())
                    .setWorkingCapacity(20)
                    .setEpisodicPartitionCapacity(100)
                    .setSemanticCapacity(50)
                    .setProceduralCapacity(20)
                    .setHebbianGraphCapacity(100)
                    .setTemporalChainCapacity(100);
            SpectorMemory diskMemory = DefaultSpectorMemory.builder(memProps)
                    .embeddingProvider(embeddingProvider)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .persistence(testDataDir)
                    .build();

            diskMemory.remember("persist-001", "This is a test memory for persistence validation",
                    MemoryType.EPISODIC, com.spectrayan.spector.memory.cortex.MemorySource.OBSERVED,
                    "test", "persistence");
            diskMemory.remember("persist-002", "Second test memory for round-trip verification",
                    MemoryType.SEMANTIC, com.spectrayan.spector.memory.cortex.MemorySource.REFLECTED,
                    "test", "verification");

            int countBefore = diskMemory.totalMemories();
            log.info("DISK memory count before save: {}", countBefore);

            // 2. Close (triggers save)
            diskMemory.close();

            // 3. Verify persistence files exist
            assertThat(Files.exists(StoragePaths.runtimeBundleFile(testDataDir)))
                    .as("Runtime storage bundle should exist").isTrue();

            // 4. Reload from disk
            var reloadProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                    .setDimensions(embeddingProvider.dimensions())
                    .setWorkingCapacity(20)
                    .setEpisodicPartitionCapacity(100)
                    .setSemanticCapacity(50)
                    .setProceduralCapacity(20)
                    .setHebbianGraphCapacity(100)
                    .setTemporalChainCapacity(100);
            SpectorMemory reloaded = DefaultSpectorMemory.builder(reloadProps)
                    .embeddingProvider(embeddingProvider)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .persistence(testDataDir)
                    .build();

            int countAfter = reloaded.totalMemories();
            log.info("DISK memory count after reload: {}", countAfter);

            assertThat(countAfter)
                    .as("Memory count should survive round-trip")
                    .isEqualTo(countBefore);

            // 5. Verify recall works after reload
            List<CognitiveResult> results = reloaded.recall("persistence test memory",
                    RecallOptions.builder().topK(5).build());

            assertThat(results).as("Recall should work after reload").isNotEmpty();
            assertRecallContainsAny(results, "persist-001", "persist-002");

            // 6. Verify specific ID is in the reloaded index
            assertThat(reloaded.admin().index().locate("persist-001"))
                    .as("persist-001 should be in reloaded index").isNotNull();
            assertThat(reloaded.admin().index().locate("persist-002"))
                    .as("persist-002 should be in reloaded index").isNotNull();

            reloaded.close();

        } finally {
            // Clean up test data directory
            deleteRecursively(testDataDir);
        }
    }

    @Test
    @Order(21)
    @DisplayName("Double close is idempotent (AutoCloseable contract)")
    void doubleCloseIsIdempotent() throws Exception {
        Path testDataDir = Path.of(".test-data", "e2e-double-close-" + System.currentTimeMillis());
        Files.createDirectories(testDataDir);

        try {
            var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                    .setDimensions(embeddingProvider.dimensions())
                    .setWorkingCapacity(10)
                    .setEpisodicPartitionCapacity(50)
                    .setSemanticCapacity(20)
                    .setProceduralCapacity(10)
                    .setHebbianGraphCapacity(50)
                    .setTemporalChainCapacity(50);
            SpectorMemory diskMemory = DefaultSpectorMemory.builder(memProps)
                    .embeddingProvider(embeddingProvider)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .persistence(testDataDir)
                    .build();

            diskMemory.close();

            // DefaultSpectorMemory close() is idempotent
            assertThatCode(diskMemory::close)
                    .as("Double close should be idempotent")
                    .doesNotThrowAnyException();
        } finally {
            deleteRecursively(testDataDir);
        }
    }

    // ── Utility ──

    private static void deleteRecursively(Path dir) throws Exception {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
            }
        }
    }
}
