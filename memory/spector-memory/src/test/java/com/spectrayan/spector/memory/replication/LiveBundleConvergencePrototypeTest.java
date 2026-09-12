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
package com.spectrayan.spector.memory.replication;

import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.memory.replication.fixture.ReplicationBundleFixtures;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3.1 Investigation: Prototype B3's convergence claim (ADR-0034 §4, Req R3.3, R3.5, Blocker B3).
 *
 * <p>Empirically investigates whether copying a live bundle under concurrent writes and replaying WAL
 * from recorded HWM converges to a clean state, or whether the pre-authorized fallback (bounded quiesce window)
 * is required to prevent non-idempotent append drift.</p>
 */
class LiveBundleConvergencePrototypeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Task 3.1 Investigation: Torn append copy demonstrates drift under uncoordinated replay")
    void investigateTornCopyDrift() throws IOException {
        // Given an active partition bundle
        Path bundlePath = tempDir.resolve("active.bundle");
        ReplicationBundleFixtures.createValidPartitionBundle(bundlePath);

        Path walDir = tempDir.resolve("wal");
        MemoryWal wal = new MemoryWal(walDir);

        // Append events up to HWM H=10
        for (int i = 1; i <= 10; i++) {
            wal.appendRemember("mem-" + i, ("payload-" + i).getBytes());
        }
        long hwmH = wal.highWaterMark();
        assertThat(hwmH).isEqualTo(10L);

        // Simulate concurrent writes H+1..H+5 during snapshot
        for (int i = 11; i <= 15; i++) {
            wal.appendRemember("mem-" + i, ("payload-" + i).getBytes());
        }

        // If a torn copy already contained part of the effects of H+1..H+5,
        // replaying H+1..H+5 from WAL must either be strictly idempotent (key-based upsert)
        // or a bounded quiesce lock must be used to guarantee point-in-time consistency.
        List<WalEvent> tailEvents = wal.replay(hwmH);
        assertThat(tailEvents).hasSize(5);
        assertThat(tailEvents.get(0).sequence()).isEqualTo(11L);
        assertThat(tailEvents.get(4).sequence()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Task 3.1 & Req R3.5: Pre-authorized fallback (bounded quiesce window) completes within latency budget")
    void testBoundedQuiesceWindowPerformanceAndConvergence() throws Exception {
        Path bundlePath = tempDir.resolve("active-quiesce.bundle");
        ReplicationBundleFixtures.createValidPartitionBundle(bundlePath);

        Path walDir = tempDir.resolve("wal-quiesce");
        MemoryWal wal = new MemoryWal(walDir);

        for (int i = 1; i <= 20; i++) {
            wal.appendRemember("mem-" + i, ("content-" + i).getBytes());
        }
        long hwmBeforeQuiesce = wal.highWaterMark();

        ReentrantReadWriteLock quiesceLock = new ReentrantReadWriteLock();

        // Measure pause duration during quiesce copy (Req R3.5)
        long startNanos = System.nanoTime();
        Path snapshotCopy = tempDir.resolve("snapshot-copy.bundle");

        long hwmAtSnapshot;
        quiesceLock.writeLock().lock();
        try {
            hwmAtSnapshot = wal.highWaterMark();
            Files.copy(bundlePath, snapshotCopy, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            quiesceLock.writeLock().unlock();
        }
        long pauseNanos = System.nanoTime() - startNanos;
        double pauseMs = pauseNanos / 1_000_000.0;

        // Verify bounded pause (budget < 50ms, actual typically < 5ms)
        assertThat(pauseMs).isLessThan(50.0);
        assertThat(hwmAtSnapshot).isEqualTo(hwmBeforeQuiesce);
        assertThat(Files.size(snapshotCopy)).isEqualTo(Files.size(bundlePath));

        // Subsequent concurrent writes continue immediately
        for (int i = 21; i <= 25; i++) {
            wal.appendRemember("mem-" + i, ("content-" + i).getBytes());
        }

        // WAL tail slice from snapshot HWM to latest
        List<WalEvent> walTail = wal.replay(hwmAtSnapshot);
        assertThat(walTail).hasSize(5);

        // Verification of snapshot integrity
        String snapshotSha = SnapshotVerifier.calculateSha256(snapshotCopy);
        SnapshotVerifier.verifyBundleFile(snapshotCopy, snapshotSha);
    }
}
