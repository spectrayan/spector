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
package com.spectrayan.spector.kernel.score;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * 64-bit inline Bloom filter encoder for synaptic tags (R17.1).
 *
 * <p>Pure, stateless, all static mathematical utility for synaptic tagging and capture.</p>
 */
public final class SynapticTagEncoder {

    private static final int K = 3;
    private static final int M = 64;

    private SynapticTagEncoder() {}

    public static long encode(String... tags) {
        long filter = 0L;
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    filter |= encodeTag(tag);
                }
            }
        }
        return filter;
    }

    public static long encode(Collection<String> tags) {
        long filter = 0L;
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    filter |= encodeTag(tag);
                }
            }
        }
        return filter;
    }

    public static long encodeTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return 0L;
        }
        byte[] bytes = tag.toLowerCase().getBytes(StandardCharsets.UTF_8);
        long h = hash64(bytes);
        long h1 = h;
        long h2 = h * 0x9e3779b97f4a7c15L;
        h2 ^= h2 >>> 33;
        h2 *= 0xc4ceb9fe1a85ec53L;
        h2 ^= h2 >>> 33;
        h2 |= 1L; // Ensure step size is odd so that gcd(h2, 64) = 1 for power-of-two M

        long filter = 0L;
        for (int i = 0; i < K; i++) {
            int bit = (int) ((h1 + (long) i * h2) & (M - 1));
            filter |= (1L << bit);
        }
        return filter;
    }

    public static boolean matches(long recordFilter, long queryMask) {
        if (queryMask == 0L) {
            return true;
        }
        return (recordFilter & queryMask) == queryMask;
    }

    public static boolean matches(long recordFilter, String tag) {
        long queryMask = encodeTag(tag);
        return matches(recordFilter, queryMask);
    }

    public static float overlapRatio(long recordTags, long queryTags) {
        if (queryTags == 0L) {
            return 1.0f;
        }
        if (recordTags == 0L) {
            return 0.0f;
        }
        int queryBits = Long.bitCount(queryTags);
        int matchedBits = Long.bitCount(recordTags & queryTags);
        return (float) matchedBits / queryBits;
    }

    public static long merge(long a, long b) {
        return a | b;
    }

    public static int bitCount(long filter) {
        return Long.bitCount(filter);
    }

    public static int popcount(long filter) {
        return Long.bitCount(filter);
    }

    public static double falsePositiveProbability(int numTags) {
        if (numTags <= 0) {
            return 0.0;
        }
        double exponent = -(double) (K * numTags) / (double) M;
        double p = 1.0 - Math.exp(exponent);
        return Math.pow(p, K);
    }

    private static long hash64(byte[] data) {
        long h = 0xCBF29CE484222325L;
        for (byte b : data) {
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
