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

/**
 * Pure mathematical kernel for continuous mass-dilated recency decay with continuous lambda recency scaling
 * (ADR-0031 / ADR-0033 Domain 2).
 *
 * <p>Formula: {@code R_λ(Δt, M_i) = 1 / (1 + λ * ln(1 + Δt_days) / (1 + M_i)) * arousalModifier * reconsolidationBoost}</p>
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class MassDilatedDecayKernel {

    public static final double MS_PER_DAY = 86_400_000.0;

    private MassDilatedDecayKernel() {}

    /**
     * Computes the continuous mass-dilated recency decay factor.
     *
     * @param timestampMs      creation timestamp in epoch millis
     * @param nowMs            reference query clock in epoch millis
     * @param cognitiveMass    dynamic cognitive mass M_i
     * @param arousal          arousal intensity byte
     * @param agentRecallCount number of prior agent recalls for reconsolidation
     * @param zeroTimeDecay    true if time decay is suspended
     * @param lambda           continuous recency scaling factor (1.0 for recall, 0.3 for wander/dream, 0.0 for timeless)
     * @return decay multiplier clamped in [0.0, 1.0]
     */
    public static float compute(
            final long timestampMs, final long nowMs, final float cognitiveMass,
            final byte arousal, final int agentRecallCount, final boolean zeroTimeDecay,
            final float lambda) {

        final float arousalMod = PowerLawDecayKernel.arousalModifier(arousal);
        final float reconsolidationBoost = 1.0f + 0.05f * Math.min(agentRecallCount, 10);

        if (zeroTimeDecay || lambda <= 0.0f) {
            return Math.min(1.0f, 1.0f * arousalMod * reconsolidationBoost);
        }

        final double elapsedDays = Math.max(0.0, (nowMs - timestampMs) / MS_PER_DAY);
        final float logTerm = (float) Math.log1p(elapsedDays);
        final float massDenominator = 1.0f + Math.max(0.0f, cognitiveMass);

        final float dilatedDecay = 1.0f / (1.0f + ((lambda * logTerm) / massDenominator));
        final float finalDecay = dilatedDecay * arousalMod * reconsolidationBoost;

        return Math.min(1.0f, Math.max(0.0f, finalDecay));
    }

    /**
     * Batch calculation of continuous mass-dilated decay over candidate arrays (Principle 3).
     */
    public static void computeBatch(
            final long[] timestampsMs, final float[] cognitiveMasses, final byte[] arousals,
            final int[] recallCounts, final boolean[] zeroTimeDecays,
            final long nowMs, final float lambda, final float[] outDecays, final int count) {
        for (int i = 0; i < count; i++) {
            outDecays[i] = compute(
                    timestampsMs[i], nowMs, cognitiveMasses[i], arousals[i],
                    recallCounts[i], zeroTimeDecays[i], lambda);
        }
    }
}
