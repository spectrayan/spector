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

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.dream.DreamJournalMemory;
import com.spectrayan.spector.memory.dream.relay.DreamConfig;
import com.spectrayan.spector.memory.dream.relay.DreamMode;
import com.spectrayan.spector.memory.dream.relay.DreamReport;
import com.spectrayan.spector.memory.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DreamPathwayTest {

    @Test
    void testFullDreamPathwayExecution() throws Exception {
        int dim = 8;
        DreamConfig dreamConfig = DreamConfig.builder()
                .enabled(true)
                .dreamNoiseScale(0.15f)
                .journalEnabled(true)
                .langevinSteps(20)
                .build();

        AismeConfig aismeConfig = AismeConfig.defaultConfig();
        HebbianGraphMemory hebbianGraph = new HebbianGraphMemory(50);

        try (DistributedMemoryTensor dmt = new DistributedMemoryTensor(dim);
             DreamJournalMemory journal = new DreamJournalMemory(null, 50, 256);
             DreamPathway pathway = DreamPathway.builder()
                     .dreamConfig(dreamConfig)
                     .aismeConfig(aismeConfig)
                     .hebbianGraph(hebbianGraph)
                     .distributedMemoryTensor(dmt)
                     .dreamJournalMemory(journal)
                     .build()) {

            float[] v1 = new float[]{0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            float[] v2 = new float[]{0.0f, 0.0f, 0.8f, 0.6f, 0.0f, 0.0f, 0.0f, 0.0f};

            // Seed holographic memory tensor
            dmt.accumulate(v1, 1.0f);
            dmt.accumulate(v2, 1.0f);

            DreamSignal signal = DreamSignal.builder()
                    .mode(DreamMode.REM)
                    .config(dreamConfig)
                    .aismeConfig(aismeConfig)
                    .hebbianGraph(hebbianGraph)
                    .distributedMemoryTensor(dmt)
                    .dreamJournalMemory(journal)
                    .seedMemoryIds(List.of("seed-mem-1", "seed-mem-2"))
                    .seedVectors(List.of(v1, v2))
                    .build();

            DreamReport report = pathway.conduct(signal);

            assertThat(report).isNotNull();
            assertThat(report.seedsSampled()).isEqualTo(2);
            assertThat(report.scenesConstructed()).isGreaterThanOrEqualTo(1);
            assertThat(report.mode()).isEqualTo(DreamMode.REM);
            assertThat(signal.fragments()).isNotEmpty();
            assertThat(signal.constructedScenes()).isNotEmpty();
            assertThat(signal.survivingScenes()).isNotEmpty();
        }
    }

    @Test
    void testThoughtExperimentModeExecution() throws Exception {
        int dim = 8;
        DreamConfig dreamConfig = DreamConfig.builder()
                .enabled(true)
                .dreamTemperatureThought(0.5f)
                .build();

        try (DistributedMemoryTensor dmt = new DistributedMemoryTensor(dim);
             DreamPathway pathway = DreamPathway.builder()
                     .dreamConfig(dreamConfig)
                     .distributedMemoryTensor(dmt)
                     .build()) {

            float[] v1 = new float[]{0.2f, 0.8f, 0.1f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

            DreamSignal signal = DreamSignal.builder()
                    .mode(DreamMode.THOUGHT_EXPERIMENT)
                    .config(dreamConfig)
                    .seedMemoryIds(List.of("decision-fork-1"))
                    .seedVectors(List.of(v1))
                    .build();

            DreamReport report = pathway.conduct(signal);

            assertThat(report).isNotNull();
            assertThat(report.mode()).isEqualTo(DreamMode.THOUGHT_EXPERIMENT);
            assertThat(signal.temperature()).isEqualTo(0.5f);
        }
    }

    @Test
    void testDreamPathwayConvenienceMethod() throws Exception {
        DreamConfig dreamConfig = DreamConfig.builder()
                .enabled(true)
                .build();

        try (DreamPathway pathway = DreamPathway.builder()
                .dreamConfig(dreamConfig)
                .build()) {

            DreamReport report = pathway.dream(DreamMode.DAYDREAM, null, null);
            assertThat(report).isNotNull();
            assertThat(report.mode()).isEqualTo(DreamMode.DAYDREAM);
        }
    }

    @Test
    void testCustomMemoryIdGeneratorPropagation() throws Exception {
        DreamConfig dreamConfig = DreamConfig.builder()
                .enabled(true)
                .build();

        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(100);
        com.spectrayan.spector.memory.id.MemoryIdGenerator customIdGen = () -> "CUSTOM-ID-" + counter.getAndIncrement();

        try (DreamPathway pathway = DreamPathway.builder()
                .dreamConfig(dreamConfig)
                .idGenerator(customIdGen)
                .build()) {

            float[] v1 = new float[]{0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

            DreamSignal signal = DreamSignal.builder()
                    .mode(DreamMode.REM)
                    .config(dreamConfig)
                    .idGenerator(customIdGen)
                    .seedMemoryIds(List.of("seed-1"))
                    .seedVectors(List.of(v1))
                    .build();

            DreamReport report = pathway.conduct(signal);
            assertThat(report).isNotNull();
            assertThat(signal.constructedScenes()).isNotEmpty();
            for (var scene : signal.constructedScenes()) {
                assertThat(scene.id()).startsWith("CUSTOM-ID-");
            }
        }
    }

    @Test
    void testSoulConditionedDreamPathwayExecution() throws Exception {
        int dim = 8;
        DreamConfig dreamConfig = DreamConfig.builder()
                .enabled(true)
                .identityResonanceThreshold(0.70f)
                .build();

        float[] soulEmbedding = new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

        com.spectrayan.spector.memory.model.AgentSoul soul = new com.spectrayan.spector.memory.model.AgentSoul(
                "agent-researcher-1",
                "Neuron AI",
                "Cognitive Neuroscience Researcher",
                "System prompt",
                "Cognitive research",
                "creative and open",
                List.of("neuroscience"),
                List.of("Accuracy"),
                List.of(),
                new com.spectrayan.spector.memory.model.AgentSoul.EmotionalBaseline((byte) 25, (byte) 160),
                "technical",
                "gpt-4",
                List.of(),
                soulEmbedding,
                soulEmbedding,
                (short) 1,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        com.spectrayan.spector.memory.model.SalienceProfile profile = com.spectrayan.spector.memory.model.SalienceProfile.builder()
                .interest("neuroscience", com.spectrayan.spector.memory.model.InterestLevel.CRITICAL, soulEmbedding)
                .build();

        try (DreamPathway pathway = DreamPathway.builder()
                .dreamConfig(dreamConfig)
                .primarySoul(soul)
                .salienceProfile(profile)
                .build()) {

            float[] v1 = new float[]{0.95f, 0.05f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

            DreamSignal signal = DreamSignal.builder()
                    .mode(DreamMode.REM)
                    .config(dreamConfig)
                    .primarySoul(soul)
                    .salienceProfile(profile)
                    .seedMemoryIds(List.of("seed-neuro-1"))
                    .seedVectors(List.of(v1))
                    .build();

            DreamReport report = pathway.conduct(signal);

            assertThat(report).isNotNull();
            assertThat(signal.primarySoul()).isEqualTo(soul);
            assertThat(signal.salienceProfile()).isEqualTo(profile);
            assertThat(signal.constructedScenes()).isNotEmpty();
            // At least one constructed scene aligns with soul and gets IDENTITY
            assertThat(signal.survivingScenes()).anyMatch(s -> s.triageOutcome() == DreamSignal.TriageOutcome.IDENTITY);
        }
    }
}
