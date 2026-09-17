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
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background cooperative drift detection and reconciliation engine for derived index views (ADR-0082).
 *
 * <p>Periodically verifies consistency between primary stores and secondary views:
 * <ol>
 *   <li><b>Entity Reverse Index</b>: Detects forward entity-to-memory references missing from {@code memoryToEntities} reverse map and repairs them, and detects/drops dangling reverse entries.</li>
 *   <li><b>Lexical Index (BM25)</b>: Detects memory texts present in {@link MemoryIndex} but missing from {@link MemoryBM25Index} and re-indexes them, and detects/drops stale postings.</li>
 *   <li><b>Learned Sparse Index (SPLADE)</b>: Detects memories missing from {@link MemorySpladeIndex} and flags drift for administrative rebuild (neural inference is avoided during cooperative slice), and detects/drops stale postings.</li>
 * </ol>
 *
 * <p>Note: Hypergraph vertex quarantine (tombstoning and isolating corrupted hyper-edge vertices) is
 * designated as a named follow-up under Issue #946 / Phase 2.1.</p>
 *
 * <p>Executes with cooperative rate-limiting (default ≤ 50ms time-slice, ≤ 500 repairs per cycle) to guarantee zero
 * throughput degradation on hot ingestion pathways.</p>
 *
 * @since 1.1.0
 */
public final class IndexReconcileEngine {

    private static final Logger log = LoggerFactory.getLogger(IndexReconcileEngine.class);

    public static final long DEFAULT_TIME_SLICE_MS = 50L;
    public static final int DEFAULT_MAX_REPAIRS = 500;

    private final EntityDirectory entityDirectory;
    private final MemoryIndex memoryIndex;
    private final MemoryBM25Index bm25Index;
    private final MemorySpladeIndex spladeIndex;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final long maxTimeSliceMs;
    private final int maxRepairsPerCycle;
    private final QuarantineRegistry quarantineRegistry;

    // Entity scan cursor for round-robin incremental scanning across cycles
    private final AtomicInteger entityScanCursor = new AtomicInteger(0);

    // Lexical/sparse scan cursor for round-robin incremental scanning across cycles
    private final AtomicInteger lexicalScanCursor = new AtomicInteger(0);

    // Hypergraph scan cursor for round-robin incremental scanning across cycles
    private final AtomicInteger hypergraphScanCursor = new AtomicInteger(0);

    public IndexReconcileEngine(EntityDirectory entityDirectory, MemoryIndex memoryIndex, MemoryBM25Index bm25Index) {
        this(entityDirectory, memoryIndex, bm25Index, null, null, DEFAULT_TIME_SLICE_MS, DEFAULT_MAX_REPAIRS);
    }

    public IndexReconcileEngine(EntityDirectory entityDirectory, MemoryIndex memoryIndex, MemoryBM25Index bm25Index, MemorySpladeIndex spladeIndex) {
        this(entityDirectory, memoryIndex, bm25Index, spladeIndex, null, DEFAULT_TIME_SLICE_MS, DEFAULT_MAX_REPAIRS);
    }

    public IndexReconcileEngine(EntityDirectory entityDirectory, MemoryIndex memoryIndex, MemoryBM25Index bm25Index, MemorySpladeIndex spladeIndex, HyperEntityGraphMemory hyperEntityGraph) {
        this(entityDirectory, memoryIndex, bm25Index, spladeIndex, hyperEntityGraph, DEFAULT_TIME_SLICE_MS, DEFAULT_MAX_REPAIRS);
    }

    public IndexReconcileEngine(
            EntityDirectory entityDirectory,
            MemoryIndex memoryIndex,
            MemoryBM25Index bm25Index,
            long maxTimeSliceMs,
            int maxRepairsPerCycle
    ) {
        this(entityDirectory, memoryIndex, bm25Index, null, null, maxTimeSliceMs, maxRepairsPerCycle);
    }

    public IndexReconcileEngine(
            EntityDirectory entityDirectory,
            MemoryIndex memoryIndex,
            MemoryBM25Index bm25Index,
            MemorySpladeIndex spladeIndex,
            long maxTimeSliceMs,
            int maxRepairsPerCycle
    ) {
        this(entityDirectory, memoryIndex, bm25Index, spladeIndex, null, maxTimeSliceMs, maxRepairsPerCycle);
    }

    public IndexReconcileEngine(
            EntityDirectory entityDirectory,
            MemoryIndex memoryIndex,
            MemoryBM25Index bm25Index,
            MemorySpladeIndex spladeIndex,
            HyperEntityGraphMemory hyperEntityGraph,
            long maxTimeSliceMs,
            int maxRepairsPerCycle
    ) {
        this.entityDirectory = entityDirectory;
        this.memoryIndex = memoryIndex;
        this.bm25Index = bm25Index;
        this.spladeIndex = spladeIndex;
        this.hyperEntityGraph = hyperEntityGraph;
        this.maxTimeSliceMs = maxTimeSliceMs > 0 ? maxTimeSliceMs : DEFAULT_TIME_SLICE_MS;
        this.maxRepairsPerCycle = maxRepairsPerCycle > 0 ? maxRepairsPerCycle : DEFAULT_MAX_REPAIRS;
        this.quarantineRegistry = new QuarantineRegistry();
    }

    /**
     * Executes a cooperative reconciliation cycle.
     *
     * @return report of scanned items, drift detected, and repairs executed
     */
    public IndexReconcileReport reconcile() {
        long startNs = System.nanoTime();
        long maxTimeSliceNs = TimeUnit.MILLISECONDS.toNanos(maxTimeSliceMs);

        long scannedEntities = 0;
        long missingReverse = 0;
        long danglingReverse = 0;
        long repairedReverse = 0;

        long scannedLexical = 0;
        long missingLexical = 0;
        long staleLexical = 0;
        long repairedLexical = 0;

        long scannedSplade = 0;
        long missingSplade = 0;
        long staleSplade = 0;
        long repairedSplade = 0;

        boolean truncated = false;
        int repairsCount = 0;

        // 1. Reconcile Entity Reverse Index
        if (entityDirectory != null && entityDirectory.entityCount() > 0) {
            int totalEntities = entityDirectory.entityCount();
            int startIndex = entityScanCursor.get() % totalEntities;
            if (startIndex < 0) startIndex = 0;

            // 1a. Forward scan: check forward entity -> memory references have reverse entries
            for (int step = 0; step < totalEntities; step++) {
                if (System.nanoTime() - startNs >= maxTimeSliceNs || repairsCount >= maxRepairsPerCycle) {
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

            // 1b. Dangling reverse scan: check reverse entries have valid forward references
            if (!truncated && repairsCount < maxRepairsPerCycle) {
                Set<Integer> slots = entityDirectory.indexedMemorySlots();
                for (int slot : slots) {
                    if (System.nanoTime() - startNs >= maxTimeSliceNs || repairsCount >= maxRepairsPerCycle) {
                        truncated = true;
                        break;
                    }

                    Set<Integer> entityIds = entityDirectory.entityIdsForMemory(slot);
                    if (entityIds != null && !entityIds.isEmpty()) {
                        for (int entityId : entityIds) {
                            boolean dangling = false;
                            if (entityId < 0 || entityId >= entityDirectory.entityCount()) {
                                dangling = true;
                            } else {
                                int refCount = entityDirectory.memoryRefCount(entityId);
                                boolean found = false;
                                for (int r = 0; r < refCount; r++) {
                                    if (entityDirectory.memoryRefAt(entityId, r) == slot) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    dangling = true;
                                }
                            }

                            if (dangling) {
                                danglingReverse++;
                                if (entityDirectory.removeMemoryToEntityMapping(slot, entityId)) {
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
                }
            }
        }

        // 2. Reconcile BM25 Lexical Index & SPLADE Sparse Index (Forward Scan)
        if (memoryIndex != null && (bm25Index != null || spladeIndex != null) && !truncated && repairsCount < maxRepairsPerCycle) {
            var locMap = memoryIndex.locationMap();
            if (locMap != null && !locMap.isEmpty()) {
                int totalDocs = locMap.size();
                int startIndex = lexicalScanCursor.get() % totalDocs;
                if (startIndex < 0) startIndex = 0;

                int currentIndex = 0;
                int scannedCount = 0;

                // Zero-allocation iteration: pass 0 scans [startIndex, end); pass 1 wraps around [0, startIndex)
                for (int pass = 0; pass < 2 && scannedCount < totalDocs && !truncated; pass++) {
                    currentIndex = 0;
                    for (String id : locMap.keySet()) {
                        if (pass == 0) {
                            if (currentIndex < startIndex) {
                                currentIndex++;
                                continue;
                            }
                        } else {
                            if (currentIndex >= startIndex) {
                                break;
                            }
                        }

                        if (System.nanoTime() - startNs >= maxTimeSliceNs || repairsCount >= maxRepairsPerCycle) {
                            truncated = true;
                            break;
                        }

                        // 2a. BM25 missing check
                        if (bm25Index != null) {
                            scannedLexical++;
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

                        // 3a. SPLADE missing check (flag only, zero neural inference in 50ms slice)
                        if (spladeIndex != null) {
                            scannedSplade++;
                            if (!spladeIndex.contains(id)) {
                                missingSplade++;
                                log.debug("SPLADE index missing document id={}; flagged for admin rebuild", id);
                            }
                        }

                        currentIndex++;
                        scannedCount++;
                        lexicalScanCursor.set(currentIndex % totalDocs);
                    }
                }
            }
        }

        // 2b. Stale BM25 entries check: doc in BM25Index but not in MemoryIndex
        if (memoryIndex != null && bm25Index != null && !truncated && repairsCount < maxRepairsPerCycle) {
            Set<String> bm25DocIds = bm25Index.docIds();
            if (bm25DocIds != null) {
                for (String id : bm25DocIds) {
                    if (System.nanoTime() - startNs >= maxTimeSliceNs || repairsCount >= maxRepairsPerCycle) {
                        truncated = true;
                        break;
                    }

                    if (!memoryIndex.locationMap().containsKey(id)) {
                        staleLexical++;
                        bm25Index.remove(id);
                        repairedLexical++;
                        repairsCount++;
                    }
                }
            }
        }

        // 3b. Stale SPLADE entries check: doc in SpladeIndex but not in MemoryIndex
        if (memoryIndex != null && spladeIndex != null && !truncated && repairsCount < maxRepairsPerCycle) {
            Set<String> spladeDocIds = spladeIndex.docIds();
            if (spladeDocIds != null) {
                for (String id : spladeDocIds) {
                    if (System.nanoTime() - startNs >= maxTimeSliceNs || repairsCount >= maxRepairsPerCycle) {
                        truncated = true;
                        break;
                    }

                    if (!memoryIndex.locationMap().containsKey(id)) {
                        staleSplade++;
                        spladeIndex.remove(id);
                        repairedSplade++;
                        repairsCount++;
                    }
                }
            }
        }

        // 4. Reconcile Hypergraph Vertex Integrity (Phase 2.1, #946)
        long scannedHypergraph = 0;
        long quarantinedHypergraph = 0;

        if (hyperEntityGraph != null && !truncated && repairsCount < maxRepairsPerCycle) {
            // Build transient set of live graph slots for O(1) memoryIdx verification
            Set<Integer> liveGraphSlots = new HashSet<>();
            if (memoryIndex != null) {
                var locMap = memoryIndex.locationMap();
                if (locMap != null) {
                    for (MemoryLocation loc : locMap.values()) {
                        liveGraphSlots.add(loc.graphSlot());
                    }
                }
            }

            int entityCount = entityDirectory != null ? entityDirectory.entityCount() : 0;
            int totalEdgeIds = hyperEntityGraph.totalHyperedges();
            // Scan by edgeId range (0..nextHyperedgeId), not totalHyperedges (which excludes tombstoned)
            // We use totalHyperedges + estimate for the scan range upper bound via getHyperedge null-check
            int scanLimit = totalEdgeIds > 0 ? totalEdgeIds * 2 : 0; // upper bound estimate
            int startIndex = hypergraphScanCursor.get();
            if (startIndex < 0) startIndex = 0;

            for (int step = 0; step < scanLimit && !truncated; step++) {
                if (System.nanoTime() - startNs >= maxTimeSliceNs || repairsCount >= maxRepairsPerCycle) {
                    truncated = true;
                    break;
                }

                int edgeId = startIndex + step;
                HyperEntityGraphMemory.HyperEdge edge = hyperEntityGraph.getHyperedge(edgeId);
                if (edge == null) {
                    // No more edges beyond this point, or tombstoned — wrap cursor
                    if (step > 0 && edgeId > startIndex + totalEdgeIds) {
                        break; // exhausted scan range
                    }
                    continue;
                }

                scannedHypergraph++;

                // Check vertex entity validity
                boolean allDangling = true;
                boolean hasDanglingEntity = false;
                for (HyperEntityGraphMemory.HyperEdgeVertex v : edge.vertices()) {
                    if (v.entityId() < 0 || v.entityId() >= entityCount) {
                        hasDanglingEntity = true;
                    } else {
                        allDangling = false;
                    }
                }

                // Check memoryIdx validity
                boolean hasDanglingMemory = false;
                if (edge.memoryIdx() >= 0 && !liveGraphSlots.isEmpty()
                        && !liveGraphSlots.contains(edge.memoryIdx())) {
                    hasDanglingMemory = true;
                }

                // Determine quarantine reason
                QuarantineReason reason = null;
                if (allDangling && hasDanglingEntity) {
                    reason = QuarantineReason.ORPHANED_HYPEREDGE;
                } else if (hasDanglingEntity) {
                    reason = QuarantineReason.DANGLING_ENTITY;
                } else if (hasDanglingMemory) {
                    reason = QuarantineReason.DANGLING_MEMORY;
                }

                if (reason != null && !quarantineRegistry.isQuarantined(edgeId)) {
                    quarantineRegistry.quarantine(edgeId, reason);
                    quarantinedHypergraph++;
                    repairsCount++;
                    log.debug("Quarantined hyperedge edgeId={} reason={}", edgeId, reason);
                }

                hypergraphScanCursor.set(edgeId + 1);
            }

            // Wrap cursor if we've exhausted the range
            if (!truncated) {
                hypergraphScanCursor.set(0);
            }
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        IndexReconcileReport report = new IndexReconcileReport(
                scannedEntities, missingReverse, danglingReverse, repairedReverse,
                scannedLexical, missingLexical, staleLexical, repairedLexical,
                scannedSplade, missingSplade, staleSplade, repairedSplade,
                scannedHypergraph, quarantinedHypergraph, 0L,
                elapsedMs, truncated
        );

        if (report.hasRepairs()) {
            log.info("Index reconciliation completed with repairs: {}", report);
        } else {
            log.debug("Index reconciliation completed without drift: {}", report);
        }

        return report;
    }

    /**
     * Returns the quarantine registry for hypergraph vertex quarantine (Phase 2.1, #946).
     */
    public QuarantineRegistry quarantineRegistry() {
        return quarantineRegistry;
    }

    public long maxTimeSliceMs() {
        return maxTimeSliceMs;
    }

    public int maxRepairsPerCycle() {
        return maxRepairsPerCycle;
    }

    int entityScanCursor() {
        return entityScanCursor.get();
    }

    int lexicalScanCursor() {
        return lexicalScanCursor.get();
    }

    int hypergraphScanCursor() {
        return hypergraphScanCursor.get();
    }
}

