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

/**
 * Immutable metrics evaluating the instantaneous information-theoretic event density \(\nu(o_t)\)
 * and epistemic compression state for an incoming multimodal observation frame.
 *
 * <h3>Biological Analog: Retinal & Cochlear Epistemic Novelty Transduction</h3>
 * <p>Quantifies the informational surprise, state divergence, and predictive coding gradient
 * of continuous sensory observations to determine sensory gating and sampling rate adaptation.</p>
 *
 * @param klDivergence analytical KL divergence \(D_{\text{KL}}(q(s_t) \parallel p(s_t))\) between working posterior and prior
 * @param freeEnergyGradientNorm precision-weighted prediction error gradient magnitude \(\|\nabla \mathcal{F}(o_t)\|\)
 * @param surprisal normalized sensory surprisal / quadratic prediction error \(\text{Surprise}(o_t)\)
 * @param eventDensity fused instantaneous event density score \(\nu(o_t)\)
 * @param isSalientSpike {@code true} if \(\nu(o_t) \ge \tau_{\text{density}}\), indicating an informative event spike
 * @param dynamicSamplingRateHz recommended sensor sampling rate in Hz
 */
public record EventDensityMetrics(
        float klDivergence,
        float freeEnergyGradientNorm,
        float surprisal,
        float eventDensity,
        boolean isSalientSpike,
        float dynamicSamplingRateHz
) {
    public static EventDensityMetrics zero(float defaultSamplingRateHz) {
        return new EventDensityMetrics(0.0f, 0.0f, 0.0f, 0.0f, false, defaultSamplingRateHz);
    }
}
