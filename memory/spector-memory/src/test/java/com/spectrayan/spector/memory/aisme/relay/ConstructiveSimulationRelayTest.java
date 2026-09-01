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
package com.spectrayan.spector.memory.aisme.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.narrative.NarrativeSelfEngine;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ConstructiveSimulationRelay}.
 */
class ConstructiveSimulationRelayTest {

    @Test
    void relayName_isConstructiveSimulation() {
        ConstructiveSimulationRelay relay = new ConstructiveSimulationRelay(null, null, null);
        assertThat(relay.relayName()).isEqualTo("constructive-simulation");
    }

    @Test
    void unconfigured_isPassThrough() {
        ConstructiveSimulationRelay relay = new ConstructiveSimulationRelay(null, null, null);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        CognitiveResult item = createResult("m1", 0.5f);
        signal.candidates().add(item);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isEqualTo(0.5f);
    }

    @Test
    void configured_modulatesScoresByNarrativeAlignment() {
        NarrativeSelfEngine engine = new NarrativeSelfEngine(null, 2);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.0f}); // aligned with [1, 0]
        vectors.put("m2", new float[]{-1.0f, 0.0f}); // unaligned

        ConstructiveSimulationRelay relay = new ConstructiveSimulationRelay(engine, null, vectors::get, 0.5f);

        float[] query = {1.0f, 0.0f};
        RecallSignal signal = RecallSignal.forVectorQuery(query, RecallOptions.builder().build());
        signal.setQueryVector(query);

        CognitiveResult item1 = createResult("m1", 0.5f);
        CognitiveResult item2 = createResult("m2", 0.5f);
        signal.candidates().add(item1);
        signal.candidates().add(item2);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();

        float score1 = signal.candidates().get(0).score();
        float score2 = signal.candidates().get(1).score();

        assertThat(score1).isGreaterThan(score2);
    }

    @Test
    void configured_synthesizesCounterfactualEpisode_whenComplementaryCandidatesAlignWithNarrative() {
        NarrativeSelfEngine engine = new NarrativeSelfEngine(null, 2);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.2f});
        vectors.put("m2", new float[]{0.8f, 0.1f});

        ConstructiveSimulationRelay relay = new ConstructiveSimulationRelay(engine, null, vectors::get, 0.5f);

        float[] query = {1.0f, 0.0f};
        RecallSignal signal = RecallSignal.forVectorQuery(query, RecallOptions.builder().build());
        signal.setQueryVector(query);

        signal.candidates().add(createResult("m1", 0.8f));
        signal.candidates().add(createResult("m2", 0.7f));

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();

        // Should synthesize a 3rd candidate representing the recombined counterfactual episode
        assertThat(signal.candidates()).hasSize(3);
        CognitiveResult simulated = signal.candidates().get(2);
        assertThat(simulated.id()).isNotNull().hasSize(13);
        assertThat(com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.isSimulated(simulated.consolidationFlags())).isTrue();
        assertThat(simulated.text()).contains("[Constructive Simulation");
        assertThat(simulated.synapticTags()).contains("simulated", "counterfactual", "constructive");
        assertThat(simulated.memoryType()).isEqualTo(MemoryType.EPISODIC);
        assertThat(simulated.source()).isEqualTo(MemorySource.INFERRED);
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
