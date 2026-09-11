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

import com.spectrayan.spector.core.math.SigmoidKernel;

/**
 * Pure mathematical kernel for ADHD-informed sigmoid-gated ICNU salience synthesis (ADR-0033 Domain 7).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class IcnuSalienceKernel {

    public static final float MIN_IMPORTANCE = 0.05f;
    public static final float MAX_IMPORTANCE = 10.0f;
    public static final float IMPORTANCE_SPAN = MAX_IMPORTANCE - MIN_IMPORTANCE;

    private IcnuSalienceKernel() {}

    /**
     * Computes the fused importance score from ICNU signals with sigmoid gating.
     *
     * <p>Formula:
     * <ul>
     *   <li>When {@code steepness <= 0}: linear fallback</li>
     *   <li>When {@code steepness > 0}:
     *       {@code stimulus = w_I·(I×N) + w_C·C + w_U·U},
     *       {@code gated = σ(k · (stimulus - θ))},
     *       {@code importance = clamp(MIN + gated · (MAX - MIN), MIN, MAX)}
     *   </li>
     * </ul>
     * </p>
     *
     * @param interestVal  interest signal in [0.0, 1.0]
     * @param challengeVal challenge signal in [0.0, 1.0]
     * @param noveltyNorm  novelty signal in [0.0, 1.0]
     * @param urgencyVal   urgency signal in [0.0, 1.0]
     * @param wInterest    normalized weight for interest
     * @param wChallenge   normalized weight for challenge
     * @param wNovelty     normalized weight for novelty
     * @param wUrgency     normalized weight for urgency
     * @param threshold    sigmoid threshold θ
     * @param steepness    sigmoid steepness k
     * @return fused importance clamped to [0.05, 10.0]
     */
    public static float fuse(
            final float interestVal, final float challengeVal, final float noveltyNorm, final float urgencyVal,
            final float wInterest, final float wChallenge, final float wNovelty, final float wUrgency,
            final float threshold, final float steepness) {

        if (steepness <= 0.0f) {
            final float raw = wInterest * interestVal
                    + wChallenge * challengeVal
                    + wNovelty * noveltyNorm
                    + wUrgency * urgencyVal;
            final float scaled = MIN_IMPORTANCE + raw * IMPORTANCE_SPAN;
            return Math.clamp(scaled, MIN_IMPORTANCE, MAX_IMPORTANCE);
        }

        // Biologically grounded dopaminergic gating: I x N interaction
        final float stimulus = wInterest * (interestVal * noveltyNorm)
                + wChallenge * challengeVal
                + wUrgency * urgencyVal;

        final float gated = SigmoidKernel.sigmoid(steepness * (stimulus - threshold));
        final float scaled = MIN_IMPORTANCE + gated * IMPORTANCE_SPAN;
        return Math.clamp(scaled, MIN_IMPORTANCE, MAX_IMPORTANCE);
    }
}
