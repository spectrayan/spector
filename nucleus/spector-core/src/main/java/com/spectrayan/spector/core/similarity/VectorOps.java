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
package com.spectrayan.spector.core.similarity;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.core.simd.SimdCapability;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * SIMD-accelerated vector utility operations.
 *
 * <p>Provides common vector algebra operations (normalize, add, scale, magnitude)
 * all implemented with branchless SIMD kernels. These are the building blocks
 * used by the higher-level similarity functions and index structures.</p>
 */
public final class VectorOps {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private VectorOps() {
        // utility class
    }

    // ─────────────────────── Magnitude ───────────────────────

    /**
     * Computes the L2 magnitude (Euclidean norm) of a vector.
     *
     * @param v the vector
     * @return ‖v‖₂
     */
    public static float magnitude(float[] v) {
        return (float) Math.sqrt(magnitudeSquared(v, 0, v.length));
    }

    /**
     * Computes the squared L2 magnitude of a vector slice.
     *
     * @param v      the vector array
     * @param offset offset into {@code v}
     * @param length number of elements
     * @return ‖v‖₂²
     */
    public static float magnitudeSquared(float[] v, int offset, int length) {
        validateSlice(v, offset, length);

        int laneCount = SPECIES.length();
        FloatVector sum = FloatVector.zero(SPECIES);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vv = FloatVector.fromArray(SPECIES, v, offset + i);
            sum = vv.fma(vv, sum);
        }

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vv = FloatVector.fromArray(SPECIES, v, offset + i, mask);
            sum = sum.add(vv.mul(vv, mask));
        }

        return sum.reduceLanes(VectorOperators.ADD);
    }

    // ─────────────────────── Normalize ───────────────────────

    /**
     * Normalizes a vector to unit length (L2 normalization) and returns a new array.
     *
     * <p>If the vector has zero magnitude, returns a zero-filled array.</p>
     *
     * @param v the vector to normalize
     * @return a new array containing the unit vector
     */
    public static float[] normalize(float[] v) {
        float[] result = new float[v.length];
        normalize(v, 0, result, 0, v.length);
        return result;
    }

    /**
     * Normalizes a vector slice and writes the result to a destination slice.
     *
     * @param src       source array
     * @param srcOffset offset into source
     * @param dst       destination array
     * @param dstOffset offset into destination
     * @param length    number of elements
     */
    public static void normalize(float[] src, int srcOffset, float[] dst, int dstOffset, int length) {
        validateSlice(src, srcOffset, length);
        validateSlice(dst, dstOffset, length);

        float mag = (float) Math.sqrt(magnitudeSquared(src, srcOffset, length));
        if (mag == 0.0f) {
            java.util.Arrays.fill(dst, dstOffset, dstOffset + length, 0.0f);
            return;
        }

        float invMag = 1.0f / mag;
        scale(src, srcOffset, dst, dstOffset, length, invMag);
    }

    // ─────────────────────── Scale ───────────────────────

    /**
     * Scales a vector by a scalar factor and returns a new array.
     *
     * @param v      the vector
     * @param scalar the scaling factor
     * @return a new array containing the scaled vector
     */
    public static float[] scale(float[] v, float scalar) {
        float[] result = new float[v.length];
        scale(v, 0, result, 0, v.length, scalar);
        return result;
    }

    /**
     * Scales a vector slice by a scalar and writes to a destination slice.
     *
     * @param src       source array
     * @param srcOffset offset into source
     * @param dst       destination array
     * @param dstOffset offset into destination
     * @param length    number of elements
     * @param scalar    the scaling factor
     */
    public static void scale(float[] src, int srcOffset, float[] dst, int dstOffset, int length, float scalar) {
        validateSlice(src, srcOffset, length);
        validateSlice(dst, dstOffset, length);

        int laneCount = SPECIES.length();
        FloatVector vScalar = FloatVector.broadcast(SPECIES, scalar);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vv = FloatVector.fromArray(SPECIES, src, srcOffset + i);
            vv.mul(vScalar).intoArray(dst, dstOffset + i);
        }

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vv = FloatVector.fromArray(SPECIES, src, srcOffset + i, mask);
            vv.mul(vScalar).intoArray(dst, dstOffset + i, mask);
        }
    }

    // ─────────────────────── Add ───────────────────────

    /**
     * Adds two vectors element-wise and returns a new array.
     *
     * @param a first vector
     * @param b second vector
     * @return a new array containing a + b
     */
    public static float[] add(float[] a, float[] b) {
        float[] result = new float[a.length];
        add(a, 0, b, 0, result, 0, a.length);
        return result;
    }

    /**
     * Adds two vector slices element-wise and writes to a destination slice.
     */
    public static void add(float[] a, int aOffset, float[] b, int bOffset,
                           float[] dst, int dstOffset, int length) {
        validateSlice(a, aOffset, length);
        validateSlice(b, bOffset, length);
        validateSlice(dst, dstOffset, length);

        int laneCount = SPECIES.length();

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, aOffset + i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, bOffset + i);
            va.add(vb).intoArray(dst, dstOffset + i);
        }

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector va = FloatVector.fromArray(SPECIES, a, aOffset + i, mask);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, bOffset + i, mask);
            va.add(vb).intoArray(dst, dstOffset + i, mask);
        }
    }

    // ─────────────────────── Subtract ───────────────────────

    /**
     * Subtracts two vectors element-wise (a - b) and returns a new array.
     *
     * @param a first vector
     * @param b second vector
     * @return a new array containing a - b
     */
    public static float[] subtract(float[] a, float[] b) {
        float[] result = new float[a.length];
        subtract(a, 0, b, 0, result, 0, a.length);
        return result;
    }

    /**
     * Subtracts two vector slices element-wise and writes to a destination slice.
     */
    public static void subtract(float[] a, int aOffset, float[] b, int bOffset,
                                float[] dst, int dstOffset, int length) {
        validateSlice(a, aOffset, length);
        validateSlice(b, bOffset, length);
        validateSlice(dst, dstOffset, length);

        int laneCount = SPECIES.length();

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, aOffset + i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, bOffset + i);
            va.sub(vb).intoArray(dst, dstOffset + i);
        }

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector va = FloatVector.fromArray(SPECIES, a, aOffset + i, mask);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, bOffset + i, mask);
            va.sub(vb).intoArray(dst, dstOffset + i, mask);
        }
    }

    // ─────────────────────── Accumulate ───────────────────────

    /**
     * Accumulates the source vector into the destination vector in-place (dst[i] += src[i]).
     *
     * @param dst destination array (modified in-place)
     * @param src source array to add
     */
    public static void accumulate(float[] dst, float[] src) {
        if (dst == null || src == null || dst.length != src.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arrays must not be null and must have identical lengths");
        }
        accumulate(dst, 0, src, 0, dst.length);
    }

    /**
     * Accumulates a slice of source vector into destination slice in-place.
     */
    public static void accumulate(float[] dst, int dstOffset, float[] src, int srcOffset, int length) {
        validateSlice(dst, dstOffset, length);
        validateSlice(src, srcOffset, length);

        int laneCount = SPECIES.length();
        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vd = FloatVector.fromArray(SPECIES, dst, dstOffset + i);
            FloatVector vs = FloatVector.fromArray(SPECIES, src, srcOffset + i);
            vd.add(vs).intoArray(dst, dstOffset + i);
        }

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vd = FloatVector.fromArray(SPECIES, dst, dstOffset + i, mask);
            FloatVector vs = FloatVector.fromArray(SPECIES, src, srcOffset + i, mask);
            vd.add(vs, mask).intoArray(dst, dstOffset + i, mask);
        }
    }

    // ─────────────────────── Centroid ───────────────────────

    /**
     * Computes the centroid (mean vector) across a collection of vectors using SIMD acceleration.
     *
     * @param vectors    list of vectors to average
     * @param dimensions expected dimensionality
     * @return new array containing the centroid vector
     */
    public static float[] centroid(java.util.List<float[]> vectors, int dimensions) {
        if (vectors == null || vectors.isEmpty()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "vectors list must not be null or empty");
        }
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dimensions must be positive");
        }

        float[] result = new float[dimensions];
        for (float[] v : vectors) {
            if (v == null || v.length != dimensions) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector dimension mismatch in centroid computation");
            }
            accumulate(result, 0, v, 0, dimensions);
        }

        float invCount = 1.0f / vectors.size();
        scale(result, 0, result, 0, dimensions, invCount);
        return result;
    }

    // ─────────────────────── Validation ───────────────────────

    private static void validateSlice(float[] arr, int offset, int length) {
        if (length < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "length", length);
        }
        if (offset < 0 || offset + length > arr.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, String.format("offset=%d, length=%d, array.length=%d", offset, length, arr.length));
        }
    }

    /**
     * Validates two parallel float array slices for dual-operand operations
     * (e.g., dot product, cosine similarity, Euclidean distance).
     *
     * <p>Checks that {@code length} is non-negative and that both
     * {@code [aOffset, aOffset+length)} and {@code [bOffset, bOffset+length)}
     * are within their respective array bounds.</p>
     *
     * @param a       first array
     * @param aOffset offset into {@code a}
     * @param b       second array
     * @param bOffset offset into {@code b}
     * @param length  number of elements to process
     * @throws SpectorValidationException if any bounds check fails
     */
    public static void validateSliceInputs(float[] a, int aOffset, float[] b, int bOffset, int length) {
        if (length < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "length", length);
        }
        if (aOffset < 0 || aOffset + length > a.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, String.format("a: offset=%d, length=%d, array.length=%d", aOffset, length, a.length));
        }
        if (bOffset < 0 || bOffset + length > b.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, String.format("b: offset=%d, length=%d, array.length=%d", bOffset, length, b.length));
        }
    }

    // ─────────────────────── Linear Algebra ───────────────────────

    /**
     * SIMD-accelerated weighted linear combination of pattern vectors:
     * {@code outState = Σ weights[p] × patterns[p]} for {@code p ∈ [0, N)}.
     *
     * <p>Extracted from HopfieldKernel and LsrHopfieldKernel where this operation
     * was duplicated line-for-line. Uses masked SIMD tail handling for branchless
     * execution across all vector dimensions.</p>
     *
     * <p>Sparsity optimization: patterns with zero weight are skipped entirely.</p>
     *
     * @param patterns    array of N pattern vectors in ℝ^D
     * @param weights     normalized weights in ℝ^N
     * @param outState    destination vector in ℝ^D (overwritten with the result)
     * @throws SpectorValidationException if arguments are null, weights length
     *         doesn't match number of patterns, or any pattern has wrong dimension
     */
    public static void matrixVectorProduct(float[][] patterns, float[] weights, float[] outState) {
        if (patterns == null || weights == null || outState == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        int numPatterns = patterns.length;
        if (weights.length != numPatterns) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Weights length must match number of patterns");
        }
        if (numPatterns == 0) {
            return;
        }
        int dim = outState.length;
        java.util.Arrays.fill(outState, 0.0f);

        int laneCount = SPECIES.length();
        int limit = SPECIES.loopBound(dim);

        for (int p = 0; p < numPatterns; p++) {
            float w = weights[p];
            if (w == 0.0f) {
                continue;
            }
            float[] pattern = patterns[p];
            if (pattern.length != dim) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Pattern dimension mismatch at index " + p);
            }

            FloatVector vW = FloatVector.broadcast(SPECIES, w);

            int d = 0;
            for (; d < limit; d += laneCount) {
                FloatVector vAcc = FloatVector.fromArray(SPECIES, outState, d);
                FloatVector vPat = FloatVector.fromArray(SPECIES, pattern, d);
                vAcc = vAcc.add(vPat.mul(vW));
                vAcc.intoArray(outState, d);
            }

            if (d < dim) {
                VectorMask<Float> mask = SPECIES.indexInRange(d, dim);
                FloatVector vAcc = FloatVector.fromArray(SPECIES, outState, d, mask);
                FloatVector vPat = FloatVector.fromArray(SPECIES, pattern, d, mask);
                vAcc = vAcc.add(vPat.mul(vW, mask), mask);
                vAcc.intoArray(outState, d, mask);
            }
        }
    }
}