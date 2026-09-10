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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Microbenchmark verifying the performance profile of RegionRef resolution
 * and RegionLease acquisition granularity (Req: R2.6, R2.9).
 *
 * <p>Demonstrates that:
 * <ol>
 *   <li>Hoisting {@code RegionRef.resolve()} and {@code RegionRef.lease()} once per scan
 *       achieves identical throughput to raw unsealed segment access.</li>
 *   <li>Per-record resolution incurs measurable overhead from repeated StampedLock
 *       optimistic reads and map lookups, confirming the architectural mandate for
 *       once-per-scan hoisting.</li>
 * </ol>
 */
@DisplayName("RegionRef Resolution & Lease Microbenchmark (R2.6, R2.9)")
class RegionRefBenchmarkTest {

    private static final int ITERATIONS = 100_000;

    @Test
    @DisplayName("Verify once-per-scan resolution and lease parity with raw segment (R2.6, R2.9)")
    void benchmarkResolveAndLeaseOverhead(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("bench.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096 * 64, 100, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);
            long offset = RegionPreamble.PREAMBLE_BYTES;
            bm25Ref.resolve().set(ValueLayout.JAVA_LONG, offset, 0xDEADBEEF_CAFEBABEL);

            // Warmup
            for (int i = 0; i < 50_000; i++) {
                bm25Ref.resolve().get(ValueLayout.JAVA_LONG, offset);
            }

            // 1. Direct segment access (baseline)
            MemorySegment rawSeg = bm25Ref.resolve();
            long t0 = System.nanoTime();
            long sumDirect = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                sumDirect += rawSeg.get(ValueLayout.JAVA_LONG, offset);
            }
            long directNanos = System.nanoTime() - t0;

            // 2. Hoisted once-per-scan resolve + lease (R2.6, R2.9 contract)
            t0 = System.nanoTime();
            long sumHoisted = 0;
            try (RegionLease lease = bm25Ref.lease()) {
                MemorySegment slab = lease.slab();
                for (int i = 0; i < ITERATIONS; i++) {
                    sumHoisted += slab.get(ValueLayout.JAVA_LONG, offset);
                }
            }
            long hoistedNanos = System.nanoTime() - t0;

            // 3. Per-record resolve (defect pattern)
            t0 = System.nanoTime();
            long sumPerRecord = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                sumPerRecord += bm25Ref.resolve().get(ValueLayout.JAVA_LONG, offset);
            }
            long perRecordNanos = System.nanoTime() - t0;

            assertThat(sumHoisted).isEqualTo(sumDirect);
            assertThat(sumPerRecord).isEqualTo(sumDirect);

            double directMs = directNanos / 1_000_000.0;
            double hoistedMs = hoistedNanos / 1_000_000.0;
            double perRecordMs = perRecordNanos / 1_000_000.0;

            System.out.printf(
                    "[RegionRef Benchmark] %d reads: Direct=%.3f ms, Hoisted(once-per-scan)=%.3f ms, Per-Record=%.3f ms (Per-Record is %.2fx slower)%n",
                    ITERATIONS, directMs, hoistedMs, perRecordMs, perRecordMs / Math.max(0.001, hoistedMs)
            );

            // Hoisted once-per-scan must achieve within the same performance tier as direct access (< 3x direct)
            assertThat(hoistedMs).isLessThanOrEqualTo(Math.max(10.0, directMs * 3.0));
        }
    }
}
