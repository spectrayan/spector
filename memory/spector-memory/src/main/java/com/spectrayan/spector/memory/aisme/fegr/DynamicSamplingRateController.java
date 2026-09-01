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

/**
 * Dynamically modulates sensor sampling frequency \(f(t)\) based on instantaneous epistemic event density \(\nu(o_t)\).
 *
 * <h3>Biological Analog: Pupillary Saccadic Rate Modulation & Hippocampal Theta Gating</h3>
 * <p>Scales down sensory intake frequency to baseline during redundant or familiar environments,
 * while rapidly upscaling sampling frequency to capture high-density novelty bursts.</p>
 */
public final class DynamicSamplingRateController {

    private final float minSamplingRateHz;
    private final float maxSamplingRateHz;
    private final float densityThreshold;
    private final float temperature;

    public DynamicSamplingRateController(float minSamplingRateHz, float maxSamplingRateHz, float densityThreshold) {
        this(minSamplingRateHz, maxSamplingRateHz, densityThreshold, 0.15f);
    }

    public DynamicSamplingRateController(float minSamplingRateHz, float maxSamplingRateHz, float densityThreshold, float temperature) {
        if (minSamplingRateHz <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "minSamplingRateHz must be positive");
        }
        if (maxSamplingRateHz < minSamplingRateHz) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxSamplingRateHz must be >= minSamplingRateHz");
        }
        if (densityThreshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "densityThreshold must be non-negative");
        }
        if (temperature <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "temperature must be positive");
        }
        this.minSamplingRateHz = minSamplingRateHz;
        this.maxSamplingRateHz = maxSamplingRateHz;
        this.densityThreshold = densityThreshold;
        this.temperature = temperature;
    }

    /**
     * Computes the recommended sensor sampling rate in Hz for the given event density \(\nu(o_t)\).
     *
     * @param eventDensity instantaneous event density score \(\nu(o_t)\)
     * @return sampling rate in \([f_{\text{min}}, f_{\text{max}}]\)
     */
    public float computeSamplingRate(float eventDensity) {
        float normalizedSigmoid = 1.0f / (1.0f + (float) Math.exp(-(eventDensity - densityThreshold) / temperature));
        float targetRate = minSamplingRateHz + (maxSamplingRateHz - minSamplingRateHz) * normalizedSigmoid;
        return Math.clamp(targetRate, minSamplingRateHz, maxSamplingRateHz);
    }

    public float minSamplingRateHz() {
        return minSamplingRateHz;
    }

    public float maxSamplingRateHz() {
        return maxSamplingRateHz;
    }

    public float densityThreshold() {
        return densityThreshold;
    }

    public float temperature() {
        return temperature;
    }
}
