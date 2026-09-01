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
package com.spectrayan.spector.memory.aisme.manifold;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Derives and updates the personal Riemannian metric tensor from Hebbian co-activations,
 * temporal memory chains, and reflection cycles.
 *
 * <h3>Biological Analog: Sleep Consolidation of Cognitive Map Topography</h3>
 * <p>During sleep replay and reflection cycles, associative links between memories warp the
 * underlying cognitive geometry, adjusting coordinate precision and low-rank cross-coupling.</p>
 */
public final class ManifoldConsolidator {

    private static final Logger log = LoggerFactory.getLogger(ManifoldConsolidator.class);

    private final float defaultLearningRate;
    private final int maxLowRankComponents;

    /**
     * Constructs a ManifoldConsolidator with default parameters (learningRate=0.05, maxRank=4).
     */
    public ManifoldConsolidator() {
        this(0.05f, 4);
    }

    /**
     * Constructs a ManifoldConsolidator with custom parameters.
     *
     * @param defaultLearningRate learning rate for metric tensor adaptation in (0, 1]
     * @param maxLowRankComponents maximum number of low-rank factors to maintain
     */
    public ManifoldConsolidator(float defaultLearningRate, int maxLowRankComponents) {
        if (defaultLearningRate <= 0.0f || defaultLearningRate > 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Learning rate must be in (0, 1]");
        }
        if (maxLowRankComponents < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxLowRankComponents cannot be negative");
        }
        this.defaultLearningRate = defaultLearningRate;
        this.maxLowRankComponents = maxLowRankComponents;
    }

    /**
     * Consolidates co-activated memory pairs into an updated PersonalMetricTensor.
     *
     * @param current current metric tensor
     * @param coActivatedPairs list of paired memory difference vectors (a - b)
     * @param learningRate adaptation step size
     * @return updated PersonalMetricTensor
     */
    public PersonalMetricTensor consolidate(PersonalMetricTensor current, List<float[]> coActivatedPairs, float learningRate) {
        if (current == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Current metric tensor must not be null");
        }
        if (coActivatedPairs == null || coActivatedPairs.isEmpty()) {
            return current;
        }

        int dim = current.dimensions();
        float[] newDiag = Arrays.copyOf(current.diagonalScaling(), dim);
        float lr = (learningRate > 0.0f) ? learningRate : defaultLearningRate;

        for (float[] diff : coActivatedPairs) {
            if (diff == null || diff.length != dim) {
                continue;
            }
            for (int k = 0; k < dim; k++) {
                float dVal = diff[k];
                // Exponential moving adjustment of coordinate scaling
                newDiag[k] = Math.max(0.1f, newDiag[k] + (lr * dVal * dVal));
            }
        }

        float[][] newRank = current.lowRankComponents();
        if (maxLowRankComponents > 0 && !coActivatedPairs.isEmpty()) {
            // Incorporate leading co-activation direction as low-rank perturbation
            float[] sampleDiff = coActivatedPairs.get(0);
            if (sampleDiff.length == dim) {
                float[] normSample = new float[dim];
                float normSq = 0.0f;
                for (float v : sampleDiff) {
                    normSq += v * v;
                }
                float invNorm = (normSq > 0.0f) ? (float) (Math.sqrt(lr) / Math.sqrt(normSq)) : 0.0f;
                for (int k = 0; k < dim; k++) {
                    normSample[k] = sampleDiff[k] * invNorm;
                }

                if (newRank.length < maxLowRankComponents) {
                    float[][] extended = Arrays.copyOf(newRank, newRank.length + 1);
                    extended[extended.length - 1] = normSample;
                    newRank = extended;
                } else if (newRank.length > 0) {
                    // Update existing component
                    for (int k = 0; k < dim; k++) {
                        newRank[0][k] = (1.0f - lr) * newRank[0][k] + lr * normSample[k];
                    }
                }
            }
        }

        int nextVersion = current.version() + 1;
        if (log.isTraceEnabled()) {
            log.trace("Consolidated metric tensor to version {} with {} co-activation pairs",
                    nextVersion, coActivatedPairs.size());
        }

        return new PersonalMetricTensor(newDiag, newRank, nextVersion, System.currentTimeMillis());
    }

    public float defaultLearningRate() {
        return defaultLearningRate;
    }

    public int maxLowRankComponents() {
        return maxLowRankComponents;
    }
}
