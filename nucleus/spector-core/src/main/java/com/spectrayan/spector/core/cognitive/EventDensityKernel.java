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
package com.spectrayan.spector.core.cognitive;

/**
 * Pure mathematical kernel for epistemic event density gating and dynamic sampling rate modulation
 * (ADR-0033 Domain 13, #33).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class EventDensityKernel {

    private EventDensityKernel() {}

    /**
     * Computes instantaneous information-theoretic event density:
     * <p>{@code ν(o_t) = α · D_KL(q ∥ p) + β · ∥∇_s F∥ + γ · Surprise}</p>
     *
     * @param klDivergence KL divergence between posterior and prior \(D_{\text{KL}}(q \parallel p)\)
     * @param gradNorm     free energy gradient norm \(\|\nabla_s F\|\)
     * @param surprise     sensory surprise
     * @param alpha        KL weight \(\alpha\)
     * @param beta         gradient norm weight \(\beta\)
     * @param gamma        surprise weight \(\gamma\)
     * @return composite event density score
     */
    public static float computeEventDensity(
            final float klDivergence,
            final float gradNorm,
            final float surprise,
            final float alpha,
            final float beta,
            final float gamma) {
        return (alpha * klDivergence) + (beta * gradNorm) + (gamma * surprise);
    }

    /**
     * Computes the dynamic sensor sampling rate in Hz based on instantaneous event density:
     * <p>{@code rate = minHz + (maxHz - minHz) · σ((ν - θ) / T)}</p>
     *
     * @param eventDensity instantaneous event density score \(\nu\)
     * @param threshold    salience threshold \(\theta\)
     * @param temperature  transition temperature \(T &gt; 0\)
     * @param minHz        minimum baseline sampling frequency in Hz
     * @param maxHz        maximum novelty burst sampling frequency in Hz
     * @return clamped sampling frequency in \([minHz, maxHz]\)
     */
    public static float computeDynamicSamplingRate(
            final float eventDensity,
            final float threshold,
            final float temperature,
            final float minHz,
            final float maxHz) {
        final float temp = (temperature > 0.0f) ? temperature : 0.15f;
        final float normalizedSigmoid = 1.0f / (1.0f + (float) Math.exp(-(eventDensity - threshold) / temp));
        final float targetRate = minHz + (maxHz - minHz) * normalizedSigmoid;
        return Math.clamp(targetRate, minHz, maxHz);
    }
}
