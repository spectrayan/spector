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
package com.spectrayan.spector.memory.aisme.narrative;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.memory.model.AgentSoul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Generates autobiographical narrative priors and ensures self-schema consistency.
 *
 * <h3>Biological Analog: Default Mode Network (DMN) Autobiographical Simulation</h3>
 * <p>Constructs overarching narrative expectations aligned with the persona's persistent identity,
 * values, and life themes, preventing jarring inconsistencies during memory recall.</p>
 */
public final class NarrativeSelfEngine {

    private static final Logger log = LoggerFactory.getLogger(NarrativeSelfEngine.class);

    private final AgentSoul soul;
    private final int dimensions;
    private final float[] identityPrior;

    /**
     * Constructs a NarrativeSelfEngine for an AgentSoul.
     *
     * @param soul the persistent agent soul (nullable)
     * @param dimensions embedding space dimensionality
     */
    public NarrativeSelfEngine(AgentSoul soul, int dimensions) {
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions must be positive");
        }
        this.soul = soul;
        this.dimensions = dimensions;
        this.identityPrior = new float[dimensions];

        if (soul != null && soul.identityEmbedding() != null) {
            float[] idEmb = soul.identityEmbedding();
            int copyLen = Math.min(dimensions, idEmb.length);
            System.arraycopy(idEmb, 0, identityPrior, 0, copyLen);
        }
    }

    /**
     * Derives a situated narrative prior by blending the identity baseline with the current situational context.
     *
     * @param contextVector current conversation or query vector in R^D (nullable)
     * @param contextWeight weight given to situational context in [0, 1]
     * @return blended narrative prior vector in R^D
     */
    public float[] deriveNarrativePrior(float[] contextVector, float contextWeight) {
        float[] prior = Arrays.copyOf(identityPrior, dimensions);
        if (contextVector != null && contextVector.length == dimensions) {
            float w = Math.max(0.0f, Math.min(1.0f, contextWeight));
            for (int d = 0; d < dimensions; d++) {
                prior[d] = (1.0f - w) * prior[d] + w * contextVector[d];
            }
        }
        return prior;
    }

    /**
     * Evaluates the narrative alignment of a candidate memory vector against the active narrative prior.
     *
     * @param memoryVector candidate memory embedding vector
     * @param narrativePrior active narrative prior vector
     * @return alignment score in [0, 1]
     */
    public float evaluateAlignment(float[] memoryVector, float[] narrativePrior) {
        if (memoryVector == null || narrativePrior == null) {
            return 0.5f;
        }
        if (memoryVector.length != dimensions || narrativePrior.length != dimensions) {
            return 0.5f;
        }

        float cosSim = CosineSimilarity.compute(memoryVector, narrativePrior);
        // Normalize cosine from [-1, 1] to [0, 1]
        return Math.max(0.0f, Math.min(1.0f, 0.5f * (cosSim + 1.0f)));
    }

    public AgentSoul soul() {
        return soul;
    }

    public int dimensions() {
        return dimensions;
    }
}
