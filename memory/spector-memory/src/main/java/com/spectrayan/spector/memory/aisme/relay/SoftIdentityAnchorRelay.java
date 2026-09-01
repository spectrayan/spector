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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.reflect.relay.ReflectSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synaptic relay executing the Soft Identity Anchor control law during biological sleep reflection.
 *
 * <h3>Biological Analog: Multi-Epoch Insular-Prefrontal Restoring Force</h3>
 * <p>Applies an infinitesimal homeostatic restorative pull \(\boldsymbol{p}_{t+1} \leftarrow (1-\eta)\boldsymbol{p}_t + \eta \boldsymbol{p}_{\text{core}}\)
 * back toward the entity's foundational core identity attractor, guaranteeing Lyapunov trajectory stability
 * and eliminating autobiographical drift over multi-decade horizons.</p>
 */
public final class SoftIdentityAnchorRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(SoftIdentityAnchorRelay.class);

    private final float defaultEta;
    private final float defaultThreshold;

    /**
     * Constructs a SoftIdentityAnchorRelay with default parameters.
     */
    public SoftIdentityAnchorRelay() {
        this(0.0001f, 0.15f);
    }

    /**
     * Constructs a SoftIdentityAnchorRelay with custom restorative parameters.
     *
     * @param defaultEta fallback learning rate \(\eta_{\text{anchor}}\)
     * @param defaultThreshold fallback Lyapunov distance threshold
     */
    public SoftIdentityAnchorRelay(float defaultEta, float defaultThreshold) {
        this.defaultEta = defaultEta > 0.0f ? defaultEta : 0.0001f;
        this.defaultThreshold = defaultThreshold > 0.0f ? defaultThreshold : 0.15f;
    }

    @Override
    public String relayName() {
        return RelayNames.SOFT_IDENTITY_ANCHOR;
    }

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal == null || !signal.softIdentityAnchorEnabled()) {
            return true;
        }

        MentalStateTracker tracker = signal.mentalStateTracker();
        if (tracker == null || tracker.coreAnchor() == null) {
            return true;
        }

        try {
            float eta = signal.identityAnchorEta() > 0.0f ? signal.identityAnchorEta() : defaultEta;
            float threshold = signal.identityLyapunovThreshold() > 0.0f ? signal.identityLyapunovThreshold() : defaultThreshold;

            // 1. Apply Soft Identity Anchor Lyapunov restoring pull
            tracker.applyIdentityAnchorRestoration(eta);

            // 2. Measure post-restoration metric distance on Riemannian cognitive manifold
            CognitiveManifold manifold = signal.cognitiveManifold();
            PersonalMetricTensor tensor = (manifold != null) ? manifold.currentTensor() : null;

            float dM = tracker.computeManifoldDistanceToAnchor(tensor);
            float continuity = tracker.computeContinuityScore(tensor, 1.0f);
            boolean stable = tracker.isWithinLyapunovBasin(tensor, threshold);

            signal.setIdentityAnchorDistance(dM);
            signal.setIdentityContinuityScore(continuity);
            signal.setIdentityLyapunovStable(stable);

            log.info("SoftIdentityAnchor: applied Lyapunov restoring force (eta={}, dM={}, continuity={}, stable={})",
                    eta, String.format("%.4f", dM), String.format("%.4f", continuity), stable);

            if (!stable) {
                log.warn("SoftIdentityAnchor: identity trajectory exceeded Lyapunov basin threshold! (dM={} > limit={})",
                        dM, threshold);
            }
        } catch (final RuntimeException e) {
            log.warn("Failed to apply soft identity anchor restoring force during reflection: {}", e.getMessage(), e);
        }

        return true;
    }
}
