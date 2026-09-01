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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.pathway.*;
import com.spectrayan.spector.memory.persist.*;
import com.spectrayan.spector.memory.assembly.*;

import com.spectrayan.spector.memory.pathway.*;
import com.spectrayan.spector.memory.persist.*;
import com.spectrayan.spector.memory.assembly.*;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.cortex.ContinuityRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.wander.relay.WanderReport;
import com.spectrayan.spector.memory.wander.relay.WanderSignal;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WanderPathwayTest {

    @Test
    void endToEndWanderPathwayExecution() {
        int dim = 8;
        float[] mins = new float[dim];
        float[] maxs = new float[dim];
        Arrays.fill(mins, -1.0f);
        Arrays.fill(maxs, 1.0f);
        ScalarQuantizer quantizer = ScalarQuantizer.fromBounds(dim, mins, maxs);

        AgentSoul soul = AgentSoul.builder()
                .id("atlas")
                .name("Strategist")
                .description("Strategist soul")
                .purpose("analysis")
                .soulVersion((short) 1)
                .build();
        GenerativeSelfModel selfModel = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, dim);
        MentalStateTracker mentalStateTracker = new MentalStateTracker(selfModel);
        CognitiveManifold manifold = new CognitiveManifold(dim);
        ContinuousHopfieldNetwork hopfieldNetwork = new ContinuousHopfieldNetwork();
        HomeostaticCore homeostaticCore = new HomeostaticCore();
        HebbianGraphMemory hebbianGraph = new HebbianGraphMemory(50);
        ContinuityRecordMemory continuityMemory = ContinuityRecordMemory.heap(100);

        AismeConfig config = AismeConfig.defaultConfig();

        try (WanderPathway pathway = WanderPathway.builder()
                .quantizer(quantizer)
                .mentalStateTracker(mentalStateTracker)
                .cognitiveManifold(manifold)
                .hopfieldNetwork(hopfieldNetwork)
                .homeostaticCore(homeostaticCore)
                .hebbianGraph(hebbianGraph)
                .continuityMemory(continuityMemory)
                .aismeConfig(config)
                .build()) {

            // Build signal with pre-seeded sampled vectors for wandering
            WanderSignal signal = WanderSignal.builder()
                    .quantizer(quantizer)
                    .mentalStateTracker(mentalStateTracker)
                    .cognitiveManifold(manifold)
                    .hopfieldNetwork(hopfieldNetwork)
                    .homeostaticCore(homeostaticCore)
                    .hebbianGraph(hebbianGraph)
                    .continuityMemory(continuityMemory)
                    .aismeConfig(config)
                    .lastActivityTimestampMs(System.currentTimeMillis() - 100_000L) // 100s ago
                    .idleThresholdSeconds(60)
                    .build();

            // Add 4 test vectors
            float[] v1 = new float[]{0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            float[] v2 = new float[]{0.48f, 0.52f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            float[] v3 = new float[]{-0.5f, -0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            float[] v4 = new float[]{0.0f, 0.0f, 0.8f, 0.6f, 0.0f, 0.0f, 0.0f, 0.0f};

            signal.sampledVectors().addAll(List.of(v1, v2, v3, v4));
            signal.sampledMemoryIds().addAll(List.of("sem-0-1", "sem-0-2", "sem-0-3", "sem-0-4"));

            WanderReport report = pathway.conduct(signal);

            assertThat(report).isNotNull();
            assertThat(report.snapshotRecorded()).isTrue();
            assertThat(report.associationsFormed()).isGreaterThan(0);
            assertThat(report.discoveredAssociations()).isNotEmpty();
            assertThat(continuityMemory.totalSnapshots()).isEqualTo(1);
            assertThat(continuityMemory.latestSnapshot()).isPresent();
            assertThat(continuityMemory.latestSnapshot().get().phiCc()).isEqualTo(0.85f);
        }
    }
}
