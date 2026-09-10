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
 * Pure mathematical kernel for Bi &amp; Poo (1998) asymmetric Spike-Timing-Dependent Plasticity (STDP)
 * (ADR-0033 Domain 4).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class StdpPlasticityKernel {

    public static final float DEFAULT_A_PLUS = 0.1f;
    public static final float DEFAULT_A_MINUS = 0.05f;
    public static final float DEFAULT_TAU_PLUS = 30000.0f;
    public static final float DEFAULT_TAU_MINUS = 30000.0f;

    private StdpPlasticityKernel() {}

    /**
     * Computes causal LTP weight potentiation:
     * <p>{@code dW_causal = A_+ · exp(-Δt / τ_+)}</p>
     *
     * @param dtMs  positive time difference (t_after - t_before) in milliseconds
     * @param aPlus causal amplitude
     * @param tauPlus causal time constant
     * @return weight increment
     */
    public static float computeCausalDeltaWeight(final long dtMs, final float aPlus, final float tauPlus) {
        if (dtMs < 0 || tauPlus <= 0.0f) {
            return 0.0f;
        }
        return aPlus * (float) Math.exp(-dtMs / tauPlus);
    }

    /**
     * Computes anti-causal LTD weight depression:
     * <p>{@code dW_anti = -A_- · exp(-Δt / τ_-)}</p>
     *
     * @param dtMs     positive time difference in milliseconds
     * @param aMinus   anti-causal amplitude
     * @param tauMinus anti-causal time constant
     * @return negative weight increment
     */
    public static float computeAntiCausalDeltaWeight(final long dtMs, final float aMinus, final float tauMinus) {
        if (dtMs < 0 || tauMinus <= 0.0f) {
            return 0.0f;
        }
        return -aMinus * (float) Math.exp(-dtMs / tauMinus);
    }

    /**
     * Computes asymmetric STDP delta weight based on signed time delta:
     * <ul>
     *   <li>dt &gt; 0: causal potentiation</li>
     *   <li>dt &lt; 0: anti-causal depression</li>
     *   <li>dt == 0: 0.0f</li>
     * </ul>
     */
    public static float computeDeltaWeight(
            final long dtMs, final float aPlus, final float aMinus, final float tauPlus, final float tauMinus) {
        if (dtMs > 0) {
            return computeCausalDeltaWeight(dtMs, aPlus, tauPlus);
        } else if (dtMs < 0) {
            return computeAntiCausalDeltaWeight(Math.abs(dtMs), aMinus, tauMinus);
        }
        return 0.0f;
    }

    /**
     * Updates an edge weight with delta, clamped to bounds [minWeight, maxWeight].
     */
    public static float updateWeight(
            final float currentWeight, final float deltaWeight, final float minWeight, final float maxWeight) {
        return Math.clamp(currentWeight + deltaWeight, minWeight, maxWeight);
    }
}
