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

import com.spectrayan.spector.core.similarity.VectorOps;

/**
 * Pure mathematical kernel for dopaminergic prediction error scaling, dual spatial-temporal surprise,
 * and flashbulb memory gating criteria (ADR-0033 Domain 6).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class DopaminergicSurpriseKernel {

    public static final float DEFAULT_CENTER = 1.0f;
    public static final float DEFAULT_STEEPNESS = 1.2f;
    public static final float MIN_IMPORTANCE = 0.05f;
    public static final float MAX_IMPORTANCE = 10.0f;
    public static final float IMPORTANCE_SPAN = MAX_IMPORTANCE - MIN_IMPORTANCE;
    public static final double DEFAULT_FLASHBULB_THRESHOLD = 3.0;

    private DopaminergicSurpriseKernel() {}

    /**
     * Converts a prediction error z-score to importance using a shifted sigmoid transfer:
     * <p>{@code I(z) = 0.05 + 9.95 * σ(k · (z - center))}</p>
     *
     * @param zScore    standardized surprise deviation
     * @param center    midpoint threshold (typically 1.0)
     * @param steepness sigmoid steepness (typically 1.2)
     * @return importance in [0.05, 10.0]
     */
    public static float zScoreToImportance(final double zScore, final float center, final float steepness) {
        final float sigmoid = VectorOps.sigmoid((float) (steepness * (zScore - center)));
        return MIN_IMPORTANCE + sigmoid * IMPORTANCE_SPAN;
    }

    /**
     * Convenience method using default center (1.0) and steepness (1.2).
     */
    public static float zScoreToImportance(final double zScore) {
        return zScoreToImportance(zScore, DEFAULT_CENTER, DEFAULT_STEEPNESS);
    }

    /**
     * Combines spatial and temporal surprise components into a single metric.
     *
     * @param spatialSurprise  surprise from feature space distance
     * @param temporalSurprise surprise from elapsed time since similar stimulus
     * @param spatialWeight    relative weight for spatial surprise in [0, 1]
     * @return combined surprise value
     */
    public static float dualSurprise(
            final float spatialSurprise, final float temporalSurprise, final float spatialWeight) {
        return spatialWeight * spatialSurprise + (1.0f - spatialWeight) * temporalSurprise;
    }

    /**
     * Flashbulb memory gating criterion (Brown &amp; Kulik, 1977).
     * High dopamine spikes trigger maximum-fidelity encoding and exemption from pruning.
     *
     * @param zScore    surprise z-score
     * @param threshold activation threshold (typically >= 3.0σ)
     * @return true if memory qualifies for flashbulb fidelity
     */
    public static boolean isFlashbulb(final double zScore, final double threshold) {
        return zScore > threshold;
    }

    /**
     * Flashbulb gating criterion using the default 3.0σ threshold.
     */
    public static boolean isFlashbulb(final double zScore) {
        return isFlashbulb(zScore, DEFAULT_FLASHBULB_THRESHOLD);
    }
}
