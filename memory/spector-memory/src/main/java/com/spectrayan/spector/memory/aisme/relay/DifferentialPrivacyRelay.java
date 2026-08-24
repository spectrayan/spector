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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.privacy.DifferentialPrivacyEngine;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synaptic pathway relay executing Differential Privacy noise perturbation on continuous embedding vectors and scalar metrics.
 *
 * <h3>Biological Analog: Synaptic Noise Injection & Memory Generalization</h3>
 * <p>Applies \((\epsilon, \delta)\)-calibrated Gaussian noise to embedding representations
 * and Laplace noise to scalar importance metrics before long-term cortical persistence.</p>
 */
public final class DifferentialPrivacyRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(DifferentialPrivacyRelay.class);

    private final AismeConfig config;
    private final DifferentialPrivacyEngine engine;

    public DifferentialPrivacyRelay(AismeConfig config, DifferentialPrivacyEngine engine) {
        this.config = config;
        this.engine = engine;
    }

    @Override
    public String relayName() {
        return RelayNames.DIFFERENTIAL_PRIVACY;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (signal == null || config == null || engine == null) {
            return true;
        }

        if (config.enablePrivacy()) {
            // 1. Perturb embedding vector
            float[] vec = signal.vector();
            if (vec != null) {
                float[] noisyVec = engine.perturbVector(vec);
                signal.privacyPerturbedVector(noisyVec);
            }

            // 2. Perturb scalar importance
            if (signal.importance() > 0.0f) {
                float noisyImportance = engine.perturbScalar(signal.importance(), 1.0f);
                signal.importance(Math.max(0.0f, Math.min(1.0f, noisyImportance)));
            }

            log.debug("DifferentialPrivacyRelay: injected DP noise for signal id={}, consumedEpsilon={}",
                    signal.id(), engine.consumedEpsilon());
        }

        return true;
    }
}
