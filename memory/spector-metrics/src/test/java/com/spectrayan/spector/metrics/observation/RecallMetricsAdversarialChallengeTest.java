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

import com.spectrayan.spector.commons.observation.PathwayObservationHooks;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.pathway.recall.relay.CorticalTierScanRelay.PartitionVisitBudgetStage;
import com.spectrayan.spector.metrics.PathwayMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Empirical challenger test suite for Milestone 3:
 * Adversarially verifies Micrometer recall counters (partitions_visited, partitions_skipped, partitions_budgeted),
 * exact execution counts, namespace isolation, cumulative addition, and PartitionVisitBudgetStage invariant conservation.
 */
@DisplayName("RecallMetricsAdversarialChallengeTest — M3 Empirical Challenger Suite")
class RecallMetricsAdversarialChallengeTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    private PartitionHandle createHandle(int seq, Long maxTs) {
        PartitionHandle handle = mock(PartitionHandle.class);
        when(handle.seq()).thenReturn(seq);
        if (maxTs != null) {
            PartitionSummary summary = new PartitionSummary(seq, 0L, maxTs, 0L, 0L, 10, 0, 0, false);
            when(handle.summary()).thenReturn(summary);
        } else {
            when(handle.summary()).thenReturn(null);
        }
        return handle;
    }

    @Test
    @DisplayName("Challenge 1.1: Exact counter recording and null safety in RecallBudgetMetrics")
    void record_exactCountsAndNullSafety() {
        // Null registry should be a no-op, no NPE
        RecallBudgetMetrics.record(null, "ns-test", 5, 3, 2);

        // Record on valid registry
        RecallBudgetMetrics.record(registry, "tenant-alpha", 4, 6, 2);

        Counter visited = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_VISITED)
                .tag(RecallBudgetMetrics.TAG_NAMESPACE, "tenant-alpha").counter();
        Counter skipped = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_SKIPPED)
                .tag(RecallBudgetMetrics.TAG_NAMESPACE, "tenant-alpha").counter();
        Counter budgeted = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_BUDGETED)
                .tag(RecallBudgetMetrics.TAG_NAMESPACE, "tenant-alpha").counter();

        assertThat(visited).isNotNull();
        assertThat(visited.count()).isEqualTo(4.0);

        assertThat(skipped).isNotNull();
        assertThat(skipped.count()).isEqualTo(6.0);

        assertThat(budgeted).isNotNull();
        assertThat(budgeted.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Challenge 1.2: Cumulative addition across multiple queries")
    void record_cumulativeAcrossQueries() {
        // Query 1: visited 2, skipped 3, budgeted 1
        RecallBudgetMetrics.record(registry, "ns-multi", 2, 3, 1);
        // Query 2: visited 1, skipped 4, budgeted 0
        RecallBudgetMetrics.record(registry, "ns-multi", 1, 4, 0);
        // Query 3: visited 3, skipped 1, budgeted 2
        RecallBudgetMetrics.record(registry, "ns-multi", 3, 1, 2);

        Counter visited = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_VISITED)
                .tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-multi").counter();
        Counter skipped = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_SKIPPED)
                .tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-multi").counter();
        Counter budgeted = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_BUDGETED)
                .tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-multi").counter();

        assertThat(visited.count()).isEqualTo(6.0); // 2 + 1 + 3
        assertThat(skipped.count()).isEqualTo(8.0); // 3 + 4 + 1
        assertThat(budgeted.count()).isEqualTo(3.0); // 1 + 0 + 2
    }

    @Test
    @DisplayName("Challenge 1.3: Namespace isolation prevents cross-tenant pollution")
    void record_namespaceIsolation() {
        RecallBudgetMetrics.record(registry, "ns-1", 5, 2, 1);
        RecallBudgetMetrics.record(registry, "ns-2", 3, 8, 0);
        RecallBudgetMetrics.record(registry, null, 1, 1, 1); // defaults to "default"

        Counter v1 = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_VISITED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-1").counter();
        Counter v2 = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_VISITED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-2").counter();
        Counter vDef = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_VISITED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "default").counter();

        assertThat(v1.count()).isEqualTo(5.0);
        assertThat(v2.count()).isEqualTo(3.0);
        assertThat(vDef.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Challenge 1.4: Zero count behavior — registers counters without incrementing")
    void record_zeroCountsDoNotIncrement() {
        RecallBudgetMetrics.record(registry, "ns-zero", 0, 0, 0);

        Counter visited = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_VISITED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-zero").counter();
        Counter skipped = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_SKIPPED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-zero").counter();
        Counter budgeted = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_BUDGETED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-zero").counter();

        // When 0, meters should either be null (not registered) or have count 0.0
        if (visited != null) assertThat(visited.count()).isZero();
        if (skipped != null) assertThat(skipped.count()).isZero();
        if (budgeted != null) assertThat(budgeted.count()).isZero();
    }

    @Test
    @DisplayName("Challenge 1.5: End-to-end hook pipeline via PathwayMetrics and PathwayObservationHooks")
    void pathwayMetricsHook_integration() {
        PathwayMetrics pm = PathwayMetrics.bind(registry);
        assertThat(PathwayObservationHooks.global()).isSameAs(pm);

        PathwayObservationHooks.global().onRecallPartitionStats("ns-hook", 7, 13, 4);

        Counter visited = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_VISITED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-hook").counter();
        Counter skipped = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_SKIPPED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-hook").counter();
        Counter budgeted = registry.find(RecallBudgetMetrics.METRIC_PARTITIONS_BUDGETED).tag(RecallBudgetMetrics.TAG_NAMESPACE, "ns-hook").counter();

        assertThat(visited.count()).isEqualTo(7.0);
        assertThat(skipped.count()).isEqualTo(13.0);
        assertThat(budgeted.count()).isEqualTo(4.0);
    }

    @ParameterizedTest(name = "totalPartitions={0}, surviving={1}, budget={2} -> expected visited={3}, skipped={4}, budgeted={5}, truncated={6}")
    @CsvSource({
            "10, 5, 2, 2, 5, 3, true",   // budget bites: 5 surviving > budget 2
            "10, 5, 5, 5, 5, 0, false",  // budget equals surviving: no truncation
            "10, 5, 8, 5, 5, 0, false",  // budget exceeds surviving: no truncation
            "10, 5, 0, 5, 5, 0, false",  // budget 0 = unbounded: no truncation
            "10, 0, 2, 0, 10, 0, false", // pruner skipped all 10: 0 surviving
            "1,  1, 1, 1, 0, 0, false",  // single partition exactly matching
            "20, 15, 1, 1, 5, 14, true"  // aggressive budget 1 out of 15 surviving
    })
    @DisplayName("Challenge 1.6: PartitionVisitBudgetStage conservation law (visited + skipped + budgeted == total)")
    void budgetStage_conservationLaw(int totalPartitions, int survivingCount, int budget,
                                     int expectedVisited, int expectedSkipped, int expectedBudgeted, boolean expectedTruncated) {
        List<PartitionHandle> surviving = new ArrayList<>();
        for (int i = 1; i <= survivingCount; i++) {
            surviving.add(createHandle(i, 1000L * i));
        }

        PartitionVisitBudgetStage.BudgetResult result =
                PartitionVisitBudgetStage.apply(surviving, totalPartitions, budget);

        assertThat(result.visited()).isEqualTo(expectedVisited);
        assertThat(result.skipped()).isEqualTo(expectedSkipped);
        assertThat(result.budgeted()).isEqualTo(expectedBudgeted);
        assertThat(result.truncated()).isEqualTo(expectedTruncated);

        // Core conservation invariant: every partition must be accounted for
        assertThat(result.visited() + result.skipped() + result.budgeted())
                .as("Conservation invariant: visited + skipped + budgeted == totalPartitions")
                .isEqualTo(totalPartitions);
    }

    @Test
    @DisplayName("Challenge 1.7: Recency ordering stress test — newest timestamp, then highest sequence, with active partition priority")
    void budgetStage_recencyOrderingPriority() {
        // Setup handles:
        // p1: frozen, ts=1000, seq=1
        // p2: frozen, ts=5000, seq=2
        // p3: frozen, ts=5000, seq=5 (same ts as p2, higher seq)
        // p4: frozen, ts=3000, seq=4
        // pActive: active partition (summary == null -> Long.MAX_VALUE), seq=10
        PartitionHandle p1 = createHandle(1, 1000L);
        PartitionHandle p2 = createHandle(2, 5000L);
        PartitionHandle p3 = createHandle(5, 5000L);
        PartitionHandle p4 = createHandle(4, 3000L);
        PartitionHandle pActive = createHandle(10, null);

        List<PartitionHandle> surviving = List.of(p1, p2, p3, p4, pActive);

        // Budget 3 should pick:
        // 1st: pActive (summary == null -> Long.MAX_VALUE)
        // 2nd: p3 (ts=5000, seq=5)
        // 3rd: p2 (ts=5000, seq=2)
        PartitionVisitBudgetStage.BudgetResult result =
                PartitionVisitBudgetStage.apply(surviving, 5, 3);

        assertThat(result.truncated()).isTrue();
        assertThat(result.visited()).isEqualTo(3);
        assertThat(result.budgeted()).isEqualTo(2);
        assertThat(result.candidates()).containsExactly(pActive, p3, p2);
    }
}
