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

/**
 * Empirical metrics resulting from a single-namespace scale benchmark execution.
 *
 * <p>Enforces Requirement R5 / R6 metrics:
 * <ul>
 *   <li>p50 and p99 recall latency under post-pruning visit budget</li>
 *   <li>Partition count, partitions visited vs skipped (demonstrating visit budget effectiveness)</li>
 *   <li>Cold-start time (time to first query after engine restart, validating O(partitions) cold start)</li>
 *   <li>Process RSS and off-heap/heap memory consumption</li>
 *   <li>Recall latency comparison with Hebbian graph expansion enabled vs disabled</li>
 * </ul>
 * </p>
 *
 * @param tier                       scale tier identifier (e.g., "100k", "1M", "10M", "smoke")
 * @param engramCount                total synthetic engrams in the single namespace
 * @param partitionCount             total partitions active and frozen in the namespace
 * @param partitionCapacity          target engrams per partition chunk (roll threshold)
 * @param coldHeaderScanMs           duration of pure O(partitions) partition bundle header seek and read (ms)
 * @param coldStartTimeMs            duration to reopen engine from disk and execute first query
 * @param p50RecallLatencyMs         50th percentile recall latency with visit budget (ms)
 * @param p99RecallLatencyMs         99th percentile recall latency with visit budget (ms)
 * @param avgRecallLatencyMs         mean recall latency with visit budget (ms)
 * @param partitionsVisited          partitions visited during budgeted recall scan
 * @param partitionsSkipped          partitions pruned or skipped due to temporal gating and visit budget
 * @param partitionsBudgeted         number of candidate partitions dropped by visit budget cap
 * @param visitBudget                configured partition visit budget cap (0 = unbounded)
 * @param truncated                  whether recall candidate stream was truncated by budget
 * @param p50LatencyGraphEnabledMs   p50 recall latency with Hebbian graph expansion enabled (ms)
 * @param p99LatencyGraphEnabledMs   p99 recall latency with Hebbian graph expansion enabled (ms)
 * @param p50LatencyGraphDisabledMs  p50 recall latency with Hebbian graph expansion disabled (ms)
 * @param p99LatencyGraphDisabledMs  p99 recall latency with Hebbian graph expansion disabled (ms)
 * @param graphExpansionDeltaMs      latency overhead delta of Hebbian graph expansion (ms)
 * @param rssMemoryMb                resident set size of process (MB)
 * @param heapMemoryMb               JVM heap memory utilized (MB)
 * @param diskFootprintMb            total disk storage consumed by partition bundles (MB)
 * @param hardwareProfile            host hardware and JVM execution environment
 * @param timestamp                  UTC ISO-8601 execution timestamp
 */
public record ScaleBenchmarkResult(
        String tier,
        long engramCount,
        int partitionCount,
        int partitionCapacity,
        double coldHeaderScanMs,
        double coldStartTimeMs,
        double p50RecallLatencyMs,
        double p99RecallLatencyMs,
        double avgRecallLatencyMs,
        int partitionsVisited,
        int partitionsSkipped,
        int partitionsBudgeted,
        int visitBudget,
        boolean truncated,
        double p50LatencyGraphEnabledMs,
        double p99LatencyGraphEnabledMs,
        double p50LatencyGraphDisabledMs,
        double p99LatencyGraphDisabledMs,
        double graphExpansionDeltaMs,
        double rssMemoryMb,
        double heapMemoryMb,
        double diskFootprintMb,
        String hardwareProfile,
        String timestamp
) {
    /**
     * Backward-compatible 23-parameter constructor defaulting {@code coldHeaderScanMs} to 0.0.
     */
    public ScaleBenchmarkResult(
            String tier,
            long engramCount,
            int partitionCount,
            int partitionCapacity,
            double coldStartTimeMs,
            double p50RecallLatencyMs,
            double p99RecallLatencyMs,
            double avgRecallLatencyMs,
            int partitionsVisited,
            int partitionsSkipped,
            int partitionsBudgeted,
            int visitBudget,
            boolean truncated,
            double p50LatencyGraphEnabledMs,
            double p99LatencyGraphEnabledMs,
            double p50LatencyGraphDisabledMs,
            double p99LatencyGraphDisabledMs,
            double graphExpansionDeltaMs,
            double rssMemoryMb,
            double heapMemoryMb,
            double diskFootprintMb,
            String hardwareProfile,
            String timestamp
    ) {
        this(tier, engramCount, partitionCount, partitionCapacity,
             0.0,
             coldStartTimeMs, p50RecallLatencyMs, p99RecallLatencyMs, avgRecallLatencyMs,
             partitionsVisited, partitionsSkipped, partitionsBudgeted, visitBudget, truncated,
             p50LatencyGraphEnabledMs, p99LatencyGraphEnabledMs, p50LatencyGraphDisabledMs,
             p99LatencyGraphDisabledMs, graphExpansionDeltaMs, rssMemoryMb, heapMemoryMb,
             diskFootprintMb, hardwareProfile, timestamp);
    }
}
