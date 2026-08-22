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
package com.spectrayan.spector.memory.aisme.hopfield;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.similarity.HopfieldKernel;
import com.spectrayan.spector.core.similarity.VectorOps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Continuous Modern Hopfield Network for associative memory pattern retrieval and gestalt synthesis.
 *
 * <h3>Biological Analog: CA3 Autoassociative Attractor Settlement</h3>
 * <p>Iteratively relaxes a cognitive query vector into the lowest energy basin defined by the
 * candidate memory patterns, synthesizing multi-memory superpositions into cohesive mental representations.</p>
 */
public final class ContinuousHopfieldNetwork {

    private static final Logger log = LoggerFactory.getLogger(ContinuousHopfieldNetwork.class);

    private final int maxIterations;
    private final float convergenceTolerance;

    /**
     * Constructs a ContinuousHopfieldNetwork with default parameters (maxIterations=5, tolerance=1e-4).
     */
    public ContinuousHopfieldNetwork() {
        this(5, 1e-4f);
    }

    /**
     * Constructs a ContinuousHopfieldNetwork with custom convergence parameters.
     *
     * @param maxIterations maximum relaxation iterations (must be >= 1)
     * @param convergenceTolerance L2 distance threshold for early convergence
     */
    public ContinuousHopfieldNetwork(int maxIterations, float convergenceTolerance) {
        if (maxIterations < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxIterations must be at least 1");
        }
        if (convergenceTolerance < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "convergenceTolerance cannot be negative");
        }
        this.maxIterations = maxIterations;
        this.convergenceTolerance = convergenceTolerance;
    }

    /**
     * Relaxes an initial query vector into the nearest associative attractor defined by pattern memories.
     *
     * @param initialQuery initial retrieval query vector xi_0 in R^D
     * @param patterns array of N candidate pattern memory vectors in R^D
     * @param beta inverse temperature scaling parameter
     * @return converged AttractorState
     */
    public AttractorState retrieveAttractor(float[] initialQuery, float[][] patterns, float beta) {
        if (initialQuery == null || patterns == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Initial query and patterns must not be null");
        }
        int numPatterns = patterns.length;
        int dim = initialQuery.length;
        if (numPatterns == 0) {
            float[] emptyWeights = new float[0];
            return new AttractorState(initialQuery, emptyWeights, AttractorType.DIFFUSE, 0.0f, 0, System.currentTimeMillis());
        }

        float[] current = Arrays.copyOf(initialQuery, dim);
        float[] next = new float[dim];
        float[] weights = new float[numPatterns];

        int iter = 0;
        for (; iter < maxIterations; iter++) {
            HopfieldKernel.update(current, patterns, beta, next, weights);

            // Check L2 convergence: ||next - current||
            float diffNormSq = 0.0f;
            for (int d = 0; d < dim; d++) {
                float diff = next[d] - current[d];
                diffNormSq += diff * diff;
            }

            System.arraycopy(next, 0, current, 0, dim);

            if (Math.sqrt(diffNormSq) < convergenceTolerance) {
                iter++;
                break;
            }
        }

        float energy = HopfieldKernel.continuousEnergy(current, patterns, beta);
        AttractorType type = AttractorType.classify(weights);

        if (log.isTraceEnabled()) {
            log.trace("Hopfield attractor reached in {} iters: type={}, energy={}", iter, type, energy);
        }

        return new AttractorState(current, weights, type, energy, iter, System.currentTimeMillis());
    }

    public int maxIterations() {
        return maxIterations;
    }

    public float convergenceTolerance() {
        return convergenceTolerance;
    }
}
