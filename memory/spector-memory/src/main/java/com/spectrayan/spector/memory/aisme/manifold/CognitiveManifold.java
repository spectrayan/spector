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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.similarity.NeuralManifoldDistance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe manager for the personal Riemannian cognitive manifold.
 *
 * <h3>Biological Analog: Experiential Manifold Curvature & Geodesic Calculation</h3>
 * <p>Maintains the active metric tensor representation, evaluating subjective distances and
 * similarities across memory vectors along experiential geodesic paths.</p>
 */
public final class CognitiveManifold {

    private static final Logger log = LoggerFactory.getLogger(CognitiveManifold.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final int dimensions;
    private PersonalMetricTensor currentTensor;

    /**
     * Constructs a CognitiveManifold initialized with an identity metric tensor.
     *
     * @param dimensions embedding space dimensionality
     */
    public CognitiveManifold(int dimensions) {
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimension must be positive");
        }
        this.dimensions = dimensions;
        this.currentTensor = PersonalMetricTensor.identity(dimensions);
    }

    /**
     * Constructs a CognitiveManifold with an initial explicit metric tensor.
     *
     * @param initialTensor the initial personal metric tensor
     */
    public CognitiveManifold(PersonalMetricTensor initialTensor) {
        if (initialTensor == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Initial metric tensor must not be null");
        }
        this.dimensions = initialTensor.dimensions();
        this.currentTensor = initialTensor;
    }

    /**
     * Computes the squared Riemannian distance between two vectors on this manifold.
     *
     * @param x first vector
     * @param y second vector
     * @return squared Riemannian distance
     */
    public float squaredDistance(float[] x, float[] y) {
        PersonalMetricTensor tensor = currentTensor();
        return NeuralManifoldDistance.squaredDistance(x, y, tensor.diagonalScaling(), tensor.lowRankComponents());
    }

    /**
     * Computes the Riemannian distance between two vectors on this manifold.
     *
     * @param x first vector
     * @param y second vector
     * @return Riemannian distance
     */
    public float distance(float[] x, float[] y) {
        PersonalMetricTensor tensor = currentTensor();
        return NeuralManifoldDistance.distance(x, y, tensor.diagonalScaling(), tensor.lowRankComponents());
    }

    /**
     * Computes Gaussian manifold similarity between two vectors on this manifold.
     *
     * @param x first vector
     * @param y second vector
     * @param sigma kernel bandwidth
     * @return similarity score in (0, 1]
     */
    public float similarity(float[] x, float[] y, float sigma) {
        PersonalMetricTensor tensor = currentTensor();
        return NeuralManifoldDistance.similarity(x, y, tensor.diagonalScaling(), tensor.lowRankComponents(), sigma);
    }

    /**
     * Batch computes manifold similarity for a query vector against multiple candidate vectors.
     *
     * @param query query vector in R^D
     * @param candidates array of candidate vectors
     * @param sigma kernel bandwidth
     * @return array of similarity scores
     */
    public float[] batchSimilarity(float[] query, float[][] candidates, float sigma) {
        PersonalMetricTensor tensor = currentTensor();
        return NeuralManifoldDistance.batchSimilarity(query, candidates, tensor.diagonalScaling(), tensor.lowRankComponents(), sigma);
    }

    /**
     * @return an immutable snapshot of the active PersonalMetricTensor
     */
    public PersonalMetricTensor currentTensor() {
        lock.lock();
        try {
            return currentTensor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the active metric tensor with a newly consolidated version.
     *
     * @param newTensor the new metric tensor (must have matching dimensions)
     */
    public void updateTensor(PersonalMetricTensor newTensor) {
        if (newTensor == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Metric tensor must not be null");
        }
        if (newTensor.dimensions() != dimensions) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Metric tensor dimension mismatch");
        }

        lock.lock();
        try {
            PersonalMetricTensor old = currentTensor;
            currentTensor = newTensor;
            if (log.isTraceEnabled()) {
                log.trace("Updated metric tensor from version {} to {}", old.version(), newTensor.version());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the manifold back to a Euclidean identity metric.
     */
    public void reset() {
        lock.lock();
        try {
            currentTensor = PersonalMetricTensor.identity(dimensions);
            log.debug("Reset cognitive manifold to Euclidean identity");
        } finally {
            lock.unlock();
        }
    }

    public int dimensions() {
        return dimensions;
    }
}
