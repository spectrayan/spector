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

import com.spectrayan.spector.kernel.graph.EntityDirectory;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background cooperative drift detection and reconciliation engine for derived index views (ADR-0082).
 *
 * <p>Periodically verifies consistency between primary stores and secondary views:
 * <ol>
 *   <li><b>Entity Reverse Index</b>: Detects forward entity-to-memory references missing from {@code memoryToEntities} reverse map and repairs them.</li>
 *   <li><b>Lexical Index (BM25)</b>: Detects memory texts present in {@link MemoryIndex} but missing from {@link MemoryBM25Index} and re-indexes them.</li>
 * </ol>
 *
 * <p>Executes with cooperative rate-limiting (default ≤ 50ms time-slice, ≤ 500 repairs per cycle) to guarantee zero
 * throughput degradation on hot ingestion pathways.</p>
 *
 * @since 1.1.0
 */
public class IndexReconcileEngine {

    private static final Logger log = LoggerFactory.getLogger(IndexReconcileEngine.class);

    public static final long DEFAULT_TIME_SLICE_MS = 50L;
    public static final int DEFAULT_MAX_REPAIRS = 500;

    private final EntityDirectory entityDirectory;
    private final MemoryIndex memoryIndex;
    private final MemoryBM25Index bm25Index;
    private final long maxTimeSliceMs;
    private final int maxRepairsPerCycle;

    // Entity scan cursor for round-robin incremental scanning across cycles
    private final AtomicInteger entityScanCursor = new AtomicInteger(0);

    public IndexReconcileEngine(EntityDirectory entityDirectory, MemoryIndex memoryIndex, MemoryBM25Index bm25Index) {
        this(entityDirectory, memoryIndex, bm25Index, DEFAULT_TIME_SLICE_MS, DEFAULT_MAX_REPAIRS);
    }

    public IndexReconcileEngine(
            EntityDirectory entityDirectory,
            MemoryIndex memoryIndex,
            MemoryBM25Index bm25Index,
            long maxTimeSliceMs,
            int maxRepairsPerCycle
    ) {
        this.entityDirectory = entityDirectory;
        this.memoryIndex = memoryIndex;
        this.bm25Index = bm25Index;
        this.maxTimeSliceMs = maxTimeSliceMs > 0 ? maxTimeSliceMs : DEFAULT_TIME_SLICE_MS;
        this.maxRepairsPerCycle = maxRepairsPerCycle > 0 ? maxRepairsPerCycle : DEFAULT_MAX_REPAIRS;
    }

    /**
     * Executes a cooperative reconciliation cycle.
     *
     * @return report of scanned items, drift detected, and repairs executed
     */
    public IndexReconcileReport reconcile() {
        long start = System.currentTimeMillis();

        long scannedEntities = 0;
        long missingReverse = 0;
        long repairedReverse = 0;

        long scannedLexical = 0;
        long missingLexical = 0;
        long repairedLexical = 0;

        boolean truncated = false;
        int repairsCount = 0;

        // 1. Reconcile Entity Reverse Index
        if (entityDirectory != null && entityDirectory.entityCount() > 0) {
            int totalEntities = entityDirectory.entityCount();
            int startIndex = entityScanCursor.get() % totalEntities;
            if (startIndex < 0) startIndex = 0;

            for (int step = 0; step < totalEntities; step++) {
                if (System.currentTimeMillis() - start >= maxTimeSliceMs || repairsCount >= maxRepairsPerCycle) {
                    truncated = true;
                    break;
                }

                int entityId = (startIndex + step) % totalEntities;
                scannedEntities++;

                int refCount = entityDirectory.memoryRefCount(entityId);
                for (int r = 0; r < refCount; r++) {
                    int memSlot = entityDirectory.memoryRefAt(entityId, r);
                    if (memSlot >= 0) {
                        Set<Integer> entities = entityDirectory.entityIdsForMemory(memSlot);
                        if (!entities.contains(entityId)) {
                            missingReverse++;
                            boolean repaired = entityDirectory.repairMemoryToEntityMapping(memSlot, entityId);
                            if (repaired) {
                                repairedReverse++;
                                repairsCount++;
                                if (repairsCount >= maxRepairsPerCycle) {
                                    truncated = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                entityScanCursor.set((entityId + 1) % totalEntities);
            }
        }

        // 2. Reconcile BM25 Lexical Index
        if (memoryIndex != null && bm25Index != null && !truncated && repairsCount < maxRepairsPerCycle) {
            if (bm25Index.totalDocuments() < memoryIndex.size()) {
                var entries = memoryIndex.locationMap().entrySet();
                for (var entry : entries) {
                    if (System.currentTimeMillis() - start >= maxTimeSliceMs || repairsCount >= maxRepairsPerCycle) {
                        truncated = true;
                        break;
                    }

                    scannedLexical++;
                    String id = entry.getKey();
                    if (!bm25Index.contains(id)) {
                        missingLexical++;
                        String text = memoryIndex.text(id);
                        if (text != null && !text.isEmpty()) {
                            bm25Index.index(0, id, text);
                            repairedLexical++;
                            repairsCount++;
                        }
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        IndexReconcileReport report = new IndexReconcileReport(
                scannedEntities, missingReverse, repairedReverse,
                scannedLexical, missingLexical, repairedLexical,
                elapsed, truncated
        );

        if (report.hasRepairs()) {
            log.info("Index reconciliation completed with repairs: {}", report);
        } else {
            log.debug("Index reconciliation completed without drift: {}", report);
        }

        return report;
    }

    public long maxTimeSliceMs() {
        return maxTimeSliceMs;
    }

    public int maxRepairsPerCycle() {
        return maxRepairsPerCycle;
    }
}
