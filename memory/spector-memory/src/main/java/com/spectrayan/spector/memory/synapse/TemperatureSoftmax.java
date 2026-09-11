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

import com.spectrayan.spector.core.math.SoftmaxKernel;
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
        float[] scores = new float[n];
        for (int i = 0; i < n; i++) {
            scores[i] = results.get(i).score();
        }

        SoftmaxKernel.applySoftmaxTemperature(scores, temperature);

        for (int i = 0; i < n; i++) {
            CognitiveResult r = results.get(i);
            float newScore = scores[i];
            if (r.breakdown() != null) {
                results.set(i, r.withScoreAndBreakdown(newScore, r.breakdown().withFinalScore(newScore)));
            } else {
                results.set(i, r.withScore(newScore));
            }
        }
    }
}
