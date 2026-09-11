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
 * Pure mathematical kernel for sensory habituation diminishing returns and Inhibition of Return (IOR)
 * refractory period recovery (ADR-0033 Domain 7).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class HabituationKernel {

    private HabituationKernel() {}

    /**
     * Computes the repetition suppression multiplier for a memory returned multiple times:
     * <p>{@code P(k) = 1 / (1 + (k - 1) · λ)}</p>
     *
     * @param timesReturned number of times the memory was returned (>= 1)
     * @param decayRate     habituation rate λ (higher = faster habituation)
     * @return suppression multiplier in (0.0, 1.0]
     */
    public static float penalty(final int timesReturned, final float decayRate) {
        if (timesReturned <= 1) {
            return 1.0f;
        }
        return 1.0f / (1.0f + (timesReturned - 1) * decayRate);
    }

    /**
     * Computes the Inhibition of Return (IOR) refractory recovery multiplier:
     * <p>{@code IOR(Δt) = floor + (1 - floor) · min(1.0, Δt / TTL)}</p>
     *
     * @param elapsedMs elapsed milliseconds since last recall
     * @param ttlMs     refractory period TTL in milliseconds
     * @param floor     minimum multiplier during immediate recall (e.g. 0.1)
     * @return recovery multiplier in [floor, 1.0]
     */
    public static float inhibitionOfReturn(final long elapsedMs, final long ttlMs, final float floor) {
        if (ttlMs <= 0 || elapsedMs >= ttlMs) {
            return 1.0f;
        }
        if (elapsedMs <= 0) {
            return floor;
        }
        final float ratio = (float) elapsedMs / (float) ttlMs;
        return floor + (1.0f - floor) * Math.min(1.0f, ratio);
    }
}
