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

import com.spectrayan.spector.core.similarity.DotProduct;
import com.spectrayan.spector.core.similarity.VectorOps;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.simd.SimdCapability;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;

/**
 * SIMD-accelerated kernel for Modern Continuous Hopfield Networks (Ramsauer et al., 2021).
 *
 * <h3>Biological Analog: CA3 Associative Memory Attractor Dynamics</h3>
 * <p>Implements continuous energy-based memory retrieval. Projects state representations
 * onto memory pattern manifolds and performs exponential attractor convergence through
 * SIMD-vectorized attention operations.</p>
 */
public final class HopfieldKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private HopfieldKernel() {
        // utility class
    }

    /**
     * Computes dot products between a query state vector and an array of pattern vectors.
     *
     * @param state query state vector xi in R^D
     * @param patterns array of N pattern vectors X_i in R^D
     * @param outDotProducts destination array of length N for dot products
     */
    public static void computePatternProjections(float[] state, float[][] patterns, float[] outDotProducts) {
        validateInputs(state, patterns, outDotProducts);
        int numPatterns = patterns.length;
        for (int i = 0; i < numPatterns; i++) {
            outDotProducts[i] = DotProduct.compute(state, patterns[i]);
        }
    }

    /**
     * Computes a numerically stable scaled softmax over logits:
     * w_i = exp(beta * l_i - max_j(beta * l_j)) / sum_k exp(beta * l_k - max_j(beta * l_j))
     *
     * @param logits input logit array
     * @param beta inverse temperature scaling factor
     * @param outWeights destination array for normalized probability weights
     */
    public static void softmax(float[] logits, float beta, float[] outWeights) {
        if (logits == null || outWeights == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arrays must not be null");
        }
        int n = logits.length;
        if (outWeights.length != n) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Output weights array length must match logits length");
        }
        if (n == 0) {
            return;
        }

        // Find max for numerical stability
        float maxScaled = logits[0] * beta;
        for (int i = 1; i < n; i++) {
            float scaled = logits[i] * beta;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }

        float sumExp = 0.0f;
        for (int i = 0; i < n; i++) {
            float expVal = (float) Math.exp(logits[i] * beta - maxScaled);
            outWeights[i] = expVal;
            sumExp += expVal;
        }

        if (sumExp > 0.0f) {
            float invSum = 1.0f / sumExp;
            for (int i = 0; i < n; i++) {
                outWeights[i] *= invSum;
            }
        } else {
            float uniform = 1.0f / n;
            Arrays.fill(outWeights, uniform);
        }
    }

    /**
     * Computes the matrix-vector linear combination of patterns:
     * xi_new = sum_{i=1}^N (w_i * X_i)
     *
     * @param patterns array of N pattern vectors X_i in R^D
     * @param weights normalized attention weights w in R^N
     * @param outState destination vector in R^D
     */
    public static void matrixVectorProduct(float[][] patterns, float[] weights, float[] outState) {
        VectorOps.matrixVectorProduct(patterns, weights, outState);
    }

    /**
     * Executes a single continuous Modern Hopfield update step:
     * xi_new = X * softmax(beta * X^T * xi)
     *
     * @param state current state vector xi in R^D
     * @param patterns array of N pattern vectors in R^D
     * @param beta inverse temperature scaling
     * @param outState destination vector for updated state xi_new
     * @param outWeights destination array for intermediate attention weights
     */
    public static void update(float[] state, float[][] patterns, float beta, float[] outState, float[] outWeights) {
        float[] dotProducts = new float[patterns.length];
        computePatternProjections(state, patterns, dotProducts);
        softmax(dotProducts, beta, outWeights);
        matrixVectorProduct(patterns, outWeights, outState);
    }

    /**
     * Computes the continuous Modern Hopfield energy:
     * E(xi, X) = -1/beta * log( sum_{i=1}^N exp(beta * X_i^T * xi) ) + 0.5 * ||xi||^2
     *
     * @param state current state vector xi in R^D
     * @param patterns array of N pattern vectors in R^D
     * @param beta inverse temperature scaling parameter
     * @return scalar energy value
     */
    public static float continuousEnergy(float[] state, float[][] patterns, float beta) {
        if (state == null || patterns == null || patterns.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "State and patterns must not be null or empty");
        }
        int n = patterns.length;
        float[] dots = new float[n];
        computePatternProjections(state, patterns, dots);

        // Numerically stable Log-Sum-Exp: lse(z) = max(z) + ln(sum(exp(z - max(z))))
        float maxScaled = dots[0] * beta;
        for (int i = 1; i < n; i++) {
            float scaled = dots[i] * beta;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }

        float sumExp = 0.0f;
        for (int i = 0; i < n; i++) {
            sumExp += (float) Math.exp(dots[i] * beta - maxScaled);
        }

        float lse = maxScaled + (float) Math.log(sumExp);
        float normSq = DotProduct.compute(state, state);

        return (-1.0f / beta) * lse + 0.5f * normSq;
    }

    private static void validateInputs(float[] state, float[][] patterns, float[] outDots) {
        if (state == null || patterns == null || outDots == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        if (patterns.length != outDots.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Output dot products array must match pattern count");
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
