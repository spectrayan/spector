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
package com.spectrayan.spector.memory.model;
import com.spectrayan.spector.kernel.api.MemoryType;

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
        long synapticTagMaskHi,
        float minImportance,
        MemoryType[] memoryTypes,
        byte minValence,
        byte maxValence
) {
    /** No filters — all memories eligible. */
    public static final FilterOptions NONE = new FilterOptions(
            0L, 0L, 0.0f, null, Byte.MIN_VALUE, Byte.MAX_VALUE);

    /** Backward-compatible constructor for 64-bit low mask callers. */
    public FilterOptions(
            long synapticTagMask,
            float minImportance,
            MemoryType[] memoryTypes,
            byte minValence,
            byte maxValence) {
        this(synapticTagMask, 0L, minImportance, memoryTypes, minValence, maxValence);
    }

    /** Returns true if any filter is active. */
    public boolean hasTagFilter() {
        return synapticTagMask != 0L || synapticTagMaskHi != 0L;
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
