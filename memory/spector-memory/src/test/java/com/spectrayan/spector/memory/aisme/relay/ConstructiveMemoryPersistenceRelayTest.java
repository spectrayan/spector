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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.RememberPathway;
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

        signal.candidates().add(createResult("0123456789ABC", 0.85f, SynapticHeaderConstants.FLAG_SIMULATED));
        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
    }

    @Test
    void highAlignmentSimulation_isPersisted() {
        RememberPathway target = mock(RememberPathway.class);
        org.mockito.Mockito.when(target.currentSoulVersion()).thenReturn((short) 5);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("0123456789ABC", new float[]{1.0f, 0.0f});

        ConstructiveMemoryPersistenceRelay relay = new ConstructiveMemoryPersistenceRelay(target, vectors::get, 0.70f);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        signal.candidates().add(createResult("0123456789ABC", 0.85f, SynapticHeaderConstants.FLAG_SIMULATED));
        signal.candidates().add(createResult("0123456789LOW", 0.50f, SynapticHeaderConstants.FLAG_SIMULATED));
        signal.candidates().add(createResult("0123456789NRM", 0.95f, (byte) 0)); // Not simulated

        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        org.mockito.ArgumentCaptor<com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader> headerCaptor =
                org.mockito.ArgumentCaptor.forClass(com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader.class);
        verify(target, times(1)).ingestCognitiveWithHeader(
                anyString(), anyString(), any(float[].class), eq(MemoryType.EPISODIC), any(), eq(MemorySource.INFERRED), headerCaptor.capture());

        var capturedHeader = headerCaptor.getValue();
        assertThat(SynapticHeaderConstants.isSimulated(capturedHeader.consolidationFlags())).isTrue();
        assertThat(capturedHeader.soulVersion()).isEqualTo((short) 5);
    }

    @Test
    void belowThresholdSimulation_isNotPersisted() {
        RememberPathway target = mock(RememberPathway.class);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("0123456789ABC", new float[]{1.0f, 0.0f});

        ConstructiveMemoryPersistenceRelay relay = new ConstructiveMemoryPersistenceRelay(target, vectors::get, 0.70f);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        signal.candidates().add(createResult("0123456789ABC", 0.65f, SynapticHeaderConstants.FLAG_SIMULATED));

        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        verify(target, never()).ingestCognitiveWithHeader(
                anyString(), anyString(), any(float[].class), any(), any(), any(), any());
    }

    private static CognitiveResult createResult(String id, float score, byte consolidationFlags) {
        return new CognitiveResult(
                id,
                "Simulated memory text " + id,
                score,
                1.0f,
                0.0f,
                0,
                (byte) 0,
                MemoryType.EPISODIC,
                MemorySource.INFERRED,
                new String[]{"simulated", "counterfactual"},
                1.0f,
                1.0f,
                CognitiveResult.RetrievalMode.STANDARD,
                null,
                null,
                com.spectrayan.spector.memory.model.SourceModality.TEXT,
                Map.of("simulation", "counterfactual_recombination"),
                consolidationFlags
        );
    }
}
