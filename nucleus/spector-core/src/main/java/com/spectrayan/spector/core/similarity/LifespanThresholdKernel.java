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
package com.spectrayan.spector.core.similarity;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Mathematical kernel evaluating the dynamic lifespan-adaptive forgetting and retention threshold \(\tau(t)\).
 *
 * <h3>Theoretical Formulation</h3>
 * <pre>
 * \tau(t) = \text{clamp}\left( \tau_0 \cdot \left(1 + k \cdot \ln\left(1 + \frac{t}{T_0}\right)\right) \cdot \left(\frac{V(t)}{V_{\text{target}}}\right)^\gamma, 0.0, 1.0 \right)
 * </pre>
 *
 * <p>Where:
 * <ul>
 *   <li>\(\tau_0\) is the baseline retention threshold (default 0.30).</li>
 *   <li>\(k\) is the lifespan logarithmic scaling factor (default 0.15).</li>
 *   <li>\(t\) is the elapsed operational lifespan epoch count (sleep reflection cycles).</li>
 *   <li>\(T_0\) is the characteristic lifespan epoch scaling constant (e.g. 365 cycles / 1 year).</li>
 *   <li>\(V(t)\) is the current active episodic / working memory volume.</li>
 *   <li>\(V_{\text{target}}\) is the steady-state target storage volume capacity.</li>
 *   <li>\(\gamma\) is the capacity utilization power exponent tuning pruning aggressiveness under storage pressure.</li>
 * </ul>
 */
public final class LifespanThresholdKernel {

    private LifespanThresholdKernel() {
        // Utility class
    }

    /**
     * Computes the lifespan-adaptive retention threshold \(\tau(t)\).
     *
     * @param tau0 baseline retention threshold \([0.0, 1.0]\)
     * @param k logarithmic lifespan scaling rate (\(\ge 0.0\))
     * @param operationalEpoch elapsed operational epoch count \(t \ge 0\)
     * @param t0Epochs characteristic lifespan epoch scaling constant \(T_0 > 0\)
     * @param currentVolume current active stored memory count / volume \(V(t) \ge 0\)
     * @param targetVolume target steady-state memory volume \(V_{\text{target}} > 0\)
     * @param gamma capacity pressure exponent (\(\gamma \ge 0.0\))
     * @return dynamic retention threshold clamped to \([0.0, 1.0]\)
     */
    public static float compute(
            final float tau0,
            final float k,
            final long operationalEpoch,
            final long t0Epochs,
            final long currentVolume,
            final long targetVolume,
            final float gamma) {

        if (tau0 < 0.0f || tau0 > 1.0f) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "tau0 must be between 0.0 and 1.0, got: " + tau0);
        }
        if (k < 0.0f) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "k must be non-negative, got: " + k);
        }
        if (t0Epochs <= 0) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "t0Epochs must be positive, got: " + t0Epochs);
        }
        if (targetVolume <= 0) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "targetVolume must be positive, got: " + targetVolume);
        }
        if (gamma < 0.0f) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "gamma must be non-negative, got: " + gamma);
        }

        long safeEpoch = Math.max(0L, operationalEpoch);
        long safeVolume = Math.max(0L, currentVolume);

        // Age factor: 1 + k * ln(1 + t / T0)
        double normalizedAge = (double) safeEpoch / (double) t0Epochs;
        double ageFactor = 1.0 + (double) k * Math.log1p(normalizedAge);

        // Capacity pressure factor: (V(t) / V_target) ^ gamma
        double volumeRatio = (double) safeVolume / (double) targetVolume;
        double capacityFactor = Math.pow(volumeRatio, (double) gamma);

        double rawTau = (double) tau0 * ageFactor * capacityFactor;

        // Clamp to [0.0, 1.0]
        if (Double.isNaN(rawTau) || rawTau <= 0.0) {
            return 0.0f;
        }
        if (rawTau >= 1.0) {
            return 1.0f;
        }
        return (float) rawTau;
    }

    /**
     * Batch evaluation of \(\tau(t)\) across multiple volume states.
     *
     * @param tau0 baseline retention threshold \([0.0, 1.0]\)
     * @param k logarithmic lifespan scaling rate (\(\ge 0.0\))
     * @param operationalEpoch elapsed operational epoch count \(t \ge 0\)
     * @param t0Epochs characteristic lifespan epoch scaling constant \(T_0 > 0\)
     * @param volumes array of current stored memory volumes
     * @param targetVolume target steady-state memory volume \(V_{\text{target}} > 0\)
     * @param gamma capacity pressure exponent (\(\gamma \ge 0.0\))
     * @param output destination array for evaluated \(\tau(t)\) thresholds
     */
    public static void computeBatch(
            final float tau0,
            final float k,
            final long operationalEpoch,
            final long t0Epochs,
            final long[] volumes,
            final long targetVolume,
            final float gamma,
            final float[] output) {

        if (volumes == null || output == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "volumes and output arrays must not be null");
        }
        int length = Math.min(volumes.length, output.length);
        for (int i = 0; i < length; i++) {
            output[i] = compute(tau0, k, operationalEpoch, t0Epochs, volumes[i], targetVolume, gamma);
        }
    }
}
