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
import com.spectrayan.spector.memory.aisme.config.AismeConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance information-theoretic event density evaluator and epistemic compression filter.
 *
 * <h3>Biological Analog: Thalamocortical Epistemic Gating & Sensory Adaptation</h3>
 * <p>Evaluates the instantaneous event density \(\nu(o_t)\) across active generative beliefs,
 * filtering redundant sensory frames during static intervals while prioritizing high-entropy spikes.</p>
 */
public final class EventDensityFilter {

    private static final Logger log = LoggerFactory.getLogger(EventDensityFilter.class);

    private final float threshold;
    private final float alphaKl;
    private final float betaGradient;
    private final float gammaSurprise;
    private final DynamicSamplingRateController rateController;

    public EventDensityFilter(float threshold, float alphaKl, float betaGradient, float gammaSurprise,
                              float minSamplingRateHz, float maxSamplingRateHz) {
        if (threshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "threshold must be non-negative");
        }
        if (alphaKl < 0.0f || betaGradient < 0.0f || gammaSurprise < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Weights must be non-negative");
        }
        float sumWeights = alphaKl + betaGradient + gammaSurprise;
        if (sumWeights <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Sum of weights must be positive");
        }

        this.threshold = threshold;
        this.alphaKl = alphaKl;
        this.betaGradient = betaGradient;
        this.gammaSurprise = gammaSurprise;
        this.rateController = new DynamicSamplingRateController(minSamplingRateHz, maxSamplingRateHz, threshold);
    }

    public static EventDensityFilter fromConfig(AismeConfig config) {
        if (config == null) {
            return new EventDensityFilter(0.50f, 0.40f, 0.30f, 0.30f, 0.10f, 30.0f);
        }
        return new EventDensityFilter(
                config.eventDensityThreshold(),
                config.eventDensityAlphaKl(),
                config.eventDensityBetaGradient(),
                config.eventDensityGammaSurprise(),
                config.eventDensitySamplingMinHz(),
                config.eventDensitySamplingMaxHz()
        );
    }

    /**
     * Evaluates instantaneous information-theoretic event density \(\nu(o_t)\) and gating metrics.
     *
     * @param posterior current working posterior belief distribution \(q(s_t)\)
     * @param selfModel generative self-model providing prior \(p(s_t)\) and observation precision
     * @param observation sensory observation vector \(o_t\)
     * @return evaluated EventDensityMetrics
     */
    public EventDensityMetrics evaluate(MentalStatePosterior posterior, GenerativeSelfModel selfModel, float[] observation) {
        if (posterior == null || selfModel == null || observation == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        if (posterior.dimensions() != selfModel.dimensions() || observation.length != selfModel.dimensions()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions mismatch between posterior, self-model, and observation");
        }

        float kl = FreeEnergyKernel.gaussianKLDivergence(
                posterior.mean(),
                posterior.precision(),
                selfModel.priorMean(),
                selfModel.priorPrecision()
        );

        float gradNorm = FreeEnergyKernel.freeEnergyGradientNorm(
                posterior.mean(),
                observation,
                selfModel.observationPrecision()
        );

        float surprisal = FreeEnergyKernel.sensorySurprisal(
                posterior.mean(),
                observation,
                selfModel.observationPrecision()
        );

        float eventDensity = (alphaKl * kl) + (betaGradient * gradNorm) + (gammaSurprise * surprisal);
        boolean isSalient = eventDensity >= threshold;
        float samplingRate = rateController.computeSamplingRate(eventDensity);

        if (log.isTraceEnabled()) {
            log.trace("EventDensity: kl={}, gradNorm={}, surprisal={}, density={}, salient={}, samplingRate={}Hz",
                    kl, gradNorm, surprisal, eventDensity, isSalient, samplingRate);
        }

        return new EventDensityMetrics(kl, gradNorm, surprisal, eventDensity, isSalient, samplingRate);
    }

    public float threshold() {
        return threshold;
    }

    public float alphaKl() {
        return alphaKl;
    }

    public float betaGradient() {
        return betaGradient;
    }

    public float gammaSurprise() {
        return gammaSurprise;
    }

    public DynamicSamplingRateController rateController() {
        return rateController;
    }
}
