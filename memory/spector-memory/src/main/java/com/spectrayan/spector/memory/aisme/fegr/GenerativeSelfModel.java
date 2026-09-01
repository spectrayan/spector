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
import com.spectrayan.spector.memory.model.SoulContext;

import java.util.Arrays;
import java.util.List;

/**
 * Encapsulates the generative prior distribution p(s|m) and observation precision model p(o|s)
 * derived from the entity's persistent soul context and cognitive profile.
 *
 * <h3>Biological Analog: Top-Down Generative Priors & Cortical Precision Allocations</h3>
 * <p>Represents the internal generative model of the world and self. Modulates baseline expectations
 * and attentional precision weighting according to personality and neurocognitive traits.</p>
 */
public record GenerativeSelfModel(
        float[] priorMean,
        float[] priorPrecision,
        float[] observationPrecision,
        SoulContext soul,
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
     * Backward-compatible accessor returning the soul as an {@link AgentSoul} if applicable.
     *
     * @return AgentSoul or null if the soul is not an AgentSoul
     */
    public AgentSoul agentSoul() {
        return soul instanceof AgentSoul agent ? agent : null;
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
     * Constructs a GenerativeSelfModel from a polymorphic SoulContext and CognitiveProfile.
     *
     * @param soul the persistent identity context (AgentSoul, UserSoul, TenantSoul, OrgUnitSoul)
     * @param profile the cognitive profile governing precision dynamics (may be null)
     * @param dimensions target embedding dimension
     * @return configured GenerativeSelfModel
     */
    public static GenerativeSelfModel fromSoulAndProfile(SoulContext soul, CognitiveProfile profile, int dimensions) {
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions must be positive");
        }

        float[] mean = new float[dimensions];
        if (soul != null && soul.identityEmbedding() != null) {
            float[] idEmb = soul.identityEmbedding();
            int copyLen = Math.min(dimensions, idEmb.length);
            System.arraycopy(idEmb, 0, mean, 0, copyLen);
        }

        float basePrecision = precisionForProfile(profile);

        float[] priorPrec = new float[dimensions];
        Arrays.fill(priorPrec, basePrecision);

        float[] obsPrec = new float[dimensions];
        Arrays.fill(obsPrec, 4.0f);

        return new GenerativeSelfModel(mean, priorPrec, obsPrec, soul, profile);
    }

    /**
     * Constructs a composite GenerativeSelfModel blending multiple soul contexts into a unified generative prior.
     *
     * @param soulContexts list of active soul contexts in the identity stack
     * @param profile the cognitive profile governing precision dynamics
     * @param dimensions target embedding dimension
     * @return configured GenerativeSelfModel with composite prior
     */
    public static GenerativeSelfModel fromSoulsAndProfile(List<SoulContext> soulContexts, CognitiveProfile profile, int dimensions) {
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions must be positive");
        }
        if (soulContexts == null || soulContexts.isEmpty()) {
            return fromSoulAndProfile((SoulContext) null, profile, dimensions);
        }
        if (soulContexts.size() == 1) {
            return fromSoulAndProfile(soulContexts.get(0), profile, dimensions);
        }

        float[] blendedMean = new float[dimensions];
        int count = 0;
        for (SoulContext sc : soulContexts) {
            if (sc != null && sc.identityEmbedding() != null) {
                float[] emb = sc.identityEmbedding();
                int copyLen = Math.min(dimensions, emb.length);
                for (int i = 0; i < copyLen; i++) {
                    blendedMean[i] += emb[i];
                }
                count++;
            }
        }
        if (count > 1) {
            for (int i = 0; i < dimensions; i++) {
                blendedMean[i] /= count;
            }
        }

        float basePrecision = precisionForProfile(profile);

        float[] priorPrec = new float[dimensions];
        Arrays.fill(priorPrec, basePrecision);

        float[] obsPrec = new float[dimensions];
        Arrays.fill(obsPrec, 4.0f);

        SoulContext primary = soulContexts.get(0);
        return new GenerativeSelfModel(blendedMean, priorPrec, obsPrec, primary, profile);
    }

    private static float precisionForProfile(CognitiveProfile profile) {
        if (profile == null) return 2.5f;
        return switch (profile) {
            case HYPERFOCUS -> 8.0f;
            case SYSTEMATIZER -> 6.0f;
            case BALANCED -> 3.0f;
            case DIVERGENT -> 1.0f;
            case EXPLORING -> 1.5f;
            case EXECUTIVE_DYSFUNCTION -> 0.8f;
            case PARANOID_SENTINEL -> 10.0f;
            case DEFAULT_MODE_NETWORK -> 2.0f;
            default -> 2.5f;
        };
    }

    /**
     * Returns a new GenerativeSelfModel with the prior mean adapted toward a target experiential centroid.
     *
     * <p>Biological Analog: Long-term experiential synaptic plasticity of the autobiographical generative prior
     * during biological sleep consolidation: \mu_0 \leftarrow (1 - \eta)\mu_0 + \eta c.</p>
     *
     * @param targetCentroid target experiential centroid (e.g. autobiographical memory mean)
     * @param learningRate plasticity learning rate \eta in [0, 1]
     * @return updated GenerativeSelfModel
     */
    public GenerativeSelfModel withAdaptedPriorMean(float[] targetCentroid, float learningRate) {
        if (targetCentroid == null || targetCentroid.length != dimensions()) {
            return this;
        }
        float eta = Math.max(0.0f, Math.min(1.0f, learningRate));
        float[] newMean = new float[dimensions()];
        for (int i = 0; i < dimensions(); i++) {
            newMean[i] = (1.0f - eta) * priorMean[i] + eta * targetCentroid[i];
        }
        return new GenerativeSelfModel(newMean, priorPrecision, observationPrecision, soul, profile);
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
