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
package com.spectrayan.spector.memory.kernel;

import java.lang.foreign.*;

/**
 * The base interface for all persistent structures in the Spector Memory Kernel.
 *
 * @param <L> The MemoryLayout type describing the schema of this memory's records.
 */
public interface Memory<L extends MemoryLayout> extends AutoCloseable {
    
    /** 
     * Stable identity for logs, metrics, WAL redo target. 
     * 
     * @return The unique identifier of this memory.
     */
    MemoryId id();
    
    /** 
     * The schema describing what a record/slot looks like. 
     * 
     * @return The memory layout.
     */
    L layout();
    
    /** 
     * Region-scoped arena; sub-slices must not outlive it. 
     * 
     * @return The arena managing the lifecycle of the underlying memory segment.
     */
    Arena arena();
    
    /** 
     * Root segment; kernels sub-slice this for records/adjacency/etc. 
     * 
     * @return The root memory segment backing this memory.
     */
    MemorySegment segment();
    
    /** 
     * Live record count, published with release/acquire semantics. 
     * 
     * @return The current number of live records.
     */
    int size();
    
    /** 
     * Capacity in records / slots (bounded upper limit). 
     * 
     * @return The maximum number of records this memory can hold.
     */
    int capacity();
    
    /** 
     * Schema version stamped in the on-disk header. 
     * 
     * @return The schema version.
     */
    int schemaVersion();
    
    /** 
     * The shape of this memory. 
     * 
     * @return The structural shape of the memory.
     */
    MemoryShape shape();
    
    /** 
     * msync/force this memory only (not the whole file). 
     * Ensures durability of modifications.
     */
    void flush();
    
    /** 
     * Close: releases arena, does not delete backing file. 
     */
    @Override
    void close();

    /**
     * Binds a Write-Ahead Log (WAL) to this memory.
     */
    default void bindWal(com.spectrayan.spector.memory.sync.MemoryWal wal) {}

    /**
     * Sets whether WAL writes should be bypassed (useful during recovery/replay).
     */
    default void setBypassWal(boolean bypass) {}

    /**
     * Returns whether WAL writes are bypassed.
     */
    default boolean isBypassWal() { return false; }

    /**
     * Returns the bound Write-Ahead Log, if any.
     */
    default com.spectrayan.spector.memory.sync.MemoryWal getWal() { return null; }
}
