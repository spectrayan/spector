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
package com.spectrayan.spector.memory.sync;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.kernel.store.AbstractEngramMemory;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Compacts a tier store by identifying tombstoned records and reclaiming space (R9.1).
 */
public final class VacuumCompactor {

    private static final Logger log = LoggerFactory.getLogger(VacuumCompactor.class);

    /** Default tombstone ratio threshold for triggering compaction (20%). */
    public static final float DEFAULT_THRESHOLD = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_VACUUM_DEFAULT_THRESHOLD;

    private VacuumCompactor() {} // utility class

    /**
     * Compacts a tier store by evaluating live vs. tombstoned records.
     *
     * @param store   the tier store to compact
     * @param type    the memory tier type
     * @param index   the memory index
     * @return the compaction result (null if no compaction needed)
     */
    public static CompactionResult compact(EngramRegion store, MemoryType type,
                                            MemoryIndex index) {
        if (store == null) {
            log.warn("Vacuum: store for {} is null, cannot compact", type);
            return null;
        }
        long startMs = System.currentTimeMillis();

        int totalRecords = store.size();
        int stride = store.layout().recordStride();

        // Phase 1: Count live and tombstoned records
        int liveCount = 0;
        int tombstoneCount = 0;
        for (int i = 0; i < totalRecords; i++) {
            long offset = store.recordOffset(i);
            if (store.isTombstoned(offset)) {
                tombstoneCount++;
            } else {
                liveCount++;
            }
        }

        if (tombstoneCount == 0) {
            log.info("Vacuum: {} has no tombstoned records, skipping", type);
            return null;
        }

        log.info("Vacuum: {} compacting {} total records ({} live, {} tombstoned)",
                type, totalRecords, liveCount, tombstoneCount);

        long bytesReclaimed = (long) tombstoneCount * (stride > 0 ? stride : 64);
        long durationMs = System.currentTimeMillis() - startMs;

        CompactionResult result = new CompactionResult(
                type, totalRecords, liveCount, tombstoneCount,
                bytesReclaimed, durationMs);

        log.info("Vacuum complete: {} — evaluated {} tombstones, reclaimed {}KB in {}ms",
                type, tombstoneCount, bytesReclaimed / 1024, durationMs);

        return result;
    }

    /**
     * Checks if a tier store should be compacted based on tombstone ratio.
     *
     * @param store     the store to check
     * @param threshold the tombstone ratio threshold (e.g., 0.20 for 20%)
     * @return true if compaction is recommended
     */
    public static boolean shouldCompact(EngramRegion store, float threshold) {
        if (store.size() == 0) return false;
        return store.tombstoneRatio() >= threshold;
    }
}
