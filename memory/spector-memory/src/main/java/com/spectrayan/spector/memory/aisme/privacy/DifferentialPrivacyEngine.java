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
package com.spectrayan.spector.memory.aisme.privacy;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.privacy.DifferentialPrivacyKernel;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stateful Differential Privacy Engine managing budget accounting and calibrated noise injection.
 *
 * <h3>Biological Analog: Synaptic Noise & Cognitive Obfuscation</h3>
 * <p>Perturbs sensory embeddings and cognitive metrics with mathematically bounded Gaussian
 * and Laplace noise to provide strict \((\epsilon, \delta)\)-differential privacy guarantees.</p>
 */
public final class DifferentialPrivacyEngine {

    private static final Logger log = LoggerFactory.getLogger(DifferentialPrivacyEngine.class);

    private final AismeConfig config;
    private final float sigma;
    private final Random rng;
    private final DoubleAdder totalConsumedEpsilon = new DoubleAdder();
    private final AtomicLong perturbationCount = new AtomicLong();
    private final ReentrantLock lock = new ReentrantLock();

    public DifferentialPrivacyEngine(AismeConfig config) {
        this(config, new Random());
    }

    public DifferentialPrivacyEngine(AismeConfig config, Random rng) {
        if (config == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "config must not be null");
        }
        this.config = config;
        this.rng = (rng != null) ? rng : new Random();
        this.sigma = DifferentialPrivacyKernel.computeGaussianSigma(
                config.privacyClippingNorm(),
                config.privacyEpsilon(),
                config.privacyDelta()
        );
        log.info("Initialized DifferentialPrivacyEngine with epsilon={}, delta={}, clippingNorm={}, sigma={}",
                config.privacyEpsilon(), config.privacyDelta(), config.privacyClippingNorm(), sigma);
    }

    /**
     * Perturbs a high-dimensional sensory embedding vector with \(L_2\) norm clipping and Gaussian noise.
     *
     * @param vector raw embedding vector
     * @return perturbed differential-private embedding vector
     */
    public float[] perturbVector(float[] vector) {
        if (vector == null) {
            return null;
        }
        if (!config.enablePrivacy()) {
            return vector.clone();
        }

        // 1. Clip L2 norm to sensitivity bound C
        float[] clipped = DifferentialPrivacyKernel.clipVectorL2(vector, config.privacyClippingNorm());

        // 2. Inject calibrated Gaussian noise
        float[] noisy = DifferentialPrivacyKernel.injectGaussianNoise(clipped, sigma, rng);

        totalConsumedEpsilon.add(config.privacyEpsilon());
        perturbationCount.incrementAndGet();

        return noisy;
    }

    /**
     * Perturbs a scalar cognitive metric using the Laplace mechanism.
     *
     * @param scalar      input scalar value
     * @param sensitivity L1 sensitivity bound
     * @return perturbed scalar value
     */
    public float perturbScalar(float scalar, float sensitivity) {
        if (!config.enablePrivacy()) {
            return scalar;
        }

        float noisy = DifferentialPrivacyKernel.injectLaplaceNoise(scalar, sensitivity, config.privacyEpsilon(), rng);
        totalConsumedEpsilon.add(config.privacyEpsilon());
        perturbationCount.incrementAndGet();

        return noisy;
    }

    /**
     * Returns the calculated Gaussian noise standard deviation \(\sigma\).
     *
     * @return noise standard deviation
     */
    public float sigma() {
        return sigma;
    }

    /**
     * Returns the cumulative consumed privacy budget \(\sum \epsilon\).
     *
     * @return cumulative consumed epsilon
     */
    public double consumedEpsilon() {
        return totalConsumedEpsilon.sum();
    }

    /**
     * Returns the total count of perturbed signals.
     *
     * @return perturbation count
     */
    public long perturbationCount() {
        return perturbationCount.get();
    }

    /**
     * Resets the consumed privacy budget counters.
     */
    public void resetBudget() {
        lock.lock();
        try {
            totalConsumedEpsilon.reset();
            perturbationCount.set(0);
            log.info("Reset DifferentialPrivacyEngine privacy budget accounting");
        } finally {
            lock.unlock();
        }
    }
}
