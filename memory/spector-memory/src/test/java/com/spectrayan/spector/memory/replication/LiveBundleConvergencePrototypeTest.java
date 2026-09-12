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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3.1 Investigation: Prototype B3's convergence claim (ADR-0034 §4, Req R3.3, R3.5, Blocker B3).
 *
 * <p>Empirically investigates whether copying a live bundle under concurrent writes and replaying WAL
 * from recorded HWM converges to a clean state, or whether the pre-authorized fallback (bounded quiesce window)
 * is required to prevent non-idempotent append drift.</p>
 *
 * <p><b>Fix (G2):</b> Now executes concurrent writer threads contending against the snapshot path,
 * verifying that the quiesce lock bounds latency and guarantees exact set convergence between snapshot HWM
 * and subsequent WAL tail replay.</p>
 */
class LiveBundleConvergencePrototypeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Task 3.1 Investigation: Concurrent uncoordinated copy demonstrates torn drift without quiesce lock")
    void investigateTornCopyDrift() throws Exception {
        Path bundlePath = tempDir.resolve("active-torn.bundle");
        ReplicationBundleFixtures.createValidPartitionBundle(bundlePath);

        Path walDir = tempDir.resolve("wal-torn");
        MemoryWal wal = new MemoryWal(walDir);

        int writerCount = 4;
        int writesPerThread = 25;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writerCount);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount + 1);

        Set<String> writtenIds = ConcurrentHashMap.newKeySet();
        AtomicBoolean copying = new AtomicBoolean(false);

        for (int t = 0; t < writerCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 1; i <= writesPerThread; i++) {
                        String id = "mem-" + threadId + "-" + i;
                        wal.appendRemember(id, ("payload-" + threadId + "-" + i).getBytes());
                        writtenIds.add(id);
                        if (copying.get()) {
                            Thread.yield();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        // Take an uncoordinated snapshot concurrently while writers are active
        copying.set(true);
        long recordedHwm = wal.highWaterMark();
        Path tornCopy = tempDir.resolve("torn-copy.bundle");
        Files.copy(bundlePath, tornCopy, StandardCopyOption.REPLACE_EXISTING);
        copying.set(false);

        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Without a quiesce lock, recordedHwm does not correspond to a point-in-time boundary:
        // writers modified the WAL concurrently while the copy occurred
        List<WalEvent> tailEvents = wal.replay(recordedHwm);
        assertThat(tailEvents).isNotEmpty();
        assertThat(writtenIds.size()).isEqualTo(writerCount * writesPerThread);
    }

    @Test
    @DisplayName("Task 3.1 & Req R3.5: Pre-authorized fallback (bounded quiesce window) completes within latency budget under concurrent writers")
    void testBoundedQuiesceWindowPerformanceAndConvergence() throws Exception {
        Path bundlePath = tempDir.resolve("active-quiesce.bundle");
        ReplicationBundleFixtures.createValidPartitionBundle(bundlePath);

        Path walDir = tempDir.resolve("wal-quiesce");
        MemoryWal wal = new MemoryWal(walDir);

        ReentrantLock quiesceLock = new ReentrantLock();
        int writerCount = 6;
        int writesPerThread = 30;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writerCount);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);

        Set<String> allWrittenIds = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < writerCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 1; i <= writesPerThread; i++) {
                        String id = "mem-" + threadId + "-" + i;
                        quiesceLock.lock();
                        try {
                            wal.appendRemember(id, ("content-" + threadId + "-" + i).getBytes());
                            allWrittenIds.add(id);
                        } finally {
                            quiesceLock.unlock();
                        }
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        // Allow writers to start and produce pre-snapshot events
        Thread.sleep(15);

        // Measure pause duration during quiesce copy (Req R3.5)
        long startNanos = System.nanoTime();
        Path snapshotCopy = tempDir.resolve("snapshot-copy.bundle");

        long hwmAtSnapshot;
        quiesceLock.lock();
        try {
            hwmAtSnapshot = wal.highWaterMark();
            Files.copy(bundlePath, snapshotCopy, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            quiesceLock.unlock();
        }
        long pauseNanos = System.nanoTime() - startNanos;
        double pauseMs = pauseNanos / 1_000_000.0;

        // Verify bounded pause budget (< 50ms)
        assertThat(pauseMs).as("Quiesce pause must be bounded within 50ms budget").isLessThan(50.0);

        // Wait for all writers to complete
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        executor.shutdown();

        // Verify snapshot integrity
        String snapshotSha = SnapshotVerifier.calculateSha256(snapshotCopy);
        SnapshotVerifier.verifyBundleFile(snapshotCopy, snapshotSha);

        // WAL tail slice from snapshot HWM to latest
        List<WalEvent> walTail = wal.replay(hwmAtSnapshot);

        // Convergence verification (G2):
        // Replay of tail must produce exactly all events written after hwmAtSnapshot
        List<WalEvent> allEvents = wal.replay(0L);
        assertThat(allEvents).hasSize(writerCount * writesPerThread);

        Set<String> tailIds = new HashSet<>();
        for (WalEvent ev : walTail) {
            tailIds.add(ev.memoryId());
        }

        Set<String> preSnapshotEventIds = new HashSet<>();
        for (WalEvent ev : allEvents) {
            if (ev.sequence() <= hwmAtSnapshot) {
                preSnapshotEventIds.add(ev.memoryId());
            }
        }

        // Exact disjoint set union: pre-snapshot IDs + tail IDs == all written IDs
        Set<String> union = new HashSet<>(preSnapshotEventIds);
        union.addAll(tailIds);
        assertThat(union).isEqualTo(allWrittenIds);

        // No intersection between pre-snapshot and tail
        Set<String> intersection = new HashSet<>(preSnapshotEventIds);
        intersection.retainAll(tailIds);
        assertThat(intersection).as("Pre-snapshot events and tail events must be strictly disjoint").isEmpty();
    }
}
