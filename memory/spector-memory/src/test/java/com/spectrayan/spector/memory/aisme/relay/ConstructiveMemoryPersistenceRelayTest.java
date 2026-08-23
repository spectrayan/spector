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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ConstructiveMemoryPersistenceRelay}.
 */
class ConstructiveMemoryPersistenceRelayTest {

    @Test
    void relayName_isConstructiveMemoryPersistence() {
        ConstructiveMemoryPersistenceRelay relay = new ConstructiveMemoryPersistenceRelay(null, null, 0.70f);
        assertThat(relay.relayName()).isEqualTo("constructive_memory_persistence");
    }

    @Test
    void unconfigured_isPassThrough() {
        ConstructiveMemoryPersistenceRelay relay = new ConstructiveMemoryPersistenceRelay(null, null, 0.70f);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        signal.candidates().add(createResult("sim-1-2", 0.85f));
        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
    }

    @Test
    void highAlignmentSimulation_isPersisted() {
        CognitiveIngestionTarget target = mock(CognitiveIngestionTarget.class);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("sim-1-2", new float[]{1.0f, 0.0f});

        ConstructiveMemoryPersistenceRelay relay = new ConstructiveMemoryPersistenceRelay(target, vectors::get, 0.70f);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        signal.candidates().add(createResult("sim-1-2", 0.85f));
        signal.candidates().add(createResult("sim-low", 0.50f));
        signal.candidates().add(createResult("mem-normal", 0.95f));

        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        verify(target, times(1)).ingest(startsWith("sim-durable-"), anyString(), any(float[].class));
    }

    @Test
    void belowThresholdSimulation_isNotPersisted() {
        CognitiveIngestionTarget target = mock(CognitiveIngestionTarget.class);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("sim-1-2", new float[]{1.0f, 0.0f});

        ConstructiveMemoryPersistenceRelay relay = new ConstructiveMemoryPersistenceRelay(target, vectors::get, 0.70f);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        signal.candidates().add(createResult("sim-1-2", 0.65f));

        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        verify(target, never()).ingest(anyString(), anyString(), any(float[].class));
    }

    private static CognitiveResult createResult(String id, float score) {
        return new CognitiveResult(
                id,
                "Simulated memory text " + id,
                score,
                1.0f,
                0.0f,
                0,
                (byte) 0,
                MemoryType.EPISODIC,
                MemorySource.REFLECTED,
                new String[]{"simulated", "counterfactual"},
                1.0f,
                1.0f,
                CognitiveResult.RetrievalMode.STANDARD,
                null,
                null,
                null,
                Map.of("simulation", "counterfactual_recombination")
        );
    }
}
