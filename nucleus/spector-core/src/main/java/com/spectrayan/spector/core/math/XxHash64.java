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
package com.spectrayan.spector.core.math;

/**
 * Pure-Java xxHash64 implementation (ADR-0033 Domain 12, #31).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class XxHash64 {

    private static final long PRIME1 = 0x9E3779B185EBCA87L;
    private static final long PRIME2 = 0xC2B2AE3D27D4EB4FL;
    private static final long PRIME3 = 0x165667B19E3779F9L;
    private static final long PRIME4 = 0x85EBCA77C2B2AE63L;
    private static final long PRIME5 = 0x27D4EB2F165667C5L;

    private XxHash64() {
        // Prevent instantiation
    }

    /**
     * Computes the 64-bit hash of the given byte array.
     *
     * @param input the input bytes
     * @return the computed 64-bit hash
     */
    public static long hash(byte[] input) {
        if (input == null) {
            return 0L;
        }
        int len = input.length;
        long h64;
        int index = 0;

        if (len >= 32) {
            long v1 = PRIME1 + PRIME2;
            long v2 = PRIME2;
            long v3 = 0;
            long v4 = -PRIME1;

            int limit = len - 32;
            while (index <= limit) {
                v1 = round(v1, readLongLE(input, index));
                index += 8;
                v2 = round(v2, readLongLE(input, index));
                index += 8;
                v3 = round(v3, readLongLE(input, index));
                index += 8;
                v4 = round(v4, readLongLE(input, index));
                index += 8;
            }

            h64 = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7) + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
            h64 = mergeRound(h64, v1);
            h64 = mergeRound(h64, v2);
            h64 = mergeRound(h64, v3);
            h64 = mergeRound(h64, v4);
        } else {
            h64 = PRIME5;
        }

        h64 += len;

        int limit = len - 8;
        while (index <= limit) {
            long k1 = round(0, readLongLE(input, index));
            h64 ^= k1;
            h64 = Long.rotateLeft(h64, 27) * PRIME1 + PRIME4;
            index += 8;
        }

        limit = len - 4;
        if (index <= limit) {
            h64 ^= (readIntLE(input, index) & 0xFFFFFFFFL) * PRIME1;
            h64 = Long.rotateLeft(h64, 23) * PRIME2 + PRIME3;
            index += 4;
        }

        while (index < len) {
            h64 ^= (input[index] & 0xFFL) * PRIME5;
            h64 = Long.rotateLeft(h64, 11) * PRIME1;
            index++;
        }

        h64 ^= h64 >>> 33;
        h64 *= PRIME2;
        h64 ^= h64 >>> 29;
        h64 *= PRIME3;
        h64 ^= h64 >>> 32;

        return h64;
    }

    private static long round(long acc, long input) {
        acc += input * PRIME2;
        acc = Long.rotateLeft(acc, 31);
        acc *= PRIME1;
        return acc;
    }

    private static long mergeRound(long acc, long val) {
        val = round(0, val);
        acc ^= val;
        acc = acc * PRIME1 + PRIME4;
        return acc;
    }

    private static long readLongLE(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF))
                | (((long) (bytes[offset + 1] & 0xFF)) << 8)
                | (((long) (bytes[offset + 2] & 0xFF)) << 16)
                | (((long) (bytes[offset + 3] & 0xFF)) << 24)
                | (((long) (bytes[offset + 4] & 0xFF)) << 32)
                | (((long) (bytes[offset + 5] & 0xFF)) << 40)
                | (((long) (bytes[offset + 6] & 0xFF)) << 48)
                | (((long) (bytes[offset + 7] & 0xFF)) << 56);
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
