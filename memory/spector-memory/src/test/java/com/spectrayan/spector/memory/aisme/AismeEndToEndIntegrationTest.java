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
package com.spectrayan.spector.memory.aisme;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ConsolidationRelay;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.recall.relay.RecallPathwayFactory;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;
import com.spectrayan.spector.memory.recall.relay.RrfRescoreRelay;
import com.spectrayan.spector.memory.recall.relay.SortAndTruncateRelay;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * End-to-end integration tests for the Active Inference Self-Model Engine (AISME).
 *
 * <p>Verifies the end-to-end multi-layer pipeline: Homeostatic Core -> Free Energy Minimization ->
 * Continuous Hopfield Attractors -> Riemannian Manifold Geometry -> Predictive Coding Narrative Self ->
 * Consciousness Continuity (Phi_CC) -> Global Workspace Conscious Access Bottleneck.</p>
 */
class AismeEndToEndIntegrationTest {

    @Test
    void endToEnd_fullAismePipeline_modulatesAndConsciouslyBroadcastsMemories() {
        int dim = 4;
        Map<String, float[]> memoryVectors = new HashMap<>();
        memoryVectors.put("mem-childhood", new float[]{1.0f, 0.0f, 0.0f, 0.0f});
        memoryVectors.put("mem-career", new float[]{0.9f, 0.1f, 0.0f, 0.0f});
        memoryVectors.put("mem-values", new float[]{0.95f, 0.05f, 0.0f, 0.0f});
        memoryVectors.put("mem-trivia", new float[]{0.1f, 0.9f, 0.0f, 0.0f});
        memoryVectors.put("mem-noise", new float[]{0.0f, 0.1f, 0.9f, 0.0f});

        AgentSoul soul = AgentSoul.builder()
                .id("soul-ancestor")
                .name("GreatGrandparent")
                .purposeEmbedding(new float[]{1.0f, 0.0f, 0.0f, 0.0f})
                .expertiseEmbedding(new float[]{1.0f, 0.0f, 0.0f, 0.0f})
                .build();

        AismeConfig config = AismeConfig.builder()
                .enabled(true)
                .globalWorkspaceCapacity(3)
                .build();

        AismeBundle aismeBundle = AismeBuilder.build(config, soul, dim, memoryVectors::get);
        assertThat(aismeBundle).isNotNull();

        CognitivePathway<RecallSignal> pathway = RecallPathwayFactory.create(
                null,
                signal -> true, // transduction
                signal -> true, // prospective
                signal -> true, // governedReleaseGate
                aismeBundle.homeostaticBiasRelay(),
                signal -> true, // vectorSearch
                aismeBundle.freeEnergyGuidedRelay(),
                signal -> true, // scoring
                signal -> true, // graphExpansion
                aismeBundle.hopfieldAssociativeRelay(),
                signal -> true, // evidenceFusion
                signal -> true, // bm25
                new RrfRescoreRelay(null, null, null),
                aismeBundle.manifoldRerankRelay(),
                aismeBundle.constructiveSimulationRelay(),
                aismeBundle.consciousnessContinuityRelay(),
                new SortAndTruncateRelay(null),
                null,
                null,
                null,
                aismeBundle.consciousAccessRelay(),
                null, // constructiveMemoryPersistenceRelay
                null, // epistemicLearningRelay
                new ConsolidationRelay<>("consolidation", s -> {})
        );

        RecallOptions options = RecallOptions.builder()
                .aismeConfig(config)
                .topK(5)
                .build();

        RecallSignal signal = RecallSignal.forTextQuery("family wisdom and core values", options);
        signal.candidates().add(createResult("mem-childhood", 0.70f));
        signal.candidates().add(createResult("mem-career", 0.65f));
        signal.candidates().add(createResult("mem-values", 0.80f));
        signal.candidates().add(createResult("mem-trivia", 0.30f));
        signal.candidates().add(createResult("mem-noise", 0.20f));

        RecallSignal resultSignal = pathway.conduct(signal);
        assertThat(resultSignal).isNotNull();

        // 1. Output must be bounded by Global Workspace conscious capacity = 3
        assertThat(resultSignal.candidates()).hasSize(3);

        // 2. High-identity, soul-aligned memories must be elevated
        assertThat(resultSignal.candidates().get(0).id()).isIn("mem-values", "mem-childhood", "mem-career");
        assertThat(resultSignal.candidates().get(0).score()).isGreaterThan(0.0f);
    }

    private static CognitiveResult createResult(String id, float score) {
        return new CognitiveResult(
                id,
                "text " + id,
                score,
                1.0f,
                0.1f,
                0,
                (byte) 0,
                MemoryType.EPISODIC,
                MemorySource.USER_STATED,
                new String[0],
                1.0f,
                1.0f,
                CognitiveResult.RetrievalMode.STANDARD,
                null,
                null,
                null,
                Map.of()
        );
    }
}
