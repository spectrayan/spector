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
package com.spectrayan.spector.memory.aisme.phi;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.IntegratedInformationKernel;

import java.util.List;

/**
 * Calculates Gaussian Integrated Information Theory (IIT) synergy Phi(G) on candidate memory subgraphs.
 *
 * <h3>Biological Analog: Tononi's Integrated Information Metric (IIT)</h3>
 * <p>Quantifies the holistic interconnectedness and causal density of a memory set, determining
 * whether retrieved fragments form an integrated gestalt rather than disconnected pieces.</p>
 */
public final class IntegratedInformationCalculator {

    private final float regularization;

    /**
     * Constructs a calculator with default regularization (lambda=1e-3).
     */
    public IntegratedInformationCalculator() {
        this(1e-3f);
    }

    /**
     * Constructs a calculator with custom regularization.
     *
     * @param regularization Tikhonov diagonal regularization
     */
    public IntegratedInformationCalculator(float regularization) {
        if (regularization < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Regularization must be non-negative");
        }
        this.regularization = regularization;
    }

    /**
     * Calculates the integrated information Phi(G) for a list of candidate memory vectors.
     *
     * @param vectors list of embedding vectors in R^D
     * @return integrated information Phi(G) >= 0.0f
     */
    public float calculatePhi(List<float[]> vectors) {
        if (vectors == null || vectors.size() <= 1) {
            return 0.0f;
        }

        int n = vectors.size();
        float[][] vArray = vectors.toArray(new float[n][]);
        float[][] gram = new float[n][n];

        IntegratedInformationKernel.computeGramMatrix(vArray, gram, regularization);
        return IntegratedInformationKernel.computePhi(gram);
    }

    public float regularization() {
        return regularization;
    }
}
