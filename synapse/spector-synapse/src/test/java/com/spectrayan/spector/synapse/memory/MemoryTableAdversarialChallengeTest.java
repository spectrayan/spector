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
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.synapse.config.GlobalExceptionHandler;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 5 (Requirement R5 / Features F13–F17).
 *
 * <p>Validates:
 * <ul>
 *   <li>1. Monotonicity: full page walks have zero duplicates and zero skipped records across all pages.</li>
 *   <li>2. Timestamp collision: secondary sort on id DESC strictly breaks ties with deterministic page boundaries.</li>
 *   <li>3. Concurrent writes: newer and older writes during cursor walks maintain total order stability (Invariant V3).</li>
 *   <li>4. Malformed cursor: invalid base64 and non-numeric inputs yield clean 400 Bad Request without 500 NPE or crash.</li>
 * </ul>
 */
@DisplayName("MemoryTableAdversarialChallengeTest — M5 Cursor Listing & Total Order Adversarial Challenge")
class MemoryTableAdversarialChallengeTest {

    private MemoryAccessObject mao;
    private SpectorMemory mockMemory;
    private SpectorMemoryAdmin mockAdmin;

    @BeforeEach
    void setup() {
        mao = new MemoryAccessObject();
        mockMemory = mock(SpectorMemory.class);
        mockAdmin = mock(SpectorMemoryAdmin.class);
        when(mockMemory.admin()).thenReturn(mockAdmin);
    }

    private CognitiveRecord createRecord(String id, long timestampMs, MemoryType type, MemorySource source, boolean tombstoned) {
        CognitiveRecord rec = mock(CognitiveRecord.class);
        when(rec.id()).thenReturn(id);
        when(rec.timestampMs()).thenReturn(timestampMs);
        when(rec.memoryType()).thenReturn(type != null ? type : MemoryType.SEMANTIC);
        when(rec.source()).thenReturn(source != null ? source : MemorySource.OBSERVED);
        when(rec.isTombstoned()).thenReturn(tombstoned);
        when(rec.text()).thenReturn("Content for " + id);
        when(rec.createdAt()).thenReturn(Instant.ofEpochMilli(timestampMs));
        when(rec.metadata()).thenReturn(Collections.emptyMap());
        when(rec.tags()).thenReturn(new String[0]);
        when(rec.synapticTags()).thenReturn(0L);
        return rec;
    }

    @Nested
    @DisplayName("Challenge 1: Monotonicity & Complete Paging Traversals")
    class MonotonicityTests {

        @ParameterizedTest(name = "Page size: {0}")
        @ValueSource(ints = {1, 2, 5, 13, 25, 50, 100})
        @DisplayName("Zero duplicates and zero skipped records across multi-page traversals of diverse page sizes")
        void walkEntireDataset_zeroDuplicatesZeroSkips(int pageSize) {
            int totalRecords = 100;
            List<CognitiveRecord> store = new ArrayList<>();
            for (int i = 0; i < totalRecords; i++) {
                store.add(createRecord("mem-" + String.format("%04d", i), 1000L + (i * 10L),
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            }
            when(mockAdmin.listAll()).thenReturn(store);

            List<String> visitedIds = new ArrayList<>();
            String cursor = null;
            int pageCount = 0;

            while (true) {
                MemoryTableResponse response = mao.getMemoryTable(mockMemory, cursor, 0, pageSize,
                        null, null, null, null, false);
                assertThat(response).isNotNull();
                pageCount++;

                List<MemoryTableRow> rows = response.rows();
                if (rows.isEmpty()) {
                    break;
                }

                for (MemoryTableRow row : rows) {
                    visitedIds.add(row.id());
                }

                cursor = response.nextCursor();
                if (cursor == null) {
                    break; // Final page reached
                }
            }

            assertThat(visitedIds)
                    .as("Must visit exactly all %d records across pages", totalRecords)
                    .hasSize(totalRecords);

            Set<String> uniqueIds = new HashSet<>(visitedIds);
            assertThat(uniqueIds)
                    .as("Must contain ZERO duplicates across pages")
                    .hasSize(totalRecords);

            // Verify order matches expected total order (timestampMs DESC, id DESC)
            List<CognitiveRecord> expectedOrder = new ArrayList<>(store);
            expectedOrder.sort(MemoryAccessObject.TOTAL_ORDER_COMPARATOR);
            List<String> expectedIds = expectedOrder.stream().map(CognitiveRecord::id).toList();

            assertThat(visitedIds)
                    .as("Visited IDs must strictly match total order sequence without skips or misordering")
                    .containsExactlyElementsOf(expectedIds);

            int expectedPages = (int) Math.ceil((double) totalRecords / pageSize);
            assertThat(pageCount)
                    .as("Page count should match expected page boundaries")
                    .isEqualTo(expectedPages);
        }

        @Test
        @DisplayName("Single-record dataset returns the record with null nextCursor")
        void singleRecord_returnsCleanNullCursor() {
            CognitiveRecord single = createRecord("solo", 5000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false);
            when(mockAdmin.listAll()).thenReturn(List.of(single));

            MemoryTableResponse resp = mao.getMemoryTable(mockMemory, null, 0, 10,
                    null, null, null, null, false);

            assertThat(resp.rows()).hasSize(1);
            assertThat(resp.rows().get(0).id()).isEqualTo("solo");
            assertThat(resp.nextCursor()).isNull();
        }

        @Test
        @DisplayName("Empty dataset returns zero rows with null nextCursor")
        void emptyDataset_returnsEmptyRowsNullCursor() {
            when(mockAdmin.listAll()).thenReturn(List.of());

            MemoryTableResponse resp = mao.getMemoryTable(mockMemory, null, 0, 10,
                    null, null, null, null, false);

            assertThat(resp.rows()).isEmpty();
            assertThat(resp.nextCursor()).isNull();
            assertThat(resp.totalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Exact multiple of pageSize produces null nextCursor on the final page without extra empty page")
        void exactMultipleOfPageSize_terminatesCleanlyOnFinalPage() {
            int totalRecords = 20;
            int pageSize = 10;
            List<CognitiveRecord> store = new ArrayList<>();
            for (int i = 0; i < totalRecords; i++) {
                store.add(createRecord("mem-" + i, 1000L + (i * 10L), MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            }
            when(mockAdmin.listAll()).thenReturn(store);

            // Page 1
            MemoryTableResponse page1 = mao.getMemoryTable(mockMemory, null, 0, pageSize, null, null, null, null, false);
            assertThat(page1.rows()).hasSize(10);
            assertThat(page1.nextCursor()).isNotNull();

            // Page 2 (Final page)
            MemoryTableResponse page2 = mao.getMemoryTable(mockMemory, page1.nextCursor(), 0, pageSize, null, null, null, null, false);
            assertThat(page2.rows()).hasSize(10);
            assertThat(page2.nextCursor())
                    .as("Final page of exact multiple must have null nextCursor")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Challenge 2: Timestamp Collisions & Tie-Breaker Total Order Stability")
    class TimestampCollisionTests {

        @Test
        @DisplayName("50 records with identical millisecond timestamps are strictly sorted by ID descending")
        void identicalTimestamp_sortedStrictlyByIdDescending() {
            long sharedTs = 1700000000000L;
            List<CognitiveRecord> records = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                records.add(createRecord("item-" + String.format("%02d", i), sharedTs,
                        MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }
            Collections.shuffle(records, new Random(42));
            when(mockAdmin.listAll()).thenReturn(records);

            MemoryTableResponse response = mao.getMemoryTable(mockMemory, null, 0, 50,
                    null, null, null, null, false);

            List<String> returnedIds = response.rows().stream().map(MemoryTableRow::id).toList();
            List<String> expectedIds = records.stream()
                    .map(CognitiveRecord::id)
                    .sorted(Comparator.reverseOrder())
                    .toList();

            assertThat(returnedIds)
                    .as("Identical timestamps must be tie-broken strictly by id DESC")
                    .containsExactlyElementsOf(expectedIds);
        }

        @Test
        @DisplayName("Paging across identical-timestamp boundary retains zero duplicates and zero skips")
        void identicalTimestamp_pagingAcrossBoundaries_zeroDuplicatesZeroSkips() {
            long sharedTs = 1700000000000L;
            int total = 30;
            List<CognitiveRecord> records = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                records.add(createRecord("rec-" + String.format("%02d", i), sharedTs,
                        MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }
            when(mockAdmin.listAll()).thenReturn(records);

            int pageSize = 7; // Non-divisor to test boundary splits
            List<String> pagedIds = new ArrayList<>();
            String cursor = null;

            while (true) {
                MemoryTableResponse resp = mao.getMemoryTable(mockMemory, cursor, 0, pageSize,
                        null, null, null, null, false);
                if (resp.rows().isEmpty()) break;

                for (MemoryTableRow row : resp.rows()) {
                    pagedIds.add(row.id());
                }

                cursor = resp.nextCursor();
                if (cursor == null) break;
            }

            assertThat(pagedIds).hasSize(total);
            assertThat(new HashSet<>(pagedIds)).hasSize(total); // Zero duplicates

            List<String> expected = records.stream().map(CognitiveRecord::id).sorted(Comparator.reverseOrder()).toList();
            assertThat(pagedIds).containsExactlyElementsOf(expected); // Zero skips
        }

        @Test
        @DisplayName("Multiple collision clusters (discrete timestamp buckets) maintain deterministic total order")
        void multipleCollisionClusters_maintainsDeterministicTotalOrder() {
            // Three discrete timestamp clusters: 3000L, 2000L, 1000L
            List<CognitiveRecord> cluster3k = List.of(
                    createRecord("c3-a", 3000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false),
                    createRecord("c3-b", 3000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false),
                    createRecord("c3-c", 3000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false)
            );
            List<CognitiveRecord> cluster2k = List.of(
                    createRecord("c2-x", 2000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false),
                    createRecord("c2-y", 2000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false)
            );
            List<CognitiveRecord> cluster1k = List.of(
                    createRecord("c1-m", 1000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false),
                    createRecord("c1-n", 1000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false)
            );

            List<CognitiveRecord> all = new ArrayList<>();
            all.addAll(cluster1k);
            all.addAll(cluster2k);
            all.addAll(cluster3k);
            Collections.shuffle(all, new Random(99));
            when(mockAdmin.listAll()).thenReturn(all);

            int pageSize = 2;
            List<String> visited = new ArrayList<>();
            String cursor = null;

            while (true) {
                MemoryTableResponse resp = mao.getMemoryTable(mockMemory, cursor, 0, pageSize,
                        null, null, null, null, false);
                if (resp.rows().isEmpty()) break;
                resp.rows().forEach(r -> visited.add(r.id()));
                cursor = resp.nextCursor();
                if (cursor == null) break;
            }

            // Expected order:
            // 3000L: c3-c, c3-b, c3-a
            // 2000L: c2-y, c2-x
            // 1000L: c1-n, c1-m
            assertThat(visited).containsExactly("c3-c", "c3-b", "c3-a", "c2-y", "c2-x", "c1-n", "c1-m");
        }
    }

    @Nested
    @DisplayName("Challenge 3: Invariant V3 — Concurrent Writes Stability")
    class ConcurrentWritesStabilityTests {

        @Test
        @DisplayName("V3: Concurrent insertion of NEWER timestamps does not alter downstream cursor window")
        void concurrentWrites_newerTimestamps_zeroDuplicatesZeroSkips() {
            List<CognitiveRecord> liveStore = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 50; i++) {
                liveStore.add(createRecord("initial-" + String.format("%03d", i), 1000L + (i * 10L),
                        MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }
            when(mockAdmin.listAll()).thenAnswer(inv -> new ArrayList<>(liveStore));

            int pageSize = 10;
            List<String> collected = new ArrayList<>();

            // 1. Fetch Page 1
            MemoryTableResponse page1 = mao.getMemoryTable(mockMemory, null, 0, pageSize, null, null, null, null, false);
            assertThat(page1.rows()).hasSize(10);
            page1.rows().forEach(r -> collected.add(r.id()));
            String cursor = page1.nextCursor();
            assertThat(cursor).isNotNull();

            // 2. Concurrently insert 40 brand NEW records with newer timestamps (ts: 5000L..5400L)
            for (int j = 0; j < 40; j++) {
                liveStore.add(createRecord("burst-" + String.format("%03d", j), 5000L + (j * 10L),
                        MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }

            // 3. Continue paginating using the cursor
            while (cursor != null) {
                MemoryTableResponse nextPage = mao.getMemoryTable(mockMemory, cursor, 0, pageSize, null, null, null, null, false);
                if (nextPage.rows().isEmpty()) break;
                nextPage.rows().forEach(r -> collected.add(r.id()));
                cursor = nextPage.nextCursor();
            }

            // All 50 initial records must be collected
            List<String> initialCollected = collected.stream().filter(id -> id.startsWith("initial-")).toList();
            assertThat(initialCollected)
                    .as("All 50 initial records must be collected despite concurrent newer writes")
                    .hasSize(50);

            // Zero duplicate records across the walk
            Set<String> unique = new HashSet<>(collected);
            assertThat(unique)
                    .as("Concurrent newer writes must cause zero duplicates across pages")
                    .hasSize(collected.size());

            // Burst records must NOT appear downstream from the cursor
            List<String> burstInCollected = collected.stream().filter(id -> id.startsWith("burst-")).toList();
            assertThat(burstInCollected)
                    .as("Newer burst records inserted after cursor start must not appear downstream")
                    .isEmpty();
        }

        @Test
        @DisplayName("V3: Concurrent insertion of OLDER timestamps appears cleanly downstream without duplicating prior records")
        void concurrentWrites_olderTimestamps_traversedDownstreamCleanly() {
            List<CognitiveRecord> liveStore = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 20; i++) {
                liveStore.add(createRecord("initial-" + String.format("%02d", i), 5000L + (i * 10L),
                        MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }
            when(mockAdmin.listAll()).thenAnswer(inv -> new ArrayList<>(liveStore));

            int pageSize = 10;
            List<String> collected = new ArrayList<>();

            // 1. Fetch Page 1
            MemoryTableResponse page1 = mao.getMemoryTable(mockMemory, null, 0, pageSize, null, null, null, null, false);
            assertThat(page1.rows()).hasSize(10);
            page1.rows().forEach(r -> collected.add(r.id()));
            String cursor = page1.nextCursor();

            // 2. Concurrently insert 10 older records (ts: 100L..200L)
            for (int j = 0; j < 10; j++) {
                liveStore.add(createRecord("old-" + String.format("%02d", j), 100L + (j * 10L),
                        MemoryType.EPISODIC, MemorySource.USER_STATED, false));
            }

            // 3. Continue paginating
            while (cursor != null) {
                MemoryTableResponse nextPage = mao.getMemoryTable(mockMemory, cursor, 0, pageSize, null, null, null, null, false);
                if (nextPage.rows().isEmpty()) break;
                nextPage.rows().forEach(r -> collected.add(r.id()));
                cursor = nextPage.nextCursor();
            }

            // Must have collected 20 initial + 10 old = 30 total records
            assertThat(collected).hasSize(30);
            assertThat(new HashSet<>(collected)).hasSize(30); // Zero duplicates
        }

        @Test
        @DisplayName("V3: Concurrent insertion of records with SAME timestamp as cursor correctly splits by ID")
        void concurrentWrites_sameTimestamp_correctlySplitById() {
            long ts = 3000L;
            List<CognitiveRecord> liveStore = new CopyOnWriteArrayList<>();
            liveStore.add(createRecord("id-50", ts, MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            liveStore.add(createRecord("id-40", ts, MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            liveStore.add(createRecord("id-20", ts, MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            liveStore.add(createRecord("id-10", ts, MemoryType.SEMANTIC, MemorySource.OBSERVED, false));

            when(mockAdmin.listAll()).thenAnswer(inv -> new ArrayList<>(liveStore));

            // Fetch Page 1 (pageSize 2) -> returns id-50, id-40
            MemoryTableResponse page1 = mao.getMemoryTable(mockMemory, null, 0, 2, null, null, null, null, false);
            assertThat(page1.rows().stream().map(MemoryTableRow::id).toList()).containsExactly("id-50", "id-40");

            String cursor = page1.nextCursor();
            // Cursor represents (3000L, "id-40")

            // Concurrent writes:
            // 1) id-45: same ts, ID > "id-40" (belongs upstream, must NOT appear in page 2)
            // 2) id-30: same ts, ID < "id-40" (belongs downstream, MUST appear in page 2)
            liveStore.add(createRecord("id-45", ts, MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            liveStore.add(createRecord("id-30", ts, MemoryType.SEMANTIC, MemorySource.OBSERVED, false));

            // Fetch Page 2
            MemoryTableResponse page2 = mao.getMemoryTable(mockMemory, cursor, 0, 5, null, null, null, null, false);
            List<String> page2Ids = page2.rows().stream().map(MemoryTableRow::id).toList();

            assertThat(page2Ids)
                    .as("Page 2 must contain id-30 (lower ID) and initial downstream items, but NOT id-45 (higher ID)")
                    .containsExactly("id-30", "id-20", "id-10");
        }

        @Test
        @DisplayName("V3: Heavy concurrent multi-threaded writes and reads maintain monotonic page progression")
        void multiThreadedConcurrency_invariantV3Maintained() throws Exception {
            List<CognitiveRecord> store = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 200; i++) {
                store.add(createRecord("seed-" + String.format("%04d", i), 1000L + (i * 5L),
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
            }
            when(mockAdmin.listAll()).thenAnswer(inv -> new ArrayList<>(store));

            int readers = 4;
            int writers = 2;
            ExecutorService executor = Executors.newFixedThreadPool(readers + writers);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicBoolean stop = new AtomicBoolean(false);
            AtomicInteger writeCounter = new AtomicInteger(0);
            List<Future<List<String>>> readerFutures = new ArrayList<>();

            // Launch writers
            for (int w = 0; w < writers; w++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        while (!stop.get()) {
                            int idx = writeCounter.incrementAndGet();
                            long randomTs = 500L + (long) (Math.random() * 5000L);
                            store.add(createRecord("dyn-" + idx, randomTs, MemoryType.SEMANTIC, MemorySource.OBSERVED, false));
                            Thread.sleep(2);
                        }
                    } catch (Exception ignored) {}
                });
            }

            // Launch readers
            for (int r = 0; r < readers; r++) {
                readerFutures.add(executor.submit(() -> {
                    startLatch.await();
                    List<String> allRead = new ArrayList<>();
                    String cursor = null;
                    int pagesRead = 0;
                    while (pagesRead < 15) {
                        MemoryTableResponse resp = mao.getMemoryTable(mockMemory, cursor, 0, 10,
                                null, null, null, null, false);
                        pagesRead++;
                        if (resp.rows().isEmpty()) break;
                        for (MemoryTableRow row : resp.rows()) {
                            allRead.add(row.id());
                        }
                        cursor = resp.nextCursor();
                        if (cursor == null) break;
                    }
                    return allRead;
                }));
            }

            startLatch.countDown();
            // Let it run for 1 second under active concurrency
            Thread.sleep(1000);
            stop.set(true);
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            for (Future<List<String>> future : readerFutures) {
                List<String> readIds = future.get();
                Set<String> uniqueIds = new HashSet<>(readIds);
                assertThat(uniqueIds)
                        .as("Every concurrent reader must see zero duplicate records")
                        .hasSize(readIds.size());
            }
        }
    }

    @Nested
    @DisplayName("Challenge 4: Malformed Cursors & HTTP 400 Bad Request Semantics")
    class MalformedCursorAndErrorHandlingTests {

        private MockMvc mockMvc;

        @BeforeEach
        void initControllerMvc() {
            EventPublisher eventPublisher = mock(EventPublisher.class);
            TsidGenerator tsid = mock(TsidGenerator.class);
            @SuppressWarnings("unchecked")
            ObjectProvider<SpectorMemory> memoryProvider = mock(ObjectProvider.class);
            when(memoryProvider.getIfAvailable()).thenReturn(mockMemory);

            MemoryService service = new MemoryService(mao, eventPublisher, tsid, null, memoryProvider, null);
            MemoryController controller = new MemoryController(service);

            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
        }

        @ParameterizedTest(name = "Malformed cursor raw input: \"{0}\"")
        @ValueSource(strings = {
                "not-valid-base64-!@#$",
                "bm9jb2xvbg",                  // Base64 for "nocolon"
                "bm9udW1lcmljOnNvbWVpZA",      // Base64 for "nonumeric:someid"
                "Om9ubHlpZA",                  // Base64 for ":onlyid"
                "LS1pbnZhbGlkOnNvbWVpZA",      // Base64 for "--invalid:someid"
                "OTk5OTk5OTk5OTk5OTk5OTk5OTk5OTk5OTk5OTk5OTk5OTk5Omlk" // Base64 for overflow long:id
        })
        @DisplayName("CursorToken.decode throws IllegalArgumentException on all invalid formats")
        void cursorTokenDecode_throwsIllegalArgumentException(String malformed) {
            assertThatThrownBy(() -> CursorToken.decode(malformed))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CursorToken.decode returns null on null or blank inputs")
        void cursorTokenDecode_nullOrBlank_returnsNull() {
            assertThat(CursorToken.decode(null)).isNull();
            assertThat(CursorToken.decode("")).isNull();
            assertThat(CursorToken.decode("   ")).isNull();
        }

        @Test
        @DisplayName("Complex ID with colons and special characters round-trips flawlessly")
        void complexId_withMultipleColons_roundTrips() {
            long ts = 1727220000000L;
            String complexId = "urn:spector:mem:core:v1:01HX87654321";

            String encoded = CursorToken.encode(ts, complexId);
            CursorToken decoded = CursorToken.decode(encoded);

            assertThat(decoded).isNotNull();
            assertThat(decoded.timestampMs()).isEqualTo(ts);
            assertThat(decoded.id()).isEqualTo(complexId);
        }

        @Test
        @DisplayName("GET /table with invalid base64 cursor returns HTTP 400 Bad Request, NOT 500")
        void controller_invalidBase64Cursor_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/memory/table")
                            .param("cursor", "invalid!@#$base64"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.error", is("Bad Request")));
        }

        @Test
        @DisplayName("GET /table with non-numeric timestamp cursor returns HTTP 400 Bad Request, NOT 500")
        void controller_nonNumericTimestampCursor_returns400() throws Exception {
            // "nonumeric:id123" in URL-safe base64
            String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString("nonumeric:id123".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(get("/api/v1/memory/table")
                            .param("cursor", cursor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.error", is("Bad Request")));
        }

        @Test
        @DisplayName("GET /table with missing separator cursor returns HTTP 400 Bad Request, NOT 500")
        void controller_missingSeparatorCursor_returns400() throws Exception {
            // "noseparatorhere" in URL-safe base64
            String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString("noseparatorhere".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(get("/api/v1/memory/table")
                            .param("cursor", cursor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.error", is("Bad Request")))
                    .andExpect(jsonPath("$.message", containsString("missing timestamp separator")));
        }

        @Test
        @DisplayName("GET /table with empty or blank cursor gracefully defaults to page 0 with HTTP 200")
        void controller_blankCursor_defaultsToPage0() throws Exception {
            when(mockAdmin.listAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/memory/table")
                            .param("cursor", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page", is(0)))
                    .andExpect(jsonPath("$.rows").isEmpty());
        }

        @Test
        @DisplayName("GET /table with inverted time bounds (created_from > created_to) returns HTTP 200 with empty rows")
        void controller_invertedTimeRange_returnsEmptyRows() throws Exception {
            CognitiveRecord r = createRecord("mem-1", 5000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false);
            when(mockAdmin.listAll()).thenReturn(List.of(r));

            mockMvc.perform(get("/api/v1/memory/table")
                            .param("created_from", "10000")
                            .param("created_to", "5000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows").isEmpty())
                    .andExpect(jsonPath("$.totalCount", is(0)));
        }
    }

    @Nested
    @DisplayName("Challenge 5: Index-Assisted Temporal Partition Seeking & Scorer Isolation")
    class IndexSeekingAndScorerIsolationChallengeTests {

        @Test
        @DisplayName("Temporal partition seeking bypasses out-of-range partitions via PartitionSummary bounds")
        void temporalSeeking_bypassesOutOfRangePartitions() {
            // Partition 1: [1000, 2000]
            PartitionHandle h1 = mock(PartitionHandle.class);
            when(h1.seq()).thenReturn(1);
            when(h1.summary()).thenReturn(new PartitionSummary(1, 1000L, 2000L, 0L, 0L, 1, 0, 0, false));

            // Partition 2: [3000, 4000]
            PartitionHandle h2 = mock(PartitionHandle.class);
            when(h2.seq()).thenReturn(2);
            when(h2.summary()).thenReturn(new PartitionSummary(2, 3000L, 4000L, 0L, 0L, 1, 0, 0, false));

            // Partition 3: [5000, 6000]
            PartitionHandle h3 = mock(PartitionHandle.class);
            when(h3.seq()).thenReturn(3);
            when(h3.summary()).thenReturn(new PartitionSummary(3, 5000L, 6000L, 0L, 0L, 1, 0, 0, false));

            PartitionRegistry registry = mock(PartitionRegistry.class);
            when(registry.snapshot()).thenReturn(List.of(h1, h2, h3));
            when(mockAdmin.partitionRegistry()).thenReturn(registry);

            MemoryIndex index = mock(MemoryIndex.class);
            MemoryLocation loc1 = mock(MemoryLocation.class);
            when(loc1.colocatedPartition()).thenReturn(1);
            when(loc1.type()).thenReturn(MemoryType.SEMANTIC);

            MemoryLocation loc2 = mock(MemoryLocation.class);
            when(loc2.colocatedPartition()).thenReturn(2);
            when(loc2.type()).thenReturn(MemoryType.SEMANTIC);

            MemoryLocation loc3 = mock(MemoryLocation.class);
            when(loc3.colocatedPartition()).thenReturn(3);
            when(loc3.type()).thenReturn(MemoryType.SEMANTIC);

            ConcurrentHashMap<String, MemoryLocation> locationMap = new ConcurrentHashMap<>();
            locationMap.put("mem-p1", loc1);
            locationMap.put("mem-p2", loc2);
            locationMap.put("mem-p3", loc3);
            doReturn(locationMap).when(index).locationMap();
            when(mockAdmin.index()).thenReturn(index);

            CognitiveRecord recP2 = createRecord("mem-p2", 3500L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false);
            when(mockMemory.inspect("mem-p2")).thenReturn(recP2);

            // Filter for [2500, 4500] -> touches ONLY Partition 2
            MemoryTableResponse resp = mao.getMemoryTable(mockMemory, null, 0, 10,
                    2500L, 4500L, null, null, false);

            assertThat(resp.rows()).hasSize(1);
            assertThat(resp.rows().get(0).id()).isEqualTo("mem-p2");

            // Inspect should NEVER be called for mem-p1 or mem-p3
            verify(mockMemory, never()).inspect("mem-p1");
            verify(mockMemory, never()).inspect("mem-p3");
        }

        @Test
        @DisplayName("Listing path never invokes 6-phase vector recall scorer")
        void listingPath_scorerNeverInvoked() {
            CognitiveRecord rec1 = createRecord("rec-1", 1000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, false);
            when(mockAdmin.listAll()).thenReturn(List.of(rec1));

            mao.getMemoryTable(mockMemory, null, 0, 10, null, null, null, null, false);

            verify(mockMemory, never()).recall(anyString());
            verify(mockMemory, never()).recall(anyString(), any(com.spectrayan.spector.memory.model.RecallOptions.class));
        }
    }
}
