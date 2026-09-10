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

import java.util.Arrays;

/**
 * Pure mathematical kernel for Riemannian metric tensor online experiential adaptation (ADR-0033 Domain 13, #32).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class RiemannianManifoldKernel {

    public static final float MIN_DIAGONAL_SCALE = 0.1f;

    private RiemannianManifoldKernel() {}

    /**
     * Updates diagonal metric tensor coordinate scalings in-place from co-activation difference vectors:
     * <p>{@code d_k ← max(0.1, d_k + η · (x_k - y_k)^2)}</p>
     *
     * @param diagonalScaling array of diagonal scale factors (modified in-place)
     * @param diffVectors     array of pairwise coordinate difference vectors
     * @param learningRate    adaptation learning rate \(\eta &gt; 0\)
     */
    public static void updateDiagonalMetric(
            final float[] diagonalScaling, final float[][] diffVectors, final float learningRate) {
        if (diagonalScaling == null || diffVectors == null || learningRate <= 0.0f) {
            return;
        }

        final int dim = diagonalScaling.length;
        for (final float[] diff : diffVectors) {
            if (diff == null || diff.length != dim) {
                continue;
            }
            for (int k = 0; k < dim; k++) {
                final float dVal = diff[k];
                diagonalScaling[k] = Math.max(MIN_DIAGONAL_SCALE, diagonalScaling[k] + (learningRate * dVal * dVal));
            }
        }
    }

    /**
     * Incorporates leading co-activation difference vector as a low-rank perturbation component.
     *
     * @param existingComponents current low-rank component vectors (may be empty or null)
     * @param diffVector         co-activation difference vector
     * @param learningRate       adaptation rate
     * @param maxRank            maximum number of low-rank components permitted
     * @return updated low-rank components matrix
     */
    public static float[][] updateLowRankComponents(
            final float[][] existingComponents, final float[] diffVector, final float learningRate, final int maxRank) {
        if (maxRank <= 0 || diffVector == null) {
            return existingComponents != null ? existingComponents : new float[0][];
        }

        final float[][] current = existingComponents != null ? existingComponents : new float[0][];
        final int dim = diffVector.length;
        if (dim == 0) {
            return current;
        }

        final float[] normSample = new float[dim];
        float normSq = 0.0f;
        for (final float v : diffVector) {
            normSq += v * v;
        }
        final float invNorm = (normSq > 0.0f) ? (float) (Math.sqrt(learningRate) / Math.sqrt(normSq)) : 0.0f;
        for (int k = 0; k < dim; k++) {
            normSample[k] = diffVector[k] * invNorm;
        }

        if (current.length < maxRank) {
            final float[][] extended = Arrays.copyOf(current, current.length + 1);
            extended[extended.length - 1] = normSample;
            return extended;
        } else if (current.length > 0) {
            final float[][] updated = new float[current.length][];
            for (int i = 0; i < current.length; i++) {
                updated[i] = Arrays.copyOf(current[i], current[i].length);
            }
            final float[] first = updated[0];
            for (int k = 0; k < dim; k++) {
                first[k] = (1.0f - learningRate) * first[k] + learningRate * normSample[k];
            }
            return updated;
        }

        return current;
    }
}
