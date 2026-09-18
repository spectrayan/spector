/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.aisme.fegr;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.EventDensityKernel;

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
        return EventDensityKernel.computeDynamicSamplingRate(
                eventDensity, densityThreshold, temperature, minSamplingRateHz, maxSamplingRateHz);
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
