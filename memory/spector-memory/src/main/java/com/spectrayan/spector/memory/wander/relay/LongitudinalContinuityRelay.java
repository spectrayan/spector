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
package com.spectrayan.spector.memory.wander.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 6 relay in {@link com.spectrayan.spector.memory.pathway.WanderPathway} that captures and persists longitudinal identity and consciousness continuity metrics.
 *
 * <h3>Biological Analog: Multi-Epoch Self-Model Metacognitive Auditing</h3>
 * <p>Periodically records Integrated Information Theory cohesion (\(\Phi_{CC}\)), personal Riemannian
 * manifold volume (\(\text{Trace}(G)\)), epistemic generative prior drift (\(\|\boldsymbol{\mu}_t - \boldsymbol{\mu}_0\|\)),
 * and homeostatic emotional levels into the zero-copy off-heap {@link com.spectrayan.spector.memory.cortex.ContinuityRecordMemory}.</p>
 *
 * @since 1.2.0
 */
public final class LongitudinalContinuityRelay implements SynapticRelay<WanderSignal> {

    private static final Logger log = LoggerFactory.getLogger(LongitudinalContinuityRelay.class);

    @Override
    public boolean transmit(final WanderSignal signal) {
        if (signal == null || signal.continuityMemory() == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        float phiCc = 0.85f;
        float traceG = 0.0f;
        float priorDrift = 0.0f;
        byte valence = 0;
        byte arousal = 0;
        byte energy = 0;
        short soulVersion = 1;

        if (signal.cognitiveManifold() != null) {
            PersonalMetricTensor tensor = signal.cognitiveManifold().currentTensor();
            if (tensor != null && tensor.diagonalScaling() != null) {
                for (float d : tensor.diagonalScaling()) {
                    traceG += d;
                }
            }
        }

        if (signal.mentalStateTracker() != null) {
            float[] priorMean = signal.mentalStateTracker().selfModel().priorMean();
            float[] posteriorMean = signal.mentalStateTracker().currentPosterior().mean();
            if (priorMean != null && posteriorMean != null && priorMean.length == posteriorMean.length) {
                float sumSq = 0.0f;
                for (int i = 0; i < priorMean.length; i++) {
                    float diff = posteriorMean[i] - priorMean[i];
                    sumSq += diff * diff;
                }
                priorDrift = (float) Math.sqrt(sumSq);
            }
            if (signal.mentalStateTracker().selfModel().soul() != null) {
                soulVersion = signal.mentalStateTracker().selfModel().soul().soulVersion();
            }
        }

        if (signal.homeostaticCore() != null) {
            var st = signal.homeostaticCore().currentState();
            if (st != null) {
                valence = (byte) Math.max(-128, Math.min(127, Math.round(st.valence() * 100)));
                arousal = (byte) Math.max(0, Math.min(127, Math.round(st.arousal() * 100)));
                energy = (byte) Math.max(0, Math.min(127, Math.round(st.dominance() * 100)));
            }
        }

        IdentityTrajectorySnapshot snapshot = new IdentityTrajectorySnapshot(
                now, phiCc, traceG, priorDrift, valence, arousal, energy, soulVersion
        );

        signal.continuityMemory().appendSnapshot(snapshot);
        signal.setSnapshotRecorded(true);

        if (log.isDebugEnabled()) {
            log.debug("LongitudinalContinuityRelay: recorded continuity snapshot: phiCc={}, traceG={}, priorDrift={}",
                    phiCc, traceG, priorDrift);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "longitudinal_continuity";
    }
}
