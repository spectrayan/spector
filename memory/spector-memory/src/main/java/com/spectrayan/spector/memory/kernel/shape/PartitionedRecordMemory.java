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

import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryLayout;

/**
 * Shape interface for segmented record storage spanning multiple physical files.
 * Backs large, append-heavy tables with historic partitions.
 *
 * @param <L> the memory layout type
 */
public interface PartitionedRecordMemory<L extends MemoryLayout> extends Memory<L> {
    /** 
     * Total number of partitions.
     * @return number of partitions
     */
    int partitionCount();
    
    /** 
     * The currently active partition for writes.
     * @return active partition
     */
    RecordMemory<L> activePartition();
    
    /** 
     * Get a specific partition by sequence number.
     * @param seq partition sequence number
     * @return the partition memory
     */
    RecordMemory<L> partition(int seq);
    
    /** 
     * All partitions for cross-partition scans.
     * @return iterable over all partitions
     */
    Iterable<RecordMemory<L>> partitions();
    
    /** 
     * Create a new active partition, making the current one read-only.
     */
    void rollPartition();
}
