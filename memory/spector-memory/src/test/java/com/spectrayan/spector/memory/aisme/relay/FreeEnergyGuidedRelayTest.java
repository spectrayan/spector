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

import com.spectrayan.spector.memory.aisme.fegr.FreeEnergyCalculator;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link FreeEnergyGuidedRelay}.
 */
class FreeEnergyGuidedRelayTest {

    @Test
    void relayName_isFreeEnergyGuided() {
        FreeEnergyGuidedRelay relay = new FreeEnergyGuidedRelay(null, null, null, null, null);
        assertThat(relay.relayName()).isEqualTo("free-energy-guided");
    }

    @Test
    void unconfigured_isPassThrough() {
        FreeEnergyGuidedRelay relay = new FreeEnergyGuidedRelay(null, null, null, null, null);
        RecallSignal signal = RecallSignal.forTextQuery("test query", RecallOptions.builder().build());

        CognitiveResult item = createResult("m1", 0.75f);
        signal.candidates().add(item);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isEqualTo(0.75f);
    }

    @Test
    void configured_withVectorQueryAndEmbeddingLookup_appliesFersScoring() {
        int dim = 2;
        GenerativeSelfModel selfModel = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.BALANCED, dim);
        MentalStateTracker tracker = new MentalStateTracker(selfModel);
        FreeEnergyCalculator calculator = new FreeEnergyCalculator();

        Map<String, float[]> memoryVectors = new HashMap<>();
        memoryVectors.put("m1", new float[]{1.0f, 1.0f});

        FreeEnergyGuidedRelay relay = new FreeEnergyGuidedRelay(
                tracker, calculator, null, null, memoryVectors::get);

        float[] queryVec = {1.0f, 1.0f};
        RecallSignal signal = RecallSignal.forVectorQuery(queryVec, RecallOptions.builder().build());
        signal.setQueryVector(queryVec);

        CognitiveResult item = createResult("m1", 0.5f);
        signal.candidates().add(item);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();

        // The score should be modified according to FERS and include breakdown
        CognitiveResult result = signal.candidates().get(0);
        assertThat(result.score()).isNotEqualTo(0.5f);
        assertThat(result.breakdown()).isNotNull();
        assertThat(result.breakdown().epistemicWeight()).isEqualTo(0.5f);
        assertThat(result.breakdown().teleologicalWeight()).isEqualTo(0.35f);
        assertThat(result.breakdown().pragmaticWeight()).isEqualTo(0.15f);
        assertThat(result.breakdown().scoringRegime()).isEqualTo(com.spectrayan.spector.memory.model.ScoringRegime.GENERIC);
    }

    private static CognitiveResult createResult(String id, float score) {
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
                Map.of()
        );
    }
}
