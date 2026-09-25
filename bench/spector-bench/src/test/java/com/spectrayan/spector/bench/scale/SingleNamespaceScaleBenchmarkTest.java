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
package com.spectrayan.spector.bench.scale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verification test suite for Single-Namespace Scale Benchmark (Milestone 6 / Requirement R6).
 *
 * <p>Validates:
 * <ul>
 *   <li>Synthetic engram generator determinism and temporal spread</li>
 *   <li>Percentile calculation exactness for p50, p95, p99 latencies</li>
 *   <li>Process memory probe RSS and heap telemetry</li>
 *   <li>Report writer Markdown and JSON serialization compliance</li>
 *   <li>Cold start, recall visit budget, and Hebbian graph expansion end-to-end execution</li>
 * </ul>
 * </p>
 */
@DisplayName("SingleNamespaceScaleBenchmarkTest — Scale Benchmark Suite Verification (R6)")
class SingleNamespaceScaleBenchmarkTest {

    @Test
    @DisplayName("1. Synthetic engram generator produces deterministic engrams with monotonically increasing timestamps")
    void testSyntheticEngramGenerator() {
        EngramScaleGenerator generator = new EngramScaleGenerator(12345L, 1_000_000L, 5_000L);

        EngramScaleGenerator.GeneratedEngram e0 = generator.generate(0);
        EngramScaleGenerator.GeneratedEngram e1 = generator.generate(1);
        EngramScaleGenerator.GeneratedEngram e2 = generator.generate(2);

        assertThat(e0.id()).isEqualTo("scale-engram-00000000");
        assertThat(e1.id()).isEqualTo("scale-engram-00000001");
        assertThat(e2.id()).isEqualTo("scale-engram-00000002");

        assertThat(e0.timestampMs()).isEqualTo(1_000_000L);
        assertThat(e1.timestampMs()).isEqualTo(1_005_000L);
        assertThat(e2.timestampMs()).isEqualTo(1_010_000L);

        assertThat(e1.timestampMs()).isGreaterThan(e0.timestampMs());
        assertThat(e2.timestampMs()).isGreaterThan(e1.timestampMs());

        assertThat(e0.tags()).isNotEmpty();
        assertThat(e0.importance()).isBetween(1.0f, 10.0f);
        assertThat(e0.text()).isNotBlank();
    }

    @Test
    @DisplayName("2. PercentileTracker correctly calculates min, max, avg, p50, p95, and p99")
    void testPercentileTracker() {
        PercentileTracker tracker = new PercentileTracker();
        for (int i = 1; i <= 100; i++) {
            tracker.record(i);
        }

        assertThat(tracker.count()).isEqualTo(100);
        assertThat(tracker.min()).isEqualTo(1.0);
        assertThat(tracker.max()).isEqualTo(100.0);
        assertThat(tracker.avg()).isCloseTo(50.5, within(0.001));
        assertThat(tracker.p50()).isEqualTo(50.0);
        assertThat(tracker.p95()).isEqualTo(95.0);
        assertThat(tracker.p99()).isEqualTo(99.0);
    }

    @Test
    @DisplayName("3. ProcessMemoryProbe queries non-zero RSS, heap, and hardware profile")
    void testProcessMemoryProbe() {
        double rssMb = ProcessMemoryProbe.getProcessRssMb();
        double heapMb = ProcessMemoryProbe.getUsedHeapMb();
        String hw = ProcessMemoryProbe.getHardwareProfile();

        assertThat(rssMb).isGreaterThan(0.0);
        assertThat(heapMb).isGreaterThan(0.0);
        assertThat(hw).contains("CPU cores").contains("Java");
    }

    @Test
    @DisplayName("4. ScaleBenchmarkReportWriter generates compliant Markdown report and JSON payload")
    void testReportWriter() {
        ScaleBenchmarkResult mockResult = new ScaleBenchmarkResult(
                "100k",
                100_000L,
                10,
                10_000,
                4.25,
                1.85,
                3.40,
                2.05,
                5,
                5,
                2,
                5,
                true,
                2.10,
                4.15,
                1.85,
                3.40,
                0.25,
                185.0,
                92.0,
                64.5,
                "macOS aarch64, 10 cores, Java 25",
                "2026-09-24T20:00:00Z"
        );

        String md = ScaleBenchmarkReportWriter.toMarkdownReport(List.of(mockResult));
        assertThat(md).contains("# Single-Namespace Scale Benchmark Empirical Report")
                .contains("Invariant V5")
                .contains("| **100k** |")
                .contains("100,000")
                .contains("macOS aarch64");

        String json = ScaleBenchmarkReportWriter.toJson(mockResult);
        assertThat(json).contains("\"tier\": \"100k\"")
                .contains("\"engramCount\": 100000")
                .contains("\"coldStartTimeMs\": 4.250")
                .contains("\"truncated\": true");
    }

    @Test
    @DisplayName("5. Scale extrapolation models linear O(partitions) cold start and bounded visit budget")
    void testScaleExtrapolation() {
        ScaleBenchmarkResult baseline = new ScaleBenchmarkResult(
                "100k",
                100_000L,
                10,
                10_000,
                5.0, // 0.5 ms per partition
                2.0,
                4.0,
                2.2,
                10,
                0,
                0,
                10,
                false,
                2.5,
                4.8,
                2.0,
                4.0,
                0.5,
                200.0,
                100.0,
                50.0,
                "Test HW",
                "2026-09-24T20:00:00Z"
        );

        ScaleBenchmarkResult result1M = SingleNamespaceScaleBenchmark.extrapolateScale(
                baseline, "1M", 1_000_000L, 10_000);

        assertThat(result1M.engramCount()).isEqualTo(1_000_000L);
        assertThat(result1M.partitionCount()).isEqualTo(100);
        // Cold start should scale linearly with partition count: 10x partitions = ~10x cold start
        assertThat(result1M.coldStartTimeMs()).isCloseTo(50.0, within(0.01));
        // Visited partitions must be bounded to the visit budget (10)
        assertThat(result1M.partitionsVisited()).isEqualTo(10);
        assertThat(result1M.partitionsSkipped()).isEqualTo(90);
        assertThat(result1M.partitionsBudgeted()).isEqualTo(90);
        assertThat(result1M.truncated()).isTrue();
    }

    @Test
    @DisplayName("6. End-to-end benchmark cycle with real on-disk persistence, partition roll, cold start, and recall budget")
    void testEndToEndScaleBenchmarkExecution(@TempDir Path tempDir) throws Exception {
        SingleNamespaceScaleBenchmark benchmark = new SingleNamespaceScaleBenchmark();

        // Run smoke tier: 300 engrams, partition capacity 100 (creates 3-4 partitions), visit budget 2
        SingleNamespaceScaleBenchmark.BenchmarkConfig config = new SingleNamespaceScaleBenchmark.BenchmarkConfig(
                "smoke-e2e",
                300L,
                100,
                32,
                10,
                2, // Visit budget cap = 2 partitions
                tempDir,
                false // Keep files for verification
        );

        ScaleBenchmarkResult result = benchmark.run(config);

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("smoke-e2e");
        assertThat(result.engramCount()).isEqualTo(300L);
        assertThat(result.partitionCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.coldStartTimeMs()).isGreaterThan(0.0);
        assertThat(result.p50RecallLatencyMs()).isGreaterThan(0.0);
        assertThat(result.p99RecallLatencyMs()).isGreaterThanOrEqualTo(result.p50RecallLatencyMs());

        // Verify visit budget effectiveness
        assertThat(result.visitBudget()).isEqualTo(2);
        assertThat(result.partitionsVisited()).isLessThanOrEqualTo(2);
        assertThat(result.partitionsSkipped()).isGreaterThanOrEqualTo(1);
        assertThat(result.truncated()).isTrue();

        // Verify Hebbian graph expansion measurement
        assertThat(result.p50LatencyGraphEnabledMs()).isGreaterThan(0.0);
        assertThat(result.p50LatencyGraphDisabledMs()).isGreaterThan(0.0);

        // Verify memory measurements
        assertThat(result.rssMemoryMb()).isGreaterThan(0.0);
        assertThat(result.diskFootprintMb()).isGreaterThan(0.0);
    }
}
