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

/**
 * SIMD-accelerated kernel for Neural Manifold Distance (NMD) on curved cognitive manifolds.
 *
 * <h3>Biological Analog: Subjective Cognitive Distance Warping</h3>
 * <p>Computes Riemannian metric distance on personal cognitive spaces warped by subjective experience.
 * Evaluates the Mahalanobis quadratic form {@code (x - y)^T M (x - y)} where {@code M = diag(d) + U U^T},
 * capturing both coordinate scaling and non-orthogonal experiential associations.</p>
 */
public final class NeuralManifoldDistance {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private NeuralManifoldDistance() {
        // utility class
    }

    /**
     * Computes the squared Riemannian distance on the personal cognitive manifold:
     * d^2(x, y) = sum_k d_k * (x_k - y_k)^2 + sum_r (U_r^T * (x - y))^2
     *
     * @param x first embedding vector in R^D
     * @param y second embedding vector in R^D
     * @param diagonalScaling diagonal metric vector d in R^D (nullable, defaults to identity [1..1])
     * @param lowRankComponents low-rank factor vectors U_r in R^D (nullable or empty)
     * @return squared Riemannian distance >= 0.0f
     */
    public static float squaredDistance(float[] x, float[] y, float[] diagonalScaling, float[][] lowRankComponents) {
        validateVectors(x, y);
        int dim = x.length;

        if (diagonalScaling != null && diagonalScaling.length != dim) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Diagonal scaling length must match vector dimension");
        }

        int laneCount = SPECIES.length();
        int limit = SPECIES.loopBound(dim);
        float[] diff = new float[dim];

        FloatVector sumDiag = FloatVector.zero(SPECIES);

        int i = 0;
        for (; i < limit; i += laneCount) {
            FloatVector vx = FloatVector.fromArray(SPECIES, x, i);
            FloatVector vy = FloatVector.fromArray(SPECIES, y, i);
            FloatVector vDiff = vx.sub(vy);
            vDiff.intoArray(diff, i);

            FloatVector vDiag = (diagonalScaling != null)
                    ? FloatVector.fromArray(SPECIES, diagonalScaling, i)
                    : FloatVector.broadcast(SPECIES, 1.0f);

            sumDiag = sumDiag.add(vDiag.mul(vDiff).mul(vDiff));
        }

        if (i < dim) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, dim);
            FloatVector vx = FloatVector.fromArray(SPECIES, x, i, mask);
            FloatVector vy = FloatVector.fromArray(SPECIES, y, i, mask);
            FloatVector vDiff = vx.sub(vy);
            vDiff.intoArray(diff, i, mask);

            FloatVector vDiag = (diagonalScaling != null)
                    ? FloatVector.fromArray(SPECIES, diagonalScaling, i, mask)
                    : FloatVector.broadcast(SPECIES, 1.0f);

            FloatVector term = vDiag.mul(vDiff).mul(vDiff);
            sumDiag = sumDiag.add(term, mask);
        }

        float totalSq = sumDiag.reduceLanes(VectorOperators.ADD);

        // Low-rank projection: sum_r (U_r^T * diff)^2
        if (lowRankComponents != null && lowRankComponents.length > 0) {
            for (float[] uComp : lowRankComponents) {
                if (uComp != null && uComp.length == dim) {
                    float proj = DotProduct.compute(uComp, diff);
                    totalSq += proj * proj;
                }
            }
        }

        return Math.max(0.0f, totalSq);
    }

    /**
     * Computes the Riemannian distance on the personal cognitive manifold.
     *
     * @param x first vector
     * @param y second vector
     * @param diagonalScaling diagonal metric vector
     * @param lowRankComponents low-rank factor vectors
     * @return Riemannian distance d_NMD(x, y)
     */
    public static float distance(float[] x, float[] y, float[] diagonalScaling, float[][] lowRankComponents) {
        return (float) Math.sqrt(squaredDistance(x, y, diagonalScaling, lowRankComponents));
    }

    /**
     * Computes the Gaussian Riemannian manifold similarity:
     * sim_NMD(x, y) = exp(-d^2_NMD(x, y) / (2 * sigma^2))
     *
     * @param x first vector
     * @param y second vector
     * @param diagonalScaling diagonal metric vector
     * @param lowRankComponents low-rank factor vectors
     * @param sigma kernel bandwidth parameter
     * @return similarity score in (0, 1]
     */
    public static float similarity(float[] x, float[] y, float[] diagonalScaling, float[][] lowRankComponents, float sigma) {
        if (sigma <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Sigma must be positive");
        }
        float sqDist = squaredDistance(x, y, diagonalScaling, lowRankComponents);
        return (float) Math.exp(-sqDist / (2.0 * sigma * sigma));
    }

    /**
     * Batch computes manifold similarity for a query vector against multiple candidate vectors.
     *
     * @param query query vector in R^D
     * @param candidates candidate vectors array
     * @param diagonalScaling diagonal metric vector
     * @param lowRankComponents low-rank factor vectors
     * @param sigma kernel bandwidth
     * @return array of similarity scores
     */
    public static float[] batchSimilarity(float[] query, float[][] candidates, float[] diagonalScaling, float[][] lowRankComponents, float sigma) {
        if (candidates == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Candidates array must not be null");
        }
        float[] results = new float[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            results[i] = similarity(query, candidates[i], diagonalScaling, lowRankComponents, sigma);
        }
        return results;
    }

    private static void validateVectors(float[] x, float[] y) {
        if (x == null || y == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vectors must not be null");
        }
        if (x.length != y.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector dimensions must match");
        }
        if (x.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector dimension must be positive");
        }
    }
}
