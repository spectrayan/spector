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
package com.spectrayan.spector.memory.aisme.simulation;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.fegr.EventDensityFilter;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.relay.EventDensityGatingRelay;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

/**
 * 1,000-frame continuous multimodal sensory stream compression benchmark.
 *
 * <p>Validates that Information-Theoretic Event Density Gating \(\nu(o_t)\) achieves
 * \(\ge 80\%\) compression/suppression of static ambient background frames while
 * preserving \(100\%\) of salient high-entropy event spikes and modulating sampling frequency.</p>
 */
class ContinuousSensoryStreamCompressionBenchmarkTest {

    private static final int DIMENSIONS = 16;
    private static final int TOTAL_FRAMES = 1_000;
    private static final int SALIENT_SPIKE_INTERVAL = 50; // Every 50th frame is a novel event spike

    @Test
    @DisplayName("Benchmark 1,000 sensory frames: >=80% static compression with 100% salient event retention")
    void continuousStream_compressesStaticBackgroundAndRetainsSalientSpikes() {
        float[] baseEmbedding = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            baseEmbedding[i] = (float) Math.cos((i + 1) * 0.4);
        }

        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("benchmark-entity")
                .purposeEmbedding(baseEmbedding)
                .build();

        GenerativeSelfModel selfModel = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, DIMENSIONS);
        MentalStateTracker tracker = new MentalStateTracker(selfModel);
        EventDensityFilter filter = new EventDensityFilter(0.50f, 0.40f, 0.30f, 0.30f, 0.10f, 30.0f);
        EventDensityGatingRelay relay = new EventDensityGatingRelay(filter, tracker, true);

        Random random = new Random(1337L);

        int totalGated = 0;
        int totalPassed = 0;
        int expectedSpikes = 0;
        int correctlyCapturedSpikes = 0;
        float idleRateSum = 0.0f;
        int idleCount = 0;
        float spikeRateSum = 0.0f;

        for (int frame = 0; frame < TOTAL_FRAMES; frame++) {
            boolean isSpike = (frame % SALIENT_SPIKE_INTERVAL == 0);
            float[] obs = new float[DIMENSIONS];

            if (isSpike) {
                expectedSpikes++;
                // High-information burst / anomaly
                for (int d = 0; d < DIMENSIONS; d++) {
                    obs[d] = selfModel.priorMean()[d] + (float) (2.5 + random.nextGaussian() * 0.2);
                }
            } else {
                // Static ambient background with minor Gaussian sensor noise
                for (int d = 0; d < DIMENSIONS; d++) {
                    obs[d] = selfModel.priorMean()[d] + (float) (random.nextGaussian() * 0.01);
                }
            }

            RememberSignal signal = RememberSignal.forCognitive(
                    "frame-" + frame,
                    isSpike ? "salient event spike " + frame : "ambient background " + frame,
                    obs,
                    MemoryType.SEMANTIC,
                    new String[]{isSpike ? "spike" : "ambient"},
                    null,
                    null,
                    SalienceProfile.NEUTRAL,
                    (short) 1
            );

            boolean passed = relay.transmit(signal);
            EventDensityMetrics metrics = signal.eventDensityMetrics();

            if (passed) {
                totalPassed++;
                if (isSpike) {
                    correctlyCapturedSpikes++;
                    spikeRateSum += metrics.dynamicSamplingRateHz();
                }
            } else {
                totalGated++;
                idleRateSum += metrics.dynamicSamplingRateHz();
                idleCount++;
            }
        }

        float compressionRatio = (float) totalGated / TOTAL_FRAMES;
        float spikeRecall = (float) correctlyCapturedSpikes / expectedSpikes;
        float avgIdleSamplingRate = idleRateSum / Math.max(1, idleCount);
        float avgSpikeSamplingRate = spikeRateSum / Math.max(1, correctlyCapturedSpikes);

        // Assertions:
        // 1. Compression Ratio >= 80% (ambient frames successfully gated)
        assertThat(compressionRatio)
                .as("Static ambient frames are compressed by >= 80%")
                .isGreaterThanOrEqualTo(0.80f);

        // 2. Salient Spike Recall = 100% (zero lost high-information events)
        assertThat(spikeRecall)
                .as("100% of salient event spikes are preserved")
                .isEqualTo(1.0f);

        // 3. Dynamic Sampling Rate: Scaled down during idle, scaled up during spikes
        assertThat(avgIdleSamplingRate)
                .as("Average idle sampling rate is throttled near minimum baseline")
                .isLessThan(2.0f);

        assertThat(avgSpikeSamplingRate)
                .as("Average spike sampling rate is elevated near maximum burst frequency")
                .isGreaterThan(25.0f);
    }
}
