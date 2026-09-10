/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.kernel.engram;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.store.DefaultHeaderCursor;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent lost-update test verifying that atomic delta operations on HeaderCursor
 * prevent lost updates under multi-threaded concurrency (Req: R6.9).
 *
 * <p>Demonstrates that {@link DefaultHeaderCursor#addActivationCount(int)} completes with
 * 100% update retention, while a naive read-modify-write pattern fails due to lost updates.</p>
 */
@DisplayName("Concurrent Lost-Update Verification (R6.9)")
class ConcurrentLostUpdateTest {

    private static final int THREADS = 16;
    private static final int ITERATIONS_PER_THREAD = 1_000;
    private static final int TOTAL_EXPECTED = THREADS * ITERATIONS_PER_THREAD;

    @Test
    @DisplayName("Atomic addActivationCount preserves all increments without lost updates")
    void atomicAddActivationCountPreservesAllIncrements() throws Exception {
        try (Arena arena = Arena.ofShared()) {
            EngramLayout layout = new EngramLayout(16);
            MemorySegment seg = arena.allocate(layout.stride() * 2);
            StrengthMemory strength = StrengthMemory.heap(2, 2, 2);

            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<Void>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    try (var cursor = new DefaultHeaderCursor(seg, layout, MemoryType.SEMANTIC, 2, 0, strength)) {
                        cursor.seek(0);
                        for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                            cursor.addActivationCount(1);
                        }
                    }
                    return null;
                }));
            }

            ready.await();
            start.countDown();

            for (Future<Void> f : futures) {
                f.get();
            }
            executor.shutdown();

            try (var verifyCursor = new DefaultHeaderCursor(seg, layout, MemoryType.SEMANTIC, 2, 0, strength)) {
                verifyCursor.seek(0);
                assertThat(verifyCursor.activationCount())
                        .as("Atomic addActivationCount must retain 100% of concurrent increments (no lost updates)")
                        .isEqualTo(TOTAL_EXPECTED);
            }
        }
    }

    @Test
    @DisplayName("Demonstrate that non-atomic read-modify-write loses updates under concurrency")
    void demonstrateNonAtomicReadModifyWriteLosesUpdates() throws Exception {
        try (Arena arena = Arena.ofShared()) {
            EngramLayout layout = new EngramLayout(16);
            MemorySegment seg = arena.allocate(layout.stride() * 2);
            StrengthMemory strength = StrengthMemory.heap(2, 2, 2);

            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<Void>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    try (var cursor = new DefaultHeaderCursor(seg, layout, MemoryType.SEMANTIC, 2, 0, strength)) {
                        cursor.seek(0);
                        for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                            // Non-atomic read-modify-write defect simulation
                            int cur = cursor.activationCount();
                            cursor.activationCount(cur + 1);
                        }
                    }
                    return null;
                }));
            }

            ready.await();
            start.countDown();

            for (Future<Void> f : futures) {
                f.get();
            }
            executor.shutdown();

            try (var verifyCursor = new DefaultHeaderCursor(seg, layout, MemoryType.SEMANTIC, 2, 0, strength)) {
                verifyCursor.seek(0);
                int actualCount = verifyCursor.activationCount();
                assertThat(actualCount)
                        .as("Plain read-modify-write must suffer lost updates under concurrency (demonstrating test efficacy)")
                        .isLessThan(TOTAL_EXPECTED);
            }
        }
    }
}
