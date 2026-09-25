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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Serializes {@link ScaleBenchmarkResult} into published Markdown tables and JSON representations.
 *
 * <p>Enforces Requirement R5.3: Results published with stated conditions (dataset size,
 * partition count, budget setting, hardware profile).
 */
public final class ScaleBenchmarkReportWriter {

    private ScaleBenchmarkReportWriter() {}

    /**
     * Formats multiple scale tier results into a comprehensive Markdown capacity report.
     */
    public static String toMarkdownReport(List<ScaleBenchmarkResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Single-Namespace Scale Benchmark Empirical Report\n\n");
        sb.append("> **Specification**: Milestone 6 (R6) / Requirements R5.1–R5.3\n");
        sb.append("> **Invariant V5**: Every published scale claim cites a measurement with stated conditions.\n\n");

        sb.append("## 1. Scale Tiers Empirical Matrix\n\n");
        sb.append("| Scale Tier | Engrams | Partitions | Cold Start (ms) | Recall p50 (ms) | Recall p99 (ms) | Budget (Visited / Skipped) | Graph ON p50 (ms) | Graph OFF p50 (ms) | Graph Δ (ms) | RSS (MB) |\n");
        sb.append("|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|\n");

        for (ScaleBenchmarkResult r : results) {
            String budgetStr = String.format("%d / %d", r.partitionsVisited(), r.partitionsSkipped());
            sb.append(String.format(
                    "| **%s** | %,d | %,d | %.2f | %.2f | %.2f | %s | %.2f | %.2f | %+.2f | %.1f |\n",
                    r.tier(),
                    r.engramCount(),
                    r.partitionCount(),
                    r.coldStartTimeMs(),
                    r.p50RecallLatencyMs(),
                    r.p99RecallLatencyMs(),
                    budgetStr,
                    r.p50LatencyGraphEnabledMs(),
                    r.p50LatencyGraphDisabledMs(),
                    r.graphExpansionDeltaMs(),
                    r.rssMemoryMb()
            ));
        }

        sb.append("\n## 2. Test Execution Conditions & Hardware Profile\n\n");
        if (!results.isEmpty()) {
            ScaleBenchmarkResult sample = results.get(0);
            sb.append("- **Hardware Profile**: `").append(sample.hardwareProfile()).append("`\n");
            sb.append("- **Execution Timestamp**: `").append(sample.timestamp()).append("`\n");
            sb.append("- **Partition Capacity Setting**: `").append(sample.partitionCapacity()).append("` engrams per bundle\n");
            sb.append("- **Default Visit Budget**: `").append(sample.visitBudget()).append("` partitions\n");
        }

        sb.append("\n## 3. Analysis & Key Invariants\n\n");
        sb.append("- **Cold Start O(partitions)**: Reopen time is governed strictly by reading the 64-byte `PartitionSummary` header at offset 512, with zero payload scans.\n");
        sb.append("- **Visit Budget Effectiveness**: Recall query fan-out is bounded to the configured visit budget, truncating older candidate partitions recency-first.\n");
        sb.append("- **Hebbian Graph Expansion**: Traversal overhead delta is explicitly measured between enabled and disabled modes.\n");

        return sb.toString();
    }

    /**
     * Formats a single result as a JSON string.
     */
    public static String toJson(ScaleBenchmarkResult r) {
        return String.format(
                "{\n" +
                "  \"tier\": \"%s\",\n" +
                "  \"engramCount\": %d,\n" +
                "  \"partitionCount\": %d,\n" +
                "  \"partitionCapacity\": %d,\n" +
                "  \"coldStartTimeMs\": %.3f,\n" +
                "  \"p50RecallLatencyMs\": %.3f,\n" +
                "  \"p99RecallLatencyMs\": %.3f,\n" +
                "  \"avgRecallLatencyMs\": %.3f,\n" +
                "  \"partitionsVisited\": %d,\n" +
                "  \"partitionsSkipped\": %d,\n" +
                "  \"partitionsBudgeted\": %d,\n" +
                "  \"visitBudget\": %d,\n" +
                "  \"truncated\": %b,\n" +
                "  \"p50LatencyGraphEnabledMs\": %.3f,\n" +
                "  \"p99LatencyGraphEnabledMs\": %.3f,\n" +
                "  \"p50LatencyGraphDisabledMs\": %.3f,\n" +
                "  \"p99LatencyGraphDisabledMs\": %.3f,\n" +
                "  \"graphExpansionDeltaMs\": %.3f,\n" +
                "  \"rssMemoryMb\": %.2f,\n" +
                "  \"heapMemoryMb\": %.2f,\n" +
                "  \"diskFootprintMb\": %.2f,\n" +
                "  \"hardwareProfile\": \"%s\",\n" +
                "  \"timestamp\": \"%s\"\n" +
                "}",
                r.tier(),
                r.engramCount(),
                r.partitionCount(),
                r.partitionCapacity(),
                r.coldStartTimeMs(),
                r.p50RecallLatencyMs(),
                r.p99RecallLatencyMs(),
                r.avgRecallLatencyMs(),
                r.partitionsVisited(),
                r.partitionsSkipped(),
                r.partitionsBudgeted(),
                r.visitBudget(),
                r.truncated(),
                r.p50LatencyGraphEnabledMs(),
                r.p99LatencyGraphEnabledMs(),
                r.p50LatencyGraphDisabledMs(),
                r.p99LatencyGraphDisabledMs(),
                r.graphExpansionDeltaMs(),
                r.rssMemoryMb(),
                r.heapMemoryMb(),
                r.diskFootprintMb(),
                r.hardwareProfile().replace("\"", "\\\""),
                r.timestamp()
        );
    }

    /**
     * Writes markdown report to specified path.
     */
    public static void writeMarkdownToFile(List<ScaleBenchmarkResult> results, Path filePath) throws IOException {
        String md = toMarkdownReport(results);
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.writeString(filePath, md);
    }
}
