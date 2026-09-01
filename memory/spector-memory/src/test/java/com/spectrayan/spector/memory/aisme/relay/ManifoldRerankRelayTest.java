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

import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ManifoldRerankRelay}.
 */
class ManifoldRerankRelayTest {

    @Test
    void relayName_isManifoldRerank() {
        ManifoldRerankRelay relay = new ManifoldRerankRelay(null, null);
        assertThat(relay.relayName()).isEqualTo("manifold-rerank");
    }

    @Test
    void unconfigured_isPassThrough() {
        ManifoldRerankRelay relay = new ManifoldRerankRelay(null, null);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        CognitiveResult item = createResult("m1", 0.5f);
        signal.candidates().add(item);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isEqualTo(0.5f);
    }

    @Test
    void configured_modulatesScoresWithManifoldSimilarity() {
        CognitiveManifold manifold = new CognitiveManifold(2);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.0f}); // exact match to query
        vectors.put("m2", new float[]{10.0f, 10.0f}); // distant from query

        ManifoldRerankRelay relay = new ManifoldRerankRelay(manifold, vectors::get, 2.0f, 0.5f);

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
        assertThat(score1).isGreaterThan(0.5f);
    }

    private static CognitiveResult createResult(String id, float score) {
        return new CognitiveResult(
                id,
                "memory " + id,
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
