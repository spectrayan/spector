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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.simd.SimdCapability;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * SIMD-accelerated kernel for Expected Free Energy (EFE) calculations in the AISME Policy Engine.
 *
 * <h3>Biological Analog: Active Inference and Future-Oriented Control</h3>
 * <p>Implements Expected Free Energy G(π) which organisms minimize to select action policies.
 * Balances epistemic ambiguity (information seeking / exploration) and pragmatic risk
 * (preference satisfaction / exploitation).</p>
 */
public final class ExpectedFreeEnergyKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;
    private static final float LN_2PI = 1.8378770664093453f;

    private ExpectedFreeEnergyKernel() {}

    /**
     * Computes pragmatic risk: D_KL[q(o|π) || p(o)] for a single policy.
     * Uses SIMD-accelerated Gaussian KL divergence.
     *
     * @param predictedObsMean mean vector of predicted observations q(o|π)
     * @param priorPreferenceMean mean vector of prior preferences p(o)
     * @param predictedObsPrecision precision vector of predicted observations q(o|π)
     * @param priorPreferencePrecision precision vector of prior preferences p(o)
     * @return pragmatic risk
     */
    public static float pragmaticRisk(float[] predictedObsMean, float[] priorPreferenceMean,
                                      float[] predictedObsPrecision, float[] priorPreferencePrecision) {
        validateInputs(predictedObsMean, predictedObsPrecision, priorPreferenceMean, priorPreferencePrecision);
        int length = predictedObsMean.length;
        int laneCount = SPECIES.length();
        FloatVector sumVec = FloatVector.zero(SPECIES);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vMq = FloatVector.fromArray(SPECIES, predictedObsMean, i);
            FloatVector vPq = FloatVector.fromArray(SPECIES, predictedObsPrecision, i);
            FloatVector vMp = FloatVector.fromArray(SPECIES, priorPreferenceMean, i);
            FloatVector vPp = FloatVector.fromArray(SPECIES, priorPreferencePrecision, i);

            FloatVector diff = vMq.sub(vMp);
            FloatVector logRatio = vPq.div(vPp).lanewise(VectorOperators.LOG);
            FloatVector precRatio = vPp.div(vPq);
            FloatVector quad = vPp.mul(diff).mul(diff);

            // term = log(pi_q/pi_p) + (pi_p/pi_q) + pi_p * (mu_q - mu_p)^2 - 1
            FloatVector term = logRatio.add(precRatio).add(quad).sub(1.0f);
            sumVec = sumVec.add(term);
        }

        float kl = sumVec.reduceLanes(VectorOperators.ADD);

        // Masked tail
        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vMq = FloatVector.fromArray(SPECIES, predictedObsMean, i, mask);
            FloatVector vPq = FloatVector.fromArray(SPECIES, predictedObsPrecision, i, mask);
            FloatVector vMp = FloatVector.fromArray(SPECIES, priorPreferenceMean, i, mask);
            FloatVector vPp = FloatVector.fromArray(SPECIES, priorPreferencePrecision, i, mask);

            FloatVector diff = vMq.sub(vMp);
            FloatVector logRatio = vPq.div(vPp).lanewise(VectorOperators.LOG);
            FloatVector precRatio = vPp.div(vPq);
            FloatVector quad = vPp.mul(diff).mul(diff);

            FloatVector term = logRatio.add(precRatio).add(quad).sub(1.0f);
            kl += term.reduceLanes(VectorOperators.ADD, mask);
        }

        return Math.max(0.0f, 0.5f * kl);
    }

    /**
     * Computes epistemic ambiguity: expected entropy H(q(o|s,π)) for a single policy.
     * H = 0.5 * sum(1 + LN_2PI - ln(precision_i))
     *
     * @param posteriorPrecision precision vector of the posterior distribution
     * @return epistemic ambiguity (entropy)
     */
    public static float epistemicAmbiguity(float[] posteriorPrecision) {
        if (posteriorPrecision == null || posteriorPrecision.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Precision array must not be null or empty");
        }
        int length = posteriorPrecision.length;
        int laneCount = SPECIES.length();
        FloatVector sumVec = FloatVector.zero(SPECIES);
        
        float constantTerm = 1.0f + LN_2PI;
        FloatVector vConst = FloatVector.broadcast(SPECIES, constantTerm);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vPq = FloatVector.fromArray(SPECIES, posteriorPrecision, i);
            FloatVector vLogPq = vPq.lanewise(VectorOperators.LOG);
            FloatVector term = vConst.sub(vLogPq);
            sumVec = sumVec.add(term);
        }

        float entropy = sumVec.reduceLanes(VectorOperators.ADD);

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vPq = FloatVector.fromArray(SPECIES, posteriorPrecision, i, mask);
            FloatVector vLogPq = vPq.lanewise(VectorOperators.LOG);
            FloatVector term = vConst.sub(vLogPq);
            entropy += term.reduceLanes(VectorOperators.ADD, mask);
        }

        return 0.5f * entropy;
    }

    /**
     * Computes combined Expected Free Energy G(π) = w_p * pragmaticRisk + w_e * epistemicAmbiguity.
     *
     * @param predictedObsMean predicted observations mean
     * @param priorPreferenceMean prior preferences mean
     * @param predictedObsPrecision predicted observations precision
     * @param priorPreferencePrecision prior preferences precision
     * @param posteriorPrecision posterior precision
     * @param pragmaticWeight weight for pragmatic risk
     * @param epistemicWeight weight for epistemic ambiguity
     * @return Expected Free Energy
     */
    public static float expectedFreeEnergy(float[] predictedObsMean, float[] priorPreferenceMean,
                                           float[] predictedObsPrecision, float[] priorPreferencePrecision,
                                           float[] posteriorPrecision,
                                           float pragmaticWeight, float epistemicWeight) {
        float pragmatic = pragmaticRisk(predictedObsMean, priorPreferenceMean, predictedObsPrecision, priorPreferencePrecision);
        float epistemic = epistemicAmbiguity(posteriorPrecision);
        return pragmaticWeight * pragmatic + epistemicWeight * epistemic;
    }

    private static void validateInputs(float[] a, float[] b, float[] c, float[] d) {
        if (a == null || b == null || c == null || d == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input arrays must not be null");
        }
        int len = a.length;
        if (b.length != len || c.length != len || d.length != len) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "All input vectors must have identical dimensions");
        }
        if (len == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input vector length must be greater than zero");
        }
    }
}
