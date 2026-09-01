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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.phi.ConsciousnessContinuityEvaluator;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ConsciousnessContinuityRelay}.
 */
class ConsciousnessContinuityRelayTest {

    @Test
    void relayName_isConsciousnessContinuity() {
        ConsciousnessContinuityRelay relay = new ConsciousnessContinuityRelay(null, null);
        assertThat(relay.relayName()).isEqualTo("consciousness-continuity");
    }

    @Test
    void unconfigured_isPassThrough() {
        ConsciousnessContinuityRelay relay = new ConsciousnessContinuityRelay(null, null);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        CognitiveResult item = createResult("m1", 0.5f);
        signal.candidates().add(item);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isEqualTo(0.5f);
    }

    @Test
    void configured_modulatesCandidateScores() {
        ConsciousnessContinuityEvaluator evaluator = new ConsciousnessContinuityEvaluator(null);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.0f});
        vectors.put("m2", new float[]{0.9f, 0.1f});

        ConsciousnessContinuityRelay relay = new ConsciousnessContinuityRelay(evaluator, vectors::get, 0.5f);

        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());
        signal.candidates().add(createResult("m1", 0.5f));
        signal.candidates().add(createResult("m2", 0.5f));

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isGreaterThanOrEqualTo(0.5f);
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
