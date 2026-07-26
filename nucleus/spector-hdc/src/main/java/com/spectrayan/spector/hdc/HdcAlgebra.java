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
import java.util.List;
import java.util.Objects;

/**
 * Core HDC SIMD operations.
 */
public final class HdcAlgebra {
    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    private HdcAlgebra() {
        // Utility class
    }

    /**
     * Binds two hypervectors via XOR using SIMD LongVector.
     * 
     * @param a The first hypervector.
     * @param b The second hypervector.
     * @return The bound hypervector.
     */
    public static Hypervector bind(Hypervector a, Hypervector b) {
        Objects.requireNonNull(a, "a cannot be null");
        Objects.requireNonNull(b, "b cannot be null");
        if (a.dimensions() != b.dimensions()) {
            throw new IllegalArgumentException("Dimensions must match");
        }

        long[] aw = a.words();
        long[] bw = b.words();
        int length = aw.length;
        long[] rw = new long[length];

        int limit = SPECIES.loopBound(length);
        int i = 0;
        for (; i < limit; i += SPECIES.length()) {
            LongVector va = LongVector.fromArray(SPECIES, aw, i);
            LongVector vb = LongVector.fromArray(SPECIES, bw, i);
            va.lanewise(VectorOperators.XOR, vb).intoArray(rw, i);
        }

        if (i < length) {
            VectorMask<Long> mask = SPECIES.indexInRange(i, length);
            LongVector va = LongVector.fromArray(SPECIES, aw, i, mask);
            LongVector vb = LongVector.fromArray(SPECIES, bw, i, mask);
            va.lanewise(VectorOperators.XOR, vb).intoArray(rw, i, mask);
        }

        return Hypervector.fromBits(rw, a.dimensions());
    }

    /**
     * Bundles a list of hypervectors using majority vote.
     *
     * @param vectors The list of hypervectors.
     * @return The bundled hypervector.
     */
    public static Hypervector bundle(List<Hypervector> vectors) {
        Objects.requireNonNull(vectors, "vectors cannot be null");
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("List of vectors cannot be empty");
        }
        int dimensions = vectors.get(0).dimensions();
        for (Hypervector v : vectors) {
            if (v.dimensions() != dimensions) {
                throw new IllegalArgumentException("All vectors must have the same dimensions");
            }
        }
        
        int numVectors = vectors.size();
        int threshold = numVectors / 2;
        boolean even = (numVectors % 2 == 0);
        
        long[][] allWords = new long[numVectors][];
        for (int v = 0; v < numVectors; v++) {
            allWords[v] = vectors.get(v).words();
        }
        
        int wordsLen = allWords[0].length;
        long[] resultWords = new long[wordsLen];
        
        for (int i = 0; i < wordsLen; i++) {
            long resultWord = 0;
            for (int bit = 0; bit < 64; bit++) {
                int count = 0;
                long bitMask = 1L << bit;
                for (int v = 0; v < numVectors; v++) {
                    if ((allWords[v][i] & bitMask) != 0) {
                        count++;
                    }
                }
                if (count > threshold || (even && count == threshold && (allWords[0][i] & bitMask) != 0)) {
                    resultWord |= bitMask;
                }
            }
            resultWords[i] = resultWord;
        }
        
        return Hypervector.fromBits(resultWords, dimensions);
    }

    /**
     * Permutes a hypervector by applying a cyclic bit shift.
     *
     * @param v The hypervector.
     * @param shift The number of bit positions to shift.
     * @return The permuted hypervector.
     */
    public static Hypervector permute(Hypervector v, int shift) {
        Objects.requireNonNull(v, "v cannot be null");
        int dim = v.dimensions();
        if (dim == 0) return v;
        
        shift = shift % dim;
        if (shift < 0) shift += dim;
        if (shift == 0) return Hypervector.fromBits(v.words(), dim);
        
        long[] rw = new long[v.wordCount()];
        long[] aw = v.words();
        
        for (int i = 0; i < dim; i++) {
            int oldBit = (int) ((aw[i / 64] >>> (i % 64)) & 1L);
            if (oldBit == 1) {
                int newI = (i + shift) % dim;
                rw[newI / 64] |= (1L << (newI % 64));
            }
        }
        return Hypervector.fromBits(rw, dim);
    }

    /**
     * Computes the inverse (bitwise NOT) of a hypervector.
     *
     * @param v The hypervector.
     * @return The inverted hypervector.
     */
    public static Hypervector inverse(Hypervector v) {
        Objects.requireNonNull(v, "v cannot be null");
        long[] aw = v.words();
        int length = aw.length;
        long[] rw = new long[length];
        
        int limit = SPECIES.loopBound(length);
        int i = 0;
        for (; i < limit; i += SPECIES.length()) {
            LongVector va = LongVector.fromArray(SPECIES, aw, i);
            va.not().intoArray(rw, i);
        }
        
        if (i < length) {
            VectorMask<Long> mask = SPECIES.indexInRange(i, length);
            LongVector va = LongVector.fromArray(SPECIES, aw, i, mask);
            va.not().intoArray(rw, i, mask);
        }
        
        return Hypervector.fromBits(rw, v.dimensions());
    }
}
