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

/**
 * Kernel for Integrated Information Theory (IIT) Gaussian Multi-Information and Phi_CC calculation.
 *
 * <h3>Biological Analog: Neural Subgraph Integrated Information and Causal Density</h3>
 * <p>Computes regularized Gram matrices, Cholesky log-determinants, and Gaussian multi-information
 * to quantify the irreducible holistic synergy of recalled memory clusters.</p>
 */
public final class IntegratedInformationKernel {

    private static final float DEFAULT_REGULARIZATION = 1e-3f;

    private IntegratedInformationKernel() {
        // utility class
    }

    /**
     * Computes the regularized pairwise cosine Gram matrix for an array of memory vectors.
     *
     * @param vectors array of N memory vectors in R^D
     * @param outGram destination N x N matrix
     * @param regularization Tikhonov diagonal regularization (lambda >= 0)
     */
    public static void computeGramMatrix(float[][] vectors, float[][] outGram, float regularization) {
        if (vectors == null || outGram == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        int n = vectors.length;
        if (outGram.length != n) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Output Gram matrix must be N x N");
        }

        for (int i = 0; i < n; i++) {
            if (vectors[i] == null) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector at index " + i + " must not be null");
            }
            outGram[i][i] = 1.0f + regularization;
            for (int j = i + 1; j < n; j++) {
                if (vectors[j] == null) {
                    throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector at index " + j + " must not be null");
                }
                float cosSim = CosineSimilarity.compute(vectors[i], vectors[j]);
                outGram[i][j] = cosSim;
                outGram[j][i] = cosSim;
            }
        }
    }

    /**
     * Computes the natural log-determinant ln(det(A)) of a symmetric positive-definite matrix via Cholesky decomposition.
     *
     * @param matrix symmetric positive-definite N x N matrix
     * @return ln(det(matrix))
     */
    public static float choleskyLogDeterminant(float[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Matrix must not be null or empty");
        }
        int n = matrix.length;
        float[][] l = new float[n][n];
        float sumLogDiag = 0.0f;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                float sum = matrix[i][j];
                for (int k = 0; k < j; k++) {
                    sum -= l[i][k] * l[j][k];
                }
                if (i == j) {
                    float val = Math.max(1e-8f, sum);
                    l[i][i] = (float) Math.sqrt(val);
                    sumLogDiag += (float) Math.log(l[i][i]);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }

        return 2.0f * sumLogDiag;
    }

    /**
     * Computes Gaussian multi-information I(X) = 0.5 * (sum_i ln(K_ii) - ln(det(K)))
     *
     * @param gramMatrix regularized N x N Gram matrix
     * @return Gaussian multi-information >= 0.0f
     */
    public static float computeMultiInformation(float[][] gramMatrix) {
        if (gramMatrix == null || gramMatrix.length <= 1) {
            return 0.0f;
        }
        int n = gramMatrix.length;
        float sumDiagLog = 0.0f;
        for (int i = 0; i < n; i++) {
            sumDiagLog += (float) Math.log(Math.max(1e-8f, gramMatrix[i][i]));
        }
        float logDet = choleskyLogDeterminant(gramMatrix);
        float multiInfo = 0.5f * (sumDiagLog - logDet);
        return Math.max(0.0f, multiInfo);
    }

    /**
     * Computes the holistic synergy Phi(G) using a bisection Minimum Information Partition (MIP).
     * Phi(G) = max(0, I(G) - (I(A) + I(B)))
     *
     * @param gramMatrix regularized N x N Gram matrix
     * @return integrated information Phi(G) >= 0.0f
     */
    public static float computePhi(float[][] gramMatrix) {
        if (gramMatrix == null || gramMatrix.length <= 1) {
            return 0.0f;
        }
        int n = gramMatrix.length;
        float totalI = computeMultiInformation(gramMatrix);

        if (n == 2) {
            // For 2 elements, I(A) and I(B) are single elements (multi-info = 0)
            return totalI;
        }

        int mid = n / 2;
        float[][] subA = extractSubmatrix(gramMatrix, 0, mid);
        float[][] subB = extractSubmatrix(gramMatrix, mid, n);

        float infoA = computeMultiInformation(subA);
        float infoB = computeMultiInformation(subB);

        float phi = totalI - (infoA + infoB);
        return Math.max(0.0f, phi);
    }

    private static float[][] extractSubmatrix(float[][] matrix, int start, int end) {
        int size = end - start;
        float[][] sub = new float[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                sub[i][j] = matrix[start + i][start + j];
            }
        }
        return sub;
    }
}
