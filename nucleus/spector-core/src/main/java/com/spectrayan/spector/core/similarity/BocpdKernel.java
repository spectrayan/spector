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
 * SIMD-accelerated mathematical kernel for Bayesian Online Change-Point Detection (BOCPD).
 *
 * <h3>Mathematical Analog: Conjugate Normal-Normal Predictive Likelihood</h3>
 * <p>Evaluates Gaussian predictive log-likelihoods \(\ln \pi(x_t \mid \theta_r)\) across
 * continuous cognitive dimensions using hardware-accelerated Vector API operations.</p>
 */
public final class BocpdKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    private BocpdKernel() {
        // pure utility / kernel class
    }

    /**
     * Evaluates the predictive log-likelihood \(\ln \pi(x_t \mid r)\) for a specific run-length \(r\)
     * under conjugate Normal-Normal observation model in a single SIMD pass.
     *
     * <p>For each dimension \(d\):
     * \[\text{postPrec}_d = \text{priorPrec}_d + r \cdot \text{obsPrec}_d\]
     * \[\text{postMean}_d = \frac{\text{priorPrec}_d \cdot \text{priorMean}_d + \text{obsPrec}_d \cdot \text{sumAcc}_{r, d}}{\text{postPrec}_d}\]
     * \[\text{predVar}_d = \frac{1}{\text{postPrec}_d} + \frac{1}{\text{obsPrec}_d}\]
     * \[\text{predPrec}_d = \frac{1}{\text{predVar}_d}\]
     * \[\ln \pi(x_{t, d} \mid r) = \frac{1}{2}\ln\left(\frac{\text{predPrec}_d}{2\pi}\right) - \frac{1}{2}\text{predPrec}_d(x_{t, d} - \text{postMean}_d)^2\]
     *
     * @param obs            sensory observation vector \(x_t\)
     * @param priorMean      prior mean vector \(\mu_0\)
     * @param priorPrecision prior precision vector \(\pi_0\)
     * @param sumAccR        sum of observations within run-length \(r\) (can be null if \(r=0\))
     * @param obsPrecision   sensory observation precision \(\pi_o\)
     * @param r              active run-length index
     * @return sum of log-likelihoods across all dimensions
     */
    public static float evaluateLogLikelihoodForRun(
            float[] obs,
            float[] priorMean,
            float[] priorPrecision,
            float[] sumAccR,
            float[] obsPrecision,
            int r
    ) {
        validateInputs(obs, priorMean, priorPrecision, obsPrecision);
        int length = obs.length;
        if (sumAccR != null && sumAccR.length != length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "sumAccR dimension mismatch");
        }

        int laneCount = SPECIES.length();
        FloatVector sumVec = FloatVector.zero(SPECIES);
        FloatVector vR = FloatVector.broadcast(SPECIES, (float) r);
        FloatVector vTwoPi = FloatVector.broadcast(SPECIES, TWO_PI);
        FloatVector vOne = FloatVector.broadcast(SPECIES, 1.0f);
        FloatVector vHalf = FloatVector.broadcast(SPECIES, 0.5f);
        FloatVector vMinusHalf = FloatVector.broadcast(SPECIES, -0.5f);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vObs = FloatVector.fromArray(SPECIES, obs, i);
            FloatVector vPriorMean = FloatVector.fromArray(SPECIES, priorMean, i);
            FloatVector vPriorPrec = FloatVector.fromArray(SPECIES, priorPrecision, i);
            FloatVector vObsPrec = FloatVector.fromArray(SPECIES, obsPrecision, i);
            FloatVector vSumAcc = (sumAccR != null) ? FloatVector.fromArray(SPECIES, sumAccR, i) : FloatVector.zero(SPECIES);

            FloatVector vPostPrec = vPriorPrec.add(vObsPrec.mul(vR));
            FloatVector vPostMean = vPriorPrec.mul(vPriorMean).add(vObsPrec.mul(vSumAcc)).div(vPostPrec);

            FloatVector vPredVar = vOne.div(vPostPrec).add(vOne.div(vObsPrec));
            FloatVector vPredPrec = vOne.div(vPredVar);

            FloatVector vDiff = vObs.sub(vPostMean);
            FloatVector vLogConst = vPredPrec.div(vTwoPi).lanewise(VectorOperators.LOG).mul(vHalf);
            FloatVector vQuad = vMinusHalf.mul(vPredPrec).mul(vDiff).mul(vDiff);

            sumVec = sumVec.add(vLogConst.add(vQuad));
        }

        float totalLogLik = sumVec.reduceLanes(VectorOperators.ADD);

        // Tail loop
        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vObs = FloatVector.fromArray(SPECIES, obs, i, mask);
            FloatVector vPriorMean = FloatVector.fromArray(SPECIES, priorMean, i, mask);
            FloatVector vPriorPrec = FloatVector.fromArray(SPECIES, priorPrecision, i, mask);
            FloatVector vObsPrec = FloatVector.fromArray(SPECIES, obsPrecision, i, mask);
            FloatVector vSumAcc = (sumAccR != null) ? FloatVector.fromArray(SPECIES, sumAccR, i, mask) : FloatVector.zero(SPECIES);

            FloatVector vPostPrec = vPriorPrec.add(vObsPrec.mul(vR));
            FloatVector vPostMean = vPriorPrec.mul(vPriorMean).add(vObsPrec.mul(vSumAcc)).div(vPostPrec);

            FloatVector vPredVar = vOne.div(vPostPrec).add(vOne.div(vObsPrec));
            FloatVector vPredPrec = vOne.div(vPredVar);

            FloatVector vDiff = vObs.sub(vPostMean);
            FloatVector vLogConst = vPredPrec.div(vTwoPi).lanewise(VectorOperators.LOG).mul(vHalf);
            FloatVector vQuad = vMinusHalf.mul(vPredPrec).mul(vDiff).mul(vDiff);

            FloatVector tailTerm = vLogConst.add(vQuad);
            totalLogLik += tailTerm.reduceLanes(VectorOperators.ADD, mask);
        }

        return totalLogLik;
    }

    private static void validateInputs(float[] a, float[] b, float[] c, float[] d) {
        if (a == null || b == null || c == null || d == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input arrays must not be null");
        }
        int len = a.length;
        if (b.length != len || c.length != len || d.length != len) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Array dimension mismatch in BocpdKernel");
        }
    }
}
