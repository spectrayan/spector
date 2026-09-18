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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.graph.EntityType;
import com.spectrayan.spector.memory.scheduler.QuartzMemoryScheduler;
import com.spectrayan.spector.memory.scheduler.jobs.IndexReconcileJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("ADR-0082 Index Reconcile Engine & Cooperative Drift Reconciliation Tests")
class IndexReconcileEngineTest {

    @Test
    @DisplayName("reconcile detects and repairs entity reverse index drift")
    void reconcile_entityReverseDrift_detectsAndRepairs() {
        EntityDirectory dir = mock(EntityDirectory.class);
        when(dir.entityCount()).thenReturn(2);

        // Entity 0 references memory slot 42, but reverse set is missing entity 0
        when(dir.memoryRefCount(0)).thenReturn(1);
        when(dir.memoryRefAt(0, 0)).thenReturn(42);
        when(dir.entityIdsForMemory(42)).thenReturn(Set.of()); // reverse drift
        when(dir.repairMemoryToEntityMapping(42, 0)).thenReturn(true);

        // Entity 1 references memory slot 99, reverse set is consistent
        when(dir.memoryRefCount(1)).thenReturn(1);
        when(dir.memoryRefAt(1, 0)).thenReturn(99);
        when(dir.entityIdsForMemory(99)).thenReturn(Set.of(1));

        IndexReconcileEngine engine = new IndexReconcileEngine(dir, null, null, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.scannedEntities()).isEqualTo(2);
        assertThat(report.missingReverseMappings()).isEqualTo(1);
        assertThat(report.repairedReverseMappings()).isEqualTo(1);
        assertThat(report.hasRepairs()).isTrue();
        assertThat(report.truncated()).isFalse();

        verify(dir, times(1)).repairMemoryToEntityMapping(42, 0);
    }

    @Test
    @DisplayName("reconcile detects and repairs BM25 lexical index drift")
    void reconcile_lexicalDrift_detectsAndRepairs() {
        MemoryIndex memIndex = mock(MemoryIndex.class);
        MemoryBM25Index bm25Index = mock(MemoryBM25Index.class);

        when(memIndex.size()).thenReturn(2);
        when(bm25Index.totalDocuments()).thenReturn(1);

        MemoryLocation loc1 = mock(MemoryLocation.class);
        MemoryLocation loc2 = mock(MemoryLocation.class);

        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        locMap.put("mem-1", loc1);
        locMap.put("mem-2", loc2);

        when(memIndex.locationMap()).thenReturn(locMap);
        when(bm25Index.contains("mem-1")).thenReturn(true);
        when(bm25Index.contains("mem-2")).thenReturn(false);
        when(memIndex.text("mem-2")).thenReturn("Cognitive architecture and active inference");

        IndexReconcileEngine engine = new IndexReconcileEngine(null, memIndex, bm25Index, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.scannedLexicalDocs()).isEqualTo(2);
        assertThat(report.missingLexicalEntries()).isEqualTo(1);
        assertThat(report.repairedLexicalEntries()).isEqualTo(1);
        assertThat(report.hasRepairs()).isTrue();

        verify(bm25Index, times(1)).index(0, "mem-2", "Cognitive architecture and active inference");
    }

    @Test
    @DisplayName("reconcile respects maxRepairsPerCycle budget limit")
    void reconcile_cooperativeBudget_truncatesOnMaxRepairs() {
        EntityDirectory dir = mock(EntityDirectory.class);
        when(dir.entityCount()).thenReturn(5);

        for (int i = 0; i < 5; i++) {
            when(dir.memoryRefCount(i)).thenReturn(1);
            when(dir.memoryRefAt(i, 0)).thenReturn(100 + i);
            when(dir.entityIdsForMemory(100 + i)).thenReturn(Set.of());
            when(dir.repairMemoryToEntityMapping(100 + i, i)).thenReturn(true);
        }

        // Limit to 2 repairs per cycle
        IndexReconcileEngine engine = new IndexReconcileEngine(dir, null, null, 5000L, 2);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.repairedReverseMappings()).isEqualTo(2);
        assertThat(report.truncated()).isTrue();
    }

    @Test
    @DisplayName("real EntityDirectory repairMemoryToEntityMapping adds missing mapping idempotently")
    void realEntityDirectory_repairMemoryToEntityMapping_idempotency() {
        TypeRegistryMemory reg = TypeRegistryMemory.seeded(com.spectrayan.spector.kernel.id.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        try (EntityDirectory dir = new EntityDirectory(64, reg)) {
            int alice = dir.intern("Alice", "PERSON");

            // Initial repair returns true because slot 7 is not mapped
            boolean addedFirst = dir.repairMemoryToEntityMapping(7, alice);
            assertThat(addedFirst).isTrue();
            assertThat(dir.entityIdsForMemory(7)).contains(alice);

            // Second repair returns false because slot 7 is already mapped
            boolean addedSecond = dir.repairMemoryToEntityMapping(7, alice);
            assertThat(addedSecond).isFalse();

            // Out-of-bounds or invalid returns false
            assertThat(dir.repairMemoryToEntityMapping(-1, alice)).isFalse();
            assertThat(dir.repairMemoryToEntityMapping(7, 999)).isFalse();

            // Real removeMemoryToEntityMapping removes mapping cleanly
            boolean removed = dir.removeMemoryToEntityMapping(7, alice);
            assertThat(removed).isTrue();
            assertThat(dir.entityIdsForMemory(7)).isEmpty();
            assertThat(dir.indexedMemorySlots()).doesNotContain(7);

            // Removing non-existent mapping returns false
            assertThat(dir.removeMemoryToEntityMapping(7, alice)).isFalse();
        }
    }

    @Test
    @DisplayName("reconcile detects and drops dangling entity reverse index mappings")
    void reconcile_danglingReverse_detectsAndRemoves() {
        EntityDirectory dir = mock(EntityDirectory.class);
        when(dir.entityCount()).thenReturn(1);
        when(dir.memoryRefCount(0)).thenReturn(0); // Entity 0 references no memories

        // Reverse map has slot 50 -> entity 0 (dangling forward ref) and slot 51 -> entity 999 (out of bounds)
        when(dir.indexedMemorySlots()).thenReturn(Set.of(50, 51));
        when(dir.entityIdsForMemory(50)).thenReturn(Set.of(0));
        when(dir.entityIdsForMemory(51)).thenReturn(Set.of(999));
        when(dir.removeMemoryToEntityMapping(50, 0)).thenReturn(true);
        when(dir.removeMemoryToEntityMapping(51, 999)).thenReturn(true);

        IndexReconcileEngine engine = new IndexReconcileEngine(dir, null, null, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.danglingReverseMappings()).isEqualTo(2);
        assertThat(report.repairedReverseMappings()).isEqualTo(2);
        verify(dir, times(1)).removeMemoryToEntityMapping(50, 0);
        verify(dir, times(1)).removeMemoryToEntityMapping(51, 999);
    }

    @Test
    @DisplayName("reconcile detects and drops stale BM25 index postings")
    void reconcile_staleBM25Postings_detectsAndRemoves() {
        MemoryIndex memIndex = mock(MemoryIndex.class);
        MemoryBM25Index bm25Index = mock(MemoryBM25Index.class);

        MemoryLocation loc = mock(MemoryLocation.class);
        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        locMap.put("live-doc", loc);

        when(memIndex.locationMap()).thenReturn(locMap);
        when(bm25Index.contains("live-doc")).thenReturn(true);
        when(bm25Index.docIds()).thenReturn(Set.of("live-doc", "stale-doc"));

        IndexReconcileEngine engine = new IndexReconcileEngine(null, memIndex, bm25Index, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.staleLexicalEntries()).isEqualTo(1);
        assertThat(report.repairedLexicalEntries()).isEqualTo(1);
        verify(bm25Index, times(1)).remove("stale-doc");
        verify(bm25Index, never()).remove("live-doc");
    }

    @Test
    @DisplayName("reconcile flags SPLADE drift without neural inference and drops stale postings")
    void reconcile_spladeDrift_flagsWithoutInference() {
        MemoryIndex memIndex = mock(MemoryIndex.class);
        com.spectrayan.spector.memory.cortex.MemorySpladeIndex spladeIndex =
                mock(com.spectrayan.spector.memory.cortex.MemorySpladeIndex.class);
        com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider provider =
                mock(com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider.class);

        when(spladeIndex.provider()).thenReturn(provider);

        MemoryLocation loc = mock(MemoryLocation.class);
        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        locMap.put("doc-splade", loc);

        when(memIndex.locationMap()).thenReturn(locMap);
        when(spladeIndex.contains("doc-splade")).thenReturn(false);
        when(spladeIndex.docIds()).thenReturn(Set.of("doc-splade", "stale-splade"));

        IndexReconcileEngine engine = new IndexReconcileEngine(null, memIndex, null, spladeIndex, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.missingSpladeEntries()).isEqualTo(1);
        assertThat(report.staleSpladeEntries()).isEqualTo(1);
        assertThat(report.repairedSpladeEntries()).isEqualTo(1);

        // Zero neural inference inside the cooperative time slice
        verify(provider, never()).encode(anyString());
        verify(spladeIndex, never()).index(anyInt(), anyString(), anyMap());
        verify(spladeIndex, times(1)).remove("stale-splade");
    }

    @Test
    @DisplayName("lexical scan cursor advances round-robin across cooperative cycles")
    void reconcile_lexicalScanCursor_advancesIncrementallyAcrossCycles() {
        MemoryIndex memIndex = mock(MemoryIndex.class);
        MemoryBM25Index bm25Index = mock(MemoryBM25Index.class);

        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        for (int i = 0; i < 4; i++) {
            locMap.put("doc-" + i, mock(MemoryLocation.class));
            when(memIndex.text("doc-" + i)).thenReturn("content for doc " + i);
        }

        when(memIndex.locationMap()).thenReturn(locMap);
        when(bm25Index.contains(anyString())).thenReturn(false);

        // Limit to 2 repairs per cycle
        IndexReconcileEngine engine = new IndexReconcileEngine(null, memIndex, bm25Index, 5000L, 2);

        IndexReconcileReport cycle1 = engine.reconcile();
        assertThat(cycle1.repairedLexicalEntries()).isEqualTo(2);
        assertThat(cycle1.truncated()).isTrue();
        assertThat(engine.lexicalScanCursor()).isEqualTo(2);

        IndexReconcileReport cycle2 = engine.reconcile();
        assertThat(cycle2.repairedLexicalEntries()).isEqualTo(2);
        assertThat(engine.lexicalScanCursor()).isEqualTo(0); // wrapped around
    }

    @Test
    @DisplayName("IndexReconcileJob triggers engine reconciliation via Quartz context")
    void indexReconcileJob_triggersReconcile() throws Exception {
        IndexReconcileEngine engine = mock(IndexReconcileEngine.class);
        when(engine.reconcile()).thenReturn(new IndexReconcileReport(10, 0, 0, 5, 0, 0, 2, false));

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap map = new JobDataMap();
        map.put("indexReconcileEngine", engine);
        map.put("namespaceId", "test-ns");

        when(context.getMergedJobDataMap()).thenReturn(map);
        when(context.getJobDetail()).thenReturn(jobDetail);

        IndexReconcileJob job = new IndexReconcileJob();
        job.execute(context);

        verify(engine, times(1)).reconcile();
    }

    @Test
    @DisplayName("QuartzMemoryScheduler registers and schedules TASK_INDEX_RECONCILE")
    void quartzMemoryScheduler_registersIndexReconcileTask() {
        IndexReconcileEngine engine = mock(IndexReconcileEngine.class);

        try (QuartzMemoryScheduler scheduler = QuartzMemoryScheduler.builder()
                .namespaceId("reconcile-test-ns")
                .indexReconcileEngine(engine)
                .indexReconcileIntervalSeconds(60L)
                .build()) {

            var tasks = scheduler.listTasks();
            assertThat(tasks).anyMatch(t -> t.id().equals(QuartzMemoryScheduler.TASK_INDEX_RECONCILE));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Phase 2.1 — Hypergraph Vertex Quarantine Tests (#946)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("reconcile quarantines hyperedge with dangling entity (entityId >= entityCount)")
    void reconcile_hypergraphDanglingEntity_quarantinesEdge() {
        EntityDirectory dir = mock(EntityDirectory.class);
        when(dir.entityCount()).thenReturn(10);
        when(dir.memoryRefCount(anyInt())).thenReturn(0);

        HyperEntityGraphMemory graph = new HyperEntityGraphMemory(16, 32);
        // Add hyperedge with entity 5 (valid) and entity 999 (dangling)
        graph.addHyperedge(new int[]{5, 999}, new int[]{1, 2}, 0, 1.0f, 0, System.currentTimeMillis());

        MemoryIndex memIndex = mock(MemoryIndex.class);
        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        locMap.put("mem-0", new MemoryLocation(com.spectrayan.spector.kernel.api.MemoryType.EPISODIC, 0, 0));
        when(memIndex.locationMap()).thenReturn(locMap);

        IndexReconcileEngine engine = new IndexReconcileEngine(dir, memIndex, null, null, graph, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.scannedHypergraph()).isGreaterThan(0);
        assertThat(report.quarantinedHypergraph()).isEqualTo(1);
        assertThat(engine.quarantineRegistry().isQuarantined(0)).isTrue();
        assertThat(engine.quarantineRegistry().snapshot().get(0).reason()).isEqualTo(QuarantineReason.DANGLING_ENTITY);

        graph.close();
    }

    @Test
    @DisplayName("reconcile quarantines hyperedge with dangling memory (memoryIdx not in locationMap)")
    void reconcile_hypergraphDanglingMemory_quarantinesEdge() {
        EntityDirectory dir = mock(EntityDirectory.class);
        when(dir.entityCount()).thenReturn(10);
        when(dir.memoryRefCount(anyInt())).thenReturn(0);

        HyperEntityGraphMemory graph = new HyperEntityGraphMemory(16, 32);
        // Add hyperedge with valid entities but memoryIdx=99 (not in locationMap)
        graph.addHyperedge(new int[]{3, 5}, new int[]{1, 2}, 0, 1.0f, 99, System.currentTimeMillis());

        MemoryIndex memIndex = mock(MemoryIndex.class);
        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        locMap.put("mem-0", new MemoryLocation(com.spectrayan.spector.kernel.api.MemoryType.EPISODIC, 0, 0));
        when(memIndex.locationMap()).thenReturn(locMap);

        IndexReconcileEngine engine = new IndexReconcileEngine(dir, memIndex, null, null, graph, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.quarantinedHypergraph()).isEqualTo(1);
        assertThat(engine.quarantineRegistry().snapshot().get(0).reason()).isEqualTo(QuarantineReason.DANGLING_MEMORY);

        graph.close();
    }

    @Test
    @DisplayName("reconcile quarantines orphaned hyperedge (all vertices dangling)")
    void reconcile_hypergraphOrphanedEdge_quarantinesEdge() {
        EntityDirectory dir = mock(EntityDirectory.class);
        when(dir.entityCount()).thenReturn(5); // Only 5 entities
        when(dir.memoryRefCount(anyInt())).thenReturn(0);

        HyperEntityGraphMemory graph = new HyperEntityGraphMemory(16, 32);
        // Add hyperedge with both entities out of bounds (>= 5)
        graph.addHyperedge(new int[]{100, 200}, new int[]{1, 2}, 0, 1.0f, 0, System.currentTimeMillis());

        MemoryIndex memIndex = mock(MemoryIndex.class);
        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        locMap.put("mem-0", new MemoryLocation(com.spectrayan.spector.kernel.api.MemoryType.EPISODIC, 0, 0));
        when(memIndex.locationMap()).thenReturn(locMap);

        IndexReconcileEngine engine = new IndexReconcileEngine(dir, memIndex, null, null, graph, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.quarantinedHypergraph()).isEqualTo(1);
        assertThat(engine.quarantineRegistry().snapshot().get(0).reason()).isEqualTo(QuarantineReason.ORPHANED_HYPEREDGE);

        graph.close();
    }

    @Test
    @DisplayName("reconcile does not quarantine clean hyperedge with valid entities and memory")
    void reconcile_hypergraphCleanEdge_notQuarantined() {
        EntityDirectory dir = mock(EntityDirectory.class);
        when(dir.entityCount()).thenReturn(10);
        when(dir.memoryRefCount(anyInt())).thenReturn(0);

        HyperEntityGraphMemory graph = new HyperEntityGraphMemory(16, 32);
        // Add clean hyperedge: valid entities (3,5) and valid memoryIdx (0)
        graph.addHyperedge(new int[]{3, 5}, new int[]{1, 2}, 0, 1.0f, 0, System.currentTimeMillis());

        MemoryIndex memIndex = mock(MemoryIndex.class);
        ConcurrentHashMap<String, MemoryLocation> locMap = new ConcurrentHashMap<>();
        locMap.put("mem-0", new MemoryLocation(com.spectrayan.spector.kernel.api.MemoryType.EPISODIC, 0, 0));
        when(memIndex.locationMap()).thenReturn(locMap);

        IndexReconcileEngine engine = new IndexReconcileEngine(dir, memIndex, null, null, graph, 1000L, 100);
        IndexReconcileReport report = engine.reconcile();

        assertThat(report.quarantinedHypergraph()).isEqualTo(0);
        assertThat(engine.quarantineRegistry().quarantinedCount()).isEqualTo(0);

        graph.close();
    }

    @Test
    @DisplayName("quarantine filter excludes quarantined edges from findHyperedgesForEntity")
    void quarantineFilter_excludesQuarantinedFromTraversal() {
        HyperEntityGraphMemory graph = new HyperEntityGraphMemory(16, 32);
        // Add two hyperedges for entity 3
        int edge0 = graph.addHyperedge(new int[]{3, 5}, new int[]{1, 2}, 0, 2.0f, 0, System.currentTimeMillis());
        int edge1 = graph.addHyperedge(new int[]{3, 7}, new int[]{1, 2}, 0, 1.0f, 1, System.currentTimeMillis());

        // Without filter: both edges visible
        assertThat(graph.findHyperedgesForEntity(3)).hasSize(2);

        // Set quarantine filter to block edge0
        graph.setQuarantineFilter(edgeId -> edgeId == edge0);

        // With filter: only edge1 visible
        List<HyperEntityGraphMemory.HyperEdge> filtered = graph.findHyperedgesForEntity(3);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).edgeId()).isEqualTo(edge1);

        // Remove filter
        graph.setQuarantineFilter(null);
        assertThat(graph.findHyperedgesForEntity(3)).hasSize(2);

        graph.close();
    }

    @Test
    @DisplayName("admin un-quarantine releases edge from quarantine registry")
    void quarantineRegistry_adminUnquarantine_releasesEdge() {
        IndexReconcileEngine engine = new IndexReconcileEngine(null, null, null, null, null, 1000L, 100);
        QuarantineRegistry registry = engine.quarantineRegistry();

        registry.quarantine(42, QuarantineReason.DANGLING_ENTITY);
        assertThat(registry.isQuarantined(42)).isTrue();

        boolean released = registry.unquarantine(42);
        assertThat(released).isTrue();
        assertThat(registry.isQuarantined(42)).isFalse();
    }
}
