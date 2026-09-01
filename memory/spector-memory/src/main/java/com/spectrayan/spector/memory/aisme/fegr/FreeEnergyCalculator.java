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
import com.spectrayan.spector.core.cognitive.FreeEnergyKernel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Calculates Variational Free Energy and situational Free-Energy Relevance Scores (FERS).
 *
 * <h3>Biological Analog: Variational Free Energy Reduction & Active Inference</h3>
 * <p>Quantifies the degree to which a candidate memory or observation minimizes surprise and
 * resolves ambiguity in the agent's generative self-model.</p>
 */
public final class FreeEnergyCalculator {

    private static final Logger log = LoggerFactory.getLogger(FreeEnergyCalculator.class);

    private final float defaultMemoryPrecision;

    public FreeEnergyCalculator() {
        this(2.0f);
    }

    public FreeEnergyCalculator(float defaultMemoryPrecision) {
        if (defaultMemoryPrecision <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Memory precision must be positive");
        }
        this.defaultMemoryPrecision = defaultMemoryPrecision;
    }

    /**
     * Calculates the Variational Free Energy F(q) of the current posterior state given prior and observation.
     *
     * @param posterior current posterior belief distribution q(s)
     * @param selfModel generative self-model providing prior p(s) and observation likelihood p(o|s)
     * @param observation current sensory observation vector o
     * @return scalar Variational Free Energy
     */
    public float calculateFreeEnergy(MentalStatePosterior posterior, GenerativeSelfModel selfModel, float[] observation) {
        if (posterior == null || selfModel == null || observation == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        if (posterior.dimensions() != selfModel.dimensions() || observation.length != selfModel.dimensions()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions mismatch between posterior, model, and observation");
        }

        return FreeEnergyKernel.variationalFreeEnergy(
                posterior.mean(),
                posterior.precision(),
                selfModel.priorMean(),
                selfModel.priorPrecision(),
                observation,
                selfModel.observationPrecision()
        );
    }

    /**
     * Calculates the Free-Energy reduction deltaF provided by conditioning on a candidate memory embedding:
     * deltaF(mu_i) = F(q(s_t)) - F(q(s_t | mu_i))
     *
     * @param posterior current posterior belief distribution q(s)
     * @param selfModel generative self-model providing prior and observation parameters
     * @param observation current sensory observation vector o
     * @param candidateEmbedding memory vector embedding e_mu
     * @param memoryPrecision precision vector for the candidate memory (or null for default scalar precision)
     * @return free energy reduction deltaF (positive indicates surprise/uncertainty reduction)
     */
    public float calculateFreeEnergyReduction(MentalStatePosterior posterior,
                                              GenerativeSelfModel selfModel,
                                              float[] observation,
                                              float[] candidateEmbedding,
                                              float[] memoryPrecision) {
        if (posterior == null || selfModel == null || observation == null || candidateEmbedding == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        int dim = posterior.dimensions();
        if (candidateEmbedding.length != dim) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Candidate embedding length must match posterior dimensions");
        }

        float[] prec = memoryPrecision;
        if (prec == null) {
            prec = new float[dim];
            Arrays.fill(prec, defaultMemoryPrecision);
        } else if (prec.length != dim) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Memory precision vector length must match posterior dimensions");
        }

        float baseFE = calculateFreeEnergy(posterior, selfModel, observation);

        MentalStatePosterior conditionedPosterior = posterior.withEvidence(
                candidateEmbedding,
                prec,
                posterior.timestampMs(),
                posterior.version() + 1
        );

        float conditionedFE = calculateFreeEnergy(conditionedPosterior, selfModel, observation);
        float deltaF = baseFE - conditionedFE;

        if (log.isTraceEnabled()) {
            log.trace("Free-energy calculation: baseFE={}, condFE={}, deltaF={}", baseFE, conditionedFE, deltaF);
        }

        return deltaF;
    }

    /**
     * Fuses base semantic similarity, free-energy reduction, and affective resonance into the Free-Energy Relevance Score (FERS).
     *
     * @param baseSimilarity base semantic similarity score (e.g. from HNSW / VectorOps)
     * @param deltaF free energy reduction deltaF
     * @param affectiveResonance affective resonance score in [0, 1]
     * @param alpha weight for base similarity
     * @param beta weight for free-energy reduction
     * @param gamma weight for affective resonance
     * @return fused FERS score
     */
    public static float calculateFersScore(float baseSimilarity, float deltaF, float affectiveResonance,
                                           float alpha, float beta, float gamma) {
        float normalizedDeltaF = 1.0f / (1.0f + (float) Math.exp(-deltaF));
        return (alpha * baseSimilarity) + (beta * normalizedDeltaF) + (gamma * affectiveResonance);
    }
}
