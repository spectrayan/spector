/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.graph.EntityDirectory;
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
        }
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
}
