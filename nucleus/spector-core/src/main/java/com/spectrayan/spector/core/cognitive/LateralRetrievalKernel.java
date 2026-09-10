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
 * Pure mathematical kernel for lateral retrieval utility tracking, suppression rates,
 * and hallucination index calculation (ADR-0033 Domain 7).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class LateralRetrievalKernel {

    private LateralRetrievalKernel() {}

    /**
     * Computes the Lateral Utility Rate (LUR):
     * <p>{@code LUR = reinforced / returned}</p>
     *
     * @param reinforced number of laterally retrieved memories reinforced by agent
     * @param returned   total number of lateral memories returned
     * @return utility rate in [0.0, 1.0]
     */
    public static float utilityRate(final int reinforced, final int returned) {
        if (returned <= 0) {
            return 0.0f;
        }
        return (float) reinforced / (float) returned;
    }

    /**
     * Computes the Lateral Suppression Rate (LSR):
     * <p>{@code LSR = suppressed / returned}</p>
     *
     * @param suppressed number of laterally retrieved memories suppressed by agent
     * @param returned   total number of lateral memories returned
     * @return suppression rate in [0.0, 1.0]
     */
    public static float suppressionRate(final int suppressed, final int returned) {
        if (returned <= 0) {
            return 0.0f;
        }
        return (float) suppressed / (float) returned;
    }

    /**
     * Computes the Lateral Hallucination Index (LHI):
     * <p>{@code LHI = (1 - LUR) * LSR}</p>
     * High LHI indicates that lateral exploration is generating noise/hallucinations rather than productive associations.
     *
     * @param lur lateral utility rate in [0, 1]
     * @param lsr lateral suppression rate in [0, 1]
     * @return hallucination index in [0.0, 1.0]
     */
    public static float hallucinationIndex(final float lur, final float lsr) {
        return (1.0f - lur) * lsr;
    }
}
