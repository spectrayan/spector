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
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.simd.SimdCapability;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * SIMD-accelerated kernel for Hierarchical Predictive Coding Memory Networks (PCMN).
 *
 * <h3>Biological Analog: Cortical Inter-Laminar Prediction Error Propagation</h3>
 * <p>Computes precision-weighted top-down prediction errors and hierarchical free-energy minimization
 * across multi-tier cortical memory representations using the Java Vector API.</p>
 */
public final class PredictiveCodingKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private PredictiveCodingKernel() {
        // utility class
    }

    /**
     * Computes the precision-weighted prediction error vector:
     * eps_tilde[k] = pi[k] * (actual[k] - predicted[k])
     *
     * @param actual actual representation vector x in R^D
     * @param predicted top-down predicted vector x_hat in R^D
     * @param precision precision weighting vector pi in R^D
     * @param outWeightedError destination array for precision-weighted error eps_tilde in R^D
     */
    public static void computePrecisionWeightedError(float[] actual, float[] predicted, float[] precision, float[] outWeightedError) {
        validateEqualDimensions(actual, predicted, precision, outWeightedError);
        int dim = actual.length;
        int laneCount = SPECIES.length();
        int limit = SPECIES.loopBound(dim);

        int i = 0;
        for (; i < limit; i += laneCount) {
            FloatVector vAct = FloatVector.fromArray(SPECIES, actual, i);
            FloatVector vPred = FloatVector.fromArray(SPECIES, predicted, i);
            FloatVector vPrec = FloatVector.fromArray(SPECIES, precision, i);

            FloatVector vErr = vPrec.mul(vAct.sub(vPred));
            vErr.intoArray(outWeightedError, i);
        }

        if (i < dim) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, dim);
            FloatVector vAct = FloatVector.fromArray(SPECIES, actual, i, mask);
            FloatVector vPred = FloatVector.fromArray(SPECIES, predicted, i, mask);
            FloatVector vPrec = FloatVector.fromArray(SPECIES, precision, i, mask);

            FloatVector vErr = vPrec.mul(vAct.sub(vPred));
            vErr.intoArray(outWeightedError, i, mask);
        }
    }

    /**
     * Computes the scalar prediction error energy at a single cortical tier:
     * E_tier = 0.5 * sum_k pi[k] * (actual[k] - predicted[k])^2
     *
     * @param actual actual tier representation
     * @param predicted predicted tier representation
     * @param precision tier precision vector
     * @return scalar tier prediction energy >= 0.0f
     */
    public static float computeTierEnergy(float[] actual, float[] predicted, float[] precision) {
        validateInputs(actual, predicted, precision);
        int dim = actual.length;
        int laneCount = SPECIES.length();
        int limit = SPECIES.loopBound(dim);

        FloatVector sumEnergy = FloatVector.zero(SPECIES);

        int i = 0;
        for (; i < limit; i += laneCount) {
            FloatVector vAct = FloatVector.fromArray(SPECIES, actual, i);
            FloatVector vPred = FloatVector.fromArray(SPECIES, predicted, i);
            FloatVector vPrec = FloatVector.fromArray(SPECIES, precision, i);

            FloatVector diff = vAct.sub(vPred);
            sumEnergy = sumEnergy.add(vPrec.mul(diff).mul(diff));
        }

        if (i < dim) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, dim);
            FloatVector vAct = FloatVector.fromArray(SPECIES, actual, i, mask);
            FloatVector vPred = FloatVector.fromArray(SPECIES, predicted, i, mask);
            FloatVector vPrec = FloatVector.fromArray(SPECIES, precision, i, mask);

            FloatVector diff = vAct.sub(vPred);
            FloatVector term = vPrec.mul(diff).mul(diff);
            sumEnergy = sumEnergy.add(term, mask);
        }

        float total = sumEnergy.reduceLanes(VectorOperators.ADD);
        return 0.5f * Math.max(0.0f, total);
    }

    /**
     * Computes the total hierarchical prediction error energy across all cortical tiers:
     * E_total = sum_{l=0}^{L-1} E_tier(l)
     *
     * @param actualTiers array of actual tier representations
     * @param predictedTiers array of top-down predicted tier representations
     * @param tierPrecisions array of tier precision vectors
     * @return total multi-tier energy
     */
    public static float computeHierarchicalEnergy(float[][] actualTiers, float[][] predictedTiers, float[][] tierPrecisions) {
        if (actualTiers == null || predictedTiers == null || tierPrecisions == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Tier arrays must not be null");
        }
        int numTiers = actualTiers.length;
        if (predictedTiers.length != numTiers || tierPrecisions.length != numTiers) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Tier array counts must match");
        }

        float totalEnergy = 0.0f;
        for (int l = 0; l < numTiers; l++) {
            totalEnergy += computeTierEnergy(actualTiers[l], predictedTiers[l], tierPrecisions[l]);
        }
        return totalEnergy;
    }

    /**
     * Computes top-down affine projection: outTarget = W * source + bias.
     *
     * @param source source higher-tier representation in R^D
     * @param weightMatrix transformation matrix W in R^{D x D}
     * @param bias optional bias vector in R^D (nullable)
     * @param outTarget destination projected array in R^D
     */
    public static void affineProjection(float[] source, float[][] weightMatrix, float[] bias, float[] outTarget) {
        if (source == null || weightMatrix == null || outTarget == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        int targetDim = outTarget.length;
        if (weightMatrix.length != targetDim) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Weight matrix rows must match target dimension");
        }

        for (int row = 0; row < targetDim; row++) {
            float dot = DotProduct.compute(weightMatrix[row], source);
            float b = (bias != null && row < bias.length) ? bias[row] : 0.0f;
            outTarget[row] = dot + b;
        }
    }

    private static void validateInputs(float[] a, float[] b, float[] c) {
        if (a == null || b == null || c == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input arrays must not be null");
        }
        int len = a.length;
        if (b.length != len || c.length != len) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input arrays must have matching length");
        }
        if (len == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Array length must be greater than zero");
        }
    }

    private static void validateEqualDimensions(float[] a, float[] b, float[] c, float[] d) {
        validateInputs(a, b, c);
        if (d == null || d.length != a.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Output array must match input dimension");
        }
    }
}
