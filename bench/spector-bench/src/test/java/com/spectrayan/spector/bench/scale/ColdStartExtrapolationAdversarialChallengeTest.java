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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Adversarial challenge suite for Milestone 6 Iteration 2:
 * 1. Cold-start scale extrapolation affine model correctness: O(1) baseline + O(partitions) header scan.
 * 2. Pure bundle header scanning isolation in Phase 2 and ScaleBenchmarkResult.
 * 3. Complete elimination of internal numerical contradictions across capacity documentation.
 */
class ColdStartExtrapolationAdversarialChallengeTest {

    @Test
    @DisplayName("Adversarial: 10M cold-start extrapolation projects to ~5.3 seconds, rejecting 7.76 minutes")
    void adversarialVerify10MColdStartProjectsToApprox5Point3SecondsNot7Point76Minutes() {
        // Baseline 100k engrams measured on disk (11 partitions)
        ScaleBenchmarkResult baseline100k = new ScaleBenchmarkResult(
                "100k",
                100_000L,
                11,
                10_000,
                1.96,       // coldHeaderScanMs
                5_122.61,   // coldStartTimeMs
                43.70,
                78.83,
                46.10,
                10,
                1,
                0,
                10,
                false,
                44.66,
                80.20,
                39.12,
                72.10,
                5.53,
                1062.5,
                480.0,
                85.0,
                "macOS aarch64",
                "2026-09-24T20:00:00Z"
        );

        ScaleBenchmarkResult result1M = SingleNamespaceScaleBenchmark.extrapolateScale(
                baseline100k, "1M", 1_000_000L, 10_000);
        ScaleBenchmarkResult result10M = SingleNamespaceScaleBenchmark.extrapolateScale(
                baseline100k, "10M", 10_000_000L, 10_000);

        // 1. Verify 1M extrapolation
        assertThat(result1M.partitionCount()).isEqualTo(100);
        // Header scan rate = 1.96 / 11 = 0.17818 ms/partition -> 100 * 0.17818 = ~17.82 ms
        assertThat(result1M.coldHeaderScanMs()).isCloseTo(17.82, within(0.1));
        // Cold start = (5122.61 - 1.96) + 17.82 = 5120.65 + 17.82 = ~5138.47 ms (~5.14 seconds)
        assertThat(result1M.coldStartTimeMs()).isCloseTo(5138.47, within(0.5));
        assertThat(result1M.coldStartTimeMs()).isLessThan(10_000.0); // Bounded under 10 seconds

        // 2. Verify 10M extrapolation
        assertThat(result10M.partitionCount()).isEqualTo(1_000);
        // Header scan = 1,000 * 0.17818 = ~178.18 ms
        assertThat(result10M.coldHeaderScanMs()).isCloseTo(178.18, within(0.5));
        // Cold start = 5120.65 + 178.18 = ~5298.83 ms (~5.30 seconds)
        assertThat(result10M.coldStartTimeMs()).isCloseTo(5298.83, within(1.0));

        // CRITICAL CHECK: Reject the previous flawed 7.76-minute (465,691 ms) linear model
        assertThat(result10M.coldStartTimeMs())
                .as("10M cold start must project to ~5.3 seconds (~5,298 ms), NOT 7.76 minutes (465,691 ms)")
                .isLessThan(6_000.0);
        assertThat(result10M.coldStartTimeMs()).isNotEqualTo(465_691.72);
    }

    @Test
    @DisplayName("Adversarial: Pure bundle header scanning rate is isolated and measured in Phase 2 and ScaleBenchmarkResult")
    void adversarialVerifyPureBundleHeaderScanningRateIsIsolated() {
        ScaleBenchmarkResult result = new ScaleBenchmarkResult(
                "smoke",
                500L,
                6,
                100,
                32.50, // coldHeaderScanMs
                78.20, // coldStartTimeMs
                1.50,
                3.20,
                1.80,
                2,
                4,
                4,
                2,
                true,
                3.10,
                5.00,
                1.60,
                3.40,
                1.50,
                220.0,
                90.0,
                12.0,
                "Test HW",
                "2026-09-24T20:00:00Z"
        );

        // Header scan is strictly positive and distinct from total cold start
        assertThat(result.coldHeaderScanMs()).isEqualTo(32.50);
        assertThat(result.coldStartTimeMs()).isEqualTo(78.20);
        assertThat(result.coldStartTimeMs()).isGreaterThan(result.coldHeaderScanMs());

        // Verify Markdown report writer output includes Header Scan column
        String md = ScaleBenchmarkReportWriter.toMarkdownReport(List.of(result));
        assertThat(md).contains("| Header Scan (ms) |")
                .contains("| 32.50 | 78.20 |");

        // Verify JSON serialization includes coldHeaderScanMs
        String json = ScaleBenchmarkReportWriter.toJson(result);
        assertThat(json).contains("\"coldHeaderScanMs\": 32.500")
                .contains("\"coldStartTimeMs\": 78.200");
    }

    @Test
    @DisplayName("Adversarial: Extrapolation handles fallback branches when coldHeaderScanMs is zero")
    void adversarialVerifyExtrapolationFallbackBranches() {
        // Branch A: coldHeaderScanMs == 0.0 and coldStartTimeMs <= 100.0 (synthetic/test fixture)
        ScaleBenchmarkResult syntheticBaseline = new ScaleBenchmarkResult(
                "synthetic",
                10_000L,
                10,
                1_000,
                0.0,    // unpopulated
                5.0,    // 5 ms total for 10 partitions (0.5 ms/partition)
                1.0, 2.0, 1.2,
                5, 5, 5, 5, true,
                1.2, 2.2, 1.0, 2.0, 0.2,
                100.0, 50.0, 10.0,
                "HW", "2026-09-24T20:00:00Z"
        );
        ScaleBenchmarkResult extSynthetic = SingleNamespaceScaleBenchmark.extrapolateScale(
                syntheticBaseline, "100k", 100_000L, 1_000);
        assertThat(extSynthetic.partitionCount()).isEqualTo(100);
        assertThat(extSynthetic.coldHeaderScanMs()).isCloseTo(50.0, within(0.01));
        assertThat(extSynthetic.coldStartTimeMs()).isCloseTo(50.0, within(0.01));

        // Branch B: coldHeaderScanMs == 0.0 and coldStartTimeMs > 100.0 (legacy/unrecorded header scan)
        ScaleBenchmarkResult legacyBaseline = new ScaleBenchmarkResult(
                "legacy",
                100_000L,
                10,
                10_000,
                0.0,        // unrecorded
                5000.0,     // 5s engine startup
                2.0, 4.0, 2.5,
                10, 0, 0, 10, false,
                2.5, 4.5, 2.0, 4.0, 0.5,
                200.0, 100.0, 50.0,
                "HW", "2026-09-24T20:00:00Z"
        );
        ScaleBenchmarkResult extLegacy10M = SingleNamespaceScaleBenchmark.extrapolateScale(
                legacyBaseline, "10M", 10_000_000L, 10_000);
        // Fallback uses 0.20 ms/partition NVMe rate
        assertThat(extLegacy10M.partitionCount()).isEqualTo(1_000);
        assertThat(extLegacy10M.coldHeaderScanMs()).isCloseTo(200.0, within(0.01));
        // Cold start = 5000.0 + 200.0 = 5200.0 ms (~5.2 seconds, NOT 500 seconds!)
        assertThat(extLegacy10M.coldStartTimeMs()).isCloseTo(5200.0, within(0.01));
    }

    @Test
    @DisplayName("Adversarial: Extreme scale (100M engrams, 10,000 partitions) exhibits affine sub-10s cold start")
    void adversarialVerifyExtremeScaleAffineProperty() {
        ScaleBenchmarkResult baseline100k = new ScaleBenchmarkResult(
                "100k",
                100_000L,
                11,
                10_000,
                1.96,
                5_122.61,
                43.70, 78.83, 46.10,
                10, 1, 0, 10, false,
                44.66, 80.20, 39.12, 72.10, 5.53,
                1062.5, 480.0, 85.0,
                "macOS aarch64", "2026-09-24T20:00:00Z"
        );

        // Extrapolate to 100,000,000 engrams (10,000 partitions)
        ScaleBenchmarkResult result100M = SingleNamespaceScaleBenchmark.extrapolateScale(
                baseline100k, "100M", 100_000_000L, 10_000);

        assertThat(result100M.partitionCount()).isEqualTo(10_000);
        // Header scan for 10,000 partitions: 10,000 * (1.96 / 11) = ~1781.82 ms (~1.78s)
        assertThat(result100M.coldHeaderScanMs()).isCloseTo(1781.82, within(1.0));
        // Total cold start = 5120.65 + 1781.82 = ~6902.47 ms (~6.90 seconds)
        assertThat(result100M.coldStartTimeMs()).isCloseTo(6902.47, within(2.0));
        assertThat(result100M.coldStartTimeMs()).isLessThan(10_000.0);
    }

    @Test
    @DisplayName("Adversarial: Capacity documentation has ZERO internal numerical contradictions")
    void adversarialVerifyCapacityDocumentationZeroNumericalContradictions() throws IOException {
        Path root = Paths.get("../../").toAbsolutePath().normalize();
        Path singleNamespaceDoc = root.resolve("docs/capacity/single-namespace-scale.md");
        Path resultsDoc = root.resolve("docs/capacity/scale-benchmark-results.md");

        assertThat(singleNamespaceDoc).exists();
        assertThat(resultsDoc).exists();

        String singleNamespaceText = Files.readString(singleNamespaceDoc);
        String resultsText = Files.readString(resultsDoc);

        // 1. Prohibit all obsolete fabricated figures and contradiction markers
        assertThat(singleNamespaceText)
                .as("Obsolete 465,691 ms linear cold-start figure must be purged")
                .doesNotContain("465,691")
                .doesNotContain("465691")
                .as("Obsolete +0.33 ms graph delta must be purged")
                .doesNotContain("+0.33 ms to +0.60 ms")
                .doesNotContain("+0.33 ms")
                .as("Obsolete 'Cold Start Time (ms)' chart title must be 'Partition Header Scan Time (ms)'")
                .doesNotContain("Cold Start Time (ms)\n  200")
                .as("Section 3.1 must not claim un-partitioned 465s cold start")
                .doesNotContain("7.76 minutes");

        assertThat(resultsText)
                .as("Results doc must not contain 465691")
                .doesNotContain("465691")
                .doesNotContain("465,691");

        // 2. Verify Table 2 figures are synchronized across both docs
        // 100k
        assertThat(singleNamespaceText).contains("| **100k** | 100,000 | 11 | 1.96 | 5,122.61 | 43.70 | 78.83 | 10 / 1 | 44.66 | 39.12 | +5.53 | 1,062.5 |");
        assertThat(resultsText).contains("| **100k** | 100,000 | 11 | 1.96 | 5122.61 | 43.70 | 78.83 | 10 / 1 | 44.66 | 39.12 | +5.53 | 1062.5 |");

        // 1M
        assertThat(singleNamespaceText).contains("| **1M** | 1,000,000 | 100 | 17.80 | 5,138.45 | 50.25 | 90.66 | 10 / 90 | 51.36 | 44.99 | +6.36 | 1,212.5 |");
        assertThat(resultsText).contains("| **1M** | 1,000,000 | 100 | 17.80 | 5138.45 | 50.25 | 90.66 | 10 / 90 | 51.36 | 44.99 | +6.36 | 1212.5 |");

        // 10M
        assertThat(singleNamespaceText).contains("| **10M** | 10,000,000 | 1,000 | 178.00 | 5,298.65 | 56.81 | 102.48 | 10 / 990 | 58.05 | 50.86 | +7.19 | 1,362.5 |");
        assertThat(resultsText).contains("| **10M** | 10,000,000 | 1,000 | 178.00 | 5298.65 | 56.81 | 102.48 | 10 / 990 | 58.05 | 50.86 | +7.19 | 1362.5 |");

        // 3. Verify Section 3.1 narrative & ASCII diagram
        assertThat(singleNamespaceText)
                .contains("Partition Bundle Header Scan Complexity**: Strict $O(\\text{partitions})$ at ~0.178 ms/partition")
                .contains("At 1,000 partitions (10M memories), bundle header scanning completes in ~178 ms.")
                .contains("Total cold start is **5,122.61 ms** at 100k, **5,138.45 ms (~5.14 s)** at 1M, and **5,298.65 ms (~5.30 s)** at 10M")
                .contains("Partition Header Scan Time (ms)")
                .contains("● 10M (178ms)");

        // 4. Verify Section 3.3 recall latency narrative matches Table 2 exactly
        assertThat(singleNamespaceText)
                .contains("Recall p50 latency scales logarithmically from **43.70 ms** at 100k engrams (11 partitions) to **50.25 ms** at 1M engrams (100 partitions) and **56.81 ms** at 10M engrams (1,000 partitions)")
                .contains("Recall p99 latency remains bounded between **78.83 ms** (100k) and **102.48 ms** (10M)");

        // 5. Verify Section 4.3 graph delta narrative matches Table 2 exactly
        assertThat(singleNamespaceText)
                .contains("multi-hop graph associative traversal adds an average of **+5.53 ms to +7.19 ms** of latency overhead across all scale tiers (+5.53 ms at 100k, +6.36 ms at 1M, +7.19 ms at 10M)")
                .contains("incremental overhead of only ~12–14% relative to base recall latency");
    }
}
