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
 * Pure mathematical kernel for linear modulation mapping of descriptive personality traits to scoring multipliers
 * (ADR-0033 Domain 9).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class PersonalityTraitKernel {

    private PersonalityTraitKernel() {}

    /**
     * Linearly projects a trait score to a centered multiplier:
     * <p>{@code modifier = center + ((traitScore - midpoint) / range) * amplitude}</p>
     *
     * @param traitScore raw trait dimension score
     * @param midpoint   neutral midpoint for the trait
     * @param range      normalizing range span
     * @param amplitude  scaling amplitude
     * @param center     neutral baseline multiplier
     * @return derived scoring modifier
     */
    public static float linearModulate(
            final float traitScore, final float midpoint, final float range,
            final float amplitude, final float center) {
        if (range == 0.0f) {
            return center;
        }
        return center + ((traitScore - midpoint) / range) * amplitude;
    }
}
