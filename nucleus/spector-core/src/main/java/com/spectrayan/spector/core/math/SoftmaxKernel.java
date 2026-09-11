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
 *   shift = max_j (s_j * k)
 *   w_i   = exp(s_i * k - shift)
 *   p_i   = w_i / sum_j(w_j)
 * </pre>
 *
 * <p>where {@code k} is either the inverse temperature {@code 1/T}
 * ({@link #computeProbabilities}) or the precision {@code beta}
 * ({@link #computeProbabilitiesScaled}). Both forms delegate to a single private
 * implementation — there is exactly one Log-Sum-Exp loop in this class.</p>
 *
 * <h3>Degenerate Input Policy</h3>
 * <p>When {@code sum(exp(...))} underflows to zero, overflows, or evaluates to NaN, the
 * distribution is undefined. Two policies exist and the choice is <b>deliberate per method</b>,
 * because the migrated call sites had different pre-existing behaviour (ADR-0033 Principle 6):</p>
 * <ul>
 *   <li>{@link DegeneratePolicy#UNIFORM} — emit {@code 1/n} for every element. Used by the
 *       probability-producing methods, whose callers require a valid distribution that sums to 1.</li>
 *   <li>{@link DegeneratePolicy#PRESERVE} — leave the input untouched. Used by
 *       {@link #applySoftmaxTemperature}, matching the pre-migration {@code TemperatureSoftmax}
 *       contract where a degenerate temperature must not destroy the underlying ranking.</li>
 * </ul>
 */
public final class SoftmaxKernel {

    /** Smallest temperature accepted before clamping, guarding division by zero. */
    public static final float MIN_TEMPERATURE = 0.01f;

    /** Half-width of the band around T=1.0 treated as the identity transform. */
    public static final float IDENTITY_TEMPERATURE_EPSILON = 1e-4f;

    private SoftmaxKernel() {
        // utility class
    }

    /**
     * Behaviour selector for numerically degenerate inputs.
     */
    public enum DegeneratePolicy {
        /** Emit a uniform {@code 1/n} distribution. */
        UNIFORM,
        /** Leave the destination array untouched. */
        PRESERVE
    }

    /**
     * Computes normalized softmax probabilities from an array of scores modulated by a temperature parameter.
     *
     * <p>Employs maximum-subtracted Log-Sum-Exp shift stabilization to prevent IEEE 754 float overflow.
     * Degenerate inputs yield a uniform distribution ({@link DegeneratePolicy#UNIFORM}).</p>
     *
     * @param scores           input raw scores array
     * @param temperature      positive temperature parameter (clamped to minimum {@value #MIN_TEMPERATURE})
     * @param outProbabilities destination array for normalized probabilities (must have length &gt;= scores.length)
     */
    public static void computeProbabilities(float[] scores, float temperature, float[] outProbabilities) {
        if (scores == null || outProbabilities == null) {
            return;
        }
        final float invTemp = 1.0f / Math.max(MIN_TEMPERATURE, temperature);
        softmaxInto(scores, invTemp, outProbabilities,
                Math.min(scores.length, outProbabilities.length), DegeneratePolicy.UNIFORM);
    }

    /**
     * Computes normalized softmax probabilities from logits scaled by an inverse temperature / precision parameter {@code beta}.
     *
     * <p>Standard Boltzmann distribution form: {@code p_i = exp(beta * logits_i) / sum(exp(beta * logits_j))}.
     * Applies maximum-subtracted stabilization, protecting against large {@code beta * logits} overflows.
     * Degenerate inputs yield a uniform distribution ({@link DegeneratePolicy#UNIFORM}).</p>
     *
     * @param logits           raw logit values
     * @param beta             scaling factor (precision or inverse temperature); may be negative
     * @param outProbabilities destination array for normalized probabilities
     */
    public static void computeProbabilitiesScaled(float[] logits, float beta, float[] outProbabilities) {
        if (logits == null || outProbabilities == null) {
            return;
        }
        softmaxInto(logits, beta, outProbabilities,
                Math.min(logits.length, outProbabilities.length), DegeneratePolicy.UNIFORM);
    }

    /**
     * Modulates candidate scores in-place using softmax temperature scaling, redistributing the original
     * total score proportionally according to the softmax distribution.
     *
     * <p>Allocates a single {@code float[scores.length]} scratch buffer. Callers on a hot path should
     * use {@link #applySoftmaxTemperature(float[], float, float[])} and supply a reusable buffer.</p>
     *
     * <p>On degenerate input the scores are left <b>untouched</b> ({@link DegeneratePolicy#PRESERVE}),
     * preserving the pre-migration {@code TemperatureSoftmax} contract.</p>
     *
     * @param scores      candidate scores to modulate in-place
     * @param temperature effective retrieval temperature (T=1.0 is identity)
     */
    public static void applySoftmaxTemperature(float[] scores, float temperature) {
        if (scores == null || scores.length <= 1) {
            return;
        }
        applySoftmaxTemperature(scores, temperature, new float[scores.length]);
    }

    /**
     * Buffer-reusing variant of {@link #applySoftmaxTemperature(float[], float)} for allocation-free hot paths.
     *
     * <p>The {@code scratch} array is used as scratch space only; its contents on return are
     * unspecified. It must be at least as long as {@code scores}. If it is too short (or null)
     * a correctly sized buffer is allocated internally.</p>
     *
     * @param scores      candidate scores to modulate in-place
     * @param temperature effective retrieval temperature (T=1.0 is identity)
     * @param scratch     reusable scratch buffer of length &gt;= {@code scores.length}
     */
    public static void applySoftmaxTemperature(float[] scores, float temperature, float[] scratch) {
        if (scores == null || scores.length <= 1) {
            return;
        }
        if (Math.abs(temperature - 1.0f) < IDENTITY_TEMPERATURE_EPSILON) {
            return; // T = 1.0 is the identity transform
        }

        final int n = scores.length;
        final float[] probs = (scratch != null && scratch.length >= n) ? scratch : new float[n];
        final float invTemp = 1.0f / Math.max(MIN_TEMPERATURE, temperature);

        // Total is accumulated from the untouched input before any mutation occurs.
        float totalOriginalScore = 0.0f;
        for (int i = 0; i < n; i++) {
            totalOriginalScore += scores[i];
        }

        // PRESERVE: on degenerate input `probs` is not written, `scores` is not mutated.
        if (!softmaxInto(scores, invTemp, probs, n, DegeneratePolicy.PRESERVE)) {
            return;
        }

        final float scaleMultiplier = totalOriginalScore > 0.0f ? totalOriginalScore : 1.0f;
        for (int i = 0; i < n; i++) {
            scores[i] = probs[i] * scaleMultiplier;
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

    // ─────────────────────── Single Log-Sum-Exp implementation ───────────────────────

    /**
     * The one and only max-shifted Log-Sum-Exp loop in this class.
     *
     * <p>Evaluates {@code Math.exp} exactly once per element: the weights are cached in
     * {@code out} during the accumulation pass and normalized in place afterwards. {@code exp}
     * is a non-vectorizable transcendental intrinsic and is by far the dominant cost here, so it
     * must never be recomputed to save a buffer.</p>
     *
     * @param src    source values (never mutated)
     * @param scale  multiplier applied to each source value (1/T, or beta)
     * @param out    destination for normalized probabilities
     * @param n      number of elements to process
     * @param policy behaviour when the exponential sum is degenerate
     * @return {@code true} if {@code out[0..n)} holds a valid distribution, {@code false} if the
     *         input was degenerate and {@code policy} was {@link DegeneratePolicy#PRESERVE}. On
     *         {@code false} the contents of {@code out} are unspecified (it holds partially
     *         accumulated weights) and must be discarded by the caller — {@code src} is never
     *         mutated, which is what makes the PRESERVE contract safe for in-place callers.
     */
    private static boolean softmaxInto(
            final float[] src, final float scale, final float[] out, final int n, final DegeneratePolicy policy) {

        if (n <= 0) {
            return false;
        }
        if (n == 1) {
            out[0] = 1.0f;
            return true;
        }

        // Pass 1: max-shift for numerical stability.
        float maxScaled = src[0] * scale;
        for (int i = 1; i < n; i++) {
            final float scaled = src[i] * scale;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }

        // Pass 2: single exp evaluation per element, cached into `out`.
        double sumExp = 0.0;
        for (int i = 0; i < n; i++) {
            final double w = Math.exp((src[i] * scale) - maxScaled);
            out[i] = (float) w;
            sumExp += w;
        }

        if (sumExp <= 0.0 || Double.isNaN(sumExp) || Double.isInfinite(sumExp)) {
            if (policy == DegeneratePolicy.PRESERVE) {
                return false;
            }
            Arrays.fill(out, 0, n, 1.0f / n);
            return true;
        }

        // Pass 3: normalize the cached weights.
        final float invSum = (float) (1.0 / sumExp);
        for (int i = 0; i < n; i++) {
            out[i] *= invSum;
        }
        return true;
    }
}
