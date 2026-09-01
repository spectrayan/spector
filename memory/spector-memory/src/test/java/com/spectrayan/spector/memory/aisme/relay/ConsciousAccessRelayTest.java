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

import com.spectrayan.spector.memory.aisme.workspace.GlobalWorkspace;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit tests for {@link ConsciousAccessRelay}.
 */
class ConsciousAccessRelayTest {

    @Test
    void relayName_isConsciousAccess() {
        ConsciousAccessRelay relay = new ConsciousAccessRelay(null);
        assertThat(relay.relayName()).isEqualTo("conscious-access");
    }

    @Test
    void unconfigured_isPassThrough() {
        ConsciousAccessRelay relay = new ConsciousAccessRelay(null);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        CognitiveResult item = createResult("m1", 0.5f);
        signal.candidates().add(item);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates()).hasSize(1);
    }

    @Test
    void configured_gatesCandidatesToCapacity() {
        GlobalWorkspace workspace = new GlobalWorkspace(2);
        ConsciousAccessRelay relay = new ConsciousAccessRelay(workspace);

        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());
        signal.candidates().add(createResult("m1", 0.1f));
        signal.candidates().add(createResult("m2", 0.9f));
        signal.candidates().add(createResult("m3", 0.5f));

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates()).hasSize(2);
        assertThat(signal.candidates().get(0).id()).isEqualTo("m2");
        assertThat(signal.candidates().get(1).id()).isEqualTo("m3");
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
