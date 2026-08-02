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
 * Regression tests for issue #443 (Phase 1): in-process multi-partition recall
 * fan-out and partition-keyed direct-resolve.
 *
 * <p>DISK mode with tiny tier capacities forces {@code PartitionManager.rollPartition()}
 * mid-test, so records straddle a frozen partition (000) and the active partition (001).
 * Before the fix, recall stayed pinned to the active partition and never returned
 * pre-roll records; direct-resolve resolved against the active store only.</p>
 */
@DisplayName("issue #443 — multi-partition recall fan-out + direct-resolve (Phase 1)")
class PartitionRecallFanoutTest {

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

    // ── D6 test 1: in-process episodic roll ───────────────────────

    @Test
    @DisplayName("episodic roll: a PRE-roll record is still returned by recall (fails pre-#443)")
    void recallSpansFrozenEpisodicPartition(@TempDir Path dir) throws Exception {
        memory = build(dir, /*episodicCap*/ 2, /*semanticCap*/ 64);

        // Fill partition 000 (cap 2), then overflow → roll to partition 001.
        memory.remember("epi-0", "the database migration failed on shard seven",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp").join();
        memory.remember("epi-1", "cache warmup completed for the payments service",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp").join();
        memory.remember("epi-2", "kafka consumer lag spiked during the deploy",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp").join();

        assertThat(partitionDirCount(dir)).as("a roll must have occurred").isGreaterThanOrEqualTo(2);
        assertThat(memory.memoryCount(MemoryType.EPISODIC)).isEqualTo(3);

        // Recall the PRE-roll record (lives in the now-frozen partition 000).
        List<CognitiveResult> results = memory.recall("the database migration failed on shard seven",
                RecallOptions.builder().topK(10).build());
        assertThat(ids(results)).as("pre-roll frozen-partition record must be recalled").contains("epi-0");
    }

    // ── D6 test 2: in-process semantic roll (similarity-bearing) ──

    @Test
    @DisplayName("semantic roll: a PRE-roll semantic record returns with a similarity-bearing score")
    void recallSpansFrozenSemanticPartitionWithSimilarity(@TempDir Path dir) throws Exception {
        memory = build(dir, /*episodicCap*/ 64, /*semanticCap*/ 2);

        memory.remember("sem-0", "postgres uses MVCC for concurrency control",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "grp").join();
        memory.remember("sem-1", "redis is an in-memory key value store",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "grp").join();
        memory.remember("sem-2", "kubernetes schedules pods onto worker nodes",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "grp").join();

        assertThat(partitionDirCount(dir)).as("a roll must have occurred").isGreaterThanOrEqualTo(2);

        List<CognitiveResult> results = memory.recall("postgres uses MVCC for concurrency control",
                RecallOptions.builder().topK(10).build());

        CognitiveResult preRoll = results.stream()
                .filter(r -> "sem-0".equals(r.id())).findFirst().orElse(null);
        assertThat(preRoll).as("pre-roll frozen-semantic record must be recalled").isNotNull();
        // Frozen-semantic slab scan computes similarity (SemanticRecordMemory stores the
        // quantized vector), so the score is similarity-bearing, not importance-only.
        assertThat(preRoll.breakdown()).isNotNull();
        assertThat(preRoll.breakdown().similarity())
                .as("frozen-semantic slab scan yields a similarity component").isGreaterThan(0f);
    }

    // ── D6 test 5: direct-resolve across partitions ──────────────

    @Test
    @DisplayName("direct-resolve: inspect/forget/reinforce/markResolved/browse hit the correct frozen store")
    void directResolveAcrossPartitions(@TempDir Path dir) throws Exception {
        memory = build(dir, /*episodicCap*/ 2, /*semanticCap*/ 64);

        memory.remember("d-0", "the incident postmortem is due friday",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp").join();
        memory.remember("d-1", "rotate the tls certificate before it expires",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp").join();
        memory.remember("d-2", "the on-call rotation changes next week",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "grp").join();

        assertThat(partitionDirCount(dir)).as("a roll must have occurred").isGreaterThanOrEqualTo(2);

        // inspect a FROZEN-partition record → returns its OWN text/header (no collision
        // with the active partition, whose first record shares the same physical offset).
        var recD0 = memory.inspect("d-0");
        assertThat(recD0).isNotNull();
        assertThat(recD0.text()).isEqualTo("the incident postmortem is due friday");
        assertThat(recD0.memoryType()).isEqualTo(MemoryType.EPISODIC);

        var recD1 = memory.inspect("d-1");
        assertThat(recD1).isNotNull();
        assertThat(recD1.text()).isEqualTo("rotate the tls certificate before it expires");

        // reinforce + markResolved on a frozen-partition record must not fail.
        memory.reinforce("d-0", (byte) 50);
        memory.markResolved("d-0");

        // browse by tag surfaces frozen-partition records too.
        var browsed = memory.browse("grp").stream()
                .map(r -> r.id()).collect(Collectors.toSet());
        assertThat(browsed).contains("d-0", "d-1", "d-2");

        // forget the frozen-partition record → tombstones the RIGHT record; recall excludes it,
        // while its partition sibling (d-1) remains recallable.
        memory.forget("d-0");
        assertThat(memory.inspect("d-0")).as("forgotten record removed from index").isNull();

        List<CognitiveResult> after = memory.recall("the incident postmortem is due friday",
                RecallOptions.builder().topK(10).build());
        assertThat(ids(after)).as("forgotten frozen-partition record excluded from recall").doesNotContain("d-0");

        var recD1After = memory.inspect("d-1");
        assertThat(recD1After).as("sibling in same frozen partition is untouched").isNotNull();
        assertThat(recD1After.text()).isEqualTo("rotate the tls certificate before it expires");
    }
}
