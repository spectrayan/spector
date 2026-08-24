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
 * SIMD-accelerated kernel for Variational Free Energy and Active Inference calculations.
 *
 * <h3>Biological Analog: Cortical Precision-Weighted Prediction Error Minimization</h3>
 * <p>Implements the mathematical foundation of Karl Friston's Free Energy Principle for Gaussian
 * density distributions. Computes analytical Kullback-Leibler (KL) divergence and expected log-likelihood
 * across continuous latent mental state distributions using SIMD vectorization.</p>
 */
public final class FreeEnergyKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;
    private static final float LN_2PI = 1.8378770664093453f; // ln(2*pi)

    private FreeEnergyKernel() {
        // utility class
    }

    /**
     * Computes the Kullback-Leibler divergence between two diagonal multivariate Gaussians:
     * D_KL(q || p) = 0.5 * sum( ln(pi_p / pi_q) + (pi_p / pi_q) + pi_p * (mu_q - mu_p)^2 - 1 )
     *
     * @param meanQ mean vector of posterior q(s)
     * @param precisionQ precision vector (1/var) of posterior q(s)
     * @param meanP mean vector of prior p(s)
     * @param precisionP precision vector (1/var) of prior p(s)
     * @return KL divergence >= 0.0f
     */
    public static float gaussianKLDivergence(float[] meanQ, float[] precisionQ, float[] meanP, float[] precisionP) {
        validateInputs(meanQ, precisionQ, meanP, precisionP);
        int length = meanQ.length;
        int laneCount = SPECIES.length();
        FloatVector sumVec = FloatVector.zero(SPECIES);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i);
            FloatVector vPq = FloatVector.fromArray(SPECIES, precisionQ, i);
            FloatVector vMp = FloatVector.fromArray(SPECIES, meanP, i);
            FloatVector vPp = FloatVector.fromArray(SPECIES, precisionP, i);

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
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i, mask);
            FloatVector vPq = FloatVector.fromArray(SPECIES, precisionQ, i, mask);
            FloatVector vMp = FloatVector.fromArray(SPECIES, meanP, i, mask);
            FloatVector vPp = FloatVector.fromArray(SPECIES, precisionP, i, mask);

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
     * Computes the negative expected log-likelihood -E_q[log p(o | s)] for Gaussian observation model:
     * -E_q[log p(o|s)] = 0.5 * sum( ln(2*pi) - ln(pi_o) + pi_o * ( (1/pi_q) + (mu_q - o)^2 ) )
     *
     * @param meanQ mean vector of posterior q(s)
     * @param precisionQ precision vector (1/var) of posterior q(s)
     * @param observation sensory observation vector o
     * @param obsPrecision precision vector of observation likelihood
     * @return negative expected log-likelihood
     */
    public static float negativeExpectedLogLikelihood(float[] meanQ, float[] precisionQ, float[] observation, float[] obsPrecision) {
        validateInputs(meanQ, precisionQ, observation, obsPrecision);
        int length = meanQ.length;
        int laneCount = SPECIES.length();
        FloatVector sumVec = FloatVector.zero(SPECIES);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i);
            FloatVector vPq = FloatVector.fromArray(SPECIES, precisionQ, i);
            FloatVector vObs = FloatVector.fromArray(SPECIES, observation, i);
            FloatVector vPo = FloatVector.fromArray(SPECIES, obsPrecision, i);

            FloatVector diff = vMq.sub(vObs);
            FloatVector varQ = FloatVector.broadcast(SPECIES, 1.0f).div(vPq);
            FloatVector logPo = vPo.lanewise(VectorOperators.LOG);

            // term = ln(2*pi) - ln(pi_o) + pi_o * (var_q + diff^2)
            FloatVector term = FloatVector.broadcast(SPECIES, LN_2PI).sub(logPo).add(vPo.mul(varQ.add(diff.mul(diff))));
            sumVec = sumVec.add(term);
        }

        float nll = sumVec.reduceLanes(VectorOperators.ADD);

        // Masked tail
        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i, mask);
            FloatVector vPq = FloatVector.fromArray(SPECIES, precisionQ, i, mask);
            FloatVector vObs = FloatVector.fromArray(SPECIES, observation, i, mask);
            FloatVector vPo = FloatVector.fromArray(SPECIES, obsPrecision, i, mask);

            FloatVector diff = vMq.sub(vObs);
            FloatVector varQ = FloatVector.broadcast(SPECIES, 1.0f).div(vPq);
            FloatVector logPo = vPo.lanewise(VectorOperators.LOG);

            FloatVector term = FloatVector.broadcast(SPECIES, LN_2PI).sub(logPo).add(vPo.mul(varQ.add(diff.mul(diff))));
            nll += term.reduceLanes(VectorOperators.ADD, mask);
        }

        return 0.5f * nll;
    }

    /**
     * Computes the Variational Free Energy F(q) = D_KL(q || p) - E_q[log p(o|s)].
     *
     * @param meanQ mean vector of posterior q(s)
     * @param precisionQ precision vector of posterior q(s)
     * @param meanP mean vector of prior p(s)
     * @param precisionP precision vector of prior p(s)
     * @param observation observation vector o
     * @param obsPrecision precision vector of observation model
     * @return Variational Free Energy F(q)
     */
    public static float variationalFreeEnergy(float[] meanQ, float[] precisionQ,
                                              float[] meanP, float[] precisionP,
                                              float[] observation, float[] obsPrecision) {
        float kl = gaussianKLDivergence(meanQ, precisionQ, meanP, precisionP);
        float nll = negativeExpectedLogLikelihood(meanQ, precisionQ, observation, obsPrecision);
        return kl + nll;
    }

    /**
     * Performs precision-weighted Bayesian fusion between two independent Gaussian distributions:
     * pi_post = pi_A + pi_B
     * mu_post = (pi_A * mu_A + pi_B * mu_B) / pi_post
     *
     * @param meanA mean of distribution A
     * @param precisionA precision of distribution A
     * @param meanB mean of distribution B
     * @param precisionB precision of distribution B
     * @param outMean destination array for fused mean
     * @param outPrecision destination array for fused precision
     */
    public static void precisionWeightedFusion(float[] meanA, float[] precisionA,
                                               float[] meanB, float[] precisionB,
                                               float[] outMean, float[] outPrecision) {
        validateInputs(meanA, precisionA, meanB, precisionB);
        if (outMean.length != meanA.length || outPrecision.length != meanA.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Output arrays must have matching dimensions");
        }

        int length = meanA.length;
        int laneCount = SPECIES.length();

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vMa = FloatVector.fromArray(SPECIES, meanA, i);
            FloatVector vPa = FloatVector.fromArray(SPECIES, precisionA, i);
            FloatVector vMb = FloatVector.fromArray(SPECIES, meanB, i);
            FloatVector vPb = FloatVector.fromArray(SPECIES, precisionB, i);

            FloatVector vPostPrec = vPa.add(vPb);
            FloatVector vPostMean = (vPa.mul(vMa).add(vPb.mul(vMb))).div(vPostPrec);

            vPostMean.intoArray(outMean, i);
            vPostPrec.intoArray(outPrecision, i);
        }

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vMa = FloatVector.fromArray(SPECIES, meanA, i, mask);
            FloatVector vPa = FloatVector.fromArray(SPECIES, precisionA, i, mask);
            FloatVector vMb = FloatVector.fromArray(SPECIES, meanB, i, mask);
            FloatVector vPb = FloatVector.fromArray(SPECIES, precisionB, i, mask);

            FloatVector vPostPrec = vPa.add(vPb);
            FloatVector vPostMean = (vPa.mul(vMa).add(vPb.mul(vMb))).div(vPostPrec);

            vPostMean.intoArray(outMean, i, mask);
            vPostPrec.intoArray(outPrecision, i, mask);
        }
    }

    /**
     * Computes the normalized magnitude of the precision-weighted sensory prediction error gradient:
     * ||grad_o F|| = sqrt( (1 / D) * sum( pi_o_i^2 * (o_i - mu_q_i)^2 ) )
     *
     * @param meanQ mean vector of posterior q(s)
     * @param observation sensory observation vector o
     * @param obsPrecision precision vector of observation likelihood
     * @return normalized gradient magnitude >= 0.0f
     */
    public static float freeEnergyGradientNorm(float[] meanQ, float[] observation, float[] obsPrecision) {
        validateInputs(meanQ, observation, obsPrecision);
        int length = meanQ.length;
        int laneCount = SPECIES.length();
        FloatVector sumVec = FloatVector.zero(SPECIES);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i);
            FloatVector vObs = FloatVector.fromArray(SPECIES, observation, i);
            FloatVector vPo = FloatVector.fromArray(SPECIES, obsPrecision, i);

            FloatVector diff = vObs.sub(vMq);
            FloatVector grad = vPo.mul(diff);
            FloatVector gradSq = grad.mul(grad);
            sumVec = sumVec.add(gradSq);
        }

        float sumSq = sumVec.reduceLanes(VectorOperators.ADD);

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i, mask);
            FloatVector vObs = FloatVector.fromArray(SPECIES, observation, i, mask);
            FloatVector vPo = FloatVector.fromArray(SPECIES, obsPrecision, i, mask);

            FloatVector diff = vObs.sub(vMq);
            FloatVector grad = vPo.mul(diff);
            FloatVector gradSq = grad.mul(grad);
            sumSq += gradSq.reduceLanes(VectorOperators.ADD, mask);
        }

        return (float) Math.sqrt(sumSq / length);
    }

    /**
     * Computes the normalized sensory surprisal (quadratic prediction error per dimension):
     * Surprise(o) = 0.5 * (1 / D) * sum( pi_o_i * (o_i - mu_q_i)^2 )
     *
     * @param meanQ mean vector of posterior q(s)
     * @param observation sensory observation vector o
     * @param obsPrecision precision vector of observation likelihood
     * @return normalized surprisal >= 0.0f
     */
    public static float sensorySurprisal(float[] meanQ, float[] observation, float[] obsPrecision) {
        validateInputs(meanQ, observation, obsPrecision);
        int length = meanQ.length;
        int laneCount = SPECIES.length();
        FloatVector sumVec = FloatVector.zero(SPECIES);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i);
            FloatVector vObs = FloatVector.fromArray(SPECIES, observation, i);
            FloatVector vPo = FloatVector.fromArray(SPECIES, obsPrecision, i);

            FloatVector diff = vObs.sub(vMq);
            FloatVector quad = vPo.mul(diff).mul(diff);
            sumVec = sumVec.add(quad);
        }

        float sumQuad = sumVec.reduceLanes(VectorOperators.ADD);

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector vMq = FloatVector.fromArray(SPECIES, meanQ, i, mask);
            FloatVector vObs = FloatVector.fromArray(SPECIES, observation, i, mask);
            FloatVector vPo = FloatVector.fromArray(SPECIES, obsPrecision, i, mask);

            FloatVector diff = vObs.sub(vMq);
            FloatVector quad = vPo.mul(diff).mul(diff);
            sumQuad += quad.reduceLanes(VectorOperators.ADD, mask);
        }

        return 0.5f * (sumQuad / length);
    }

    private static void validateInputs(float[] a, float[] b, float[] c) {
        if (a == null || b == null || c == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input arrays must not be null");
        }
        int len = a.length;
        if (b.length != len || c.length != len) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "All input vectors must have identical dimensions");
        }
        if (len == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Input vector length must be greater than zero");
        }
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
