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
package com.spectrayan.spector.kernel.score;

import com.spectrayan.spector.core.cognitive.ValenceMath;

/**
 * Valence constants and outcome adjustment utilities.
 *
 * @deprecated Use {@link ValenceMath} instead. Scheduled for removal in 0.3.0.
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
public final class Valence {
    private Valence() {}

    public static final byte STRONGLY_POSITIVE = ValenceMath.STRONGLY_POSITIVE;
    public static final byte POSITIVE = ValenceMath.POSITIVE;
    public static final byte NEUTRAL = ValenceMath.NEUTRAL;
    public static final byte NEGATIVE = ValenceMath.NEGATIVE;
    public static final byte STRONGLY_NEGATIVE = ValenceMath.STRONGLY_NEGATIVE;

    public static byte clamp(int value) {
        return ValenceMath.clamp(value);
    }

    public static boolean isPositive(byte valence) {
        return ValenceMath.isPositive(valence);
    }

    public static boolean isNegative(byte valence) {
        return ValenceMath.isNegative(valence);
    }

    public static byte blend(byte existing, byte newValue, float alpha) {
        return ValenceMath.blend(existing, newValue, alpha);
    }
}
