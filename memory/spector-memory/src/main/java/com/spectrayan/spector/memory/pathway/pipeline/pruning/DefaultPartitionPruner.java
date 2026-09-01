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
package com.spectrayan.spector.memory.pathway.pipeline.pruning;

import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of query-time partition pruning (#447).
 *
 * <p>Evaluates each partition's {@link PartitionSummary} against query constraints:
 * <ul>
 *   <li><b>Empty &amp; Tier check:</b> Skips partitions that contain 0 visible records across the requested {@code targetTypes}.</li>
 *   <li><b>Temporal bounds:</b> Skips partitions where {@code maxTimestampMs < options.minTimestamp()} or {@code minTimestampMs > options.maxTimestamp()}.</li>
 *   <li><b>Synaptic tag filter:</b> Skips partitions where {@code (partitionTagMask & options.synapticTagMask()) == 0}.</li>
 *   <li><b>Hyperfocus filter:</b> Skips partitions where {@code (partitionTagMask & options.hyperfocusMask()) != options.hyperfocusMask()}.</li>
 * </ul>
 *
 * <h3>Soundness Guarantee</h3>
 * <p>Pruning is provably sound with <b>zero false negatives</b>. A partition is only skipped if it
 * is mathematically impossible to contain any matching record. In-memory or uninitialized
 * summaries default to inclusive bounds.</p>
 */
public final class DefaultPartitionPruner implements PartitionPruner {

    private static final Logger log = LoggerFactory.getLogger(DefaultPartitionPruner.class);

    @Override
    public List<PartitionHandle> prune(List<PartitionHandle> partitions, RecallOptions options,
                                       MemoryType[] targetTypes, long nowMs) {
        if (partitions == null || partitions.isEmpty()) {
            return List.of();
        }
        if (partitions.size() == 1) {
            PartitionHandle single = partitions.get(0);
            return shouldPrune(single, options, targetTypes) ? List.of() : partitions;
        }

        List<PartitionHandle> candidates = new ArrayList<>(partitions.size());
        int prunedCount = 0;

        for (PartitionHandle handle : partitions) {
            if (shouldPrune(handle, options, targetTypes)) {
                prunedCount++;
            } else {
                candidates.add(handle);
            }
        }

        if (prunedCount > 0) {
            log.debug("Partition pruning pruned {}/{} partitions (kept {})",
                    prunedCount, partitions.size(), candidates.size());
        }

        return candidates;
    }

    /**
     * Determines whether a partition handle can be safely pruned (skipped).
     *
     * @param handle partition handle
     * @param options recall options
     * @param targetTypes requested memory tiers
     * @return {@code true} if provably irrelevant and safe to skip, {@code false} to include
     */
    public boolean shouldPrune(PartitionHandle handle, RecallOptions options, MemoryType[] targetTypes) {
        if (handle == null) return true;
        PartitionSummary summary = handle.summary();
        if (summary == null) {
            // No summary available -> safe fallback, do not prune
            return false;
        }

        // 1. Tier record count / empty check
        if (!summary.hasRecordsFor(targetTypes, handle.router())) {
            return true;
        }

        if (options == null) {
            return false;
        }

        // 2. Temporal gating
        Long minTs = options.minTimestamp();
        if (minTs != null && minTs > 0) {
            if (summary.maxTimestampMs() < minTs) {
                return true; // All records in partition are older than query's minTimestamp
            }
        }

        Long maxTs = options.maxTimestamp();
        if (maxTs != null && maxTs > 0) {
            if (summary.minTimestampMs() > maxTs) {
                return true; // All records in partition are newer than query's maxTimestamp
            }
        }

        // 3. Synaptic tag gating (applied to immutable frozen partitions)
        if (!summary.writable()) {
            long queryTagMask = options.synapticTagMask();
            long hyperfocusMask = options.hyperfocusMask();

            if (hyperfocusMask != 0L) {
                // Hyperfocus requires ALL mask bits to match.
                // If the partition's aggregate tag mask is missing any of the hyperfocus bits,
                // no single record in this partition can possibly have all of them.
                if ((summary.synapticTagMask() & hyperfocusMask) != hyperfocusMask) {
                    return true;
                }
            } else if (queryTagMask != 0L) {
                // Standard tag filter: requires at least one overlapping bit.
                if ((summary.synapticTagMask() & queryTagMask) == 0L) {
                    return true;
                }
            }
        }

        return false;
    }
}
