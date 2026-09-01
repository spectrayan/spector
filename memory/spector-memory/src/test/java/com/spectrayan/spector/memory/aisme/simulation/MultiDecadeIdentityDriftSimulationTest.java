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
package com.spectrayan.spector.memory.aisme.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.continuity.CoreIdentityAnchor;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;
import com.spectrayan.spector.memory.aisme.relay.SoftIdentityAnchorRelay;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

/**
 * High-fidelity 10,000-epoch simulation benchmark verifying Identity Trajectory Lyapunov Stability
 * and the Soft Identity Anchor control law across multi-decade synthetic timelines (50–150 virtual years).
 *
 * <p>Validates that while unanchored generative priors experience unbounded Brownian divergence over
 * 10,000 sleep consolidation epochs, the Soft Identity Anchor restoring force bounds the trajectory
 * into a compact Ornstein-Uhlenbeck attractor basin, preserving longitudinal continuity \(C(0, 10000) \ge 0.90\).</p>
 */
class MultiDecadeIdentityDriftSimulationTest {

    private static final int DIMENSIONS = 16;
    private static final int EPOCHS = 10_000;
    private static final float EXP_LEARNING_RATE = 0.01f;    // Daily autobiographical plasticity (\eta_exp)
    private static final float ANCHOR_ETA = 0.002f;           // Restoring force (\eta_anchor)
    private static final float LYAPUNOV_THRESHOLD = 0.15f;    // Maximum allowable manifold basin radius

    @Test
    @DisplayName("Simulate 10,000 sleep epochs: Anchored vs Unanchored Identity Trajectory")
    void simulate10000Epochs_verifiesLyapunovAttractorStability() {
        // Setup initial foundational identity embedding
        float[] foundationalEmbedding = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            foundationalEmbedding[i] = (float) Math.sin((i + 1) * 0.5);
        }

        // 1. Unanchored Tracker (Control)
        AgentSoul soulControl = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("unanchored-agent")
                .purposeEmbedding(foundationalEmbedding)
                .build();
        GenerativeSelfModel modelControl = GenerativeSelfModel.fromSoulAndProfile(soulControl, CognitiveProfile.BALANCED, DIMENSIONS);
        MentalStateTracker trackerControl = new MentalStateTracker(modelControl);

        // 2. Anchored Tracker (Soft Identity Anchor)
        AgentSoul soulAnchored = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("anchored-agent")
                .purposeEmbedding(foundationalEmbedding)
                .build();
        GenerativeSelfModel modelAnchored = GenerativeSelfModel.fromSoulAndProfile(soulAnchored, CognitiveProfile.BALANCED, DIMENSIONS);
        MentalStateTracker trackerAnchored = new MentalStateTracker(modelAnchored);

        CognitiveManifold manifold = new CognitiveManifold(DIMENSIONS);
        PersonalMetricTensor tensor = manifold.currentTensor();
        SoftIdentityAnchorRelay anchorRelay = new SoftIdentityAnchorRelay(ANCHOR_ETA, LYAPUNOV_THRESHOLD);

        Random random = new Random(42L); // Deterministic seed

        float maxAnchoredDistance = 0.0f;
        int lyapunovViolations = 0;

        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            // Generate stochastic daily sensory disturbance \epsilon ~ N(0, 0.02) with slow external drift
            float[] dailyDisturbance = new float[DIMENSIONS];
            for (int d = 0; d < DIMENSIONS; d++) {
                dailyDisturbance[d] = (float) (random.nextGaussian() * 0.02 + 0.008 * Math.cos(epoch * 0.001 + d));
            }

            // A. Update control (unconstrained random walk with experiential drift)
            float[] dailyCentroidControl = new float[DIMENSIONS];
            for (int d = 0; d < DIMENSIONS; d++) {
                dailyCentroidControl[d] = trackerControl.selfModel().priorMean()[d] + dailyDisturbance[d];
            }
            trackerControl.adaptPriorMean(dailyCentroidControl, EXP_LEARNING_RATE);

            // B. Update anchored entity (plastic adaptation + Soft Identity Anchor Lyapunov restoration)
            float[] dailyCentroidAnchored = new float[DIMENSIONS];
            for (int d = 0; d < DIMENSIONS; d++) {
                dailyCentroidAnchored[d] = trackerAnchored.selfModel().priorMean()[d] + dailyDisturbance[d];
            }
            trackerAnchored.adaptPriorMean(dailyCentroidAnchored, EXP_LEARNING_RATE);

            ReflectSignal signal = ReflectSignal.builder()
                    .mentalStateTracker(trackerAnchored)
                    .cognitiveManifold(manifold)
                    .softIdentityAnchorEnabled(true)
                    .identityAnchorEta(ANCHOR_ETA)
                    .identityLyapunovThreshold(LYAPUNOV_THRESHOLD)
                    .build();

            anchorRelay.transmit(signal);

            float dM = signal.identityAnchorDistance();
            if (dM > maxAnchoredDistance) {
                maxAnchoredDistance = dM;
            }
            if (!signal.identityLyapunovStable()) {
                lyapunovViolations++;
            }
        }

        // Evaluate Final Metrics after 10,000 epochs (50+ virtual years)
        CoreIdentityAnchor coreAnchor = trackerAnchored.coreAnchor();

        float finalControlDistance = coreAnchor.computeManifoldDistance(trackerControl.selfModel().priorMean(), tensor);
        float finalControlContinuity = coreAnchor.computeContinuityScore(trackerControl.selfModel().priorMean(), tensor, 1.0f);

        float finalAnchoredDistance = coreAnchor.computeManifoldDistance(trackerAnchored.selfModel().priorMean(), tensor);
        float finalAnchoredContinuity = coreAnchor.computeContinuityScore(trackerAnchored.selfModel().priorMean(), tensor, 1.0f);

        // Assertions:
        // 1. Control condition suffers severe unbounded drift
        assertThat(finalControlDistance)
                .as("Unanchored identity experiences significant drift")
                .isGreaterThan(0.35f);
        assertThat(finalControlContinuity)
                .as("Unanchored identity continuity degrades")
                .isLessThan(0.75f);

        // 2. Anchored condition is strictly bounded in the Lyapunov basin
        assertThat(finalAnchoredDistance)
                .as("Anchored identity distance to core anchor is tightly bounded")
                .isLessThan(LYAPUNOV_THRESHOLD);

        assertThat(maxAnchoredDistance)
                .as("Maximum observed distance across 10,000 epochs never exceeded Lyapunov threshold")
                .isLessThan(LYAPUNOV_THRESHOLD);

        assertThat(lyapunovViolations)
                .as("Zero Lyapunov stability violations over 10,000 epochs")
                .isEqualTo(0);

        assertThat(finalAnchoredContinuity)
                .as("Longitudinal continuity invariant C(0, 10000) >= 0.90 holds over 10,000 epochs")
                .isGreaterThanOrEqualTo(0.90f);
    }
}
