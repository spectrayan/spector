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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.pathway.*;
import com.spectrayan.spector.memory.persist.*;
import com.spectrayan.spector.memory.assembly.*;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Restart-correctness regression tests for issue #443 (Phase 2, ADR-0002 D6.3 &amp; D6.4).
 *
 * <p>These exercise the on-disk v6 {@code .midx} + open-all-on-load path: a DISK store is
 * forced to roll across ≥2 partitions, closed, and reopened from scratch via the factory.
 * Recall and direct-resolve must then span every persisted partition with correct text —
 * behaviour that was impossible before Phase 2 because the colocated partition was never
 * persisted and only the newest partition dir was opened on load.</p>
 */
@DisplayName("issue #443 — restart-correct multi-partition recall (Phase 2)")
class PartitionRestartRecallTest {

    private SpectorMemory memory;

    private SpectorMemory build(Path dir, int episodicCap, int semanticCap) {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        return DefaultSpectorMemory.builder()
                .dimensions(embed.dimensions())
                .embeddingProvider(embed)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .workingCapacity(32)
                .episodicPartitionCapacity(episodicCap)
                .semanticCapacity(semanticCap)
                .proceduralCapacity(32)
                .surpriseWarmup(1)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
            memory = null;
        }
    }

    private static Set<String> ids(List<CognitiveResult> results) {
        return results.stream().map(CognitiveResult::id).collect(Collectors.toSet());
    }

    private static long partitionDirCount(Path base) throws Exception {
        try (var stream = Files.newDirectoryStream(StorageLayout.partitionsDir(base))) {
            long n = 0;
            for (Path p : stream) {
                if (Files.isDirectory(p) && StorageLayout.isPartitionDir(p.getFileName().toString())) n++;
            }
            return n;
        }
    }

    // ── D6 test 3: restart across ≥2 partitions ───────────────────

    @Test
    @DisplayName("restart: recall returns records from BOTH the oldest and newest partition, with text")
    void recallSpansPartitionsAfterRestart(@TempDir Path dir) throws Exception {
        // Phase A: build a store, force a roll so records straddle partition 000 and 001.
        memory = build(dir, /*episodicCap*/ 2, /*semanticCap*/ 64);
        memory.remember("r-0", "the database migration failed on shard seven",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp");
        memory.remember("r-1", "cache warmup completed for the payments service",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp");
        memory.remember("r-2", "kafka consumer lag spiked during the deploy",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp");

        assertThat(partitionDirCount(dir)).as("a roll must have occurred").isGreaterThanOrEqualTo(2);
        memory.close();
        memory = null;

        // Phase B: reopen from disk — v6 .midx + open-all-on-load must rehydrate BOTH partitions.
        memory = build(dir, /*episodicCap*/ 2, /*semanticCap*/ 64);
        assertThat(memory.memoryCount(MemoryType.EPISODIC))
                .as("all persisted episodic records must reload").isEqualTo(3);

        // Recall a record from the OLDEST (now-frozen) partition 000.
        List<CognitiveResult> oldest = memory.recall(
                "the database migration failed on shard seven",
                RecallOptions.builder().topK(10).build());
        assertThat(ids(oldest)).as("oldest-partition record recalled after restart").contains("r-0");
        CognitiveResult r0 = oldest.stream().filter(r -> "r-0".equals(r.id())).findFirst().orElseThrow();
        assertThat(r0.text()).as("oldest-partition text resolves via its own text.dat")
                .isEqualTo("the database migration failed on shard seven");

        // Recall a record from the NEWEST (active) partition.
        List<CognitiveResult> newest = memory.recall(
                "kafka consumer lag spiked during the deploy",
                RecallOptions.builder().topK(10).build());
        assertThat(ids(newest)).as("newest-partition record recalled after restart").contains("r-2");
        CognitiveResult r2 = newest.stream().filter(r -> "r-2".equals(r.id())).findFirst().orElseThrow();
        assertThat(r2.text()).isEqualTo("kafka consumer lag spiked during the deploy");
    }

    // ── D6 test 4: reverse-key collision across partitions ────────

    @Test
    @DisplayName("reverse-key collision: same physical offset in different partitions resolve independently after reload")
    void reverseKeyCollisionResolvesPerPartitionAfterReload(@TempDir Path dir) throws Exception {
        // episodicCap=1 forces a roll after every record → each partition's first (only)
        // record lands at the SAME physical offset. Pre-#443 these collided on the reverse key.
        memory = build(dir, /*episodicCap*/ 1, /*semanticCap*/ 64);
        memory.remember("c-0", "first record lives in partition zero",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "col");
        memory.remember("c-1", "second record lives in partition one",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "col");
        memory.remember("c-2", "third record lives in partition two",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "col");

        assertThat(partitionDirCount(dir)).as("multiple rolls must have occurred").isGreaterThanOrEqualTo(3);
        memory.close();
        memory = null;

        // Reopen and inspect each id — each must return ITS OWN text/header, proving the
        // persisted colocatedPartition disambiguates the shared physical offset.
        memory = build(dir, /*episodicCap*/ 1, /*semanticCap*/ 64);

        var c0 = memory.inspect("c-0");
        var c1 = memory.inspect("c-1");
        var c2 = memory.inspect("c-2");
        assertThat(c0).isNotNull();
        assertThat(c1).isNotNull();
        assertThat(c2).isNotNull();

        assertThat(c0.text()).isEqualTo("first record lives in partition zero");
        assertThat(c1.text()).isEqualTo("second record lives in partition one");
        assertThat(c2.text()).isEqualTo("third record lives in partition two");

        assertThat(c0.memoryType()).isEqualTo(MemoryType.EPISODIC);
        assertThat(c1.memoryType()).isEqualTo(MemoryType.EPISODIC);
        assertThat(c2.memoryType()).isEqualTo(MemoryType.EPISODIC);
    }
}
