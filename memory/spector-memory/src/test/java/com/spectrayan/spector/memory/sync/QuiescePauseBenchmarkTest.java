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
package com.spectrayan.spector.memory.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Quiesce Pause Benchmark & Concurrency Validation (ADR-0034 §4.1)")
class QuiescePauseBenchmarkTest {

    private static final int NUM_WRITER_THREADS = 8;
    private static final int WARMUP_WRITES_PER_THREAD = 1000;
    private static final int MEASURED_WRITES_PER_THREAD = 5000;

    @Test
    @DisplayName("Measure writer pause duration during snapshot quiesce window")
    void measureWriterPauseDuringQuiesce() throws Exception {
        QuiesceGuard guard = new QuiesceGuard();
        ExecutorService writerPool = Executors.newFixedThreadPool(NUM_WRITER_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);
        List<Long> writerLatenciesNanos = Collections.synchronizedList(new ArrayList<>());
        AtomicLong totalWrites = new AtomicLong();

        // 1. Warm-up
        for (int i = 0; i < NUM_WRITER_THREADS; i++) {
            writerPool.submit(() -> {
                for (int w = 0; w < WARMUP_WRITES_PER_THREAD; w++) {
                    try (QuiesceGuard.Permit permit = guard.acquireWritePermit()) {
                        totalWrites.incrementAndGet();
                    }
                }
            });
        }

        // Wait for warmup to complete
        Thread.sleep(100);

        // 2. Launch concurrent measured writers
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < NUM_WRITER_THREADS; i++) {
            futures.add(writerPool.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {}

                while (running.get()) {
                    long start = System.nanoTime();
                    try (QuiesceGuard.Permit permit = guard.acquireWritePermit()) {
                        long elapsed = System.nanoTime() - start;
                        writerLatenciesNanos.add(elapsed);
                        totalWrites.incrementAndGet();
                    }
                }
            }));
        }

        // Unblock writers
        startLatch.countDown();

        // Let writers run for 50ms before triggering quiesce
        Thread.sleep(50);

        // 3. Trigger Quiesce (simulating atomic snapshot cut of live mmap bundle)
        long quiesceTriggerTime = System.nanoTime();
        long recordedQuiesceDurationNanos;
        try (QuiesceGuard.Permit quiesce = guard.acquireQuiesce(500, TimeUnit.MILLISECONDS)) {
            // Hold quiesce for 3ms to simulate bundle state capture and WAL HWM sync
            Thread.sleep(3);
        }
        recordedQuiesceDurationNanos = guard.lastQuiesceDurationNanos();

        // Let writers run for another 50ms post-quiesce
        Thread.sleep(50);
        running.set(false);

        for (Future<?> f : futures) {
            f.get(2, TimeUnit.SECONDS);
        }
        writerPool.shutdown();

        // 4. Calculate statistics
        List<Long> latencies;
        synchronized (writerLatenciesNanos) {
            latencies = new ArrayList<>(writerLatenciesNanos);
        }
        Collections.sort(latencies);

        double p50Micros = latencies.get((int) (latencies.size() * 0.50)) / 1_000.0;
        double p95Micros = latencies.get((int) (latencies.size() * 0.95)) / 1_000.0;
        double p99Micros = latencies.get((int) (latencies.size() * 0.99)) / 1_000.0;
        double maxPauseMicros = latencies.get(latencies.size() - 1) / 1_000.0;
        double quiesceDurationMillis = recordedQuiesceDurationNanos / 1_000_000.0;

        System.out.println("=================================================================");
        System.out.println(" QUIESCE PAUSE EMPIRICAL BENCHMARK REPORT (ADR-0034)");
        System.out.println(" Total Writes Processed:     " + totalWrites.get());
        System.out.println(" Quiesce Window Duration:    " + String.format("%.3f", quiesceDurationMillis) + " ms");
        System.out.println(" Writer Latency p50:         " + String.format("%.2f", p50Micros) + " µs");
        System.out.println(" Writer Latency p95:         " + String.format("%.2f", p95Micros) + " µs");
        System.out.println(" Writer Latency p99:         " + String.format("%.2f", p99Micros) + " µs");
        System.out.println(" Max Observed Writer Pause:  " + String.format("%.2f", maxPauseMicros) + " µs (" + String.format("%.3f", maxPauseMicros / 1000.0) + " ms)");
        System.out.println("=================================================================");

        // Invariants:
        // 1. Quiesce window duration was recorded accurately
        assertThat(recordedQuiesceDurationNanos).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(3));
        // 2. Writers continued to make progress (thousands of operations)
        assertThat(totalWrites.get()).isGreaterThan(1000L);
        // 3. Pause was bounded within 100ms budget under standard test storage conditions
        assertThat(maxPauseMicros / 1000.0).isLessThan(100.0);
    }
}
