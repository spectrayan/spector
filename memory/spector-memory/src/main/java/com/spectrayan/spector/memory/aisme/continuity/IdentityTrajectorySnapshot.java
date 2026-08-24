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

/**
 * Immutable value representation of a single neurocognitive identity and consciousness trajectory frame.
 *
 * <h3>Biological Analog: Multi-Epoch Self-Model Cohesion Snapshot</h3>
 * <p>Encapsulates the persona's Integrated Information Theory consciousness continuity score (\(\Phi_{CC}\)),
 * Riemannian cognitive manifold metric tensor volume (\(\text{Trace}(G)\)), epistemic generative prior drift
 * (\(\|\boldsymbol{\mu}_t - \boldsymbol{\mu}_0\|\)), homeostatic affective state, Riemannian distance to the core identity
 * anchor \(d_M(s_t, s_{\text{core}})\), and Lyapunov stability invariant status at a specific epoch.</p>
 *
 * @param timestamp epoch timestamp in milliseconds
 * @param phiCc Consciousness Continuity cohesion metric \(\in [0, 1]\)
 * @param traceG trace (volume) of the personal metric tensor \(G(s)\)
 * @param priorDrift Euclidean distance of current belief mean from initial generative prior \(\|\boldsymbol{\mu}_t - \boldsymbol{\mu}_0\|\)
 * @param valence emotional valence state \([-128, 127]\)
 * @param arousal physiological arousal state \([0, 127]\)
 * @param energy cognitive reserve / energy state \([0, 127]\)
 * @param soulVersion version identifier of the active identity
 * @param anchorDistance Riemannian distance \(d_M(s_t, s_{\text{core}})\) to the foundational core anchor
 * @param lyapunovStable whether the identity trajectory is bounded within the Lyapunov attractor basin
 */
public record IdentityTrajectorySnapshot(
        long timestamp,
        float phiCc,
        float traceG,
        float priorDrift,
        byte valence,
        byte arousal,
        byte energy,
        short soulVersion,
        float anchorDistance,
        boolean lyapunovStable
) {

    /**
     * Backward-compatible constructor for 8-parameter legacy callers.
     */
    public IdentityTrajectorySnapshot(
            long timestamp,
            float phiCc,
            float traceG,
            float priorDrift,
            byte valence,
            byte arousal,
            byte energy,
            short soulVersion
    ) {
        this(timestamp, phiCc, traceG, priorDrift, valence, arousal, energy, soulVersion, 0.0f, true);
    }

    /**
     * Compact constructor with validation.
     */
    public IdentityTrajectorySnapshot {
        if (timestamp < 0) {
            timestamp = System.currentTimeMillis();
        }
        if (Float.isNaN(phiCc)) {
            phiCc = 0.0f;
        }
        if (Float.isNaN(traceG)) {
            traceG = 0.0f;
        }
        if (Float.isNaN(priorDrift)) {
            priorDrift = 0.0f;
        }
        if (Float.isNaN(anchorDistance)) {
            anchorDistance = 0.0f;
        }
    }
}
