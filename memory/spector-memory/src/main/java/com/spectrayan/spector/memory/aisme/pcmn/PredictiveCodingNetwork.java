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
package com.spectrayan.spector.memory.aisme.pcmn;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.PredictiveCodingKernel;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Hierarchical 4-tier Predictive Coding Memory Network engine.
 *
 * <h3>Biological Analog: Multi-Tier Cortical Predictive Processing</h3>
 * <p>Orchestrates top-down generative predictions and bottom-up precision-weighted prediction
 * error signals across Working, Episodic, Semantic, and Procedural/Insular memory tiers.</p>
 */
public final class PredictiveCodingNetwork {

    private static final Logger log = LoggerFactory.getLogger(PredictiveCodingNetwork.class);

    private final int dimensions;
    private final float[][] tierPrecisions;
    private final float[][][] tierWeights; // [tier][dim][dim]

    /**
     * Constructs a PredictiveCodingNetwork with uniform precision and identity top-down generative mappings.
     *
     * @param dimensions embedding space dimensionality
     * @param tierCount number of hierarchical tiers (typically 4: Working, Episodic, Semantic, Procedural)
     */
    public PredictiveCodingNetwork(int dimensions, int tierCount) {
        this(dimensions, tierCount, CognitiveProfile.BALANCED);
    }

    /**
     * Constructs a PredictiveCodingNetwork parameterized by a CognitiveProfile.
     *
     * @param dimensions embedding dimensionality
     * @param tierCount number of tiers
     * @param profile cognitive profile modulating tier precision
     */
    public PredictiveCodingNetwork(int dimensions, int tierCount, CognitiveProfile profile) {
        if (dimensions <= 0 || tierCount <= 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions and tierCount must be positive (tierCount > 1)");
        }
        this.dimensions = dimensions;
        this.tierPrecisions = new float[tierCount][dimensions];
        this.tierWeights = new float[tierCount - 1][dimensions][dimensions];

        float basePrecision = 2.0f;
        if (profile != null) {
            switch (profile) {
                case HYPERFOCUS -> basePrecision = 5.0f;
                case SYSTEMATIZER -> basePrecision = 4.0f;
                case DIVERGENT -> basePrecision = 1.0f;
                default -> basePrecision = 2.0f;
            }
        }

        // Initialize tier precisions (increasing precision towards sensory/working memory)
        for (int l = 0; l < tierCount; l++) {
            float tierMultiplier = 1.0f + (0.5f * (tierCount - 1 - l));
            Arrays.fill(tierPrecisions[l], basePrecision * tierMultiplier);
        }

        // Initialize identity top-down generative projection matrices
        for (int l = 0; l < tierCount - 1; l++) {
            for (int d = 0; d < dimensions; d++) {
                tierWeights[l][d][d] = 1.0f;
            }
        }
    }

    /**
     * Evaluates the multi-tier hierarchy against observed tier representations.
     *
     * @param actualTiers array of actual vectors for each tier [0..tierCount-1]
     * @return HierarchicalPredictionError containing per-tier errors and total energy
     */
    public HierarchicalPredictionError evaluateHierarchy(float[][] actualTiers) {
        if (actualTiers == null || actualTiers.length != tierPrecisions.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Actual tier array length must match network tier count");
        }

        int numTiers = tierPrecisions.length;
        float[][] predictedTiers = new float[numTiers][dimensions];
        float[][] weightedErrors = new float[numTiers][dimensions];
        float[] energies = new float[numTiers];
        float totalEnergy = 0.0f;

        // Top-level tier prediction is initialized to its own prior/representation
        predictedTiers[numTiers - 1] = Arrays.copyOf(actualTiers[numTiers - 1], dimensions);

        // Top-down generative pass: predict tier l from tier l+1
        for (int l = numTiers - 2; l >= 0; l--) {
            PredictiveCodingKernel.affineProjection(actualTiers[l + 1], tierWeights[l], null, predictedTiers[l]);
        }

        // Bottom-up error pass: calculate precision-weighted errors and energies
        for (int l = 0; l < numTiers; l++) {
            PredictiveCodingKernel.computePrecisionWeightedError(
                    actualTiers[l],
                    predictedTiers[l],
                    tierPrecisions[l],
                    weightedErrors[l]
            );

            float tierE = PredictiveCodingKernel.computeTierEnergy(
                    actualTiers[l],
                    predictedTiers[l],
                    tierPrecisions[l]
            );
            energies[l] = tierE;
            totalEnergy += tierE;
        }

        if (log.isTraceEnabled()) {
            log.trace("Evaluated predictive coding hierarchy with {} tiers: total energy={}", numTiers, totalEnergy);
        }

        return new HierarchicalPredictionError(weightedErrors, energies, totalEnergy, System.currentTimeMillis());
    }

    public int dimensions() {
        return dimensions;
    }

    public int tierCount() {
        return tierPrecisions.length;
    }
}
