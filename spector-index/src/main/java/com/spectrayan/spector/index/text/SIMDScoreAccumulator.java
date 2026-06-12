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
package com.spectrayan.spector.index;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * SIMD-accelerated operations on {@code float[]} score arrays.
 *
 * <h3>Purpose</h3>
 * <p>Provides vectorized array operations used by BM25 and SPLADE scoring loops.
 * Uses the JDK Vector API ({@link FloatVector}) to process multiple float values
 * per instruction via SIMD registers (AVX2/AVX-512/SVE).</p>
 *
 * <h3>Fallback</h3>
 * <p>All operations automatically fall back to scalar loops when SIMD is unavailable
 * or when the array size is too small for vectorization to help. The cutoff is
 * determined by {@link #SPECIES}'s lane count.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Vectorized array addition: dst[i] += src[i]
 *   SIMDScoreAccumulator.addArrays(mergedScores, termScores, n);
 *
 *   // Vectorized max: find peak score
 *   float maxScore = SIMDScoreAccumulator.maxValue(scores, n);
 * }</pre>
 *
 * <h3>Performance</h3>
 * <p>On AVX-512 (16 floats/lane), array addition runs at ~16x scalar throughput
 * for large arrays. On AVX2 (8 floats/lane), ~8x. The JIT compiler emits
 * native SIMD instructions ({@code vaddps}, {@code vmaxps}) from this code.</p>
 *
 * @see FloatVector
 */
public final class SIMDScoreAccumulator {

    private SIMDScoreAccumulator() {}

    /** Preferred vector species for this hardware (AVX-512 → 16 lanes, AVX2 → 8, SSE → 4). */
    static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    /** True if SIMD is available with at least 2 lanes (i.e., not degenerate). */
    private static final boolean SIMD_AVAILABLE = SPECIES.length() >= 2;

    /**
     * SIMD-accelerated element-wise addition: {@code dst[i] += src[i]} for {@code i ∈ [0, n)}.
     *
     * <p>Uses SIMD vector addition for the bulk of the array, then a scalar tail loop
     * for remaining elements that don't fill a full vector lane.</p>
     *
     * @param dst destination array (modified in place)
     * @param src source array to add
     * @param n   number of elements to process (must be ≤ both array lengths)
     */
    public static void addArrays(float[] dst, float[] src, int n) {
        if (SIMD_AVAILABLE && n >= SPECIES.length()) {
            addArraysSIMD(dst, src, n);
        } else {
            addArraysScalar(dst, src, n);
        }
    }

    /**
     * SIMD-accelerated maximum value in array: {@code max(arr[0..n))}.
     *
     * @param arr the array to scan
     * @param n   number of elements to scan (must be ≤ array length)
     * @return the maximum value, or {@link Float#NEGATIVE_INFINITY} if n == 0
     */
    public static float maxValue(float[] arr, int n) {
        if (n == 0) return Float.NEGATIVE_INFINITY;
        if (SIMD_AVAILABLE && n >= SPECIES.length()) {
            return maxValueSIMD(arr, n);
        } else {
            return maxValueScalar(arr, n);
        }
    }

    /**
     * SIMD-accelerated element-wise multiply-add: {@code dst[i] += factor * src[i]}.
     *
     * <p>Useful for weighted score accumulation (e.g., IDF * TF-norm arrays).</p>
     *
     * @param dst    destination array (modified in place)
     * @param src    source array
     * @param factor scalar multiplier applied to each src element
     * @param n      number of elements to process
     */
    public static void fmaArrays(float[] dst, float[] src, float factor, int n) {
        if (SIMD_AVAILABLE && n >= SPECIES.length()) {
            fmaArraysSIMD(dst, src, factor, n);
        } else {
            fmaArraysScalar(dst, src, factor, n);
        }
    }

    /** Returns true if SIMD acceleration is available on this hardware. */
    public static boolean isAvailable() {
        return SIMD_AVAILABLE;
    }

    /** Returns the number of float lanes per SIMD register. */
    public static int laneCount() {
        return SPECIES.length();
    }

    // ═══════════════════════════════════════════════════════════
    // SIMD implementations
    // ═══════════════════════════════════════════════════════════

    private static void addArraysSIMD(float[] dst, float[] src, int n) {
        int i = 0;
        int limit = SPECIES.loopBound(n);

        // Vectorized bulk: process SPECIES.length() floats per iteration
        for (; i < limit; i += SPECIES.length()) {
            FloatVector a = FloatVector.fromArray(SPECIES, dst, i);
            FloatVector b = FloatVector.fromArray(SPECIES, src, i);
            a.add(b).intoArray(dst, i);
        }

        // Scalar tail: remaining elements that don't fill a full vector
        for (; i < n; i++) {
            dst[i] += src[i];
        }
    }

    private static float maxValueSIMD(float[] arr, int n) {
        int i = 0;
        int limit = SPECIES.loopBound(n);

        FloatVector maxVec = FloatVector.broadcast(SPECIES, Float.NEGATIVE_INFINITY);

        for (; i < limit; i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, arr, i);
            maxVec = maxVec.max(v);
        }

        // Reduce the vector to a single max
        float max = maxVec.reduceLanes(jdk.incubator.vector.VectorOperators.MAX);

        // Scalar tail
        for (; i < n; i++) {
            if (arr[i] > max) max = arr[i];
        }

        return max;
    }

    private static void fmaArraysSIMD(float[] dst, float[] src, float factor, int n) {
        int i = 0;
        int limit = SPECIES.loopBound(n);
        FloatVector factorVec = FloatVector.broadcast(SPECIES, factor);

        for (; i < limit; i += SPECIES.length()) {
            FloatVector d = FloatVector.fromArray(SPECIES, dst, i);
            FloatVector s = FloatVector.fromArray(SPECIES, src, i);
            d.add(s.mul(factorVec)).intoArray(dst, i);
        }

        // Scalar tail
        for (; i < n; i++) {
            dst[i] += factor * src[i];
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Scalar fallbacks
    // ═══════════════════════════════════════════════════════════

    private static void addArraysScalar(float[] dst, float[] src, int n) {
        for (int i = 0; i < n; i++) {
            dst[i] += src[i];
        }
    }

    private static float maxValueScalar(float[] arr, int n) {
        float max = arr[0];
        for (int i = 1; i < n; i++) {
            if (arr[i] > max) max = arr[i];
        }
        return max;
    }

    private static void fmaArraysScalar(float[] dst, float[] src, float factor, int n) {
        for (int i = 0; i < n; i++) {
            dst[i] += factor * src[i];
        }
    }
}
