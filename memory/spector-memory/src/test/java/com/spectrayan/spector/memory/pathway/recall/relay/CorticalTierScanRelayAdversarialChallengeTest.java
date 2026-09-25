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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Empirical Adversarial Challenger Test Suite for Milestone 3 (Requirement R3):
 * Stress-tests partition visit budget boundary conditions, tiebreaker determinism,
 * active writable partition prioritization, truncation flag propagation, and concurrency.
 */
@DisplayName("CorticalTierScanRelayAdversarialChallengeTest — M3 Empirical Challenge Suite")
public class CorticalTierScanRelayAdversarialChallengeTest {

    private PartitionHandle mockHandle(int seq, Long maxTs, boolean writable) {
        PartitionHandle handle = mock(PartitionHandle.class);
        when(handle.seq()).thenReturn(seq);
        when(handle.writable()).thenReturn(writable);
        if (maxTs != null) {
            PartitionSummary summary = new PartitionSummary(seq, 0L, maxTs, 0L, 0L, 10, 0, 0, writable);
            when(handle.summary()).thenReturn(summary);
        } else {
            when(handle.summary()).thenReturn(null);
        }
        return handle;
    }

    private PartitionHandle mockFrozenHandle(int seq, long maxTs) {
        return mockHandle(seq, maxTs, false);
    }

    private PartitionHandle mockActiveHandle(int seq) {
        return mockHandle(seq, null, true);
    }

    private PartitionHandle mockActiveHandleWithSummary(int seq) {
        return mockHandle(seq, Long.MAX_VALUE, true);
    }

    @Nested
    @DisplayName("Challenge 1: Boundary & Negative Visit Budget Semantics")
    class BudgetBoundaryTests {

        @ParameterizedTest(name = "budget = {0} should uncap recall and scan all candidates")
        @ValueSource(ints = {0, -1, -5, -100, Integer.MIN_VALUE})
        @DisplayName("Zero or negative budget leaves recall unbudgeted (all surviving candidates scanned)")
        void zeroOrNegativeBudget_uncapped(int budget) {
            List<PartitionHandle> surviving = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                surviving.add(mockFrozenHandle(i, 1000L * i));
            }
            int totalPartitions = 25;

            var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(surviving, totalPartitions, budget);

            assertThat(result.truncated())
                    .as("Truncation must be false when budget is <= 0")
                    .isFalse();
            assertThat(result.visited())
                    .as("Visited must equal surviving count")
                    .isEqualTo(10);
            assertThat(result.skipped())
                    .as("Skipped must reflect pruner drops (total - surviving)")
                    .isEqualTo(15);
            assertThat(result.budgeted())
                    .as("Budgeted must be exactly 0")
                    .isEqualTo(0);
            assertThat(result.candidates())
                    .as("All surviving candidates must be preserved")
                    .containsExactlyElementsOf(surviving);
        }

        @Test
        @DisplayName("Empty surviving candidates with zero or negative budget handles gracefully")
        void emptySurviving_zeroOrNegativeBudget() {
            List<PartitionHandle> empty = List.of();
            var resZero = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(empty, 10, 0);
            assertThat(resZero.truncated()).isFalse();
            assertThat(resZero.visited()).isEqualTo(0);
            assertThat(resZero.skipped()).isEqualTo(10);
            assertThat(resZero.budgeted()).isEqualTo(0);
            assertThat(resZero.candidates()).isEmpty();

            var resNeg = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(empty, 10, -1);
            assertThat(resNeg.truncated()).isFalse();
            assertThat(resNeg.visited()).isEqualTo(0);
            assertThat(resNeg.skipped()).isEqualTo(10);
            assertThat(resNeg.budgeted()).isEqualTo(0);
            assertThat(resNeg.candidates()).isEmpty();
        }

        @Test
        @DisplayName("Budget matching or exceeding surviving candidate count does not truncate")
        void budgetGreaterOrEqualCandidates_noTruncation() {
            List<PartitionHandle> surviving = List.of(
                    mockFrozenHandle(1, 1000L),
                    mockFrozenHandle(2, 2000L),
                    mockFrozenHandle(3, 3000L)
            );

            // Exact match
            var resExact = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(surviving, 10, 3);
            assertThat(resExact.truncated()).isFalse();
            assertThat(resExact.visited()).isEqualTo(3);
            assertThat(resExact.budgeted()).isEqualTo(0);
            assertThat(resExact.skipped()).isEqualTo(7);
            assertThat(resExact.candidates()).containsExactlyElementsOf(surviving);

            // Budget exceeding
            var resOver = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(surviving, 10, 100);
            assertThat(resOver.truncated()).isFalse();
            assertThat(resOver.visited()).isEqualTo(3);
            assertThat(resOver.budgeted()).isEqualTo(0);
            assertThat(resOver.skipped()).isEqualTo(7);
            assertThat(resOver.candidates()).containsExactlyElementsOf(surviving);
        }
    }

    @Nested
    @DisplayName("Challenge 2: Severe Budget Capping (budget = 1 with 10 Candidates)")
    class SevereBudgetCappingTests {

        @Test
        @DisplayName("budget = 1 with 10 candidate partitions strictly scans newest partition, budgets 9, and sets truncated = true")
        void budgetOne_withTenCandidates() {
            List<PartitionHandle> candidates = new ArrayList<>();
            // Intentionally insert in mixed order
            candidates.add(mockFrozenHandle(3, 3000L));
            candidates.add(mockFrozenHandle(1, 1000L));
            candidates.add(mockFrozenHandle(10, 10000L)); // newest!
            candidates.add(mockFrozenHandle(5, 5000L));
            candidates.add(mockFrozenHandle(2, 2000L));
            candidates.add(mockFrozenHandle(8, 8000L));
            candidates.add(mockFrozenHandle(4, 4000L));
            candidates.add(mockFrozenHandle(9, 9000L));
            candidates.add(mockFrozenHandle(6, 6000L));
            candidates.add(mockFrozenHandle(7, 7000L));

            int totalPartitions = 15;
            var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(candidates, totalPartitions, 1);

            assertThat(result.truncated())
                    .as("Truncation must be true when 10 candidates are capped to 1")
                    .isTrue();
            assertThat(result.visited())
                    .as("Visited must strictly equal 1")
                    .isEqualTo(1);
            assertThat(result.budgeted())
                    .as("Budgeted dropped count must strictly equal 9 (10 - 1)")
                    .isEqualTo(9);
            assertThat(result.skipped())
                    .as("Skipped must be 5 (15 - 10)")
                    .isEqualTo(5);
            assertThat(result.candidates())
                    .as("Retained candidate must strictly be the newest partition (seq 10, maxTs 10000)")
                    .hasSize(1);
            assertThat(result.candidates().getFirst().seq()).isEqualTo(10);
            assertThat(result.candidates().getFirst().summary().maxTimestampMs()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("Graduated budgets (1 to 9) consistently retain top-K recency partitions")
        void graduatedBudgets() {
            List<PartitionHandle> candidates = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                candidates.add(mockFrozenHandle(i, i * 1000L));
            }

            for (int budget = 1; budget <= 9; budget++) {
                var res = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(candidates, 10, budget);
                assertThat(res.truncated()).isTrue();
                assertThat(res.visited()).isEqualTo(budget);
                assertThat(res.budgeted()).isEqualTo(10 - budget);
                assertThat(res.candidates()).hasSize(budget);
                // Verify descending order of timestamps in retained candidates
                for (int j = 0; j < budget; j++) {
                    int expectedSeq = 10 - j;
                    long expectedTs = (10 - j) * 1000L;
                    assertThat(res.candidates().get(j).seq()).isEqualTo(expectedSeq);
                    assertThat(res.candidates().get(j).summary().maxTimestampMs()).isEqualTo(expectedTs);
                }
            }
        }
    }

    @Nested
    @DisplayName("Challenge 3: Identical maxTimestampMs Tiebreaker Determinism")
    class IdenticalTimestampTiebreakerTests {

        @Test
        @DisplayName("Multiple partitions with identical maxTimestampMs tie-break deterministically by seq DESC")
        void identicalTimestamp_deterministicSeqDesc() {
            // 5 partitions with same timestamp 5000L, distinct sequences
            PartitionHandle p1 = mockFrozenHandle(1, 5000L);
            PartitionHandle p5 = mockFrozenHandle(5, 5000L);
            PartitionHandle p2 = mockFrozenHandle(2, 5000L);
            PartitionHandle p8 = mockFrozenHandle(8, 5000L);
            PartitionHandle p3 = mockFrozenHandle(3, 5000L);

            List<PartitionHandle> candidates = List.of(p1, p5, p2, p8, p3);

            var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(candidates, 10, 2);

            assertThat(result.truncated()).isTrue();
            assertThat(result.visited()).isEqualTo(2);
            assertThat(result.budgeted()).isEqualTo(3);
            assertThat(result.candidates())
                    .as("Candidates must be ordered strictly by seq DESC when timestamps are equal: seq 8, then seq 5")
                    .containsExactly(p8, p5);
        }

        @Test
        @DisplayName("Tiebreaking stability across all permutations of input candidate list")
        void identicalTimestamp_permutationInvariance() {
            PartitionHandle p1 = mockFrozenHandle(10, 8000L);
            PartitionHandle p2 = mockFrozenHandle(20, 8000L);
            PartitionHandle p3 = mockFrozenHandle(30, 8000L);
            PartitionHandle p4 = mockFrozenHandle(40, 8000L);

            List<PartitionHandle> base = List.of(p1, p2, p3, p4);
            List<List<PartitionHandle>> permutations = List.of(
                    List.of(p1, p2, p3, p4),
                    List.of(p4, p3, p2, p1),
                    List.of(p2, p4, p1, p3),
                    List.of(p3, p1, p4, p2)
            );

            for (List<PartitionHandle> perm : permutations) {
                var res = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(perm, 5, 2);
                assertThat(res.truncated()).isTrue();
                assertThat(res.visited()).isEqualTo(2);
                assertThat(res.candidates())
                        .as("Regardless of input permutation, top 2 must strictly be seq 40, seq 30")
                        .containsExactly(p4, p3);
            }
        }
    }

    @Nested
    @DisplayName("Challenge 4: Active Writable Partition Prioritization")
    class ActiveWritablePartitionTests {

        @Test
        @DisplayName("Active writable partition with null summary evaluates to Long.MAX_VALUE and beats all frozen partitions")
        void activePartition_nullSummary_prioritizedFirst() {
            PartitionHandle active = mockActiveHandle(99); // writable=true, summary=null
            PartitionHandle frozen1 = mockFrozenHandle(100, Long.MAX_VALUE - 1000L); // frozen with huge ts
            PartitionHandle frozen2 = mockFrozenHandle(101, System.currentTimeMillis() + 10_000_000L);

            List<PartitionHandle> candidates = List.of(frozen1, active, frozen2);

            var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(candidates, 5, 1);

            assertThat(result.truncated()).isTrue();
            assertThat(result.visited()).isEqualTo(1);
            assertThat(result.budgeted()).isEqualTo(2);
            assertThat(result.candidates().getFirst())
                    .as("Active partition with null summary must evaluate to Long.MAX_VALUE and be prioritized first")
                    .isSameAs(active);
        }

        @Test
        @DisplayName("Active writable partition with summary (maxTimestampMs = Long.MAX_VALUE) beats frozen partitions")
        void activePartition_withSummary_prioritizedFirst() {
            PartitionHandle active = mockActiveHandleWithSummary(50); // writable=true, maxTs=Long.MAX_VALUE
            PartitionHandle frozen = mockFrozenHandle(51, System.currentTimeMillis());

            List<PartitionHandle> candidates = List.of(frozen, active);

            var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(candidates, 5, 1);

            assertThat(result.candidates().getFirst())
                    .as("Active partition with summary must be prioritized first")
                    .isSameAs(active);
        }

        @Test
        @DisplayName("Multiple active writable partitions tie-break by seq DESC")
        void multipleActivePartitions_tieBreakBySeqDesc() {
            PartitionHandle activeOld = mockActiveHandle(10);
            PartitionHandle activeNew = mockActiveHandle(20);
            PartitionHandle frozen = mockFrozenHandle(30, 999999L);

            List<PartitionHandle> candidates = List.of(activeOld, frozen, activeNew);

            var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(candidates, 5, 2);

            assertThat(result.candidates())
                    .as("Both active partitions should take top 2 slots, ordered by seq DESC (20, then 10)")
                    .containsExactly(activeNew, activeOld);
        }
    }

    @Nested
    @DisplayName("Challenge 5: CognitiveResult and Signal Flag Propagation")
    class FlagPropagationTests {

        @Test
        @DisplayName("CognitiveResult.withTruncated preserves all 19 record components perfectly")
        void cognitiveResult_immutabilityAndCopyPreservation() {
            long now = System.currentTimeMillis();
            CognitiveResult original = new CognitiveResult(
                    "engram-99",
                    "deep cognitive context text",
                    0.887f,
                    1.45f,
                    0.05f,
                    42,
                    (byte) 3,
                    MemoryType.EPISODIC,
                    null,
                    new String[]{"cortex", "memory"},
                    0.92f,
                    0.85f,
                    null,
                    null,
                    null,
                    null,
                    Map.of("confidence", "high"),
                    (byte) 1,
                    now
            );

            assertThat(original.truncated()).isFalse();

            CognitiveResult truncatedCopy = original.withTruncated(true);
            assertThat(truncatedCopy.truncated()).isTrue();
            assertThat(truncatedCopy.id()).isEqualTo("engram-99");
            assertThat(truncatedCopy.text()).isEqualTo("deep cognitive context text");
            assertThat(truncatedCopy.score()).isEqualTo(0.887f);
            assertThat(truncatedCopy.importance()).isEqualTo(1.45f);
            assertThat(truncatedCopy.ageDays()).isEqualTo(0.05f);
            assertThat(truncatedCopy.agentRecallCount()).isEqualTo(42);
            assertThat(truncatedCopy.valence()).isEqualTo((byte) 3);
            assertThat(truncatedCopy.memoryType()).isEqualTo(MemoryType.EPISODIC);
            assertThat(truncatedCopy.source()).isNull();
            assertThat(truncatedCopy.synapticTags()).containsExactly("cortex", "memory");
            assertThat(truncatedCopy.decayFactor()).isEqualTo(0.92f);
            assertThat(truncatedCopy.ltpAdjustedDecay()).isEqualTo(0.85f);
            assertThat(truncatedCopy.metadata()).containsEntry("confidence", "high");
            assertThat(truncatedCopy.consolidationFlags()).isEqualTo((byte) 1);
            assertThat(truncatedCopy.timestampMs()).isEqualTo(now);

            CognitiveResult falseCopy = truncatedCopy.withTruncated(false);
            assertThat(falseCopy.truncated()).isFalse();
        }

        @Test
        @DisplayName("RecallSignal tracks, forks, and merges truncation and partition counters accurately")
        void recallSignal_mergeAccumulation() {
            RecallOptions options = RecallOptions.builder().partitionVisitBudget(2).build();
            RecallSignal parent = RecallSignal.forTextQuery("query", options);

            RecallSignal fork1 = parent.fork();
            fork1.setTruncated(true);
            fork1.setPartitionsVisited(2);
            fork1.setPartitionsSkipped(3);
            fork1.setPartitionsBudgeted(5);

            RecallSignal fork2 = parent.fork();
            fork2.setTruncated(false);
            fork2.setPartitionsVisited(1);
            fork2.setPartitionsSkipped(2);
            fork2.setPartitionsBudgeted(0);

            parent.merge(List.of(fork1, fork2));

            assertThat(parent.isTruncated()).isTrue();
            assertThat(parent.partitionsVisited()).isEqualTo(3);
            assertThat(parent.partitionsSkipped()).isEqualTo(5);
            assertThat(parent.partitionsBudgeted()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Challenge 6: High-Concurrency Multithreaded Stress")
    class ConcurrencyStressTests {

        @Test
        @DisplayName("Concurrent execution from 50 threads operates safely with zero race conditions")
        void multithreaded_budgetEvaluationStress() throws Exception {
            final int threadCount = 50;
            final int iterationsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                tasks.add(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            List<PartitionHandle> list = new ArrayList<>();
                            int candidateCount = 10 + (i % 20); // 10 to 29 candidates
                            for (int c = 1; c <= candidateCount; c++) {
                                list.add(mockFrozenHandle(c, 1000L * c));
                            }
                            Collections.shuffle(list);

                            int budget = (i % 5) + 1; // 1 to 5
                            var result = CorticalTierScanRelay.PartitionVisitBudgetStage.apply(list, 50, budget);

                            if (!result.truncated()) {
                                throw new AssertionError("Thread " + threadId + ": Expected truncated=true");
                            }
                            if (result.visited() != budget) {
                                throw new AssertionError("Thread " + threadId + ": Visited mismatch");
                            }
                            if (result.budgeted() != candidateCount - budget) {
                                throw new AssertionError("Thread " + threadId + ": Budgeted mismatch");
                            }
                            if (result.candidates().size() != budget) {
                                throw new AssertionError("Thread " + threadId + ": Candidates size mismatch");
                            }
                            // Verify sorted recency descending
                            for (int k = 0; k < result.candidates().size() - 1; k++) {
                                long currTs = result.candidates().get(k).summary().maxTimestampMs();
                                long nextTs = result.candidates().get(k + 1).summary().maxTimestampMs();
                                if (currTs < nextTs) {
                                    throw new AssertionError("Thread " + threadId + ": Ordering inverted");
                                }
                            }
                        }
                    } catch (Throwable th) {
                        errors.add(th);
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            executor.shutdown();
            boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

            assertThat(finished).isTrue();
            assertThat(errors).isEmpty();
            for (Future<Void> f : futures) {
                f.get();
            }
        }
    }
}
