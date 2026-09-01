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
import com.spectrayan.spector.memory.aisme.homeostasis.AffectiveResonanceScorer;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integrates the Homeostatic Affective Core into the RecallPathway relay chain.
 *
 * <p>This relay applies affective resonance as a bias to memory candidate results,
 * adjusting scores so that mood-congruent memories surface higher.</p>
 */
public final class HomeostaticBiasRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(HomeostaticBiasRelay.class);

    private final HomeostaticCore homeostaticCore;
    private final AffectiveResonanceScorer scorer;

    // Default configuration for the affective resonance bias
    private static final float DEFAULT_WEIGHT = 0.2f;
    private static final float DEFAULT_SIGMA = 0.5f;

    /**
     * Constructs a new HomeostaticBiasRelay.
     *
     * @param homeostaticCore the engine maintaining the continuous interoceptive state (can be null)
     * @param scorer the resonance scorer computing affective bias (can be null)
     */
    public HomeostaticBiasRelay(HomeostaticCore homeostaticCore, AffectiveResonanceScorer scorer) {
        this.homeostaticCore = homeostaticCore;
        this.scorer = scorer;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (homeostaticCore == null || scorer == null) {
            return true;
        }

        InteroceptiveState currentState = homeostaticCore.currentState();
        if (currentState != null) {
            scorer.applyBias(signal.candidates(), currentState, DEFAULT_WEIGHT, DEFAULT_SIGMA);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "homeostatic-bias";
    }
}
