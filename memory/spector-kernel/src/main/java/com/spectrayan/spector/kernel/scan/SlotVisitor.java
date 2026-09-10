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
package com.spectrayan.spector.kernel.scan;

/**
 * Functional callback receiving survivors of Phases 1-5 (R7.1).
 *
 * <p>Receives primitives only; never allocates or references a MemorySegment.</p>
 */
@FunctionalInterface
public interface SlotVisitor {

    /**
     * Accepts a surviving slot with calibrated SIMD raw score and packed header bits.
     *
     * @param slot       record slot index within the tier/partition
     * @param partition  partition sequence number
     * @param offset     byte offset in the segment
     * @param headerBits packed 64-bit header and strength fields (see HeaderBits)
     * @param rawScore   calibrated vector distance (e.g. Euclidean L2 distance)
     */
    void accept(int slot, int partition, long offset, long headerBits, float rawScore);

    /**
     * Extended accept method passing timestamp and synaptic tags without heap allocation (R7.2b, R7.5).
     */
    default void accept(int slot, int partition, long offset, long headerBits, float rawScore, long timestampMs, long tagsLo) {
        accept(slot, partition, offset, headerBits, rawScore);
    }
}
