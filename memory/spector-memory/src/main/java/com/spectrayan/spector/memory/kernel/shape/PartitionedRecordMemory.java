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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.MemoryLayout;

import java.util.List;

/**
 * Interface for partition-rolling record memories in Spector Memory Kernel.
 *
 * @param <L> the memory layout type
 */
public interface PartitionedRecordMemory<L extends MemoryLayout> extends RecordMemory<L> {

    /**
     * Returns the currently active partition where new writes occur.
     *
     * @return the active partition
     */
    RecordMemory<L> activePartition();

    /**
     * Returns an unmodifiable list of historical read-only partitions.
     *
     * @return historical partitions
     */
    List<? extends RecordMemory<L>> historicalPartitions();

    /**
     * Rolls the active partition, sealing the current active partition and creating a new one.
     *
     * @return the newly active partition
     */
    RecordMemory<L> rollPartition();
}
