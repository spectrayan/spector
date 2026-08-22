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
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import java.util.Arrays;

/**
 * Encapsulates the generative prior distribution p(s|m) and observation precision model p(o|s)
 * derived from the agent's persistent identity and cognitive profile.
 *
 * <h3>Biological Analog: Top-Down Generative Priors & Cortical Precision Allocations</h3>
 * <p>Represents the internal generative model of the world and self. Modulates baseline expectations
 * and attentional precision weighting according to personality and neurocognitive traits.</p>
 */
public record GenerativeSelfModel(
        float[] priorMean,
        float[] priorPrecision,
        float[] observationPrecision,
        AgentSoul soul,
        CognitiveProfile profile
) {

    /**
     * Compact constructor with defensive copies and validation.
     */
    public GenerativeSelfModel {
        if (priorMean == null || priorPrecision == null || observationPrecision == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Model vectors must not be null");
        }
        if (priorMean.length != priorPrecision.length || priorMean.length != observationPrecision.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "All generative model vectors must have equal dimensions");
        }
        if (priorMean.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector dimension must be greater than zero");
        }

        priorMean = Arrays.copyOf(priorMean, priorMean.length);
        priorPrecision = Arrays.copyOf(priorPrecision, priorPrecision.length);
        observationPrecision = Arrays.copyOf(observationPrecision, observationPrecision.length);
    }

    /**
     * @return the dimensionality of the generative space
     */
    public int dimensions() {
        return priorMean.length;
    }

    /**
     * Creates an initial default posterior q(s_0) matching the prior p(s|m).
     *
     * @param timestampMs creation timestamp
     * @return initialized posterior matching the prior distribution
     */
    public MentalStatePosterior createInitialPosterior(long timestampMs) {
        return new MentalStatePosterior(priorMean, priorPrecision, timestampMs, 0);
    }

    /**
     * Constructs a GenerativeSelfModel from an AgentSoul and CognitiveProfile.
     *
     * @param soul the agent persistent identity (may be null)
     * @param profile the cognitive profile governing precision dynamics (may be null)
     * @param dimensions target embedding dimension
     * @return configured GenerativeSelfModel
     */
    public static GenerativeSelfModel fromSoulAndProfile(AgentSoul soul, CognitiveProfile profile, int dimensions) {
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions must be positive");
        }

        float[] mean = new float[dimensions];
        if (soul != null && soul.identityEmbedding() != null) {
            float[] idEmb = soul.identityEmbedding();
            int copyLen = Math.min(dimensions, idEmb.length);
            System.arraycopy(idEmb, 0, mean, 0, copyLen);
        }

        float basePrecision = 2.5f;
        if (profile != null) {
            switch (profile) {
                case HYPERFOCUS -> basePrecision = 8.0f;
                case SYSTEMATIZER -> basePrecision = 6.0f;
                case BALANCED -> basePrecision = 3.0f;
                case DIVERGENT -> basePrecision = 1.0f;
                case EXPLORING -> basePrecision = 1.5f;
                case EXECUTIVE_DYSFUNCTION -> basePrecision = 0.8f;
                case PARANOID_SENTINEL -> basePrecision = 10.0f;
                case DEFAULT_MODE_NETWORK -> basePrecision = 2.0f;
                default -> basePrecision = 2.5f;
            }
        }

        float[] priorPrec = new float[dimensions];
        Arrays.fill(priorPrec, basePrecision);

        float[] obsPrec = new float[dimensions];
        Arrays.fill(obsPrec, 4.0f);

        return new GenerativeSelfModel(mean, priorPrec, obsPrec, soul, profile);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GenerativeSelfModel that)) return false;
        return Arrays.equals(priorMean, that.priorMean) &&
                Arrays.equals(priorPrecision, that.priorPrecision) &&
                Arrays.equals(observationPrecision, that.observationPrecision) &&
                profile == that.profile;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(priorMean);
        result = 31 * result + Arrays.hashCode(priorPrecision);
        result = 31 * result + Arrays.hashCode(observationPrecision);
        result = 31 * result + (profile != null ? profile.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GenerativeSelfModel{" +
                "dimensions=" + priorMean.length +
                ", profile=" + profile +
                '}';
    }
}
