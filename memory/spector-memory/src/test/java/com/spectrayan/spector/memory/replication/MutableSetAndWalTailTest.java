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

import com.spectrayan.spector.memory.replication.fixture.ReplicationBundleFixtures;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Group 3 mutable-set shipping, debounce logic, WAL tail streaming,
 * gap detection, and truncation handling (ADR-0034 §9.7, §10, Req R3.1–R3.5, R4.1–R4.4).
 */
class MutableSetAndWalTailTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Task 3.3 / Req R3.2: Debounce requires both interval AND change count")
    void debounceRequiresBothIntervalAndChangeCount() {
        long t0 = 100_000L;
        MutableSetDebouncer debouncer = new MutableSetDebouncer(60_000L, 100L, t0);

        // 1. Time elapsed < 60s, changes < 100 -> false
        assertThat(debouncer.shouldSnapshot(t0 + 10_000L)).isFalse();

        // 2. 150 changes recorded, but only 30s elapsed -> false
        debouncer.recordMutations(150L);
        assertThat(debouncer.shouldSnapshot(t0 + 30_000L)).isFalse();

        // 3. 70s elapsed, but change count reset to 0 -> false
        debouncer.onSnapshotProduced(t0 + 30_000L);
        assertThat(debouncer.shouldSnapshot(t0 + 100_000L)).isFalse();

        // 4. Both satisfied: 100 changes AND 65s elapsed -> true
        debouncer.recordMutations(100L);
        assertThat(debouncer.shouldSnapshot(t0 + 95_000L)).isTrue();

        // 5. On snapshot produced, resets
        debouncer.onSnapshotProduced(t0 + 95_000L);
        assertThat(debouncer.shouldSnapshot(t0 + 95_000L)).isFalse();
        assertThat(debouncer.changeCountSinceLastSnapshot()).isZero();
    }

    @Test
    @DisplayName("Task 3.2, 3.4 & 3.5: MutableSetShipper copies mutable set under bounded pause and creates INCREMENTAL manifest")
    void mutableSetShipperCreatesIncrementalManifest() {
        Path rtPath = ReplicationBundleFixtures.createValidRuntimeBundle(tempDir.resolve("runtime.bundle"));
        Path partPath = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("partition.bundle"));

        ReplicationByteTracker byteTracker = new ReplicationByteTracker();
        ReentrantLock lock = new ReentrantLock();

        MutableSetShipper.MutableCopyResult copy = MutableSetShipper.copyMutableSet(
                rtPath,
                partPath,
                tempDir.resolve("snapshots/1"),
                lock,
                () -> 500L,
                byteTracker
        );

        assertThat(copy.hwmAtSnapshot()).isEqualTo(500L);
        assertThat(copy.pauseNanos() / 1_000_000.0).isLessThan(50.0);
        assertThat(byteTracker.mutableBytes()).isGreaterThan(0L);

        SnapshotManifest manifest = MutableSetShipper.buildIncrementalManifest(
                "tenant-1",
                "ns-1",
                "StoragePaths.namespaceDirSharded",
                1L,
                copy,
                200L,
                List.of(new SnapshotManifest.SealedPartitionEntry("p-sealed.bundle", "sha-sealed", null))
        );

        assertThat(manifest.kind()).isEqualTo(SnapshotKind.INCREMENTAL);
        assertThat(manifest.walFrom()).isEqualTo(200L);
        assertThat(manifest.walTo()).isEqualTo(500L);
        assertThat(manifest.runtime().file()).isEqualTo("runtime.bundle");
        assertThat(manifest.activePartition().id()).isEqualTo("partition.bundle");
        assertThat(manifest.sealed()).hasSize(1);
    }

    @Test
    @DisplayName("Task 3.6 & 3.7: WalTailStreamer streams ordered, gap-free WAL events")
    void walTailStreamerStreamsOrderedEvents() throws IOException {
        Path walDir = tempDir.resolve("wal");
        MemoryWal wal = new MemoryWal(walDir);

        for (int i = 1; i <= 10; i++) {
            wal.appendRemember("mem-" + i, ("val-" + i).getBytes());
        }

        // Stream tail from HWM=5 up to 10
        List<WalEvent> tail = WalTailStreamer.streamTail(wal, 5L, 10L);

        assertThat(tail).hasSize(5);
        assertThat(tail.get(0).sequence()).isEqualTo(6L);
        assertThat(tail.get(4).sequence()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Task 3.7 / Req R4.2: WalTailStreamer throws WalTailGapException on sequence gap")
    void walTailStreamerDetectsSequenceGap() {
        // If candidate events have a missing sequence number (e.g. 6 then 8)
        MemoryWal mockWal = org.mockito.Mockito.mock(MemoryWal.class);
        WalEvent event6 = new WalEvent(6L, WalEvent.EventType.REMEMBER, "m-6", java.time.Instant.now(), new byte[0]);
        WalEvent event8 = new WalEvent(8L, WalEvent.EventType.REMEMBER, "m-8", java.time.Instant.now(), new byte[0]);

        org.mockito.Mockito.when(mockWal.replay(5L)).thenReturn(List.of(event6, event8));

        assertThatThrownBy(() -> WalTailStreamer.streamTail(mockWal, 5L, 10L))
                .isInstanceOf(WalTailGapException.class)
                .satisfies(e -> {
                    WalTailGapException ge = (WalTailGapException) e;
                    assertThat(ge.expectedSequence()).isEqualTo(7L);
                    assertThat(ge.actualSequence()).isEqualTo(8L);
                })
                .hasMessageContaining("gap detected");
    }

    @Test
    @DisplayName("Task 3.9 / Req R4.4: WalTailStreamer detects WAL truncation and throws WalTruncationLagException")
    void walTailStreamerDetectsTruncationLag() {
        // Follower asks for sequence after 100, but oldest remaining event is 200 (101..199 truncated)
        MemoryWal mockWal = org.mockito.Mockito.mock(MemoryWal.class);
        WalEvent event200 = new WalEvent(200L, WalEvent.EventType.REMEMBER, "m-200", java.time.Instant.now(), new byte[0]);

        org.mockito.Mockito.when(mockWal.replay(100L)).thenReturn(List.of(event200));

        assertThatThrownBy(() -> WalTailStreamer.streamTail(mockWal, 100L, 300L))
                .isInstanceOf(WalTruncationLagException.class)
                .satisfies(e -> {
                    WalTruncationLagException tle = (WalTruncationLagException) e;
                    assertThat(tle.requestedSequence()).isEqualTo(100L);
                    assertThat(tle.earliestAvailableSequence()).isEqualTo(200L);
                })
                .hasMessageContaining("truncated");
    }
}
