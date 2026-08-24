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
package com.spectrayan.spector.core.simd;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.similarity.DotProduct;
import com.spectrayan.spector.core.similarity.EuclideanDistance;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

import java.util.Random;

/**
 * SIMD-accelerated Positive Random Feature (PRF) Projector for distributed associative memory (Hoover et al., 2024).
 *
 * <h3>Biological Analog: Pribram's Holographic Neural Transform</h3>
 * <p>Maps dense memory vectors in R^D into a distributed randomized feature space in R^Y.
 * Positive Random Features approximate Gaussian RBF kernels while guaranteeing strictly positive
 * projections, allowing linear additive accumulation of whole-brain memory states without negative energy artifacts.</p>
 *
 * <h3>Mathematical Mapping</h3>
 * <pre>
 *   Phi(x) = (exp(-beta * ||x||^2 / 2) / sqrt(Y)) * [ exp(sqrt(beta) * omega_1^T x), ..., exp(sqrt(beta) * omega_Y^T x) ]^T
 * </pre>
 */
public final class RandomFeatureProjector {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;
    public static final long DEFAULT_SEED = 0x53504543544F524CL; // "SPECTORL"
    public static final int DEFAULT_FEATURE_DIM = 2048;

    private final int inputDimension;
    private final int featureDimension;
    private final float[] omega; // row-major flat matrix [featureDimension * inputDimension]
    private final float invSqrtY;

    /**
     * Constructs a RandomFeatureProjector with default feature dimension (2048) and default seed.
     *
     * @param inputDimension input vector dimension D
     */
    public RandomFeatureProjector(int inputDimension) {
        this(inputDimension, DEFAULT_FEATURE_DIM, DEFAULT_SEED);
    }

    /**
     * Constructs a RandomFeatureProjector with custom feature dimension and seed.
     *
     * @param inputDimension input vector dimension D
     * @param featureDimension random feature projection dimension Y
     * @param seed random generator seed for deterministic projection matrix
     */
    public RandomFeatureProjector(int inputDimension, int featureDimension, long seed) {
        if (inputDimension <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input dimension must be positive");
        }
        if (featureDimension <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Feature dimension must be positive");
        }
        this.inputDimension = inputDimension;
        this.featureDimension = featureDimension;
        this.invSqrtY = (float) (1.0 / Math.sqrt(featureDimension));
        this.omega = new float[featureDimension * inputDimension];

        generateGaussianMatrix(seed);
    }

    private void generateGaussianMatrix(long seed) {
        Random rng = new Random(seed);
        for (int i = 0; i < omega.length; i++) {
            omega[i] = (float) rng.nextGaussian();
        }
    }

    /**
     * Projects an input vector x in R^D into the positive random feature space Phi(x) in R^Y.
     *
     * @param vector input vector in R^D
     * @param beta inverse temperature scaling parameter
     * @param outFeature destination array of length Y for positive random features
     */
    public void project(float[] vector, float beta, float[] outFeature) {
        if (vector == null || outFeature == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arrays must not be null");
        }
        if (vector.length != inputDimension) {
            throw new SpectorValidationException(ErrorCode.DIMENSIONS_MISMATCH, inputDimension, vector.length);
        }
        if (outFeature.length != featureDimension) {
            throw new SpectorValidationException(ErrorCode.DIMENSIONS_MISMATCH, featureDimension, outFeature.length);
        }

        float normSq = DotProduct.compute(vector, vector);
        float baseScale = (float) Math.exp(-beta * normSq) * invSqrtY;
        float sqrtBeta = (float) Math.sqrt(beta);

        int laneCount = SPECIES.length();
        int simdBound = SPECIES.loopBound(inputDimension);

        for (int y = 0; y < featureDimension; y++) {
            int rowOffset = y * inputDimension;
            FloatVector acc = FloatVector.zero(SPECIES);

            int d = 0;
            for (; d < simdBound; d += laneCount) {
                FloatVector vOmega = FloatVector.fromArray(SPECIES, omega, rowOffset + d);
                FloatVector vVec = FloatVector.fromArray(SPECIES, vector, d);
                acc = vOmega.fma(vVec, acc);
            }

            float dot = acc.reduceLanes(jdk.incubator.vector.VectorOperators.ADD);
            for (; d < inputDimension; d++) {
                dot += omega[rowOffset + d] * vector[d];
            }

            float proj = dot * sqrtBeta;
            // Bound extreme logits for numerical stability: exp(clip(proj, -80, 80))
            if (proj > 80.0f) proj = 80.0f;
            if (proj < -80.0f) proj = -80.0f;

            outFeature[y] = baseScale * (float) Math.exp(proj);
        }
    }

    /**
     * Estimates the Gaussian RBF kernel between two vectors using their positive random features:
     * k(a, b) ~ <Phi(a), Phi(b)>
     *
     * @param a first vector in R^D
     * @param b second vector in R^D
     * @param beta inverse temperature
     * @return estimated kernel value in [0, 1]
     */
    public float estimateKernel(float[] a, float[] b, float beta) {
        float[] featA = new float[featureDimension];
        float[] featB = new float[featureDimension];
        project(a, beta, featA);
        project(b, beta, featB);
        return DotProduct.compute(featA, featB);
    }

    public int inputDimension() {
        return inputDimension;
    }

    public int featureDimension() {
        return featureDimension;
    }
}
