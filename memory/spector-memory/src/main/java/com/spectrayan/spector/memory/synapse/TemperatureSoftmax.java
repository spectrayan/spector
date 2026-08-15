/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ScoreBreakdown;

import java.util.List;

/**
 * Numerically stable softmax retrieval temperature modulation.
 *
 * <h3>Biological &amp; Information-Theoretic Rationale</h3>
 * <p>Retrieval temperature controls the entropy of the recall distribution:</p>
 * <ul>
 *   <li><b>\(T &gt; 1.0\) (High Temperature / Novel Query)</b>: Flattens the candidate score
 *       distribution (higher entropy). Smaller differences between top-ranked candidates allow
 *       novel and lateral associative memories to compete, expanding recall breadth.</li>
 *   <li><b>\(T &lt; 1.0\) (Low Temperature / Familiar Query)</b>: Sharpens the distribution
 *       (lower entropy). Emphasizes dominant matches while suppressing marginal candidates,
 *       maximizing precision.</li>
 *   <li><b>\(T = 1.0\) (Identity)</b>: Preserves the original cognitive scoring distribution.</li>
 * </ul>
 *
 * <h3>Numerical Stability</h3>
 * <p>To prevent exponential overflow or underflow with large scores, the computation
 * shifts scores by the maximum scaled score:
 * \[\text{shift} = \max_j (s_j / T)\]
 * \[w_i = \exp(s_i / T - \text{shift})\]
 * \[p_i = \frac{w_i}{\sum_j w_j}\]
 * Rescaled score: \(s'_i = p_i \times \text{totalOriginalScore}\).</p>
 */
public final class TemperatureSoftmax {

    private TemperatureSoftmax() {}

    /**
     * Applies temperature-modulated softmax scaling to a list of cognitive results in-place.
     *
     * @param results     the list of cognitive results to modulate
     * @param temperature the effective retrieval temperature (must be &gt; 0)
     */
    public static void applySoftmaxTemperature(List<CognitiveResult> results, float temperature) {
        if (results == null || results.size() <= 1) return;
        if (Math.abs(temperature - 1.0f) < 1e-4f) return; // T = 1.0 is identity

        int n = results.size();
        float temp = Math.max(0.01f, temperature);

        float maxScaled = -Float.MAX_VALUE;
        float totalOriginalScore = 0.0f;
        for (int i = 0; i < n; i++) {
            float s = results.get(i).score();
            totalOriginalScore += s;
            float scaled = s / temp;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }

        double sumExp = 0.0;
        double[] expWeights = new double[n];
        for (int i = 0; i < n; i++) {
            float scaled = results.get(i).score() / temp;
            double w = Math.exp(scaled - maxScaled);
            expWeights[i] = w;
            sumExp += w;
        }

        if (sumExp <= 0.0 || Double.isNaN(sumExp)) return;

        // Scale factor: redistribute totalOriginalScore proportionally according to softmax probability
        double scaleMultiplier = totalOriginalScore > 0 ? totalOriginalScore : 1.0;

        for (int i = 0; i < n; i++) {
            CognitiveResult r = results.get(i);
            double prob = expWeights[i] / sumExp;
            float newScore = (float) (prob * scaleMultiplier);

            ScoreBreakdown bd = r.breakdown() != null
                    ? new ScoreBreakdown(
                            r.breakdown().similarity(),
                            r.breakdown().importanceDecay(),
                            r.breakdown().tagBoostFactor(),
                            r.breakdown().habituationPenalty(),
                            r.breakdown().graphBoost(),
                            r.breakdown().valenceAlignment(),
                            newScore)
                    : null;

            results.set(i, new CognitiveResult(
                    r.id(), r.text(), newScore, r.importance(), r.ageDays(),
                    r.agentRecallCount(), r.valence(), r.memoryType(), r.source(),
                    r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay(),
                    r.retrievalMode(), bd, r.trace(), r.sourceModality(), r.metadata()));
        }
    }
}
