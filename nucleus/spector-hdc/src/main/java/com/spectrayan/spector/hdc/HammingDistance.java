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
package com.spectrayan.spector.hdc;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import java.util.Objects;
import java.lang.foreign.MemorySegment;

/**
 * SIMD Hamming distance computation.
 */
public final class HammingDistance {
    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    private HammingDistance() {}

    /**
     * Computes the Hamming distance between two hypervectors.
     *
     * @param a The first hypervector.
     * @param b The second hypervector.
     * @return The Hamming distance (number of differing bits).
     */
    public static int distance(Hypervector a, Hypervector b) {
        Objects.requireNonNull(a, "a cannot be null");
        Objects.requireNonNull(b, "b cannot be null");
        if (a.dimensions() != b.dimensions()) {
            throw new IllegalArgumentException("Dimensions must match");
        }

        long[] aw = a.words();
        long[] bw = b.words();
        int length = aw.length;
        int dist = 0;

        int limit = SPECIES.loopBound(length);
        int i = 0;
        for (; i < limit; i += SPECIES.length()) {
            LongVector va = LongVector.fromArray(SPECIES, aw, i);
            LongVector vb = LongVector.fromArray(SPECIES, bw, i);
            LongVector xor = va.lanewise(VectorOperators.XOR, vb);
            dist += (int) xor.lanewise(VectorOperators.BIT_COUNT).reduceLanes(VectorOperators.ADD);
        }

        if (i < length) {
            VectorMask<Long> mask = SPECIES.indexInRange(i, length);
            LongVector va = LongVector.fromArray(SPECIES, aw, i, mask);
            LongVector vb = LongVector.fromArray(SPECIES, bw, i, mask);
            LongVector xor = va.lanewise(VectorOperators.XOR, vb);
            LongVector zero = LongVector.zero(SPECIES);
            LongVector xorMasked = xor.blend(zero, mask.not());
            dist += (int) xorMasked.lanewise(VectorOperators.BIT_COUNT).reduceLanes(VectorOperators.ADD);
        }

        return dist;
    }

    /**
     * Computes the normalized similarity between two hypervectors.
     *
     * @param a The first hypervector.
     * @param b The second hypervector.
     * @return A similarity score between 0.0 and 1.0.
     */
    public static double similarity(Hypervector a, Hypervector b) {
        int dist = distance(a, b);
        return 1.0 - ((double) dist / a.dimensions());
    }

    /**
     * Computes the Hamming distance between two raw MemorySegments.
     *
     * @param a The first MemorySegment.
     * @param b The second MemorySegment.
     * @param wordCount The number of long words to process.
     * @return The Hamming distance.
     */
    public static int distance(MemorySegment a, MemorySegment b, int wordCount) {
        Objects.requireNonNull(a, "a cannot be null");
        Objects.requireNonNull(b, "b cannot be null");
        
        int dist = 0;
        int limit = SPECIES.loopBound(wordCount);
        int i = 0;
        
        for (; i < limit; i += SPECIES.length()) {
            LongVector va = LongVector.fromMemorySegment(SPECIES, a, (long) i * 8L, java.nio.ByteOrder.nativeOrder());
            LongVector vb = LongVector.fromMemorySegment(SPECIES, b, (long) i * 8L, java.nio.ByteOrder.nativeOrder());
            LongVector xor = va.lanewise(VectorOperators.XOR, vb);
            dist += (int) xor.lanewise(VectorOperators.BIT_COUNT).reduceLanes(VectorOperators.ADD);
        }

        if (i < wordCount) {
            VectorMask<Long> mask = SPECIES.indexInRange(i, wordCount);
            LongVector va = LongVector.fromMemorySegment(SPECIES, a, (long) i * 8L, java.nio.ByteOrder.nativeOrder(), mask);
            LongVector vb = LongVector.fromMemorySegment(SPECIES, b, (long) i * 8L, java.nio.ByteOrder.nativeOrder(), mask);
            LongVector xor = va.lanewise(VectorOperators.XOR, vb);
            LongVector zero = LongVector.zero(SPECIES);
            LongVector xorMasked = xor.blend(zero, mask.not());
            dist += (int) xorMasked.lanewise(VectorOperators.BIT_COUNT).reduceLanes(VectorOperators.ADD);
        }
        
        return dist;
    }
}
