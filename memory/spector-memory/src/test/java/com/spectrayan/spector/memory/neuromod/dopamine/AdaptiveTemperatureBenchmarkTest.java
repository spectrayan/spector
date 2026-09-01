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
package com.spectrayan.spector.memory.neuromod.dopamine;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.synapse.TemperatureSoftmax;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmark test verifying Shannon entropy and score distribution progression across temperature regimes.
 */
@DisplayName("Adaptive Temperature Benchmark & Entropy Verification")
class AdaptiveTemperatureBenchmarkTest {

    private List<CognitiveResult> buildDistribution(int size) {
        List<CognitiveResult> results = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            float s = 1.0f / (1.0f + i * 0.2f); // Decaying scores: 1.0, 0.833, 0.714, ...
            ScoreBreakdown bd = new ScoreBreakdown(s, 1f, 1f, 1f, 1f, 1f, s);
            results.add(new CognitiveResult(
                    "bench-" + i, "Bench candidate " + i, s, 5.0f, 1.0f,
                    1, (byte) 0, MemoryType.SEMANTIC, MemorySource.OBSERVED,
                    new String[]{"bench"}, 1.0f, 1.0f, RetrievalMode.STANDARD, bd, null, null, null
            ));
        }
        return results;
    }

    private double calculateShannonEntropy(List<CognitiveResult> results) {
        double sum = results.stream().mapToDouble(CognitiveResult::score).sum();
        if (sum <= 0) return 0.0;
        double entropy = 0.0;
        for (CognitiveResult r : results) {
            double p = r.score() / sum;
            if (p > 0) {
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        return entropy;
    }

    @Test
    @DisplayName("Shannon entropy strictly increases monotonically with higher temperature")
    void testEntropyMonotonicity() {
        float[] temps = {0.2f, 0.5f, 1.0f, 2.0f, 4.0f};
        double prevEntropy = -1.0;

        for (float t : temps) {
            List<CognitiveResult> candidates = buildDistribution(20);
            TemperatureSoftmax.applySoftmaxTemperature(candidates, t);
            double entropy = calculateShannonEntropy(candidates);

            if (prevEntropy >= 0.0) {
                assertThat(entropy).as("Entropy at T=" + t + " should be greater than at previous T")
                        .isGreaterThan(prevEntropy);
            }
            prevEntropy = entropy;
        }
    }
}
