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
package com.spectrayan.spector.memory.aisme.segmentation;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, thread-safe Bayesian Online Change-Point Detector (BOCPD) maintaining
 * exact run-length posterior distribution \(P(r_t \mid x_{1:t})\) with \(\mathcal{O}(R_{\text{max}})\) message passing.
 *
 * <h3>Biological Analog: Hippocampal Event Boundary Transduction</h3>
 * <p>Tracks the probability that an active sensory distribution regime has terminated and a new
 * narrative episode has commenced (Adams & MacKay, 2007; Event Segmentation Theory).</p>
 */
public final class BayesianOnlineChangePointDetector {

    private final ReentrantLock lock = new ReentrantLock();
    private final int dimensions;
    private final int maxRunLength;
    private final float hazardLambda;
    private final float hazard;
    private final float[] priorMean;
    private final float[] priorPrecision;
    private final float[] obsPrecision;

    // Run-length posterior state
    private float[] rDist;
    private float[][] sumAcc;
    private int stepCount;

    public BayesianOnlineChangePointDetector(int dimensions, float hazardLambda, int maxRunLength, float[] priorMean, float[] obsPrecision) {
        this(dimensions, hazardLambda, maxRunLength, priorMean, defaultPriorPrecision(dimensions), obsPrecision);
    }

    public BayesianOnlineChangePointDetector(
            int dimensions,
            float hazardLambda,
            int maxRunLength,
            float[] priorMean,
            float[] priorPrecision,
            float[] obsPrecision
    ) {
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dimensions must be positive");
        }
        if (hazardLambda <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "hazardLambda must be positive");
        }
        if (maxRunLength <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxRunLength must be positive");
        }

        this.dimensions = dimensions;
        this.hazardLambda = hazardLambda;
        this.hazard = 1.0f / hazardLambda;
        this.maxRunLength = maxRunLength;
        this.priorMean = (priorMean != null && priorMean.length == dimensions) ? priorMean.clone() : new float[dimensions];
        this.priorPrecision = (priorPrecision != null && priorPrecision.length == dimensions) ? priorPrecision.clone() : defaultPriorPrecision(dimensions);
        this.obsPrecision = (obsPrecision != null && obsPrecision.length == dimensions) ? obsPrecision.clone() : defaultObsPrecision(dimensions);

        this.rDist = new float[maxRunLength + 1];
        this.rDist[0] = 1.0f;
        this.sumAcc = new float[maxRunLength + 1][dimensions];
        this.stepCount = 0;
    }

    private static float[] defaultPriorPrecision(int d) {
        float[] p = new float[d];
        Arrays.fill(p, 0.05f); // Diffuse prior for new regime hypothesis r=0
        return p;
    }

    private static float[] defaultObsPrecision(int d) {
        float[] p = new float[d];
        Arrays.fill(p, 1.0f);
        return p;
    }

    /**
     * Updates the run-length posterior distribution given a new observation vector \(x_t\)
     * and returns the instantaneous change-point probability \(P(r_t = 0 \mid x_{1:t})\).
     *
     * @param observation sensory observation vector \(x_t\)
     * @return change-point probability in \([0, 1]\)
     */
    public float update(float[] observation) {
        if (observation == null || observation.length != dimensions) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Observation dimension mismatch with BOCPD model");
        }

        lock.lock();
        try {
            int activeRuns = Math.min(stepCount + 1, maxRunLength);
            float[] logPred = new float[activeRuns];
            float maxLogPred = Float.NEGATIVE_INFINITY;

            // 1. Compute log predictive likelihood for each active run length
            for (int r = 0; r < activeRuns; r++) {
                logPred[r] = computeLogGaussianLikelihood(observation, r);
                if (logPred[r] > maxLogPred) {
                    maxLogPred = logPred[r];
                }
            }

            // 2. Compute unnormalized growth and change-point probabilities
            float[] newRDist = new float[maxRunLength + 1];
            float cpSum = 0.0f;

            for (int r = 0; r < activeRuns; r++) {
                float pred = (float) Math.exp(logPred[r] - maxLogPred);
                float joint = rDist[r] * pred;

                // Change point hazard (r_t = 0)
                cpSum += joint * hazard;

                // Growth (r_t = r_{t-1} + 1)
                if (r + 1 <= maxRunLength) {
                    newRDist[r + 1] = joint * (1.0f - hazard);
                }
            }

            newRDist[0] = cpSum;

            // 3. Normalize newRDist
            float totalMass = 0.0f;
            for (int r = 0; r <= maxRunLength; r++) {
                totalMass += newRDist[r];
            }

            if (totalMass > 0.0f) {
                for (int r = 0; r <= maxRunLength; r++) {
                    newRDist[r] /= totalMass;
                }
            } else {
                newRDist[0] = 1.0f;
            }

            // 4. Update sufficient statistics
            float[][] newSumAcc = new float[maxRunLength + 1][dimensions];
            // newSumAcc[0] remains all zeros (0 observations for r=0)

            for (int r = 0; r < activeRuns && r + 1 <= maxRunLength; r++) {
                com.spectrayan.spector.core.similarity.VectorOps.add(sumAcc[r], 0, observation, 0, newSumAcc[r + 1], 0, dimensions);
            }

            this.rDist = newRDist;
            this.sumAcc = newSumAcc;
            this.stepCount++;

            return rDist[0];
        } finally {
            lock.unlock();
        }
    }

    private float computeLogGaussianLikelihood(float[] obs, int r) {
        return com.spectrayan.spector.core.cognitive.BocpdKernel.evaluateLogLikelihoodForRun(
                obs,
                priorMean,
                priorPrecision,
                (r > 0) ? sumAcc[r] : null,
                obsPrecision,
                r
        );
    }

    /**
     * Resets the BOCPD state to the initial prior baseline.
     */
    public void reset() {
        lock.lock();
        try {
            Arrays.fill(rDist, 0.0f);
            rDist[0] = 1.0f;
            for (int r = 0; r <= maxRunLength; r++) {
                Arrays.fill(sumAcc[r], 0.0f);
            }
            stepCount = 0;
        } finally {
            lock.unlock();
        }
    }

    public int mapRunLength() {
        lock.lock();
        try {
            int mapR = 0;
            float maxP = rDist[0];
            for (int r = 1; r <= maxRunLength; r++) {
                if (rDist[r] > maxP) {
                    maxP = rDist[r];
                    mapR = r;
                }
            }
            return mapR;
        } finally {
            lock.unlock();
        }
    }

    public float[] runLengthDistribution() {
        lock.lock();
        try {
            return rDist.clone();
        } finally {
            lock.unlock();
        }
    }

    public float currentChangePointProbability() {
        lock.lock();
        try {
            return rDist[0];
        } finally {
            lock.unlock();
        }
    }

    public int dimensions() {
        return dimensions;
    }

    public float hazardLambda() {
        return hazardLambda;
    }

    public int maxRunLength() {
        return maxRunLength;
    }

    public int stepCount() {
        lock.lock();
        try {
            return stepCount;
        } finally {
            lock.unlock();
        }
    }
}
