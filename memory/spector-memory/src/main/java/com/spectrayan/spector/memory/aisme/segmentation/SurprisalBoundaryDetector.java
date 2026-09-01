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
package com.spectrayan.spector.memory.aisme.segmentation;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.FreeEnergyKernel;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;

/**
 * Dual-criteria episode boundary detector fusing Bayesian Online Change-Point Detection (BOCPD)
 * and variational predictive coding surprisal thresholds.
 *
 * <h3>Biological Analog: Multi-Criteria Prefrontal-Hippocampal Event Segmentation</h3>
 * <p>Identifies autobiographical episode cuts triggered by statistical distribution shifts,
 * acute prediction error shocks, or buffer lifespan timeouts.</p>
 */
public final class SurprisalBoundaryDetector {

    public record BoundaryEvaluation(
            boolean isBoundary,
            BoundaryReason reason,
            float changePointProbability,
            float surprisal
    ) {}

    private final BayesianOnlineChangePointDetector bocpd;
    private final float changePointThreshold;
    private final float surprisalCutThreshold;
    private final int maxEpisodeFrames;

    public SurprisalBoundaryDetector(
            BayesianOnlineChangePointDetector bocpd,
            float changePointThreshold,
            float surprisalCutThreshold,
            int maxEpisodeFrames
    ) {
        if (bocpd == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "BOCPD detector must not be null");
        }
        if (changePointThreshold < 0.0f || changePointThreshold > 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "changePointThreshold must be in [0, 1]");
        }
        if (surprisalCutThreshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "surprisalCutThreshold must be non-negative");
        }
        if (maxEpisodeFrames < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxEpisodeFrames must be at least 1");
        }

        this.bocpd = bocpd;
        this.changePointThreshold = changePointThreshold;
        this.surprisalCutThreshold = surprisalCutThreshold;
        this.maxEpisodeFrames = maxEpisodeFrames;
    }

    public static SurprisalBoundaryDetector fromConfig(AismeConfig config, int dimensions, float[] priorMean, float[] obsPrecision) {
        if (config == null) {
            BayesianOnlineChangePointDetector detector = new BayesianOnlineChangePointDetector(dimensions, 100.0f, 150, priorMean, obsPrecision);
            return new SurprisalBoundaryDetector(detector, 0.65f, 1.50f, 200);
        }

        BayesianOnlineChangePointDetector detector = new BayesianOnlineChangePointDetector(
                dimensions,
                config.bocpdHazardLambda(),
                config.bocpdMaxRunLength(),
                priorMean,
                obsPrecision
        );

        return new SurprisalBoundaryDetector(
                detector,
                config.bocpdChangePointThreshold(),
                config.bocpdSurprisalCutThreshold(),
                config.bocpdMaxEpisodeFrames()
        );
    }

    /**
     * Evaluates whether an incoming observation triggers an autobiographical episode boundary cut.
     *
     * @param posteriorMean working posterior belief mean \(q(s_t)\)
     * @param observation sensory observation vector \(o_t\)
     * @param obsPrecision sensory observation precision \(\pi_o\)
     * @param currentFrameCount number of frames currently buffered in the active episode
     * @return BoundaryEvaluation result
     */
    public BoundaryEvaluation evaluate(float[] posteriorMean, float[] observation, float[] obsPrecision, int currentFrameCount) {
        if (posteriorMean == null || observation == null || obsPrecision == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Evaluation vectors must not be null");
        }

        float cpProb = bocpd.update(observation);
        float surprisal = FreeEnergyKernel.sensorySurprisal(posteriorMean, observation, obsPrecision);

        if (cpProb >= changePointThreshold) {
            return new BoundaryEvaluation(true, BoundaryReason.BOCPD_CHANGE_POINT, cpProb, surprisal);
        }
        if (surprisal >= surprisalCutThreshold) {
            return new BoundaryEvaluation(true, BoundaryReason.SURPRISAL_SPIKE, cpProb, surprisal);
        }
        if (currentFrameCount >= maxEpisodeFrames) {
            return new BoundaryEvaluation(true, BoundaryReason.MAX_DURATION_TIMEOUT, cpProb, surprisal);
        }

        return new BoundaryEvaluation(false, null, cpProb, surprisal);
    }

    /**
     * Resets the underlying change-point detector after an episode boundary cut.
     */
    public void reset() {
        bocpd.reset();
    }

    public BayesianOnlineChangePointDetector bocpd() {
        return bocpd;
    }

    public float changePointThreshold() {
        return changePointThreshold;
    }

    public float surprisalCutThreshold() {
        return surprisalCutThreshold;
    }

    public int maxEpisodeFrames() {
        return maxEpisodeFrames;
    }
}
