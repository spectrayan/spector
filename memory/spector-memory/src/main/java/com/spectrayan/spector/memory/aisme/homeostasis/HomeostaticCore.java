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
package com.spectrayan.spector.memory.aisme.homeostasis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Neural ODE engine for continuous affective dynamics.
 *
 * <h3>Biological Analog: Hypothalamic Homeostatic Regulation</h3>
 * <p>Just as the hypothalamus continuously integrates internal and external signals
 * to maintain physiological equilibrium (temperature, hunger, sleep cycles), this
 * engine governs the continuous emotional and interoceptive state of an agent.
 * It applies differential equations to drag the agent's affective state back toward
 * equilibrium, perturbed by external inputs, memory recall, and stochastic noise.</p>
 */
public final class HomeostaticCore {

    private static final Logger log = LoggerFactory.getLogger(HomeostaticCore.class);

    private final ReentrantLock lock = new ReentrantLock();

    private final float[][] aPerson;
    private final float[][] bInput;
    private final float[][] cRecall;
    private final float[] sigma;

    private InteroceptiveState currentState;

    /**
     * Constructs a new HomeostaticCore.
     *
     * @param aPerson Regulation dynamics matrix (A)
     * @param bInput External input influence matrix (B)
     * @param cRecall Memory recall influence matrix (C)
     * @param sigma Stochastic noise scaling vector (σ)
     */
    public HomeostaticCore(float[][] aPerson, float[][] bInput, float[][] cRecall, float[] sigma) {
        this.aPerson = aPerson;
        this.bInput = bInput;
        this.cRecall = cRecall;
        this.sigma = sigma;
        this.currentState = InteroceptiveState.NEUTRAL;
    }

    /**
     * Advances the emotional state using an Euler step for the differential equation:
     * h(t+dt) = h(t) + dt * (A·h(t) + B·u(t) + C·recall(t) + σ·w(t))
     *
     * @param externalInput The external stimuli vector u(t)
     * @param recallInfluence The memory recall influence vector recall(t)
     * @param dt The time step delta
     */
    public void step(float[] externalInput, float[] recallInfluence, float dt) {
        lock.lock();
        try {
            float[] h = currentState.toVector();
            int dim = h.length;
            float[] nextH = new float[dim];

            for (int i = 0; i < dim; i++) {
                float aTerm = 0;
                for (int j = 0; j < dim; j++) {
                    aTerm += aPerson[i][j] * h[j];
                }

                float bTerm = 0;
                if (externalInput != null) {
                    int bDim = Math.min(dim, bInput[i].length);
                    for (int j = 0; j < bDim; j++) {
                        if (j < externalInput.length) {
                            bTerm += bInput[i][j] * externalInput[j];
                        }
                    }
                }

                float cTerm = 0;
                if (recallInfluence != null) {
                    int cDim = Math.min(dim, cRecall[i].length);
                    for (int j = 0; j < cDim; j++) {
                        if (j < recallInfluence.length) {
                            cTerm += cRecall[i][j] * recallInfluence[j];
                        }
                    }
                }

                float noise = (float) ThreadLocalRandom.current().nextGaussian() * sigma[i];

                nextH[i] = h[i] + dt * (aTerm + bTerm + cTerm + noise);
                
                // Clamp all state values to [-1, 1]
                nextH[i] = Math.max(-1.0f, Math.min(1.0f, nextH[i]));
            }

            InteroceptiveState oldState = currentState;
            currentState = InteroceptiveState.fromVector(
                    nextH, System.currentTimeMillis(), oldState.version() + 1
            ).clamp();

            if (log.isTraceEnabled()) {
                log.trace("Homeostatic step: dt={}, old_state={}, new_state={}", dt, oldState, currentState);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Convenience step method without recall influence.
     *
     * @param externalInput The external stimuli vector
     * @param dt The time step delta
     */
    public void step(float[] externalInput, float dt) {
        step(externalInput, null, dt);
    }

    /**
     * Returns the current InteroceptiveState (an immutable snapshot).
     *
     * @return The current state
     */
    public InteroceptiveState currentState() {
        lock.lock();
        try {
            return currentState;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the core to equilibrium.
     */
    public void reset() {
        lock.lock();
        try {
            currentState = InteroceptiveState.NEUTRAL;
            log.debug("Homeostatic core reset to neutral equilibrium.");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the core to the specified state.
     *
     * @param initial The state to reset to
     */
    public void reset(InteroceptiveState initial) {
        lock.lock();
        try {
            currentState = initial;
            log.debug("Homeostatic core reset to specified state: {}", initial);
        } finally {
            lock.unlock();
        }
    }
}
