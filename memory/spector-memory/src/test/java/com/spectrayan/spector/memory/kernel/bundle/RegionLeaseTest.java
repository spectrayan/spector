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
package com.spectrayan.spector.memory.kernel.bundle;

import com.spectrayan.spector.memory.kernel.region.RegionId;
import com.spectrayan.spector.memory.kernel.region.RegionSizeSpec;

import com.spectrayan.spector.memory.kernel.region.RegionPreamble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verification of RegionLease lifecycle and concurrency protection (Req: R2.7, R2.8).
 *
 * <p>Validates that:
 * <ol>
 *   <li>Resolving a segment without a lease causes an {@link IllegalStateException}
 *       if a concurrent {@code growRegion} closes the underlying arena.</li>
 *   <li>Holding an active {@link RegionLease} blocks {@code growRegion} until all
 *       leases are drained, preventing use-after-free and arena closure crashes.</li>
 *   <li>Releasing all leases allows pending grows to complete safely.</li>
 *   <li>Accessing a lease after {@link RegionLease#close()} throws {@link IllegalStateException}.</li>
 * </ol>
 */
@DisplayName("RegionLease Concurrency & Invalidation Tests (R2.7, R2.8)")
class RegionLeaseTest {

    @Test
    @DisplayName("Demonstrate crash: raw resolved segment without lease crashes with IllegalStateException on growRegion")
    void demonstrateGrowWithoutLeaseCrashesHeldSegment(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime-crash.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);
            long offset = RegionPreamble.PREAMBLE_BYTES;
            bm25Ref.resolve().set(ValueLayout.JAVA_LONG, offset, 0xCAFEBABE_DEADBEEFL);

            // Reader captures resolved segment without acquiring a RegionLease
            MemorySegment capturedSlab = bm25Ref.resolve();
            assertThat(capturedSlab.get(ValueLayout.JAVA_LONG, offset)).isEqualTo(0xCAFEBABE_DEADBEEFL);

            // Concurrent grow triggers arena.close(), unmapping the entire bundle
            bundle.growRegion(RegionId.BM25);

            // Accessing capturedSlab now throws IllegalStateException because its Arena was closed
            assertThatThrownBy(() -> capturedSlab.get(ValueLayout.JAVA_LONG, offset))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Test
    @DisplayName("Req R2.8: active RegionLease blocks arena.close() until lease drains")
    void growDuringActiveLeaseWaitsUntilLeaseClosed(@TempDir Path tempDir) throws Exception {
        Path bundlePath = tempDir.resolve("runtime-lease.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);
            long offset = RegionPreamble.PREAMBLE_BYTES;
            bm25Ref.resolve().set(ValueLayout.JAVA_LONG, offset, 0x1234567890ABCDEFL);

            CountDownLatch readerHoldingLease = new CountDownLatch(1);
            CountDownLatch growStarted = new CountDownLatch(1);

            // Thread 1 acquires lease
            CompletableFuture<Void> growFuture;
            try (RegionLease lease = bm25Ref.lease()) {
                assertThat(bundle.activeLeases()).isEqualTo(1);
                MemorySegment slab = lease.slab();

                readerHoldingLease.countDown();

                // Thread 2 attempts to grow the region concurrently
                growFuture = CompletableFuture.runAsync(() -> {
                    try {
                        readerHoldingLease.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    growStarted.countDown();
                    bundle.growRegion(RegionId.BM25);
                });

                // Wait until grow thread has started
                assertThat(growStarted.await(2, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(100); // Give grow thread time to acquire writeLock and await leases

                // growFuture must NOT be completed because activeLeases == 1
                assertThat(growFuture.isDone())
                        .as("growRegion must block while lease is outstanding")
                        .isFalse();

                // Reader safely accesses slab without IllegalStateException
                assertThat(slab.get(ValueLayout.JAVA_LONG, offset)).isEqualTo(0x1234567890ABCDEFL);

                // Exit try-with-resources, closing lease and decrementing activeLeases to 0
            }

            // Once lease is closed, growRegion finishes
            growFuture.get(5, TimeUnit.SECONDS);
            assertThat(growFuture.isDone()).isTrue();
            assertThat(bundle.activeLeases()).isEqualTo(0);

            // Re-resolving through RegionRef accesses the newly remapped slice safely
            assertThat(bm25Ref.resolve().get(ValueLayout.JAVA_LONG, offset)).isEqualTo(0x1234567890ABCDEFL);
        }
    }

    @Test
    @DisplayName("Multiple concurrent leases must all drain before growRegion executes")
    void multipleConcurrentLeasesDrainCompletelyBeforeGrow(@TempDir Path tempDir) throws Exception {
        Path bundlePath = tempDir.resolve("runtime-multi-lease.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);

            RegionLease lease1 = bm25Ref.lease();
            RegionLease lease2 = bm25Ref.lease();
            assertThat(bundle.activeLeases()).isEqualTo(2);

            CompletableFuture<Void> growFuture = CompletableFuture.runAsync(() -> {
                bundle.growRegion(RegionId.BM25);
            });

            Thread.sleep(100);
            assertThat(growFuture.isDone()).isFalse();

            // Release first lease
            lease1.close();
            assertThat(bundle.activeLeases()).isEqualTo(1);
            Thread.sleep(100);
            assertThat(growFuture.isDone()).isFalse();

            // Release second lease -> drains to 0
            lease2.close();
            growFuture.get(5, TimeUnit.SECONDS);
            assertThat(growFuture.isDone()).isTrue();
            assertThat(bundle.activeLeases()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Accessing RegionLease.slab() after close throws IllegalStateException")
    void closedLeaseThrowsIllegalStateExceptionOnSlabAccess(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime-closed-lease.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);
            RegionLease lease = bm25Ref.lease();
            assertThat(lease.isClosed()).isFalse();
            assertThat(lease.slab()).isNotNull();

            lease.close();
            assertThat(lease.isClosed()).isTrue();
            assertThatThrownBy(lease::slab)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been closed");
        }
    }

    @Test
    @DisplayName("Reentrant leases on same thread do not deadlock")
    void reentrantLeasesOnSameThreadDoNotDeadlock(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime-reentrant.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);

            try (RegionLease l1 = bm25Ref.lease();
                 RegionLease l2 = bm25Ref.lease()) {
                assertThat(bundle.activeLeases()).isEqualTo(2);
                assertThat(l1.slab()).isNotNull();
                assertThat(l2.slab()).isNotNull();
            }
            assertThat(bundle.activeLeases()).isEqualTo(0);
        }
    }
}
