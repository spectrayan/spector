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
package com.spectrayan.spector.memory.reflect;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;
import com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay;
import com.spectrayan.spector.memory.aisme.relay.SoftIdentityAnchorRelay;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.reflect.relay.ReflectSignal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * Unit tests for {@link ManifoldConsolidationRelay} and {@link SoftIdentityAnchorRelay} wiring in the reflect pathway.
 */
class ReflectPathwayAismeWiringTest {

    @Test
    void relayName_isManifoldConsolidation() {
        ManifoldConsolidationRelay relay = new ManifoldConsolidationRelay(null, null);
        assertThat(relay.relayName()).isEqualTo(RelayNames.MANIFOLD_CONSOLIDATION);
    }

    @Test
    void relayName_isSoftIdentityAnchor() {
        SoftIdentityAnchorRelay relay = new SoftIdentityAnchorRelay();
        assertThat(relay.relayName()).isEqualTo(RelayNames.SOFT_IDENTITY_ANCHOR);
    }

    @Test
    void transmit_withManifold_consolidatesMetricTensorVersion() {
        CognitiveManifold manifold = new CognitiveManifold(4);
        int initialVersion = manifold.currentTensor().version();

        List<float[]> samplePairs = List.of(new float[]{0.1f, 0.2f, 0.0f, 0.0f});
        ManifoldConsolidationRelay relay = new ManifoldConsolidationRelay(manifold, null, () -> samplePairs);
        ReflectSignal signal = ReflectSignal.builder().build();

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();

        PersonalMetricTensor updated = manifold.currentTensor();
        assertThat(updated.version()).isEqualTo(initialVersion + 1);
    }

    @Test
    void transmit_withSoftIdentityAnchor_appliesRestoration() {
        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("agent")
                .purposeEmbedding(new float[]{1.0f, 0.0f, 0.0f, 0.0f})
                .build();
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, 4);
        MentalStateTracker tracker = new MentalStateTracker(model);
        tracker.adaptPriorMean(new float[]{0.0f, 1.0f, 0.0f, 0.0f}, 0.1f);

        CognitiveManifold manifold = new CognitiveManifold(4);
        SoftIdentityAnchorRelay relay = new SoftIdentityAnchorRelay();

        ReflectSignal signal = ReflectSignal.builder()
                .mentalStateTracker(tracker)
                .cognitiveManifold(manifold)
                .softIdentityAnchorEnabled(true)
                .identityAnchorEta(0.1f)
                .identityLyapunovThreshold(0.25f)
                .build();

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.identityLyapunovStable()).isTrue();
        assertThat(signal.identityAnchorDistance()).isGreaterThan(0.0f);
    }
}
