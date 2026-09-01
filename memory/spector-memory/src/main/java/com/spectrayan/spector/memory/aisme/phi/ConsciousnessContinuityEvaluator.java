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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.similarity.EuclideanDistance;
import com.spectrayan.spector.memory.model.AgentSoul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Evaluates candidate memory clusters for holistic consciousness continuity Phi_CC and identity alignment.
 *
 * <h3>Biological Analog: Experiential Identity Continuity and Fragmentation Monitoring</h3>
 * <p>Integrates IIT synergy with distance to the persona's core identity soul vector, flagging
 * fragmented or dissociated candidate sets that fail to maintain experiential continuity.</p>
 */
public final class ConsciousnessContinuityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConsciousnessContinuityEvaluator.class);

    private final IntegratedInformationCalculator calculator;
    private final com.spectrayan.spector.memory.model.SoulContext soul;
    private final float cohesionThreshold;
    private final float sigmaSoul;

    /**
     * Constructs an evaluator with default parameters (threshold=0.05, sigmaSoul=1.0).
     *
     * @param soul the persistent soul context (nullable)
     */
    public ConsciousnessContinuityEvaluator(com.spectrayan.spector.memory.model.SoulContext soul) {
        this(soul, 0.05f, 1.0f);
    }

    /**
     * Constructs an evaluator with custom parameters.
     *
     * @param soul persistent soul context
     * @param cohesionThreshold minimum Phi_CC required for cohesive conscious experience
     * @param sigmaSoul Gaussian bandwidth for soul identity alignment
     */
    public ConsciousnessContinuityEvaluator(com.spectrayan.spector.memory.model.SoulContext soul, float cohesionThreshold, float sigmaSoul) {
        if (cohesionThreshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Cohesion threshold must be non-negative");
        }
        if (sigmaSoul <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Sigma soul must be positive");
        }
        this.calculator = new IntegratedInformationCalculator();
        this.soul = soul;
        this.cohesionThreshold = cohesionThreshold;
        this.sigmaSoul = sigmaSoul;
    }

    /**
     * Backward-compatible accessor returning the soul as an {@link AgentSoul} if applicable.
     */
    public AgentSoul agentSoul() {
        return soul instanceof AgentSoul agent ? agent : null;
    }

    /**
     * Returns the persistent soul context.
     */
    public com.spectrayan.spector.memory.model.SoulContext soul() {
        return soul;
    }

    /**
     * Evaluates a candidate memory cluster, computing raw Phi, soul alignment, and composite Phi_CC.
     *
     * @param candidateVectors list of candidate memory embedding vectors
     * @return ConsciousnessContinuityState
     */
    public ConsciousnessContinuityState evaluate(List<float[]> candidateVectors) {
        if (candidateVectors == null || candidateVectors.isEmpty()) {
            return ConsciousnessContinuityState.empty();
        }

        float rawPhi = calculator.calculatePhi(candidateVectors);
        float soulAlignment = 1.0f;

        if (soul != null && soul.identityEmbedding() != null && !candidateVectors.isEmpty()) {
            float[] centerOfMass = computeCenterOfMass(candidateVectors);
            float[] soulEmb = soul.identityEmbedding();
            if (centerOfMass.length == soulEmb.length) {
                float dist = EuclideanDistance.compute(centerOfMass, soulEmb);
                soulAlignment = (float) Math.exp(-(dist * dist) / (2.0 * sigmaSoul * sigmaSoul));
            }
        }

        float compositePhiCC = rawPhi * soulAlignment;
        boolean isCohesive = compositePhiCC >= cohesionThreshold || candidateVectors.size() <= 1;

        if (log.isTraceEnabled()) {
            log.trace("Evaluated Phi_CC: rawPhi={}, soulAlign={}, composite={}, cohesive={}",
                    rawPhi, soulAlignment, compositePhiCC, isCohesive);
        }

        return new ConsciousnessContinuityState(
                rawPhi,
                soulAlignment,
                compositePhiCC,
                isCohesive,
                candidateVectors.size(),
                System.currentTimeMillis()
        );
    }

    private static float[] computeCenterOfMass(List<float[]> vectors) {
        int dim = vectors.get(0).length;
        float[] mean = new float[dim];
        int count = vectors.size();
        for (float[] v : vectors) {
            if (v != null && v.length == dim) {
                for (int d = 0; d < dim; d++) {
                    mean[d] += v[d];
                }
            }
        }
        for (int d = 0; d < dim; d++) {
            mean[d] /= count;
        }
        return mean;
    }

    public float cohesionThreshold() {
        return cohesionThreshold;
    }
}
