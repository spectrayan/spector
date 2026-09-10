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
package com.spectrayan.spector.kernel.api;

/**
 * Tracks where an engram record is physically located.
 *
 * @param type               the cognitive memory tier (SEMANTIC, EPISODIC, PROCEDURAL, WORKING)
 * @param offset             byte offset within the tier store segment
 * @param graphSlot          semantic / Hebbian graph slot index
 * @param colocatedPartition disk partition sequence number for partition-isolated stores
 * @param textOffset         byte offset within the companion text storage (-1 if none)
 * @param textLength         byte length within the companion text storage (-1 if none)
 */
public record MemoryLocation(
        MemoryType type,
        long offset,
        int graphSlot,
        int colocatedPartition,
        long textOffset,
        int textLength
) {

    /** Convenience constructor — colocatedPartition defaults to 0, no text position. */
    public MemoryLocation(MemoryType type, long offset, int graphSlot) {
        this(type, offset, graphSlot, 0, -1L, -1);
    }

    /** Convenience constructor — colocatedPartition defaults to 0. */
    public MemoryLocation(MemoryType type, long offset, int graphSlot,
                          long textOffset, int textLength) {
        this(type, offset, graphSlot, 0, textOffset, textLength);
    }

    /** Returns true if this location points to a valid text position in a text store. */
    public boolean hasTextPosition() {
        return textOffset >= 0 && textLength >= 0;
    }
}
