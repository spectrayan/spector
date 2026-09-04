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
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit and behavioral tests for {@link LateralInhibitionRelay} (MR-04).
 */
class LateralInhibitionRelayTest {

    @Test
    @DisplayName("MR-04: Feature off is bit-identical pass through")
    void disabledPassThrough() {
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.0f});
        vectors.put("m2", new float[]{1.0f, 0.0f}); // exact duplicate vector

        LateralInhibitionRelay relay = new LateralInhibitionRelay(vectors::get);

        RecallOptions opts = RecallOptions.builder()
                .enableLateralInhibition(false)
                .build();
        RecallSignal signal = RecallSignal.forTextQuery("test", opts);

        CognitiveResult r1 = createResult("m1", 0.9f, (byte) 0);
        CognitiveResult r2 = createResult("m2", 0.85f, (byte) 0);
        signal.setCandidates(List.of(r1, r2));

        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isEqualTo(0.9f);
        assertThat(signal.candidates().get(1).score()).isEqualTo(0.85f);
    }

    @Test
    @DisplayName("MR-04: Redundant cluster applies soft graded inhibition without dropping items")
    void redundantClusterAppliesSoftInhibition() {
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.0f});
        vectors.put("m2", new float[]{0.99f, 0.01f}); // cosine > 0.99 (above 0.88 threshold)

        LateralInhibitionRelay relay = new LateralInhibitionRelay(vectors::get);

        RecallOptions opts = RecallOptions.builder()
                .enableLateralInhibition(true)
                .lateralInhibitionThreshold(0.88f)
                .lateralInhibitionSoftKappa(0.20f)
                .build();
        RecallSignal signal = RecallSignal.forTextQuery("test", opts);

        CognitiveResult r1 = createResult("m1", 0.90f, (byte) 0);
        CognitiveResult r2 = createResult("m2", 0.80f, (byte) 0);
        signal.setCandidates(List.of(r1, r2));

        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        assertThat(signal.candidates()).hasSize(2);

        CognitiveResult winner = signal.candidates().get(0);
        CognitiveResult loser = signal.candidates().get(1);

        // Rank 1 in cluster has penalty 1.0 (unpenalized)
        assertThat(winner.score()).isEqualTo(0.90f);
        assertThat(winner.breakdown().inhibitionPenalty()).isEqualTo(1.0f);
        assertThat(winner.breakdown().competitorIds()).containsExactly("m2");

        // Rank 2 in cluster has penalty 1 - 0.20 * (1 - 1/2) = 0.90 -> score = 0.80 * 0.90 = 0.72
        assertThat(loser.score()).isLessThan(0.80f);
        assertThat(loser.breakdown().inhibitionPenalty()).isLessThan(1.0f);
        assertThat(loser.breakdown().competitorIds()).containsExactly("m1");
    }

    @Test
    @DisplayName("MR-04: Contradictory cluster arbitrates confidence and penalizes loser with hard kappa")
    void contradictoryClusterArbitratesConfidence() {
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.0f});
        vectors.put("m2", new float[]{1.0f, 0.0f}); // overlapping topic

        LateralInhibitionRelay relay = new LateralInhibitionRelay(vectors::get);

        RecallOptions opts = RecallOptions.builder()
                .enableLateralInhibition(true)
                .lateralInhibitionThreshold(0.88f)
                .lateralInhibitionHardKappa(0.40f)
                .build();
        RecallSignal signal = RecallSignal.forTextQuery("test", opts);

        byte flags = EncodingHeaderFields.FLAG_CONTRADICTED;
        // m1: fresh (age 1 day, high importance 9) -> high confidence
        CognitiveResult r1 = new CognitiveResult(
                "m1", "Server runs on port 8080", 0.95f, 9.0f, 1.0f, 5, (byte) 0,
                MemoryType.SEMANTIC, MemorySource.USER_STATED, new String[0], 1.0f, 1.0f,
                CognitiveResult.RetrievalMode.STANDARD, null, null, null, Map.of(), flags
        );

        // m2: stale (age 300 days, lower importance 3) -> lower confidence
        CognitiveResult r2 = new CognitiveResult(
                "m2", "Server runs on port 9090", 0.90f, 3.0f, 300.0f, 1, (byte) 0,
                MemoryType.SEMANTIC, MemorySource.USER_STATED, new String[0], 1.0f, 1.0f,
                CognitiveResult.RetrievalMode.STANDARD, null, null, null, Map.of(), flags
        );

        signal.setCandidates(List.of(r1, r2));

        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        CognitiveResult winner = signal.candidates().get(0);
        CognitiveResult loser = signal.candidates().get(1);

        assertThat(winner.breakdown().inhibitionPenalty()).isEqualTo(1.0f);
        assertThat(winner.breakdown().competitorIds()).containsExactly("m2");

        assertThat(loser.breakdown().inhibitionPenalty()).isLessThan(1.0f);
        assertThat(loser.score()).isLessThan(0.90f);
        assertThat(loser.breakdown().competitorIds()).containsExactly("m1");
    }

    private static CognitiveResult createResult(String id, float score, byte consolidationFlags) {
        return new CognitiveResult(
                id,
                "memory text for " + id,
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
                Map.of(),
                consolidationFlags
        );
    }
}
