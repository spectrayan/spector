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
package com.spectrayan.spector.memory.aisme.continuity;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.relay.SoftIdentityAnchorRelay;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.reflect.relay.ReflectSignal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Unit tests for {@link SoftIdentityAnchorRelay}.
 */
class SoftIdentityAnchorRelayTest {

    @Test
    @DisplayName("Relay name matches constant SOFT_IDENTITY_ANCHOR")
    void relayName_matchesConstant() {
        SoftIdentityAnchorRelay relay = new SoftIdentityAnchorRelay();
        assertThat(relay.relayName()).isEqualTo(RelayNames.SOFT_IDENTITY_ANCHOR);
    }

    @Test
    @DisplayName("transmit with disabled soft identity anchor returns true without modifying prior")
    void transmit_whenDisabled_skipsRestoration() {
        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("test-agent")
                .purposeEmbedding(new float[]{1.0f, 0.0f, 0.0f, 0.0f})
                .build();
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, 4);
        MentalStateTracker tracker = new MentalStateTracker(model);

        // Perturb prior
        tracker.adaptPriorMean(new float[]{0.0f, 1.0f, 0.0f, 0.0f}, 0.5f);
        float[] beforePrior = tracker.selfModel().priorMean().clone();

        ReflectSignal signal = ReflectSignal.builder()
                .mentalStateTracker(tracker)
                .softIdentityAnchorEnabled(false)
                .build();

        SoftIdentityAnchorRelay relay = new SoftIdentityAnchorRelay();
        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        assertThat(tracker.selfModel().priorMean()).containsExactly(beforePrior);
    }

    @Test
    @DisplayName("transmit applies restoring force toward core anchor on Riemannian cognitive manifold")
    void transmit_whenEnabled_restoresPriorTowardCoreAnchor() {
        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("test-agent")
                .purposeEmbedding(new float[]{1.0f, 0.0f, 0.0f, 0.0f})
                .build();
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, 4);
        MentalStateTracker tracker = new MentalStateTracker(model);
        CognitiveManifold manifold = new CognitiveManifold(4);

        // Initial core anchor is [1.0, 0.0, 0.0, 0.0]
        assertThat(tracker.coreAnchor().corePriorMean()).containsExactly(1.0f, 0.0f, 0.0f, 0.0f);

        // Perturb current prior to [0.5, 0.5, 0.0, 0.0]
        tracker.adaptPriorMean(new float[]{0.0f, 1.0f, 0.0f, 0.0f}, 0.5f);
        assertThat(tracker.selfModel().priorMean()[0]).isEqualTo(0.5f);
        assertThat(tracker.selfModel().priorMean()[1]).isEqualTo(0.5f);

        ReflectSignal signal = ReflectSignal.builder()
                .mentalStateTracker(tracker)
                .cognitiveManifold(manifold)
                .softIdentityAnchorEnabled(true)
                .identityAnchorEta(0.2f) // 20% pull for clear test assertion
                .identityLyapunovThreshold(0.7f)
                .build();

        SoftIdentityAnchorRelay relay = new SoftIdentityAnchorRelay();
        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();

        // Expected: (1 - 0.2) * 0.5 + 0.2 * 1.0 = 0.4 + 0.2 = 0.6 for dim 0
        // Expected: (1 - 0.2) * 0.5 + 0.2 * 0.0 = 0.4 for dim 1
        float[] afterPrior = tracker.selfModel().priorMean();
        assertThat(afterPrior[0]).isCloseTo(0.6f, org.assertj.core.data.Offset.offset(1e-5f));
        assertThat(afterPrior[1]).isCloseTo(0.4f, org.assertj.core.data.Offset.offset(1e-5f));

        assertThat(signal.identityAnchorDistance()).isGreaterThan(0.0f);
        assertThat(signal.identityContinuityScore()).isBetween(0.0f, 1.0f);
        assertThat(signal.identityLyapunovStable()).isTrue();
    }

    @Test
    @DisplayName("transmit detects Lyapunov basin breach when drift exceeds threshold")
    void transmit_whenDriftExceedsThreshold_marksLyapunovUnstable() {
        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("test-agent")
                .purposeEmbedding(new float[]{1.0f, 0.0f, 0.0f, 0.0f})
                .build();
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, 4);
        MentalStateTracker tracker = new MentalStateTracker(model);
        CognitiveManifold manifold = new CognitiveManifold(4);

        // Perturb significantly
        tracker.adaptPriorMean(new float[]{0.0f, 5.0f, 0.0f, 0.0f}, 0.9f);

        ReflectSignal signal = ReflectSignal.builder()
                .mentalStateTracker(tracker)
                .cognitiveManifold(manifold)
                .softIdentityAnchorEnabled(true)
                .identityAnchorEta(0.01f)
                .identityLyapunovThreshold(0.15f) // Tight threshold
                .build();

        SoftIdentityAnchorRelay relay = new SoftIdentityAnchorRelay();
        boolean ok = relay.transmit(signal);

        assertThat(ok).isTrue();
        assertThat(signal.identityAnchorDistance()).isGreaterThan(0.15f);
        assertThat(signal.identityLyapunovStable()).isFalse();
    }
}
