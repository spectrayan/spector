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
package com.spectrayan.spector.memory.aisme.fegr;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe manager maintaining the continuous approximate posterior belief state q(s_t)
 * across conversational turns and environmental observations.
 *
 * <h3>Biological Analog: Continuous Prefrontal & Insular Latent State Tracking</h3>
 * <p>Maintains the active internal state representation, updating expectations with incoming
 * observations via precision weighting and decaying unreinforced states back toward baseline priors.</p>
 */
public final class MentalStateTracker {

    private static final Logger log = LoggerFactory.getLogger(MentalStateTracker.class);

    private final ReentrantLock lock = new ReentrantLock();
    private volatile GenerativeSelfModel selfModel;
    private MentalStatePosterior currentPosterior;

    /**
     * Constructs a MentalStateTracker for a given GenerativeSelfModel.
     *
     * @param selfModel the generative self-model providing priors and observation parameters
     */
    public MentalStateTracker(GenerativeSelfModel selfModel) {
        if (selfModel == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "GenerativeSelfModel must not be null");
        }
        this.selfModel = selfModel;
        this.currentPosterior = selfModel.createInitialPosterior(System.currentTimeMillis());
    }

    /**
     * Constructs a MentalStateTracker with an explicit initial posterior state.
     *
     * @param selfModel the generative self-model
     * @param initialPosterior the initial posterior belief state
     */
    public MentalStateTracker(GenerativeSelfModel selfModel, MentalStatePosterior initialPosterior) {
        if (selfModel == null || initialPosterior == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        if (selfModel.dimensions() != initialPosterior.dimensions()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "SelfModel and initial posterior dimension mismatch");
        }
        this.selfModel = selfModel;
        this.currentPosterior = initialPosterior;
    }

    /**
     * Updates the current posterior belief given an incoming sensory observation vector.
     *
     * @param observation sensory observation vector
     * @param timestampMs current epoch timestamp in milliseconds
     */
    public void updateWithObservation(float[] observation, long timestampMs) {
        updateWithObservation(observation, selfModel.observationPrecision(), timestampMs);
    }

    /**
     * Updates the current posterior belief given an incoming observation with explicit precision weighting.
     *
     * @param observation sensory observation vector
     * @param obsPrecision precision weighting vector for the observation
     * @param timestampMs current epoch timestamp in milliseconds
     */
    public void updateWithObservation(float[] observation, float[] obsPrecision, long timestampMs) {
        if (observation == null || obsPrecision == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Observation and precision vectors must not be null");
        }
        if (observation.length != selfModel.dimensions() || obsPrecision.length != selfModel.dimensions()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Observation dimension mismatch with model");
        }

        lock.lock();
        try {
            MentalStatePosterior oldPosterior = currentPosterior;
            currentPosterior = currentPosterior.withEvidence(
                    observation,
                    obsPrecision,
                    timestampMs,
                    oldPosterior.version() + 1
            );

            if (log.isTraceEnabled()) {
                log.trace("Updated posterior from version {} to {}", oldPosterior.version(), currentPosterior.version());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Applies temporal decay to the posterior, gently shifting expectations back toward baseline prior p(s|m).
     *
     * @param currentTimestampMs current timestamp
     * @param decayFactor decay factor in [0, 1]
     */
    public void decay(long currentTimestampMs, float decayFactor) {
        lock.lock();
        try {
            currentPosterior = currentPosterior.decayTowards(
                    selfModel.priorMean(),
                    selfModel.priorPrecision(),
                    decayFactor,
                    currentTimestampMs,
                    currentPosterior.version() + 1
            );
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an immutable snapshot of the current posterior distribution
     */
    public MentalStatePosterior currentPosterior() {
        lock.lock();
        try {
            return currentPosterior;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the underlying GenerativeSelfModel
     */
    public GenerativeSelfModel selfModel() {
        return selfModel;
    }

    /**
     * Adapts the generative prior mean vector towards an experiential centroid (e.g. during sleep reflection).
     *
     * @param targetCentroid target experiential centroid vector
     * @param learningRate plasticity learning rate \eta
     */
    public void adaptPriorMean(float[] targetCentroid, float learningRate) {
        if (targetCentroid == null || targetCentroid.length != selfModel.dimensions()) {
            return;
        }
        lock.lock();
        try {
            this.selfModel = this.selfModel.withAdaptedPriorMean(targetCentroid, learningRate);
            if (log.isDebugEnabled()) {
                log.debug("Adapted generative prior mean with learning rate {}", learningRate);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the posterior belief state back to the prior baseline.
     */
    public void reset() {
        lock.lock();
        try {
            currentPosterior = selfModel.createInitialPosterior(System.currentTimeMillis());
            log.debug("Reset mental state posterior to generative prior baseline");
        } finally {
            lock.unlock();
        }
    }
}
