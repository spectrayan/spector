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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Adversarial challenger test suite for Milestone 5 (Commit 430446b2):
 * Empirically verifies temporal partition pruning (Invariant V4), active partition safety,
 * scorer isolation, boundary conditions, and cursor traversal across partition boundaries.
 */
@DisplayName("TemporalPartitionSeekingAdversarialChallengeTest — M5 Partition Seeking & Scorer Isolation")
class TemporalPartitionSeekingAdversarialChallengeTest {

    private MemoryAccessObject mao;
    private SpectorMemory memory;
    private SpectorMemoryAdmin admin;
    private PartitionRegistry partitionRegistry;
    private MemoryIndex memoryIndex;

    @BeforeEach
    void setUp() {
        mao = new MemoryAccessObject();
        memory = mock(SpectorMemory.class);
        admin = mock(SpectorMemoryAdmin.class);
        partitionRegistry = mock(PartitionRegistry.class);
        memoryIndex = mock(MemoryIndex.class);

        when(memory.admin()).thenReturn(admin);
        when(admin.partitionRegistry()).thenReturn(partitionRegistry);
        when(admin.index()).thenReturn(memoryIndex);
    }

    private CognitiveRecord createRecord(String id, long timestampMs, MemoryType type, MemorySource source) {
        CognitiveRecord r = mock(CognitiveRecord.class);
        when(r.id()).thenReturn(id);
        when(r.timestampMs()).thenReturn(timestampMs);
        when(r.memoryType()).thenReturn(type);
        when(r.source()).thenReturn(source);
        when(r.isTombstoned()).thenReturn(false);
        when(r.isPurged()).thenReturn(false);
        when(r.text()).thenReturn("Text for " + id);
        when(r.tags()).thenReturn(new String[0]);
        when(r.createdAt()).thenReturn(java.time.Instant.ofEpochMilli(timestampMs));
        return r;
    }

    private PartitionHandle createPartitionHandle(int seq, long minTs, long maxTs, boolean writable) {
        PartitionHandle h = mock(PartitionHandle.class);
        when(h.seq()).thenReturn(seq);
        PartitionSummary summary = new PartitionSummary(seq, minTs, maxTs, 0L, 0L, 10, 0, 0, writable);
        when(h.summary()).thenReturn(summary);
        return h;
    }

    private MemoryLocation createLocation(int partitionSeq, MemoryType type) {
        return new MemoryLocation(type, 0L, 0, partitionSeq, -1L, -1);
    }

    @Nested
    @DisplayName("Challenge 1: Temporal Partition Pruning & Zero Payload/Inspect Access (Invariant V4)")
    class TemporalPruningVerification {

        @Test
        @DisplayName("Queries with [created_from, created_to] strictly bypass non-intersecting partitions without inspect()")
        void temporalPruning_strictlyBypassesNonIntersectingPartitions() {
            // Partition 1: seq 1, [1000, 2000] (frozen)
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            CognitiveRecord r1a = createRecord("rec-1a", 1200L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r1b = createRecord("rec-1b", 1800L, MemoryType.EPISODIC, MemorySource.USER_STATED);

            // Partition 2: seq 2, [3000, 4000] (frozen) - TARGET
            PartitionHandle p2 = createPartitionHandle(2, 3000L, 4000L, false);
            CognitiveRecord r2a = createRecord("rec-2a", 3200L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r2b = createRecord("rec-2b", 3800L, MemoryType.EPISODIC, MemorySource.USER_STATED);

            // Partition 3: seq 3, [5000, 6000] (frozen)
            PartitionHandle p3 = createPartitionHandle(3, 5000L, 6000L, false);
            CognitiveRecord r3a = createRecord("rec-3a", 5200L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r3b = createRecord("rec-3b", 5800L, MemoryType.EPISODIC, MemorySource.USER_STATED);

            // Partition 4: seq 4, [7000, Long.MAX_VALUE] (active writable)
            PartitionHandle p4 = createPartitionHandle(4, 7000L, Long.MAX_VALUE, true);
            CognitiveRecord r4a = createRecord("rec-4a", 7200L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r4b = createRecord("rec-4b", 8500L, MemoryType.EPISODIC, MemorySource.USER_STATED);

            when(partitionRegistry.snapshot()).thenReturn(List.of(p1, p2, p3, p4));

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-1a", createLocation(1, MemoryType.EPISODIC));
            locationMap.put("rec-1b", createLocation(1, MemoryType.EPISODIC));
            locationMap.put("rec-2a", createLocation(2, MemoryType.EPISODIC));
            locationMap.put("rec-2b", createLocation(2, MemoryType.EPISODIC));
            locationMap.put("rec-3a", createLocation(3, MemoryType.EPISODIC));
            locationMap.put("rec-3b", createLocation(3, MemoryType.EPISODIC));
            locationMap.put("rec-4a", createLocation(4, MemoryType.EPISODIC));
            locationMap.put("rec-4b", createLocation(4, MemoryType.EPISODIC));

            when(memoryIndex.locationMap()).thenReturn(locationMap);

            when(memory.inspect("rec-2a")).thenReturn(r2a);
            when(memory.inspect("rec-2b")).thenReturn(r2b);

            // Query targeting strictly Partition 2: [3000, 4000]
            MemoryTableResponse response = mao.getMemoryTable(memory, null, 0, 50, 3000L, 4000L, null, null, false);

            assertThat(response).isNotNull();
            assertThat(response.rows()).hasSize(2);
            assertThat(response.rows().stream().map(MemoryTableRow::id).toList())
                    .containsExactly("rec-2b", "rec-2a"); // total order timestampMs DESC

            // Invariant V4 verification: inspect() MUST NEVER be called on non-intersecting partitions P1, P3, P4
            verify(memory, never()).inspect("rec-1a");
            verify(memory, never()).inspect("rec-1b");
            verify(memory, never()).inspect("rec-3a");
            verify(memory, never()).inspect("rec-3b");
            verify(memory, never()).inspect("rec-4a");
            verify(memory, never()).inspect("rec-4b");

            // Only P2 records inspected
            verify(memory, times(1)).inspect("rec-2a");
            verify(memory, times(1)).inspect("rec-2b");
        }

        @Test
        @DisplayName("Query with created_from only skips older partitions and touches newer ones")
        void createdFromOnly_skipsOlderPartitions() {
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            PartitionHandle p2 = createPartitionHandle(2, 3000L, 4000L, false);
            PartitionHandle p3 = createPartitionHandle(3, 5000L, Long.MAX_VALUE, true);

            when(partitionRegistry.snapshot()).thenReturn(List.of(p1, p2, p3));

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-1", createLocation(1, MemoryType.EPISODIC));
            locationMap.put("rec-2", createLocation(2, MemoryType.EPISODIC));
            locationMap.put("rec-3", createLocation(3, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);

            CognitiveRecord r2 = createRecord("rec-2", 3500L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r3 = createRecord("rec-3", 6000L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            when(memory.inspect("rec-2")).thenReturn(r2);
            when(memory.inspect("rec-3")).thenReturn(r3);

            // Query with createdFrom = 2500L: P1 (maxTs=2000) must be skipped completely
            MemoryTableResponse response = mao.getMemoryTable(memory, null, 0, 50, 2500L, null, null, null, false);

            assertThat(response.rows()).hasSize(2);
            verify(memory, never()).inspect("rec-1");
            verify(memory, times(1)).inspect("rec-2");
            verify(memory, times(1)).inspect("rec-3");
        }

        @Test
        @DisplayName("Query with created_to only skips newer partitions and touches older ones")
        void createdToOnly_skipsNewerPartitions() {
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            PartitionHandle p2 = createPartitionHandle(2, 3000L, 4000L, false);
            PartitionHandle p3 = createPartitionHandle(3, 5000L, Long.MAX_VALUE, true);

            when(partitionRegistry.snapshot()).thenReturn(List.of(p1, p2, p3));

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-1", createLocation(1, MemoryType.EPISODIC));
            locationMap.put("rec-2", createLocation(2, MemoryType.EPISODIC));
            locationMap.put("rec-3", createLocation(3, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);

            CognitiveRecord r1 = createRecord("rec-1", 1500L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            when(memory.inspect("rec-1")).thenReturn(r1);

            // Query with createdTo = 2500L: P2 (minTs=3000) and P3 (minTs=5000) must be skipped completely
            MemoryTableResponse response = mao.getMemoryTable(memory, null, 0, 50, null, 2500L, null, null, false);

            assertThat(response.rows()).hasSize(1);
            assertThat(response.rows().get(0).id()).isEqualTo("rec-1");

            verify(memory, times(1)).inspect("rec-1");
            verify(memory, never()).inspect("rec-2");
            verify(memory, never()).inspect("rec-3");
        }
    }

    @Nested
    @DisplayName("Challenge 2: Active Writable Partition Safety (maxTimestampMs = Long.MAX_VALUE)")
    class ActiveWritablePartitionSafetyTests {

        @Test
        @DisplayName("Active partition with maxTimestampMs = Long.MAX_VALUE is checked for newly inserted records")
        void activePartition_safelyCheckedForNewRecords() {
            // Frozen partition P1: [1000, 2000]
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            // Active writable partition P2: minTs=3000, maxTs=Long.MAX_VALUE
            PartitionHandle p2 = createPartitionHandle(2, 3000L, Long.MAX_VALUE, true);

            when(partitionRegistry.snapshot()).thenReturn(List.of(p1, p2));

            CognitiveRecord r1 = createRecord("rec-old", 1500L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r2 = createRecord("rec-new", 9999L, MemoryType.EPISODIC, MemorySource.USER_STATED);

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-old", createLocation(1, MemoryType.EPISODIC));
            locationMap.put("rec-new", createLocation(2, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);

            when(memory.inspect("rec-new")).thenReturn(r2);

            // Query with created_from = 5000L (in the recent range, after P1 froze)
            MemoryTableResponse response = mao.getMemoryTable(memory, null, 0, 50, 5000L, null, null, null, false);

            assertThat(response.rows()).hasSize(1);
            assertThat(response.rows().get(0).id()).isEqualTo("rec-new");

            verify(memory, never()).inspect("rec-old");
            verify(memory, times(1)).inspect("rec-new");
        }

        @Test
        @DisplayName("Active partition is pruned when created_to is strictly before active partition minTimestampMs")
        void activePartition_prunedWhenQueryIsOlderThanActiveMin() {
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            PartitionHandle p2 = createPartitionHandle(2, 5000L, Long.MAX_VALUE, true);

            when(partitionRegistry.snapshot()).thenReturn(List.of(p1, p2));

            CognitiveRecord r1 = createRecord("rec-old", 1500L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r2 = createRecord("rec-active", 6000L, MemoryType.EPISODIC, MemorySource.USER_STATED);

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-old", createLocation(1, MemoryType.EPISODIC));
            locationMap.put("rec-active", createLocation(2, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);

            when(memory.inspect("rec-old")).thenReturn(r1);

            // Query targeting [1000, 3000]. Active partition minTs=5000 > createdTo=3000 -> Active partition must be pruned!
            MemoryTableResponse response = mao.getMemoryTable(memory, null, 0, 50, 1000L, 3000L, null, null, false);

            assertThat(response.rows()).hasSize(1);
            assertThat(response.rows().get(0).id()).isEqualTo("rec-old");

            verify(memory, times(1)).inspect("rec-old");
            verify(memory, never()).inspect("rec-active");
        }
    }

    @Nested
    @DisplayName("Challenge 3: Exact Boundary Conditions and Corner Cases")
    class BoundaryConditionTests {

        @Test
        @DisplayName("Exact boundary matches (inclusive) are NOT pruned")
        void exactBoundaryMatches_notPruned() {
            // P1: [1000, 2000]
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            when(partitionRegistry.snapshot()).thenReturn(List.of(p1));

            CognitiveRecord r1 = createRecord("rec-boundary", 2000L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-boundary", createLocation(1, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);
            when(memory.inspect("rec-boundary")).thenReturn(r1);

            // created_from == maxTimestampMs (2000L) -> must intersect
            MemoryTableResponse res1 = mao.getMemoryTable(memory, null, 0, 50, 2000L, null, null, null, false);
            assertThat(res1.rows()).hasSize(1);
            verify(memory, times(1)).inspect("rec-boundary");

            // created_to == minTimestampMs (1000L) -> must intersect
            CognitiveRecord r0 = createRecord("rec-boundary-min", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            locationMap.put("rec-boundary-min", createLocation(1, MemoryType.EPISODIC));
            when(memory.inspect("rec-boundary-min")).thenReturn(r0);

            MemoryTableResponse res2 = mao.getMemoryTable(memory, null, 0, 50, null, 1000L, null, null, false);
            assertThat(res2.rows()).hasSize(1);
            assertThat(res2.rows().get(0).id()).isEqualTo("rec-boundary-min");
        }

        @Test
        @DisplayName("Boundary off-by-one (maxTimestampMs + 1 and minTimestampMs - 1) strictly prunes")
        void boundaryOffByOne_strictlyPrunes() {
            // P1: [1000, 2000]
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            when(partitionRegistry.snapshot()).thenReturn(List.of(p1));

            CognitiveRecord r1 = createRecord("rec-p1", 1500L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-p1", createLocation(1, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);

            // created_from == 2001L (maxTs + 1) -> must prune
            MemoryTableResponse res1 = mao.getMemoryTable(memory, null, 0, 50, 2001L, null, null, null, false);
            assertThat(res1.rows()).isEmpty();
            verify(memory, never()).inspect("rec-p1");

            // created_to == 999L (minTs - 1) -> must prune
            MemoryTableResponse res2 = mao.getMemoryTable(memory, null, 0, 50, null, 999L, null, null, false);
            assertThat(res2.rows()).isEmpty();
            verify(memory, never()).inspect("rec-p1");
        }

        @Test
        @DisplayName("Null PartitionSummary preserves zero false negatives (soundness fallback)")
        void nullPartitionSummary_includedInIntersectingPartitions() {
            PartitionHandle pNull = mock(PartitionHandle.class);
            when(pNull.seq()).thenReturn(99);
            when(pNull.summary()).thenReturn(null); // Corrupt or uninitialized summary

            when(partitionRegistry.snapshot()).thenReturn(List.of(pNull));

            CognitiveRecord rFallback = createRecord("rec-fallback", 1234L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-fallback", createLocation(99, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);
            when(memory.inspect("rec-fallback")).thenReturn(rFallback);

            // Even with restrictive created_from, null summary must NOT be pruned (zero false negative guarantee)
            MemoryTableResponse res = mao.getMemoryTable(memory, null, 0, 50, 1000L, 2000L, null, null, false);
            assertThat(res.rows()).hasSize(1);
            assertThat(res.rows().get(0).id()).isEqualTo("rec-fallback");
            verify(memory, times(1)).inspect("rec-fallback");
        }

        @Test
        @DisplayName("Inverted range (created_from > created_to) short-circuits with empty result")
        void invertedRange_shortCircuitsWithoutCallingInspect() {
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            when(partitionRegistry.snapshot()).thenReturn(List.of(p1));

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-1", createLocation(1, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);

            MemoryTableResponse res = mao.getMemoryTable(memory, null, 0, 50, 5000L, 2000L, null, null, false);
            assertThat(res.rows()).isEmpty();
            assertThat(res.nextCursor()).isNull();
            verify(memory, never()).inspect(anyString());
        }
    }

    @Nested
    @DisplayName("Challenge 4: 6-Phase Vector Scoring Isolation (Invariant V4 / Interface Contract 4)")
    class ScorerIsolationTests {

        @Test
        @DisplayName("Memory table listing never invokes memory.recall() under any parameters")
        void listingNeverInvokesRecallOrScoringRelay() {
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 2000L, false);
            when(partitionRegistry.snapshot()).thenReturn(List.of(p1));

            CognitiveRecord r1 = createRecord("rec-1", 1500L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("rec-1", createLocation(1, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);
            when(memory.inspect("rec-1")).thenReturn(r1);

            // 1. Basic listing
            mao.getMemoryTable(memory, 0, 50, null, false);
            // 2. Cursor listing
            String cursor = CursorToken.encode(1600L, "rec-0");
            mao.getMemoryTable(memory, cursor, 0, 50, 1000L, 2000L, "USER_STATED", "EPISODIC", false);
            // 3. Fallback unpartitioned mode
            when(admin.partitionRegistry()).thenReturn(null);
            when(admin.listAll()).thenReturn(List.of(r1));
            mao.getMemoryTable(memory, null, 0, 50, null, null, null, null, false);

            // Invariant V4 verification: recall() is strictly avoided in table listing paths
            verify(memory, never()).recall(anyString());
            verify(memory, never()).recall(anyString(), any(RecallOptions.class));
        }
    }

    @Nested
    @DisplayName("Challenge 5: Multi-Partition Cursor Walking & Total Order Determinism")
    class CursorWalkingAcrossPartitionsTests {

        @Test
        @DisplayName("Paging across partition boundaries preserves total order (timestampMs DESC, id DESC) with zero duplicates")
        void cursorPagingAcrossPartitions_preservesTotalOrder() {
            PartitionHandle p1 = createPartitionHandle(1, 1000L, 1003L, false);
            PartitionHandle p2 = createPartitionHandle(2, 2000L, 2003L, false);
            when(partitionRegistry.snapshot()).thenReturn(List.of(p1, p2));

            // P2 records (newer)
            CognitiveRecord r2c = createRecord("r2-c", 2002L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r2b = createRecord("r2-b", 2001L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r2a = createRecord("r2-a", 2001L, MemoryType.EPISODIC, MemorySource.USER_STATED); // tie-break with r2b

            // P1 records (older)
            CognitiveRecord r1b = createRecord("r1-b", 1002L, MemoryType.EPISODIC, MemorySource.USER_STATED);
            CognitiveRecord r1a = createRecord("r1-a", 1001L, MemoryType.EPISODIC, MemorySource.USER_STATED);

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("r2-c", createLocation(2, MemoryType.EPISODIC));
            locationMap.put("r2-b", createLocation(2, MemoryType.EPISODIC));
            locationMap.put("r2-a", createLocation(2, MemoryType.EPISODIC));
            locationMap.put("r1-b", createLocation(1, MemoryType.EPISODIC));
            locationMap.put("r1-a", createLocation(1, MemoryType.EPISODIC));
            when(memoryIndex.locationMap()).thenReturn(locationMap);

            when(memory.inspect("r2-c")).thenReturn(r2c);
            when(memory.inspect("r2-b")).thenReturn(r2b);
            when(memory.inspect("r2-a")).thenReturn(r2a);
            when(memory.inspect("r1-b")).thenReturn(r1b);
            when(memory.inspect("r1-a")).thenReturn(r1a);

            // Page 1: pageSize = 2
            MemoryTableResponse page1 = mao.getMemoryTable(memory, null, 0, 2, null, null, null, null, false);
            assertThat(page1.rows()).hasSize(2);
            assertThat(page1.rows().stream().map(MemoryTableRow::id).toList())
                    .containsExactly("r2-c", "r2-b");
            assertThat(page1.nextCursor()).isNotNull();

            // Page 2: with cursor from page 1, pageSize = 2
            MemoryTableResponse page2 = mao.getMemoryTable(memory, page1.nextCursor(), 0, 2, null, null, null, null, false);
            assertThat(page2.rows()).hasSize(2);
            // Between r2-b and r2-a (same ts 2001), r2-b was on page 1, r2-a on page 2, then r1-b (ts 1002)
            assertThat(page2.rows().stream().map(MemoryTableRow::id).toList())
                    .containsExactly("r2-a", "r1-b");
            assertThat(page2.nextCursor()).isNotNull();

            // Page 3: with cursor from page 2, pageSize = 2
            MemoryTableResponse page3 = mao.getMemoryTable(memory, page2.nextCursor(), 0, 2, null, null, null, null, false);
            assertThat(page3.rows()).hasSize(1);
            assertThat(page3.rows().get(0).id()).isEqualTo("r1-a");
            assertThat(page3.nextCursor()).isNull(); // Terminal page
        }
    }
}
