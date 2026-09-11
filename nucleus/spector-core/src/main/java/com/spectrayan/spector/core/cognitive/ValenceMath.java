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
 * Pure mathematical functions and biological constants for affective valence computation.
 *
 * <p>Contract: ADR-0033 Purity Tier T1 (Pure Static). Unifies previously duplicated
 * implementations in {@code spector-kernel} and {@code spector-memory}. Operates strictly
 * on signed {@code byte} values in the range [-128, 127].</p>
 *
 * <h3>Biological Analog: Amygdala Outcome Valuation</h3>
 * <p>Represents learned outcome emotional polarity (reward vs punishment). Positive valence
 * indicates utility / problem resolution, while negative valence marks failures or counterproductive actions.</p>
 */
public final class ValenceMath {

    private ValenceMath() {
        // utility class
    }

    /** Strong positive outcome (e.g. agent response solved user issue). */
    public static final byte STRONGLY_POSITIVE = 100;

    /** Mild positive outcome. */
    public static final byte POSITIVE = 50;

    /** Neutral / unvalued outcome. */
    public static final byte NEUTRAL = 0;

    /** Mild negative outcome. */
    public static final byte NEGATIVE = -50;

    /** Strong negative outcome (e.g. fatal failure or rejected response). */
    public static final byte STRONGLY_NEGATIVE = -100;

    /**
     * Clamps an integer value to the valid signed byte range [-128, 127].
     *
     * @param value raw input integer
     * @return clamped byte
     */
    public static byte clamp(int value) {
        return (byte) Math.clamp(value, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    /**
     * Tests whether a valence score is considered distinctly positive.
     *
     * @param valence signed byte score
     * @return true if valence &gt; 10
     */
    public static boolean isPositive(byte valence) {
        return valence > 10;
    }

    /**
     * Tests whether a valence score is considered distinctly negative.
     *
     * @param valence signed byte score
     * @return true if valence &lt; -10
     */
    public static boolean isNegative(byte valence) {
        return valence < -10;
    }

    /**
     * Exponentially blends an existing valence with a new outcome observation.
     *
     * @param existing prior established valence
     * @param newValue newly observed outcome valence
     * @param alpha    learning rate / blending coefficient in range [0.0, 1.0]
     * @return updated blended byte valence
     */
    public static byte blend(byte existing, byte newValue, float alpha) {
        float blended = existing * (1.0f - alpha) + newValue * alpha;
        return clamp(Math.round(blended));
    }

    /**
     * Computes Bower (1981) mood congruence between two valence scores in range [0.0, 1.0].
     *
     * @param v1 first valence score
     * @param v2 second valence score
     * @return normalized congruence score where 1.0 is identical mood and 0.0 is maximum divergence
     */
    public static float congruence(byte v1, byte v2) {
        float diff = Math.abs((int) v1 - (int) v2) / 255.0f;
        return 1.0f - diff;
    }
}
