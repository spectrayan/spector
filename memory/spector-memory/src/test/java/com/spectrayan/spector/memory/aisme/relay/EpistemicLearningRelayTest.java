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

import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStatePosterior;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link EpistemicLearningRelay} — verifying active perception loop closure.
 */
class EpistemicLearningRelayTest {

    @Test
    void relayName_isEpistemicLearning() {
        EpistemicLearningRelay relay = new EpistemicLearningRelay(null, null, null);
        assertThat(relay.relayName()).isEqualTo(RelayNames.EPISTEMIC_LEARNING);
    }

    @Test
    void unconfigured_isPassThrough() {
        EpistemicLearningRelay relay = new EpistemicLearningRelay(null, null, null);
        RecallSignal signal = RecallSignal.forTextQuery("test query", RecallOptions.builder().build());
        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
    }

    @Test
    void transmit_updatesMentalStatePosterior_withObservationAndEvidence() {
        int dim = 2;
        GenerativeSelfModel selfModel = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.BALANCED, dim);
        MentalStateTracker tracker = new MentalStateTracker(selfModel);

        MentalStatePosterior initialPosterior = tracker.currentPosterior();
        assertThat(initialPosterior.version()).isEqualTo(0);

        Map<String, float[]> memoryVectors = new HashMap<>();
        memoryVectors.put("m1", new float[]{2.0f, 2.0f});
        memoryVectors.put("m2", new float[]{3.0f, 3.0f});

        EpistemicLearningRelay relay = new EpistemicLearningRelay(tracker, null, memoryVectors::get);

        RecallSignal signal = RecallSignal.forTextQuery("learning query", RecallOptions.builder().build());
        signal.setQueryVector(new float[]{1.0f, 1.0f});
        signal.candidates().add(createResult("m1", 0.9f, (byte) 50));
        signal.candidates().add(createResult("m2", 0.8f, (byte) 60));

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();

        MentalStatePosterior updatedPosterior = tracker.currentPosterior();
        assertThat(updatedPosterior.version()).isEqualTo(1);
        // Posterior mean should have shifted towards positive evidence (away from 0.0)
        assertThat(updatedPosterior.mean()[0]).isGreaterThan(0.0f);
        assertThat(updatedPosterior.mean()[1]).isGreaterThan(0.0f);
        // Posterior precision should have increased
        assertThat(updatedPosterior.precision()[0]).isGreaterThan(initialPosterior.precision()[0]);
    }

    @Test
    void transmit_stepsHomeostaticCore_fromQueryAndMemoryValence() {
        HomeostaticCore core = new HomeostaticCore();
        InteroceptiveState initial = core.currentState();

        EpistemicLearningRelay relay = new EpistemicLearningRelay(null, core, null);

        RecallSignal signal = RecallSignal.forTextQuery("affect query", RecallOptions.builder().minValence((byte) 64).build());
        signal.candidates().add(createResult("m1", 0.8f, (byte) 100));

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();

        InteroceptiveState updated = core.currentState();
        assertThat(updated.version()).isGreaterThanOrEqualTo(initial.version());
        assertThat(updated.epochMillis()).isGreaterThanOrEqualTo(initial.epochMillis());
    }

    private CognitiveResult createResult(String id, float score, byte valence) {
        return new CognitiveResult(
                id, "text-" + id, score, 0.8f, 1.0f, 1,
                valence, MemoryType.SEMANTIC, MemorySource.USER_STATED,
                new String[]{"tag"}, 1.0f, 1.0f, null, null, null, null, Map.of()
        );
    }
}
