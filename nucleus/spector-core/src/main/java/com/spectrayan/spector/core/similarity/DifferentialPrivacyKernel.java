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

import java.util.Random;

/**
 * SIMD-accelerated mathematical kernel for Differential Privacy (DP) mechanisms.
 *
 * <h3>Mathematical Analog: Gaussian & Laplace Mechanisms</h3>
 * <p>Provides \((\epsilon, \delta)\)-differential privacy for continuous high-dimensional
 * embedding vectors and \(\epsilon\)-differential privacy for scalar metadata.</p>
 */
public final class DifferentialPrivacyKernel {

    private DifferentialPrivacyKernel() {
        // pure utility / kernel class
    }

    /**
     * Computes the theoretical Gaussian mechanism standard deviation \(\sigma\) satisfying \((\epsilon, \delta)\)-DP.
     * \[\sigma = \frac{\Delta_2 \sqrt{2 \ln(1.25 / \delta)}}{\epsilon}\]
     *
     * @param sensitivity L2 sensitivity bound \(\Delta_2\)
     * @param epsilon     privacy budget \(\epsilon > 0\)
     * @param delta       failure probability \(0 < \delta < 1\)
     * @return standard deviation \(\sigma\)
     */
    public static float computeGaussianSigma(float sensitivity, float epsilon, float delta) {
        if (sensitivity <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "sensitivity must be positive");
        }
        if (epsilon <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "epsilon must be positive");
        }
        if (delta <= 0.0f || delta >= 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "delta must be in (0, 1)");
        }

        double term = 2.0 * Math.log(1.25 / delta);
        return (float) (sensitivity * Math.sqrt(term) / epsilon);
    }

    /**
     * Clips the L2 norm of the vector to the specified threshold \(\bar{\boldsymbol{v}} = \boldsymbol{v} / \max(1, \|\boldsymbol{v}\|_2 / C)\).
     *
     * @param vector            embedding vector
     * @param clippingThreshold maximum allowable L2 norm \(C\)
     * @return new array containing the L2-clipped vector
     */
    public static float[] clipVectorL2(float[] vector, float clippingThreshold) {
        if (vector == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "vector must not be null");
        }
        if (clippingThreshold <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "clippingThreshold must be positive");
        }

        float mag = VectorOps.magnitude(vector);
        if (mag <= clippingThreshold || mag == 0.0f) {
            return vector.clone();
        }

        float scaleFactor = clippingThreshold / mag;
        return VectorOps.scale(vector, scaleFactor);
    }

    /**
     * Injects calibrated zero-mean spherical Gaussian noise \(\mathcal{N}(0, \sigma^2 \mathbf{I})\) into the vector.
     *
     * @param vector input vector
     * @param sigma  noise standard deviation
     * @param rng    random number generator (can be null, uses standard Random if null)
     * @return new array containing the perturbed vector
     */
    public static float[] injectGaussianNoise(float[] vector, float sigma, Random rng) {
        if (vector == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "vector must not be null");
        }
        if (sigma <= 0.0f) {
            return vector.clone();
        }

        Random random = (rng != null) ? rng : new Random();
        float[] perturbed = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            float noise = (float) (random.nextGaussian() * sigma);
            perturbed[i] = vector[i] + noise;
        }

        return perturbed;
    }

    /**
     * Injects calibrated zero-mean Laplace noise \(\text{Laplace}(0, \Delta_1 / \epsilon)\) into a scalar metric.
     *
     * @param scalar      input scalar value
     * @param sensitivity L1 sensitivity bound \(\Delta_1\)
     * @param epsilon     privacy budget \(\epsilon > 0\)
     * @param rng         random number generator (can be null)
     * @return perturbed scalar value
     */
    public static float injectLaplaceNoise(float scalar, float sensitivity, float epsilon, Random rng) {
        if (sensitivity <= 0.0f || epsilon <= 0.0f) {
            return scalar;
        }

        float scale = sensitivity / epsilon;
        Random random = (rng != null) ? rng : new Random();

        // Sample uniform in (-0.5, 0.5)
        double u = random.nextDouble() - 0.5;
        while (u == 0.0) {
            u = random.nextDouble() - 0.5;
        }

        double noise = -scale * Math.signum(u) * Math.log(1.0 - 2.0 * Math.abs(u));
        return (float) (scalar + noise);
    }
}
