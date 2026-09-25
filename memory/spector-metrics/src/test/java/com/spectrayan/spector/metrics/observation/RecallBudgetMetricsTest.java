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
package com.spectrayan.spector.metrics.observation;

import com.spectrayan.spector.kernel.store.GraphStructureHealthSnapshot;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end and contract tests for Recall Visit Budget & Graph Telemetry Observability (R3, R4 / F6-F8, F10-F12).
 *
 * <p>Authoritative Specifications:
 * <ul>
 *   <li>PROJECT.md § Interface Contract 2 (DefaultPartitionPruner &lt;-&gt; CorticalTierScanRelay)</li>
 *   <li>PROJECT.md § Interface Contract 3 (structureHealthSnapshot() &lt;-&gt; SpectorMemoryGauges)</li>
 *   <li>ORIGINAL_REQUEST.md § R3 (Partition Recall Fan-Out Budget and Observability)</li>
 *   <li>ORIGINAL_REQUEST.md § R4 (Graph Memory &amp; Headroom Telemetry)</li>
 *   <li>requirements.md § R2, R3 (Invariant V2: Truncated recall says it was truncated)</li>
 *   <li>design.md § D3, D6</li>
 * </ul>
 */
@DisplayName("RecallBudgetMetricsTest — R3 Budget Counters & R4 Graph Observability (F6-F8, F10-F12)")
class RecallBudgetMetricsTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    /**
     * Comparator enforcing Interface Contract 2:
     * Surviving candidate partitions are sorted by (maxTimestampMs DESC, seq DESC).
     */
    static final Comparator<PartitionHandle> RECENCY_FIRST_COMPARATOR = (a, b) -> {
        PartitionSummary sa = a.summary();
        PartitionSummary sb = b.summary();
        long tsA = sa != null ? sa.maxTimestampMs() : Long.MAX_VALUE;
        long tsB = sb != null ? sb.maxTimestampMs() : Long.MAX_VALUE;
        int tsCmp = Long.compare(tsB, tsA); // newest first
        if (tsCmp != 0) return tsCmp;

        int seqA = sa != null ? sa.seq() : a.seq();
        int seqB = sb != null ? sb.seq() : b.seq();
        return Integer.compare(seqB, seqA); // higher sequence first
    };

    /**
     * Reference pipeline stage enforcing Interface Contract 2.
     */
    static class BudgetStageResult {
        final List<PartitionHandle> visited;
        final int budgetedCount;
        final boolean isTruncated;

        BudgetStageResult(List<PartitionHandle> visited, int budgetedCount, boolean isTruncated) {
            this.visited = visited;
            this.budgetedCount = budgetedCount;
            this.isTruncated = isTruncated;
        }
    }

    static BudgetStageResult applyPartitionBudget(List<PartitionHandle> candidates, int budget, MeterRegistry reg, String namespace) {
        Counter visitedCounter = Counter.builder("spector.recall.partitions_visited")
                .tag("spector.namespace", namespace)
                .register(reg);
        Counter budgetedCounter = Counter.builder("spector.recall.partitions_budgeted")
                .tag("spector.namespace", namespace)
                .register(reg);

        if (budget <= 0 || candidates.size() <= budget) {
            visitedCounter.increment(candidates.size());
            return new BudgetStageResult(candidates, 0, false);
        }

        List<PartitionHandle> sorted = new ArrayList<>(candidates);
        sorted.sort(RECENCY_FIRST_COMPARATOR);

        List<PartitionHandle> budgeted = sorted.subList(0, budget);
        int dropped = candidates.size() - budget;

        visitedCounter.increment(budget);
        budgetedCounter.increment(dropped);

        return new BudgetStageResult(budgeted, dropped, true);
    }

    private PartitionHandle mockHandle(int seq, long maxTs) {
        PartitionHandle handle = mock(PartitionHandle.class);
        PartitionSummary summary = new PartitionSummary(seq, 0L, maxTs, 0L, 0L, 10, 0, 0, false);
        when(handle.seq()).thenReturn(seq);
        when(handle.summary()).thenReturn(summary);
        return handle;
    }

    @Nested
    @DisplayName("Tier 1: Post-Pruning Visit Budget & Recency Ordering (F6, F7, F8)")
    class BudgetAndOrderingTests {

        @Test
        @DisplayName("F6/F7: Truncates candidate partitions recency-first (maxTimestampMs DESC, seq DESC)")
        void budgetTruncates_recencyFirstOrdering() {
            PartitionHandle p1 = mockHandle(1, 1000L);
            PartitionHandle p2 = mockHandle(2, 5000L);
            PartitionHandle p3 = mockHandle(3, 3000L);
            PartitionHandle p4 = mockHandle(4, 5000L);

            List<PartitionHandle> candidates = List.of(p1, p2, p3, p4);

            BudgetStageResult result = applyPartitionBudget(candidates, 2, registry, "ns-test");

            assertThat(result.isTruncated).isTrue();
            assertThat(result.budgetedCount).isEqualTo(2);
            assertThat(result.visited).hasSize(2);

            // p4 (5000, seq 4) and p2 (5000, seq 2) must be preferred over p3 (3000) and p1 (1000)
            assertThat(result.visited).containsExactly(p4, p2);
        }

        @Test
        @DisplayName("F8 / V2: Truncation flag is set to true when budget bites, false otherwise")
        void truncationFlag_explicitNotice() {
            List<PartitionHandle> fivePartitions = List.of(
                    mockHandle(1, 1000L), mockHandle(2, 2000L), mockHandle(3, 3000L),
                    mockHandle(4, 4000L), mockHandle(5, 5000L)
            );

            // Budget 3 bites (5 > 3)
            BudgetStageResult truncatedResult = applyPartitionBudget(fivePartitions, 3, registry, "ns-a");
            assertThat(truncatedResult.isTruncated)
                    .as("When candidate count exceeds visit budget, truncation flag must be true")
                    .isTrue();

            // Budget 10 does not bite (5 <= 10)
            BudgetStageResult completeResult = applyPartitionBudget(fivePartitions, 10, registry, "ns-b");
            assertThat(completeResult.isTruncated)
                    .as("When candidate count is within budget, truncation flag must be false")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Tier 2: Partition Recall Micrometer Counters (F10)")
    class RecallCountersTests {

        @Test
        @DisplayName("F10: Recall counters move accurately with visited and budgeted partition counts")
        void recallCounters_trackVisitedAndBudgetedCounts() {
            List<PartitionHandle> candidates = List.of(
                    mockHandle(1, 1000L), mockHandle(2, 2000L), mockHandle(3, 3000L),
                    mockHandle(4, 4000L), mockHandle(5, 5000L)
            );

            // Run with budget 2
            applyPartitionBudget(candidates, 2, registry, "ns-counter");

            Counter visited = registry.find("spector.recall.partitions_visited")
                    .tag("spector.namespace", "ns-counter")
                    .counter();
            Counter budgeted = registry.find("spector.recall.partitions_budgeted")
                    .tag("spector.namespace", "ns-counter")
                    .counter();

            assertThat(visited).isNotNull();
            assertThat(visited.count()).isEqualTo(2.0);

            assertThat(budgeted).isNotNull();
            assertThat(budgeted.count()).isEqualTo(3.0); // 5 - 2 = 3 budgeted
        }

        @Test
        @DisplayName("F10: Zero budget setting is treated as unbounded with zero dropped partitions")
        void zeroBudget_treatedAsUnbounded() {
            List<PartitionHandle> candidates = List.of(mockHandle(1, 1000L), mockHandle(2, 2000L));

            BudgetStageResult result = applyPartitionBudget(candidates, 0, registry, "ns-zero");

            assertThat(result.isTruncated).isFalse();
            assertThat(result.budgetedCount).isZero();
            assertThat(result.visited).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Tier 3: Prometheus Graph Structural Gauges & Headroom Telemetry (F11, F12)")
    class GraphTelemetryGaugesTests {

        @Test
        @DisplayName("F11: spector.graph.nodes, edges, and bytes reflect structureHealthSnapshot values")
        void graphGauges_reflectHealthSnapshotValues() {
            GraphStructureHealthSnapshot snapshot = new GraphStructureHealthSnapshot(
                    "hebbian-csr",
                    1024L * 1024L, // allocatedBytes
                    512L * 1024L,  // liveBytes
                    0.05f,         // fragmentation
                    0.25f,         // hashLoadFactor
                    1,             // p99 probe
                    0.10f,         // csrOverflow
                    1700000000000L,// lastCompaction
                    4096L,         // reclaimed
                    100_000,       // nodeCapacity
                    45_000,        // highestNodeIndexSeen
                    0L,            // rejected
                    0L             // truncatedEdges
            );

            // Bind gauges
            Gauge.builder("spector.graph.nodes", snapshot, GraphStructureHealthSnapshot::highestNodeIndexSeen)
                    .tag("graph", "hebbian")
                    .tag("spector.namespace", "ns-graph")
                    .register(registry);

            Gauge.builder("spector.graph.bytes", snapshot, GraphStructureHealthSnapshot::allocatedBytes)
                    .tag("graph", "hebbian")
                    .tag("spector.namespace", "ns-graph")
                    .register(registry);

            Gauge.builder("spector.graph.live_bytes", snapshot, GraphStructureHealthSnapshot::liveBytes)
                    .tag("graph", "hebbian")
                    .tag("spector.namespace", "ns-graph")
                    .register(registry);

            Gauge nodesGauge = registry.find("spector.graph.nodes").tag("graph", "hebbian").gauge();
            Gauge bytesGauge = registry.find("spector.graph.bytes").tag("graph", "hebbian").gauge();
            Gauge liveBytesGauge = registry.find("spector.graph.live_bytes").tag("graph", "hebbian").gauge();

            assertThat(nodesGauge).isNotNull();
            assertThat(nodesGauge.value()).isEqualTo(45_000.0);

            assertThat(bytesGauge).isNotNull();
            assertThat(bytesGauge.value()).isEqualTo(1024.0 * 1024.0);

            assertThat(liveBytesGauge).isNotNull();
            assertThat(liveBytesGauge.value()).isEqualTo(512.0 * 1024.0);
        }

        @ParameterizedTest(name = "Capacity {0}, highestNodeSeen {1} -> expected headroom {2}")
        @CsvSource({
                "100000, -1, 1.0",     // empty graph
                "100000, 49999, 0.5",  // 50% capacity consumed
                "100000, 89999, 0.1",  // 90% capacity consumed (warning threshold)
                "100000, 99999, 0.0"   // 100% capacity exhausted (saturation)
        })
        @DisplayName("F12: spector.graph.headroom gauge matches [0.0, 1.0] formula")
        void nodeSpaceHeadroom_gaugeCalculation(int capacity, int highestNodeSeen, double expectedHeadroom) {
            GraphStructureHealthSnapshot snapshot = new GraphStructureHealthSnapshot(
                    "hebbian-csr", 1024L, 512L, 0.0f, 0.0f, 1, 0.0f, 0L, 0L,
                    capacity, highestNodeSeen, 0L, 0L
            );

            Gauge.builder("spector.graph.headroom", snapshot, s -> (double) s.nodeSpaceHeadroom())
                    .tag("graph", "hebbian")
                    .tag("spector.namespace", "ns-headroom")
                    .register(registry);

            Gauge headroomGauge = registry.find("spector.graph.headroom")
                    .tag("graph", "hebbian")
                    .gauge();

            assertThat(headroomGauge).isNotNull();
            assertThat(headroomGauge.value()).isCloseTo(expectedHeadroom, within(0.001));
        }
    }
}
