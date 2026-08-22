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
package com.spectrayan.spector.memory.pathway;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ConsolidationRelay;
import com.spectrayan.spector.memory.aisme.AismeBuilder;
import com.spectrayan.spector.memory.aisme.AismeBundle;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests verifying AISME relay sequence execution and gating inside {@link RecallPathwayFactory}.
 */
class RecallPathwayAismeWiringTest {

    private Map<String, float[]> vectorStore;
    private AismeBundle bundle;
    private CognitivePathway<RecallSignal> pathway;

    @BeforeEach
    void setUp() {
        vectorStore = new HashMap<>();
        vectorStore.put("m1", new float[]{1.0f, 0.0f, 0.0f, 0.0f});
        vectorStore.put("m2", new float[]{0.9f, 0.1f, 0.0f, 0.0f});
        vectorStore.put("m3", new float[]{0.8f, 0.2f, 0.0f, 0.0f});

        AgentSoul soul = AgentSoul.builder()
                .id("soul-1")
                .name("CoreSelf")
                .purposeEmbedding(new float[]{1.0f, 0.0f, 0.0f, 0.0f})
                .build();

        AismeConfig config = AismeConfig.builder()
                .enabled(true)
                .globalWorkspaceCapacity(2)
                .build();

        bundle = AismeBuilder.build(config, soul, 4, vectorStore::get);

        pathway = RecallPathwayFactory.create(
                null,
                signal -> true, // transduction
                signal -> true, // prospective
                signal -> true, // governedReleaseGate
                bundle.homeostaticBiasRelay(),
                signal -> true, // vectorSearch
                bundle.freeEnergyGuidedRelay(),
                signal -> true, // scoring
                signal -> true, // graphExpansion
                bundle.hopfieldAssociativeRelay(),
                signal -> true, // evidenceFusion
                signal -> true, // bm25
                new RrfRescoreRelay(null, null, null),
                bundle.manifoldRerankRelay(),
                bundle.constructiveSimulationRelay(),
                bundle.consciousnessContinuityRelay(),
                new SortAndTruncateRelay(null),
                null,
                null,
                null,
                bundle.consciousAccessRelay(),
                new ConsolidationRelay<>("consolidation", s -> {})
        );
    }

    @Test
    void pathway_withAismeDisabled_preservesAllCandidatesWithoutGating() {
        RecallOptions options = RecallOptions.builder().build();
        RecallSignal signal = RecallSignal.forTextQuery("query", options);

        signal.candidates().add(createResult("m1", 0.5f));
        signal.candidates().add(createResult("m2", 0.6f));
        signal.candidates().add(createResult("m3", 0.7f));

        pathway.conduct(signal);

        // Conscious access gate is skipped, all 3 candidates remain
        assertThat(signal.candidates()).hasSize(3);
    }

    @Test
    void pathway_withAismeEnabled_appliesRelaysAndRestrictsToWorkspaceCapacity() {
        RecallOptions options = RecallOptions.builder()
                .aismeConfig(AismeConfig.builder().globalWorkspaceCapacity(2).build())
                .build();

        RecallSignal signal = RecallSignal.forTextQuery("query", options);

        signal.candidates().add(createResult("m1", 0.5f));
        signal.candidates().add(createResult("m2", 0.6f));
        signal.candidates().add(createResult("m3", 0.7f));

        pathway.conduct(signal);

        // Conscious access gate runs and restricts output to capacity=2
        assertThat(signal.candidates()).hasSize(2);
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
