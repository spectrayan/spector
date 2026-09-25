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
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.RecallOptionsBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CorticalTierScanRelayBudgetTest — M3 Post-Pruning Visit Budget Unit Tests")
class CorticalTierScanRelayBudgetTest {

    private PartitionHandle mockHandle(int seq, long maxTs) {
        PartitionHandle handle = mock(PartitionHandle.class);
        PartitionSummary summary = new PartitionSummary(seq, 0L, maxTs, 0L, 0L, 10, 0, 0, false);
        when(handle.seq()).thenReturn(seq);
        when(handle.summary()).thenReturn(summary);
        return handle;
    }

    @Test
    @DisplayName("BudgetStage: Unbounded when budget <= 0 or candidates <= budget")
    void budgetStage_unbounded() {
        PartitionHandle p1 = mockHandle(1, 1000L);
        PartitionHandle p2 = mockHandle(2, 2000L);
        List<PartitionHandle> surviving = List.of(p1, p2);

        var resZero = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(surviving, 5, 0);
        assertThat(resZero.truncated()).isFalse();
        assertThat(resZero.visited()).isEqualTo(2);
        assertThat(resZero.skipped()).isEqualTo(3);
        assertThat(resZero.budgeted()).isEqualTo(0);
        assertThat(resZero.candidates()).containsExactly(p1, p2);

        var resWithin = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(surviving, 5, 2);
        assertThat(resWithin.truncated()).isFalse();
        assertThat(resWithin.visited()).isEqualTo(2);
        assertThat(resWithin.skipped()).isEqualTo(3);
        assertThat(resWithin.budgeted()).isEqualTo(0);
    }

    @Test
    @DisplayName("BudgetStage: Truncates recency-first with (maxTimestampMs DESC, seq DESC)")
    void budgetStage_truncatesRecencyFirst() {
        PartitionHandle p1 = mockHandle(1, 1000L);
        PartitionHandle p2 = mockHandle(2, 5000L);
        PartitionHandle p3 = mockHandle(3, 3000L);
        PartitionHandle p4 = mockHandle(4, 5000L); // same maxTs as p2, but higher seq
        List<PartitionHandle> surviving = List.of(p1, p2, p3, p4);

        var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(surviving, 6, 2);
        assertThat(result.truncated()).isTrue();
        assertThat(result.visited()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(2); // 6 total - 4 surviving
        assertThat(result.budgeted()).isEqualTo(2); // 4 surviving - 2 budget
        // Ordering: p4 (5000, seq 4) before p2 (5000, seq 2), then p3 (3000), p1 (1000)
        assertThat(result.candidates()).containsExactly(p4, p2);
    }

    @Test
    @DisplayName("RecallOptions and RecallOptionsBuilder: partitionVisitBudget propagation")
    void recallOptions_builderAndCopy() {
        RecallOptions options = RecallOptions.builder()
                .partitionVisitBudget(5)
                .build();
        assertThat(options.partitionVisitBudget()).isEqualTo(5);
        assertThat(options.partitionBudget()).isEqualTo(5);

        RecallOptions copied = options.toBuilder().build();
        assertThat(copied.partitionVisitBudget()).isEqualTo(5);

        RecallOptions fromHelper = new RecallOptionsBuilder()
                .partitionBudget(8)
                .build();
        assertThat(fromHelper.partitionVisitBudget()).isEqualTo(8);
    }

    @Test
    @DisplayName("CognitiveResult: truncated flag and withTruncated copy")
    void cognitiveResult_truncatedComponent() {
        CognitiveResult result = new CognitiveResult(
                "mem-1",
                "text",
                0.95f,
                1.0f,
                0.1f,
                1,
                (byte) 0,
                MemoryType.SEMANTIC,
                null,
                new String[]{"tag1"},
                0.9f,
                0.9f,
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                (byte) 0,
                System.currentTimeMillis()
        );
        assertThat(result.truncated()).isFalse();

        CognitiveResult truncatedResult = result.withTruncated(true);
        assertThat(truncatedResult.truncated()).isTrue();
        assertThat(truncatedResult.id()).isEqualTo("mem-1");
        assertThat(truncatedResult.score()).isEqualTo(0.95f);
    }

    @Test
    @DisplayName("RecallSignal: truncated, counts, fork, and merge")
    void recallSignal_forkAndMerge() {
        RecallOptions options = RecallOptions.builder().build();
        RecallSignal signal = RecallSignal.forTextQuery("test query", options);

        signal.setTruncated(true);
        signal.setPartitionsVisited(3);
        signal.setPartitionsSkipped(2);
        signal.setPartitionsBudgeted(1);

        assertThat(signal.isTruncated()).isTrue();
        assertThat(signal.truncated()).isTrue();
        assertThat(signal.partitionsVisited()).isEqualTo(3);
        assertThat(signal.partitionsSkipped()).isEqualTo(2);
        assertThat(signal.partitionsBudgeted()).isEqualTo(1);

        RecallSignal fork = signal.fork();
        assertThat(fork.isTruncated()).isTrue();
        assertThat(fork.partitionsVisited()).isEqualTo(3);
        assertThat(fork.partitionsSkipped()).isEqualTo(2);
        assertThat(fork.partitionsBudgeted()).isEqualTo(1);

        RecallSignal merged = RecallSignal.forTextQuery("test query", options);
        merged.merge(List.of(fork));
        assertThat(merged.isTruncated()).isTrue();
        assertThat(merged.partitionsVisited()).isEqualTo(3);
        assertThat(merged.partitionsSkipped()).isEqualTo(2);
        assertThat(merged.partitionsBudgeted()).isEqualTo(1);
    }
}
