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

public final class Valence {
    private Valence() {}

    public static final byte STRONGLY_POSITIVE = 100;
    public static final byte POSITIVE = 50;
    public static final byte NEUTRAL = 0;
    public static final byte NEGATIVE = -50;
    public static final byte STRONGLY_NEGATIVE = -100;

    public static byte clamp(int value) {
        return (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, value));
    }

    public static boolean isPositive(byte valence) {
        return valence > 10;
    }

    public static boolean isNegative(byte valence) {
        return valence < -10;
    }

    public static byte blend(byte existing, byte newValue, float alpha) {
        float blended = existing * (1.0f - alpha) + newValue * alpha;
        return clamp(Math.round(blended));
    }
}
