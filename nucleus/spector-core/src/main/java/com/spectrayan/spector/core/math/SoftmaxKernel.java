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
package com.spectrayan.spector.core.math;

import java.util.Arrays;

/**
 * Numerically stable Softmax probability scaling and temperature modulation kernel.
 *
 * <p>Contract: ADR-0033 Purity Tier T1 (Pure Static). Consolidates softmax calculations
 * from {@code TemperatureSoftmax}, {@code HopfieldKernel}, and {@code PolicyInferenceEngine},
 * enforcing maximum-subtracted Log-Sum-Exp normalization to eliminate numerical overflow.</p>
 *
 * <h3>Mathematical Formulation</h3>
 * <pre>
 *   shift = max_j (s_j / T)
 *   w_i = exp(s_i / T - shift)
 *   p_i = w_i / sum_j(w_j)
 * </pre>
 */
public final class SoftmaxKernel {

    private SoftmaxKernel() {
        // utility class
    }

    /**
     * Computes normalized softmax probabilities from an array of scores modulated by a temperature parameter.
     *
     * <p>Employs maximum-subtracted Log-Sum-Exp shift stabilization to prevent IEEE 754 float overflow.</p>
     *
     * @param scores           input raw scores array
     * @param temperature      positive temperature parameter (clamped to minimum 0.01f)
     * @param outProbabilities destination array for normalized probabilities (must have length &gt;= scores.length)
     */
    public static void computeProbabilities(float[] scores, float temperature, float[] outProbabilities) {
        if (scores == null || outProbabilities == null) {
            return;
        }
        int n = Math.min(scores.length, outProbabilities.length);
        if (n == 0) {
            return;
        }
        if (n == 1) {
            outProbabilities[0] = 1.0f;
            return;
        }

        float temp = Math.max(0.01f, temperature);
        float invTemp = 1.0f / temp;

        float maxScaled = scores[0] * invTemp;
        for (int i = 1; i < n; i++) {
            float scaled = scores[i] * invTemp;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }

        double sumExp = 0.0;
        for (int i = 0; i < n; i++) {
            float scaled = scores[i] * invTemp;
            double w = Math.exp(scaled - maxScaled);
            outProbabilities[i] = (float) w;
            sumExp += w;
        }

        if (sumExp <= 0.0 || Double.isNaN(sumExp)) {
            float uniform = 1.0f / n;
            Arrays.fill(outProbabilities, 0, n, uniform);
            return;
        }

        float invSum = (float) (1.0 / sumExp);
        for (int i = 0; i < n; i++) {
            outProbabilities[i] *= invSum;
        }
    }

    /**
     * Computes normalized softmax probabilities from logits scaled by an inverse temperature / precision parameter {@code beta}.
     *
     * <p>Standard Boltzmann distribution form: {@code p_i = exp(beta * logits_i) / sum(exp(beta * logits_j))}.
     * Applies maximum-subtracted stabilization, protecting against large {@code beta * logits} overflows.</p>
     *
     * @param logits           raw logit values
     * @param beta             scaling factor (precision or inverse temperature)
     * @param outProbabilities destination array for normalized probabilities
     */
    public static void computeProbabilitiesScaled(float[] logits, float beta, float[] outProbabilities) {
        if (logits == null || outProbabilities == null) {
            return;
        }
        int n = Math.min(logits.length, outProbabilities.length);
        if (n == 0) {
            return;
        }
        if (n == 1) {
            outProbabilities[0] = 1.0f;
            return;
        }

        float maxScaled = logits[0] * beta;
        for (int i = 1; i < n; i++) {
            float scaled = logits[i] * beta;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }

        double sumExp = 0.0;
        for (int i = 0; i < n; i++) {
            double w = Math.exp((logits[i] * beta) - maxScaled);
            outProbabilities[i] = (float) w;
            sumExp += w;
        }

        if (sumExp <= 0.0 || Double.isNaN(sumExp)) {
            float uniform = 1.0f / n;
            Arrays.fill(outProbabilities, 0, n, uniform);
            return;
        }

        float invSum = (float) (1.0 / sumExp);
        for (int i = 0; i < n; i++) {
            outProbabilities[i] *= invSum;
        }
    }

    /**
     * Modulates candidate scores in-place using softmax temperature scaling, redistributing the original
     * total score proportionally according to the softmax distribution.
     *
     * @param scores      candidate scores to modulate in-place
     * @param temperature effective retrieval temperature (T=1.0 is identity)
     */
    public static void applySoftmaxTemperature(float[] scores, float temperature) {
        if (scores == null || scores.length <= 1) {
            return;
        }
        if (Math.abs(temperature - 1.0f) < 1e-4f) {
            return;
        }

        int n = scores.length;
        float temp = Math.max(0.01f, temperature);
        float invTemp = 1.0f / temp;

        float maxScaled = scores[0] * invTemp;
        float totalOriginalScore = scores[0];
        for (int i = 1; i < n; i++) {
            totalOriginalScore += scores[i];
            float scaled = scores[i] * invTemp;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }

        double sumExp = 0.0;
        for (int i = 0; i < n; i++) {
            float scaled = scores[i] * invTemp;
            double w = Math.exp(scaled - maxScaled);
            sumExp += w;
        }

        // Degenerate check: preserve scores untouched on underflow/overflow/NaN
        if (sumExp <= 0.0 || Double.isNaN(sumExp)) {
            return;
        }

        float scaleMultiplier = totalOriginalScore > 0.0f ? totalOriginalScore : 1.0f;
        double invSumExp = scaleMultiplier / sumExp;
        for (int i = 0; i < n; i++) {
            float scaled = scores[i] * invTemp;
            scores[i] = (float) (Math.exp(scaled - maxScaled) * invSumExp);
        }
    }

    /**
     * Computes adaptive retrieval temperature dynamically modulated by query novelty/surprise z-score.
     *
     * @param baseTemp   baseline resting temperature
     * @param zSurprise  standardized prediction error z-score from Welford distribution
     * @param kappa      temperature expansion sensitivity coefficient
     * @param minT       minimum allowable temperature clamp floor
     * @param maxT       maximum allowable temperature clamp ceiling
     * @return effective clamped retrieval temperature
     */
    public static float adaptiveTemperature(float baseTemp, double zSurprise, float kappa, float minT, float maxT) {
        double effective = baseTemp * (1.0 + kappa * Math.max(0.0, zSurprise));
        return (float) Math.clamp(effective, minT, maxT);
    }
}
