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

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Pure mathematical kernel for Kirsch-Mitzenmacher 64-bit double-hashing Bloom filters
 * and synaptic tag capture (ADR-0033 Domain 12, #30).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class SynapticTagMath {

    public static final int DEFAULT_K = 3;
    public static final int DEFAULT_M = 64;

    public static final int DEFAULT_K_128 = 4;
    public static final int DEFAULT_M_128 = 128;

    private SynapticTagMath() {}

    /**
     * Encodes a string tag into a 64-bit Bloom filter mask.
     *
     * @param tag tag name
     * @return 64-bit filter mask
     */
    public static long encodeTag(final String tag) {
        if (tag == null || tag.isEmpty()) {
            return 0L;
        }
        return encodeTag(tag.toLowerCase().getBytes(StandardCharsets.UTF_8), DEFAULT_K, DEFAULT_M);
    }

    /**
     * Encodes UTF-8 tag bytes into an m-bit Bloom filter mask using k hash slices.
     *
     * @param bytes UTF-8 encoded tag bytes
     * @param k     number of hash functions
     * @param m     filter bit width (must be power-of-two &lt;= 64)
     * @return 64-bit mask
     */
    public static long encodeTag(final byte[] bytes, final int k, final int m) {
        if (bytes == null || bytes.length == 0 || k <= 0 || m <= 0) {
            return 0L;
        }

        final long h = hash64(bytes);
        final long h1 = h;
        long h2 = h * 0x9e3779b97f4a7c15L;
        h2 ^= h2 >>> 33;
        h2 *= 0xc4ceb9fe1a85ec53L;
        h2 ^= h2 >>> 33;
        h2 |= 1L; // Ensure step size is odd

        long filter = 0L;
        final int mask = m - 1;
        for (int i = 0; i < k; i++) {
            final int bit = (int) ((h1 + (long) i * h2) & mask);
            filter |= (1L << bit);
        }
        return filter;
    }

    /**
     * Encodes multiple tags into a combined 64-bit Bloom filter mask.
     *
     * @param tags array of tag strings
     * @return bitwise OR merged 64-bit filter mask
     */
    public static long encode(final String... tags) {
        long filter = 0L;
        if (tags != null) {
            for (final String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    filter |= encodeTag(tag);
                }
            }
        }
        return filter;
    }

    /**
     * Encodes a collection of tags into a combined 64-bit Bloom filter mask.
     *
     * @param tags collection of tag strings
     * @return bitwise OR merged 64-bit filter mask
     */
    public static long encode(final Collection<String> tags) {
        long filter = 0L;
        if (tags != null) {
            for (final String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    filter |= encodeTag(tag);
                }
            }
        }
        return filter;
    }

    /**
     * Tests if all bits in {@code queryMask} are set in {@code recordFilter}.
     *
     * @param recordFilter record's Bloom filter
     * @param queryMask    query's Bloom filter mask
     * @return true if record satisfies query mask
     */
    public static boolean matches(final long recordFilter, final long queryMask) {
        if (queryMask == 0L) {
            return true;
        }
        return (recordFilter & queryMask) == queryMask;
    }

    /**
     * Tests if record filter contains the given tag.
     *
     * @param recordFilter record's Bloom filter
     * @param tag          tag to test
     * @return true if record matches tag
     */
    public static boolean matches(final long recordFilter, final String tag) {
        final long queryMask = encodeTag(tag);
        return matches(recordFilter, queryMask);
    }

    /**
     * Computes the fraction of query bits present in record filter:
     * <p>{@code overlap = popcount(record & query) / popcount(query)}</p>
     *
     * @param recordFilter record's Bloom filter
     * @param queryMask    query's Bloom filter mask
     * @return overlap ratio in [0.0, 1.0]
     */
    public static float overlapRatio(final long recordFilter, final long queryMask) {
        if (queryMask == 0L) {
            return 1.0f;
        }
        if (recordFilter == 0L) {
            return 0.0f;
        }
        final int queryBits = Long.bitCount(queryMask);
        final int matchedBits = Long.bitCount(recordFilter & queryMask);
        return (float) matchedBits / queryBits;
    }

    /**
     * Merges two Bloom filter masks via bitwise OR.
     */
    public static long merge(final long a, final long b) {
        return a | b;
    }

    /**
     * Returns the number of active bits set in the filter.
     */
    public static int bitCount(final long filter) {
        return Long.bitCount(filter);
    }

    /**
     * Alias for {@link #bitCount(long)}.
     */
    public static int popcount(final long filter) {
        return Long.bitCount(filter);
    }

    /**
     * Computes the theoretical false positive probability of the 64-bit (k=3, m=64) filter.
     *
     * @param numTags number of tags inserted
     * @return false positive probability in [0.0, 1.0]
     */
    public static double falsePositiveProbability(final int numTags) {
        return falsePositiveProbability(numTags, DEFAULT_K, DEFAULT_M);
    }

    /**
     * Computes the theoretical false positive probability of the 128-bit (k=4, m=128) filter.
     *
     * @param numTags number of tags inserted
     * @return false positive probability in [0.0, 1.0]
     */
    public static double falsePositiveProbability128(final int numTags) {
        return falsePositiveProbability(numTags, DEFAULT_K_128, DEFAULT_M_128);
    }

    /**
     * Encodes a string tag into an immutable 128-bit Bloom filter carrier record.
     *
     * @param tag tag name
     * @return 128-bit filter carrier
     */
    public static SynapticTag128 encodeTag128(final String tag) {
        if (tag == null || tag.isEmpty()) {
            return SynapticTag128.EMPTY;
        }
        return encodeTag128(tag.toLowerCase().getBytes(StandardCharsets.UTF_8), DEFAULT_K_128);
    }

    /**
     * Encodes UTF-8 tag bytes into a 128-bit Bloom filter carrier using k hash slices.
     *
     * @param bytes UTF-8 encoded tag bytes
     * @param k     number of hash functions (e.g. 4)
     * @return 128-bit filter carrier
     */
    public static SynapticTag128 encodeTag128(final byte[] bytes, final int k) {
        if (bytes == null || bytes.length == 0 || k <= 0) {
            return SynapticTag128.EMPTY;
        }

        final long h = hash64(bytes);
        final long h1 = h;
        long h2 = h * 0x9e3779b97f4a7c15L;
        h2 ^= h2 >>> 33;
        h2 *= 0xc4ceb9fe1a85ec53L;
        h2 ^= h2 >>> 33;
        h2 |= 1L; // Ensure step size is odd

        long lo = 0L;
        long hi = 0L;
        for (int i = 0; i < k; i++) {
            final int bit = (int) ((h1 + (long) i * h2) & 127);
            if (bit < 64) {
                lo |= (1L << bit);
            } else {
                hi |= (1L << (bit - 64));
            }
        }
        return new SynapticTag128(lo, hi);
    }

    /**
     * Encodes multiple tags into a combined 128-bit Bloom filter carrier.
     *
     * @param tags array of tag strings
     * @return bitwise OR merged 128-bit filter carrier
     */
    public static SynapticTag128 encode128(final String... tags) {
        long lo = 0L;
        long hi = 0L;
        if (tags != null) {
            for (final String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    final SynapticTag128 t = encodeTag128(tag);
                    lo |= t.lo();
                    hi |= t.hi();
                }
            }
        }
        return SynapticTag128.of(lo, hi);
    }

    /**
     * Encodes a collection of tags into a combined 128-bit Bloom filter carrier.
     *
     * @param tags collection of tag strings
     * @return bitwise OR merged 128-bit filter carrier
     */
    public static SynapticTag128 encode128(final Collection<String> tags) {
        long lo = 0L;
        long hi = 0L;
        if (tags != null) {
            for (final String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    final SynapticTag128 t = encodeTag128(tag);
                    lo |= t.lo();
                    hi |= t.hi();
                }
            }
        }
        return SynapticTag128.of(lo, hi);
    }

    /**
     * Tests if all bits in the query 128-bit mask are set in the record's 128-bit filter.
     *
     * @param recordLo record's low 64 bits
     * @param recordHi record's high 64 bits
     * @param queryLo  query's low 64 bits
     * @param queryHi  query's high 64 bits
     * @return true if record satisfies the query mask
     */
    public static boolean matches128(
            final long recordLo, final long recordHi, final long queryLo, final long queryHi) {
        if (queryLo == 0L && queryHi == 0L) {
            return true;
        }
        return (recordLo & queryLo) == queryLo && (recordHi & queryHi) == queryHi;
    }

    /**
     * Tests if the record's 128-bit filter contains the specified tag.
     */
    public static boolean matches128(final SynapticTag128 record, final String tag) {
        if (record == null) {
            return false;
        }
        final SynapticTag128 query = encodeTag128(tag);
        return record.matches(query);
    }

    /**
     * Computes the fraction of query bits present in the record filter across 128 bits:
     * {@code overlap = (popcount(recordLo & queryLo) + popcount(recordHi & queryHi)) / (popcount(queryLo) + popcount(queryHi))}.
     *
     * @param recordLo record's low 64 bits
     * @param recordHi record's high 64 bits
     * @param queryLo  query's low 64 bits
     * @param queryHi  query's high 64 bits
     * @return overlap ratio in [0.0, 1.0]
     */
    public static float overlapRatio128(
            final long recordLo, final long recordHi, final long queryLo, final long queryHi) {
        if (queryLo == 0L && queryHi == 0L) {
            return 1.0f;
        }
        if (recordLo == 0L && recordHi == 0L) {
            return 0.0f;
        }
        final int queryBits = Long.bitCount(queryLo) + Long.bitCount(queryHi);
        if (queryBits == 0) {
            return 1.0f;
        }
        final int matchedBits = Long.bitCount(recordLo & queryLo) + Long.bitCount(recordHi & queryHi);
        return (float) matchedBits / queryBits;
    }

    /**
     * Computes the theoretical false positive probability:
     * <p>{@code P_fp = (1 - exp(-k · n / m))^k}</p>
     *
     * @param numTags number of inserted elements
     * @param k       number of hash functions
     * @param m       number of filter bits
     * @return false positive probability in [0.0, 1.0]
     */
    public static double falsePositiveProbability(final int numTags, final int k, final int m) {
        if (numTags <= 0 || m <= 0 || k <= 0) {
            return 0.0;
        }
        final double exponent = -(double) (k * numTags) / (double) m;
        final double p = 1.0 - Math.exp(exponent);
        return Math.pow(p, k);
    }

    private static long hash64(final byte[] data) {
        long h = 0xCBF29CE484222325L;
        for (final byte b : data) {
            h ^= (b & 0xFF);
            h *= 0x100000001B3L;
        }
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }
}
