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
package com.spectrayan.spector.memory.model;

/**
 * Filter parameters for recall queries.
 *
 * <p>Controls which memories are eligible for recall based on
 * synaptic tag matching, importance threshold, memory tier type,
 * and valence range.</p>
 *
 * @param synapticTagMask  Bloom filter mask for tag-based filtering (0L = no filter)
 * @param minImportance    minimum importance threshold (0.0 = no filter)
 * @param memoryTypes      allowed memory types (null = all types)
 * @param minValence       minimum valence inclusive (Byte.MIN_VALUE = no filter)
 * @param maxValence       maximum valence inclusive (Byte.MAX_VALUE = no filter)
 */
public record FilterOptions(
        long synapticTagMask,
        float minImportance,
        MemoryType[] memoryTypes,
        byte minValence,
        byte maxValence
) {
    /** No filters — all memories eligible. */
    public static final FilterOptions NONE = new FilterOptions(
            0L, 0.0f, null, Byte.MIN_VALUE, Byte.MAX_VALUE);

    /** Returns true if any filter is active. */
    public boolean hasTagFilter() {
        return synapticTagMask != 0L;
    }

    /** Returns true if memory type filtering is active. */
    public boolean hasTypeFilter() {
        return memoryTypes != null && memoryTypes.length > 0;
    }

    /** Returns true if valence range is constrained. */
    public boolean hasValenceFilter() {
        return minValence != Byte.MIN_VALUE || maxValence != Byte.MAX_VALUE;
    }
}
