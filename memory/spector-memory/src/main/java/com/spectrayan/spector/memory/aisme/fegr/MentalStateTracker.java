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
import com.spectrayan.spector.memory.aisme.continuity.CoreIdentityAnchor;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe manager maintaining the continuous approximate posterior belief state q(s_t)
 * across conversational turns and environmental observations, anchored by a foundational {@link CoreIdentityAnchor}.
 *
 * <h3>Biological Analog: Continuous Prefrontal & Insular Latent State Tracking</h3>
 * <p>Maintains the active internal state representation, updating expectations with incoming
 * observations via precision weighting, decaying unreinforced states back toward baseline priors,
 * and applying the Soft Identity Anchor Lyapunov restoring pull during sleep consolidation.</p>
 */
public final class MentalStateTracker {

    private static final Logger log = LoggerFactory.getLogger(MentalStateTracker.class);

    private final ReentrantLock lock = new ReentrantLock();
    private volatile GenerativeSelfModel selfModel;
    private volatile CoreIdentityAnchor coreAnchor;
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
        this.coreAnchor = CoreIdentityAnchor.fromGenerativeModel(selfModel);
        this.currentPosterior = selfModel.createInitialPosterior(System.currentTimeMillis());
    }

    /**
     * Constructs a MentalStateTracker with an explicit initial posterior state.
     *
     * @param selfModel the generative self-model
     * @param initialPosterior the initial posterior belief state
     */
    public MentalStateTracker(GenerativeSelfModel selfModel, MentalStatePosterior initialPosterior) {
        this(selfModel, initialPosterior, null);
    }

    /**
     * Constructs a MentalStateTracker with an explicit initial posterior state and core identity anchor.
     *
     * @param selfModel the generative self-model
     * @param initialPosterior the initial posterior belief state
     * @param coreAnchor the core identity anchor snapshot (nullable; derived from selfModel if null)
     */
    public MentalStateTracker(GenerativeSelfModel selfModel, MentalStatePosterior initialPosterior, CoreIdentityAnchor coreAnchor) {
        if (selfModel == null || initialPosterior == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        if (selfModel.dimensions() != initialPosterior.dimensions()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "SelfModel and initial posterior dimension mismatch");
        }
        this.selfModel = selfModel;
        this.coreAnchor = (coreAnchor != null) ? coreAnchor : CoreIdentityAnchor.fromGenerativeModel(selfModel);
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
     * Alias for {@link #currentPosterior()}.
     */
    public MentalStatePosterior posterior() {
        return currentPosterior();
    }

    /**
     * @return the underlying GenerativeSelfModel
     */
    public GenerativeSelfModel selfModel() {
        return selfModel;
    }

    /**
     * @return the active foundational CoreIdentityAnchor
     */
    public CoreIdentityAnchor coreAnchor() {
        return coreAnchor;
    }

    /**
     * Updates or re-anchors the core identity state (e.g. upon major multi-year milestones).
     *
     * @param newAnchor the new core identity anchor
     */
    public void updateCoreAnchor(CoreIdentityAnchor newAnchor) {
        if (newAnchor == null || newAnchor.dimensions() != selfModel.dimensions()) {
            return;
        }
        this.coreAnchor = newAnchor;
        log.info("Updated CoreIdentityAnchor to version {}", newAnchor.snapshotVersion());
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
     * Applies the Soft Identity Anchor restoring force during biological sleep reflection:
     * \(\boldsymbol{p}_{t+1} \leftarrow (1 - \eta)\boldsymbol{p}_t + \eta \boldsymbol{p}_{\text{core}}\).
     *
     * @param eta restorative learning rate \(\eta_{\text{anchor}} \in [0, 1]\)
     */
    public void applyIdentityAnchorRestoration(float eta) {
        if (coreAnchor == null || eta <= 0.0f) {
            return;
        }
        lock.lock();
        try {
            this.selfModel = this.selfModel.withAdaptedPriorMean(coreAnchor.corePriorMean(), eta);
            if (log.isDebugEnabled()) {
                log.debug("Applied Soft Identity Anchor restoring force with eta={}", eta);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Computes the Riemannian distance \(d_M(\boldsymbol{p}, \boldsymbol{p}_{\text{core}})\) between current prior and core anchor.
     *
     * @param tensor active personal metric tensor
     * @return Riemannian distance to anchor
     */
    public float computeManifoldDistanceToAnchor(PersonalMetricTensor tensor) {
        if (coreAnchor == null) {
            return 0.0f;
        }
        return coreAnchor.computeManifoldDistance(selfModel.priorMean(), tensor);
    }

    /**
     * Evaluates the Longitudinal Continuity metric \(C(t, t+\Delta)\) against the core anchor.
     *
     * @param tensor active metric tensor
     * @param lambda decay sensitivity
     * @return continuity metric in \([0, 1]\)
     */
    public float computeContinuityScore(PersonalMetricTensor tensor, float lambda) {
        if (coreAnchor == null) {
            return 1.0f;
        }
        return coreAnchor.computeContinuityScore(selfModel.priorMean(), tensor, lambda);
    }

    /**
     * Checks if current generative prior is bounded within the Lyapunov attractor basin.
     *
     * @param tensor active metric tensor
     * @param lyapunovThreshold maximum allowable distance
     * @return true if bounded
     */
    public boolean isWithinLyapunovBasin(PersonalMetricTensor tensor, float lyapunovThreshold) {
        if (coreAnchor == null) {
            return true;
        }
        return coreAnchor.isWithinLyapunovBasin(selfModel.priorMean(), tensor, lyapunovThreshold);
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

    /**
     * Resets the posterior belief state directly to the given initial sensory observation.
     *
     * @param observation observation vector
     * @param timestampMs current epoch timestamp
     */
    public void resetToObservation(float[] observation, long timestampMs) {
        if (observation == null || observation.length != selfModel.dimensions()) {
            reset();
            return;
        }
        lock.lock();
        try {
            currentPosterior = new MentalStatePosterior(
                    observation.clone(),
                    selfModel.observationPrecision().clone(),
                    timestampMs,
                    1
            );
            log.debug("Reset mental state posterior to new episode observation vector");
        } finally {
            lock.unlock();
        }
    }
}
