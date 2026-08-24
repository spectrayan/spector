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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.simd.SimdCapability;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;

/**
 * SIMD-accelerated kernel for Log-Sum-ReLU (LSR) / Epanechnikov Modern Associative Memory (Hoover et al., 2025).
 *
 * <h3>Biological Analog: CA3 Sparse Autoassociative Sharp-Wave Ripple Attractor Settlement</h3>
 * <p>Implements exact single-step ($T=1$) associative memory retrieval using the statistically optimal
 * Epanechnikov kernel. Unlike Gaussian/Log-Sum-Exp networks, LSR possesses compact finite support,
 * strictly eliminating semantic noise from non-associated distant memory candidates while bypassing
 * costly transcendental instructions.</p>
 *
 * <h3>Mathematical Energy</h3>
 * <pre>
 *   E_LSR(v, X) = -ln( sum_{i=1}^N ReLU(1 - (beta / 2) * ||v - X_i||^2) )
 * </pre>
 *
 * <h3>Attractor Update</h3>
 * <pre>
 *   w_i = ReLU(1 - (beta / 2) * ||v - X_i||^2) / sum_j ReLU(1 - (beta / 2) * ||v - X_j||^2)
 *   v_settled = sum_{i=1}^N (w_i * X_i)
 * </pre>
 */
public final class LsrHopfieldKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private LsrHopfieldKernel() {
        // utility class
    }

    /**
     * Computes squared Euclidean distances between a query state vector and an array of pattern vectors.
     *
     * @param state query state vector in R^D
     * @param patterns array of N pattern vectors in R^D
     * @param outSqDistances destination array of length N for squared Euclidean distances
     */
    public static void computePatternSquaredDistances(float[] state, float[][] patterns, float[] outSqDistances) {
        validateInputs(state, patterns, outSqDistances);
        int numPatterns = patterns.length;
        for (int i = 0; i < numPatterns; i++) {
            outSqDistances[i] = EuclideanDistance.computeSquared(state, patterns[i]);
        }
    }

    /**
     * Computes normalized Epanechnikov attention weights and returns the scalar LSR energy:
     * <pre>
     *   raw_i = max(0, 1.0 - 0.5 * beta * sqDist_i)
     *   w_i = raw_i / sum_k raw_k
     *   E_LSR = -ln(sum_k raw_k)
     * </pre>
     *
     * @param sqDistances input squared Euclidean distances
     * @param beta inverse temperature scaling factor (beta > 0)
     * @param outWeights destination array for normalized Epanechnikov weights
     * @return scalar Epanechnikov energy E_LSR (Float.POSITIVE_INFINITY if outside support)
     */
    public static float computeLsrWeights(float[] sqDistances, float beta, float[] outWeights) {
        if (sqDistances == null || outWeights == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arrays must not be null");
        }
        int n = sqDistances.length;
        if (outWeights.length != n) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Output weights array length must match distance count");
        }
        if (n == 0) {
            return Float.POSITIVE_INFINITY;
        }

        float halfBeta = 0.5f * beta;
        float sumRaw = 0.0f;

        for (int i = 0; i < n; i++) {
            float score = 1.0f - halfBeta * sqDistances[i];
            float weight = score > 0.0f ? score : 0.0f;
            outWeights[i] = weight;
            sumRaw += weight;
        }

        if (sumRaw > 0.0f) {
            float invSum = 1.0f / sumRaw;
            for (int i = 0; i < n; i++) {
                outWeights[i] *= invSum;
            }
            return -(float) Math.log(sumRaw);
        } else {
            // Out of support: uniform weights fallback, infinite energy
            float uniform = 1.0f / n;
            Arrays.fill(outWeights, uniform);
            return Float.POSITIVE_INFINITY;
        }
    }

    /**
     * Computes the matrix-vector linear combination of patterns:
     * v_new = sum_{i=1}^N (w_i * X_i)
     *
     * @param patterns array of N pattern vectors X_i in R^D
     * @param weights normalized attention weights w in R^N
     * @param outState destination vector in R^D
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
        Arrays.fill(outState, 0.0f);

        int laneCount = SPECIES.length();
        int limit = SPECIES.loopBound(dim);

        for (int p = 0; p < numPatterns; p++) {
            float w = weights[p];
            if (w == 0.0f) {
                continue; // Sparsity optimization: skip zero-weight patterns immediately
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

    /**
     * Executes a single Log-Sum-ReLU (Epanechnikov) associative update step:
     * v_new = X * weights(beta, ||v - X_i||^2)
     *
     * @param state current query vector v in R^D
     * @param patterns array of N pattern vectors in R^D
     * @param beta inverse temperature scaling
     * @param outState destination vector for updated state v_new
     * @param outWeights destination array for Epanechnikov attention weights
     * @return scalar Epanechnikov energy E_LSR
     */
    public static float update(float[] state, float[][] patterns, float beta, float[] outState, float[] outWeights) {
        float[] sqDistances = new float[patterns.length];
        computePatternSquaredDistances(state, patterns, sqDistances);
        float energy = computeLsrWeights(sqDistances, beta, outWeights);
        matrixVectorProduct(patterns, outWeights, outState);
        return energy;
    }

    /**
     * Computes the scalar Epanechnikov / Log-Sum-ReLU energy for a state vector:
     * E_LSR(v, X) = -ln( sum_{i=1}^N ReLU(1 - 0.5 * beta * ||v - X_i||^2) )
     *
     * @param state current state vector v in R^D
     * @param patterns array of N pattern vectors in R^D
     * @param beta inverse temperature scaling parameter
     * @return scalar energy value (Float.POSITIVE_INFINITY if state is outside all basins)
     */
    public static float continuousEnergy(float[] state, float[][] patterns, float beta) {
        if (state == null || patterns == null || patterns.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "State and patterns must not be null or empty");
        }
        int n = patterns.length;
        float[] sqDists = new float[n];
        computePatternSquaredDistances(state, patterns, sqDists);

        float halfBeta = 0.5f * beta;
        float sumRaw = 0.0f;
        for (int i = 0; i < n; i++) {
            float score = 1.0f - halfBeta * sqDists[i];
            if (score > 0.0f) {
                sumRaw += score;
            }
        }

        return sumRaw > 0.0f ? -(float) Math.log(sumRaw) : Float.POSITIVE_INFINITY;
    }

    /**
     * Computes the compact support basin radius for a given inverse temperature beta:
     * r_c = sqrt(2 / beta)
     *
     * @param beta inverse temperature (beta > 0)
     * @return critical Euclidean basin radius
     */
    public static float basinRadius(float beta) {
        if (beta <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Beta must be positive");
        }
        return (float) Math.sqrt(2.0 / beta);
    }

    private static void validateInputs(float[] state, float[][] patterns, float[] outSqDists) {
        if (state == null || patterns == null || outSqDists == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        if (patterns.length != outSqDists.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Output distances array must match pattern count");
        }
        int dim = state.length;
        if (dim == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "State dimension must be greater than zero");
        }
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] == null || patterns[i].length != dim) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Pattern dimension mismatch at index " + i);
            }
        }
    }
}
