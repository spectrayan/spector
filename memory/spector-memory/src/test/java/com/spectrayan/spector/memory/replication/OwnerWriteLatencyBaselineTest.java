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

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Establishes baseline owner write latency prior to snapshot replication
 * (ADR-0034 §5 owner impact, Req R3.5, Task 0.5).
 */
class OwnerWriteLatencyBaselineTest {

    private static final Logger log = LoggerFactory.getLogger(OwnerWriteLatencyBaselineTest.class);

    @Test
    @DisplayName("Baselines owner write latency under typical write throughput")
    void testBaselineOwnerWriteLatency(@TempDir Path tempDir) {
        FakeEmbeddingProvider embedProvider = new FakeEmbeddingProvider();
        MemoryProperties memProps = new MemoryProperties()
                .setDimensions(embedProvider.dimensions())
                .setWorkingCapacity(100)
                .setEpisodicPartitionCapacity(1000)
                .setSemanticCapacity(500)
                .setProceduralCapacity(100);

        try (SpectorMemory memory = DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(embedProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {

            // Warmup
            for (int i = 0; i < 50; i++) {
                memory.remember("warmup-" + i, "Warmup memory text " + i,
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "tag1");
            }

            // Measured benchmark
            int iterations = 300;
            long[] durationsNanos = new long[iterations];

            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                memory.remember("bench-" + i, "Benchmarked owner memory content iteration " + i,
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "benchmark");
                durationsNanos[i] = System.nanoTime() - start;
            }

            Arrays.sort(durationsNanos);
            double meanMs = Arrays.stream(durationsNanos).average().orElse(0) / 1_000_000.0;
            double p50Ms = durationsNanos[iterations / 2] / 1_000_000.0;
            double p99Ms = durationsNanos[(int) (iterations * 0.99)] / 1_000_000.0;

            log.info("Owner write latency baseline: mean={:.3f}ms, p50={:.3f}ms, p99={:.3f}ms (over {} ops)",
                    meanMs, p50Ms, p99Ms, iterations);

            // Baseline healthy budget: mean latency must be under 20ms in-memory
            assertThat(meanMs)
                    .as("Mean write latency must be within reasonable baseline budget")
                    .isLessThan(20.0);
            assertThat(p99Ms)
                    .as("P99 write latency must be bounded")
                    .isLessThan(50.0);
        }
    }
}
