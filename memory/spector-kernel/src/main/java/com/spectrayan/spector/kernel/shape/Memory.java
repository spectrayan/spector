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
package com.spectrayan.spector.kernel.shape;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.shape.MemoryShape;

import com.spectrayan.spector.kernel.layout.RegionLayout;

import com.spectrayan.spector.kernel.region.RegionPreamble;

import java.lang.foreign.*;

/**
 * The base interface for all persistent structures in the Spector Memory Kernel.
 *
 * @param <L> The RegionLayout type describing the schema of this memory's records.
 */
public interface Memory<L extends RegionLayout> extends AutoCloseable {
    
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
     * @deprecated Use the typed shape methods or {@link com.spectrayan.spector.kernel.unsafe.RawBundleAccess} instead.
     *             Scheduled for removal in a future release (R13.3).
     */
    @Deprecated(forRemoval = true)
    Arena arena();
    
    /** 
     * Root segment; kernels sub-slice this for records/adjacency/etc. 
     * 
     * @return The root memory segment backing this memory.
     * @deprecated Use the typed shape methods or {@link com.spectrayan.spector.kernel.unsafe.RawBundleAccess} instead.
     *             Scheduled for removal in a future release (R13.3).
     */
    @Deprecated(forRemoval = true)
    MemorySegment segment();
    
    /** 
     * Root header segment containing the SMKM RegionPreamble.
     * 
     * @return The header segment backing this memory.
     */
    default MemorySegment headerSegment() {
        return null;
    }
    
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
     * Returns true if this memory is managed inside a bundle container.
     * When true, closing this memory will not close its shared arena.
     */
    default boolean isBundleManaged() {
        return false;
    }

    /**
     * Binds a Write-Ahead Log (WAL) to this memory.
     */
    default void bindWal(com.spectrayan.spector.kernel.sync.MemoryWal wal) {}

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
    default com.spectrayan.spector.kernel.sync.MemoryWal getWal() { return null; }
}
