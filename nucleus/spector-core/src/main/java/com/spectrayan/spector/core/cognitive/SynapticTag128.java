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
package com.spectrayan.spector.core.cognitive;

/**
 * Immutable 128-bit Bloom filter synaptic tag carrier record (ADR-0028, ADR-0030, #795).
 *
 * <p>Carries the lower 64 bits ({@code lo}) and upper 64 bits ({@code hi}) of the 128-bit
 * engram synaptic tag Bloom filter. Provides pure, allocation-free bitwise operations for
 * containment testing, merging, and overlap ratio calculation.</p>
 *
 * @param lo lower 64 bits of the 128-bit Bloom filter
 * @param hi upper 64 bits of the 128-bit Bloom filter
 */
public record SynapticTag128(long lo, long hi) {

    public static final SynapticTag128 EMPTY = new SynapticTag128(0L, 0L);

    public static SynapticTag128 of(final long lo, final long hi) {
        if (lo == 0L && hi == 0L) {
            return EMPTY;
        }
        return new SynapticTag128(lo, hi);
    }

    /**
     * Checks if this 128-bit filter has zero bits set.
     */
    public boolean isEmpty() {
        return lo == 0L && hi == 0L;
    }

    /**
     * Tests if all bits in {@code query} are present in this record filter.
     *
     * @param query query 128-bit Bloom filter
     * @return true if all query bits are contained in this record filter
     */
    public boolean matches(final SynapticTag128 query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        return (this.lo & query.lo) == query.lo && (this.hi & query.hi) == query.hi;
    }

    /**
     * Tests whether this filter shares at least one active bit with {@code query}.
     */
    public boolean sharesAnyBit(final SynapticTag128 query) {
        if (query == null || query.isEmpty() || this.isEmpty()) {
            return false;
        }
        return (this.lo & query.lo) != 0L || (this.hi & query.hi) != 0L;
    }

    /**
     * Bitwise OR merges this filter with another 128-bit filter.
     */
    public SynapticTag128 merge(final SynapticTag128 other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        return new SynapticTag128(this.lo | other.lo, this.hi | other.hi);
    }

    /**
     * Returns total number of active bits set across both 64-bit words.
     */
    public int popcount() {
        return Long.bitCount(lo) + Long.bitCount(hi);
    }

    /**
     * Computes the fraction of query bits present in this record filter:
     * {@code overlap = (popcount(recordLo & queryLo) + popcount(recordHi & queryHi)) / (popcount(queryLo) + popcount(queryHi))}.
     */
    public float overlapRatio(final SynapticTag128 query) {
        if (query == null || query.isEmpty()) {
            return 1.0f;
        }
        if (this.isEmpty()) {
            return 0.0f;
        }
        final int queryBits = query.popcount();
        final int matchedBits = Long.bitCount(this.lo & query.lo) + Long.bitCount(this.hi & query.hi);
        return (float) matchedBits / queryBits;
    }
}
