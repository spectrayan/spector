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
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.List;

/**
 * Prunes irrelevant partitions at query time to bound recall fan-out latency (#447).
 *
 * <p>Recall queries with temporal constraints ({@code minTimestamp}, {@code maxTimestamp}),
 * synaptic tag filters ({@code synapticTagMask}, {@code hyperfocusMask}), or memory type
 * constraints evaluate partition summary metadata before dispatching scan tasks.</p>
 */
public interface PartitionPruner {

    /**
     * Filters candidate partitions from the snapshot that could potentially contain
     * results matching the query options and target memory types.
     *
     * @param partitions full partition snapshot in ascending sequence order
     * @param options recall query options (temporal bounds, tag masks, etc.)
     * @param targetTypes requested memory types
     * @param nowMs current timestamp in epoch milliseconds
     * @return pruned list of candidate partitions to scan (never null)
     */
    List<PartitionHandle> prune(List<PartitionHandle> partitions, RecallOptions options,
                                MemoryType[] targetTypes, long nowMs);

    /**
     * Default no-op pruner that returns all partitions without filtering.
     */
    static PartitionPruner noop() {
        return (partitions, options, targetTypes, nowMs) -> partitions;
    }

    /**
     * Returns the default production pruner evaluating time windows, synaptic tags,
     * hyperfocus masks, and tier record counts.
     */
    static PartitionPruner defaultPruner() {
        return new DefaultPartitionPruner();
    }
}
