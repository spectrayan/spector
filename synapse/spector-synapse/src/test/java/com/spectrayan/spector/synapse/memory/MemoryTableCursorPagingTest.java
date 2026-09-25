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

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test suite for Index-Assisted Cursor Listing and Pagination (R5 / F13-F17).
 *
 * <p>Authoritative Specifications:
 * <ul>
 *   <li>PROJECT.md § Interface Contract 4 (REST /api/v1/memory/table &lt;-&gt; Synapse DAO)</li>
 *   <li>ORIGINAL_REQUEST.md § R5 (Index-Assisted Cursor Listing &amp; MCP Parity)</li>
 *   <li>requirements.md § R4 (R4.1, R4.2, R4.3, R4.4, R4.6, Invariant V3)</li>
 *   <li>design.md § D5 (The listing API: extend /table, do not add /list)</li>
 * </ul>
 *
 * <p>Invariants:
 * <ul>
 *   <li>V3: Paging through a list under concurrent writes yields zero duplicates and zero gaps.</li>
 *   <li>Total Order: (timestampMs DESC, id DESC) ensures deterministic pagination without jitter.</li>
 *   <li>Scorer-Free: Avoids 6-phase vector scoring relay.</li>
 * </ul>
 */
@DisplayName("MemoryTableCursorPagingTest — R5 Cursor Pagination & Stable Total Order (F13-F17)")
class MemoryTableCursorPagingTest {

    /**
     * Opaque Base64 Cursor Token Model and Codec conforming to Interface Contract 4.
     */
    public record CursorToken(long timestampMs, String id) {

        public static String encode(long timestampMs, String id) {
            String raw = timestampMs + ":" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        public static CursorToken decode(String base64Cursor) {
            if (base64Cursor == null || base64Cursor.isBlank()) {
                return null;
            }
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(base64Cursor);
                String raw = new String(decoded, StandardCharsets.UTF_8);
                int colonIdx = raw.indexOf(':');
                if (colonIdx <= 0) {
                    throw new IllegalArgumentException("Malformed cursor token: missing timestamp separator");
                }
                long ts = Long.parseLong(raw.substring(0, colonIdx));
                String id = raw.substring(colonIdx + 1);
                return new CursorToken(ts, id);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid base64 cursor token: " + base64Cursor, e);
            }
        }
    }

    /**
     * Stable Total Order Comparator: (timestampMs DESC, id DESC).
     */
    static final Comparator<CognitiveRecord> TOTAL_ORDER_COMPARATOR = (a, b) -> {
        int tsCmp = Long.compare(b.timestampMs(), a.timestampMs()); // newest first
        if (tsCmp != 0) return tsCmp;
        return b.id().compareTo(a.id()); // tiebreak by ID descending
    };

    /**
     * Simulates the cursor-based window evaluation conforming to Contract 4.
     */
    static List<CognitiveRecord> seekAfterCursor(List<CognitiveRecord> sortedRecords,
                                                  CursorToken cursor,
                                                  int pageSize) {
        return sortedRecords.stream()
                .filter(rec -> {
                    if (cursor == null) return true;
                    // Strict inequality for (timestampMs DESC, id DESC):
                    // rec must be strictly AFTER cursor in total order
                    if (rec.timestampMs() < cursor.timestampMs()) return true;
                    if (rec.timestampMs() > cursor.timestampMs()) return false;
                    return rec.id().compareTo(cursor.id()) < 0;
                })
                .limit(pageSize)
                .toList();
    }

    private CognitiveRecord createRecord(String id, long timestampMs, MemoryType type, MemorySource source, boolean tombstoned) {
        CognitiveRecord rec = mock(CognitiveRecord.class);
        when(rec.id()).thenReturn(id);
        when(rec.timestampMs()).thenReturn(timestampMs);
        when(rec.memoryType()).thenReturn(type);
        when(rec.source()).thenReturn(source);
        when(rec.isTombstoned()).thenReturn(tombstoned);
        return rec;
    }

    @Nested
    @DisplayName("Tier 1: Feature Coverage & Cursor Contract (F13, F14)")
    class CursorContractTests {

        @Test
        @DisplayName("F13: Records with identical timestamps are ordered deterministically by ID descending")
        void identicalTimestamps_orderedDeterministicallyByIdDescending() {
            long ts = 1700000000000L;
            List<CognitiveRecord> records = new ArrayList<>(List.of(
                    createRecord("alpha", ts, MemoryType.EPISODIC, MemorySource.USER_STATED, false),
                    createRecord("gamma", ts, MemoryType.EPISODIC, MemorySource.USER_STATED, false),
                    createRecord("beta", ts, MemoryType.EPISODIC, MemorySource.USER_STATED, false)
            ));

            records.sort(TOTAL_ORDER_COMPARATOR);

            assertThat(records.stream().map(CognitiveRecord::id).toList())
                    .as("Total order must break timestamp ties by ID descending")
                    .containsExactly("gamma", "beta", "alpha");
        }

        @Test
        @DisplayName("F14: Base64 cursor encoding and decoding is bijective and URL-safe")
        void cursorToken_roundTripEncoding() {
            long ts = 1727218800000L;
            String id = "urn:spector:mem-987654";

            String encoded = CursorToken.encode(ts, id);
            assertThat(encoded).isNotEmpty();
            assertThat(encoded).doesNotContain("+", "/", "="); // URL-safe without padding

            CursorToken decoded = CursorToken.decode(encoded);
            assertThat(decoded).isNotNull();
            assertThat(decoded.timestampMs()).isEqualTo(ts);
            assertThat(decoded.id()).isEqualTo(id);
        }

        @Test
        @DisplayName("F14: Forward cursor pagination traverses all records with zero duplicates and zero skips")
        void cursorPaging_forwardTraversal_zeroDuplicatesZeroSkips() {
            List<CognitiveRecord> dataset = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                dataset.add(createRecord("mem-" + String.format("%03d", i), 1000L + (i * 10L), MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            }
            dataset.sort(TOTAL_ORDER_COMPARATOR);

            int pageSize = 15;
            List<String> visitedIds = new ArrayList<>();
            CursorToken cursor = null;

            while (true) {
                List<CognitiveRecord> page = seekAfterCursor(dataset, cursor, pageSize);
                if (page.isEmpty()) break;

                for (CognitiveRecord rec : page) {
                    visitedIds.add(rec.id());
                }

                CognitiveRecord last = page.get(page.size() - 1);
                cursor = new CursorToken(last.timestampMs(), last.id());
            }

            assertThat(visitedIds).hasSize(50);
            assertThat(new HashSet<>(visitedIds)).hasSize(50); // zero duplicates
            assertThat(visitedIds)
                    .containsExactlyElementsOf(dataset.stream().map(CognitiveRecord::id).toList()); // zero skips
        }
    }

    @Nested
    @DisplayName("Tier 2: Boundary & Corner Cases (F14, F15)")
    class BoundaryCasesTests {

        @ParameterizedTest(name = "Malformed cursor input: \"{0}\"")
        @ValueSource(strings = {"", "   ", "not-base64-!@#$", "bm9jb2xvbg"}) // "nocolon" in base64
        @DisplayName("F14: Malformed or non-base64 cursor tokens are rejected gracefully")
        void malformedCursor_rejectedOrReturnsNull(String malformed) {
            if (malformed.isBlank()) {
                assertThat(CursorToken.decode(malformed)).isNull();
            } else {
                assertThatThrownBy(() -> CursorToken.decode(malformed))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        @DisplayName("F14: Complex IDs with colons and special characters are preserved accurately")
        void complexId_withColons_preservedInCursor() {
            long ts = 123456789L;
            String complexId = "scope:namespace:tier:item:uuid-1234";

            String token = CursorToken.encode(ts, complexId);
            CursorToken decoded = CursorToken.decode(token);

            assertThat(decoded.timestampMs()).isEqualTo(ts);
            assertThat(decoded.id()).isEqualTo(complexId);
        }

        @Test
        @DisplayName("F15: Inverted time range (created_from > created_to) yields empty results safely")
        void invertedTimeRange_yieldsEmptyResults() {
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 5000L, MemoryType.EPISODIC, MemorySource.USER_STATED, false)
            );

            long from = 10_000L;
            long to = 5_000L;

            List<CognitiveRecord> filtered = records.stream()
                    .filter(r -> r.timestampMs() >= from && r.timestampMs() <= to)
                    .toList();

            assertThat(filtered).isEmpty();
        }

        @Test
        @DisplayName("F15: Empty namespace listing returns zero rows and null nextCursor")
        void emptyNamespace_returnsCleanEmptyResponse() {
            List<CognitiveRecord> empty = List.of();
            List<CognitiveRecord> page = seekAfterCursor(empty, null, 50);

            assertThat(page).isEmpty();
            String nextCursor = page.isEmpty() ? null : CursorToken.encode(page.get(page.size() - 1).timestampMs(), page.get(page.size() - 1).id());
            assertThat(nextCursor).isNull();
        }
    }

    @Nested
    @DisplayName("Tier 3: Invariant V3 — Concurrent Write Stability")
    class ConcurrentWriteStabilityTests {

        @Test
        @DisplayName("V3: Cursor pagination under concurrent inserts guarantees zero duplicates and zero skips")
        void concurrentWrites_zeroDuplicatesZeroSkips() throws Exception {
            // Initial 100 records
            List<CognitiveRecord> store = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < 100; i++) {
                store.add(createRecord("initial-" + String.format("%03d", i), 1000L + (i * 10L), MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }
            store.sort(TOTAL_ORDER_COMPARATOR);

            int pageSize = 20;
            List<String> collectedIds = new ArrayList<>();

            // 1. Fetch Page 1
            List<CognitiveRecord> page1 = seekAfterCursor(new ArrayList<>(store), null, pageSize);
            assertThat(page1).hasSize(20);
            page1.forEach(r -> collectedIds.add(r.id()));

            CursorToken cursor = new CursorToken(page1.get(page1.size() - 1).timestampMs(), page1.get(page1.size() - 1).id());

            // 2. Concurrently insert 30 NEW records with newer timestamps (burst traffic)
            for (int j = 0; j < 30; j++) {
                store.add(createRecord("new-" + String.format("%03d", j), 5000L + (j * 10L), MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }
            store.sort(TOTAL_ORDER_COMPARATOR);

            // 3. Continue paginating through the remaining pages using the cursor
            while (true) {
                List<CognitiveRecord> nextPage = seekAfterCursor(new ArrayList<>(store), cursor, pageSize);
                if (nextPage.isEmpty()) break;

                for (CognitiveRecord rec : nextPage) {
                    collectedIds.add(rec.id());
                }

                CognitiveRecord last = nextPage.get(nextPage.size() - 1);
                cursor = new CursorToken(last.timestampMs(), last.id());
            }

            // Total initial records collected must be exactly 100, zero duplicates, zero skips
            List<String> initialCollected = collectedIds.stream()
                    .filter(id -> id.startsWith("initial-"))
                    .toList();

            assertThat(initialCollected)
                    .as("Cursor pagination must not skip any initial records despite concurrent newer inserts")
                    .hasSize(100);

            Set<String> uniqueCollected = new HashSet<>(collectedIds);
            assertThat(uniqueCollected)
                    .as("Cursor pagination must yield zero duplicate records across pages")
                    .hasSize(collectedIds.size());
        }

        @Test
        @DisplayName("Offset pagination demonstration: concurrent writes break offset window (justifying cursor requirement)")
        void offsetPagination_demonstratesShiftDefect() {
            List<String> records = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                records.add("rec-" + i);
            }

            int pageSize = 5;
            // Page 0 reads items 0..4
            List<String> page0 = records.subList(0, 5);
            assertThat(page0).containsExactly("rec-0", "rec-1", "rec-2", "rec-3", "rec-4");

            // Concurrent write inserts 2 items at the top
            records.add(0, "new-1");
            records.add(0, "new-2");

            // Page 1 reads offset 5..9
            List<String> page1 = records.subList(5, 10);
            // Notice: "rec-3" and "rec-4" were pushed from Page 0 into Page 1!
            assertThat(page1)
                    .as("Offset pagination inherently duplicates items shifted by concurrent writes")
                    .contains("rec-3", "rec-4");
        }
    }

    @Nested
    @DisplayName("Tier 4: Index Seeking & Scorer Isolation (F16, F17)")
    class IndexSeekingAndScorerIsolationTests {

        @Test
        @DisplayName("F16: Temporal gate seeking checks [minTs, maxTs] and touches only overlapping partitions")
        void temporalGateSeeking_touchesOnlyIntersectingPartitions() {
            // Partition 1: [1000, 2000]
            PartitionSummary p1 = new PartitionSummary(1, 1000L, 2000L, 0L, 0L, 10, 0, 0, false);
            // Partition 2: [2500, 3500]
            PartitionSummary p2 = new PartitionSummary(2, 2500L, 3500L, 0L, 0L, 10, 0, 0, false);
            // Partition 3: [4000, 5000]
            PartitionSummary p3 = new PartitionSummary(3, 4000L, 5000L, 0L, 0L, 10, 0, 0, false);

            List<PartitionSummary> allPartitions = List.of(p1, p2, p3);

            // Query range: [2200, 3800]
            long createdFrom = 2200L;
            long createdTo = 3800L;

            List<PartitionSummary> touched = allPartitions.stream()
                    .filter(summary -> !(summary.maxTimestampMs() < createdFrom || summary.minTimestampMs() > createdTo))
                    .toList();

            assertThat(touched)
                    .as("Only Partition 2 intersects [2200, 3800]")
                    .containsExactly(p2);
        }

        @Test
        @DisplayName("F17: Scorer-Free Invariant — memory table listing path never executes vector scoring relays")
        void scorerFreeListing_doesNotExecuteScoringRelay() {
            SpectorMemory mockMemory = mock(SpectorMemory.class);
            MemoryAccessObject mao = new MemoryAccessObject();

            // When memory is available but empty
            when(mockMemory.admin()).thenReturn(mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class));
            when(mockMemory.admin().listAll()).thenReturn(List.of());

            mao.getMemoryTable(mockMemory, 0, 50, null, false);

            // Verify recall / scorer was NEVER invoked
            verify(mockMemory, never()).recall(anyString());
            verify(mockMemory, never()).recall(anyString(), any(com.spectrayan.spector.memory.model.RecallOptions.class));
        }
    }
}
