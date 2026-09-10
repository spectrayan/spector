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
package com.spectrayan.spector.core.graph;

import com.spectrayan.spector.core.cognitive.ActRActivationKernel;

/**
 * Pure mathematical kernel for spreading activation and compound weight attenuation (ADR-0033 Domain 1, #3).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class SpreadingActivationKernel {

    public static final float DEFAULT_HOP_ATTENUATION = 0.7f;
    public static final float DEFAULT_ACTIVATION_CUTOFF = 0.1f;

    private SpreadingActivationKernel() {}

    /**
     * Computes the compound weight for an edge during single-step recursive spreading activation.
     *
     * @param baseWeight  current edge weight
     * @param attenuation cumulative hop attenuation factor
     * @return attenuated compound weight
     */
    public static float compoundWeight(final float baseWeight, final float attenuation) {
        return baseWeight * attenuation;
    }

    /**
     * Computes multi-hop spreading activation compound weight combining geometric attenuation,
     * ACT-R fan factor, and information-theoretic IDF attenuation (ADR-0033 #3):
     * <p>{@code W_h = W_0 · γ^h · fanFactor(d) · ln(1 + N / (d + 1))}</p>
     *
     * @param baseWeight        initial seed or edge weight
     * @param hopDepth          current graph traversal depth (0 = direct edge)
     * @param perHopAttenuation per-hop decay parameter \(\gamma \in (0, 1]\)
     * @param degree            node connectivity degree \(d\)
     * @param corpusSize        total corpus / graph size \(N\)
     * @return compound weight
     */
    public static float compoundWeight(
            final float baseWeight,
            final int hopDepth,
            final float perHopAttenuation,
            final int degree,
            final int corpusSize) {
        if (baseWeight <= 0.0f) {
            return 0.0f;
        }

        final float geometricDecay = (hopDepth <= 0)
                ? 1.0f
                : (float) Math.pow(Math.max(0.0f, perHopAttenuation), hopDepth);
        final float attenuated = baseWeight * geometricDecay;

        if (degree <= 0 || corpusSize <= 0) {
            return attenuated;
        }

        final float fanFactor = ActRActivationKernel.fanFactor(degree);
        final float idf = (float) Math.log(1.0 + (double) corpusSize / (double) (degree + 1));
        return attenuated * fanFactor * idf;
    }
}
