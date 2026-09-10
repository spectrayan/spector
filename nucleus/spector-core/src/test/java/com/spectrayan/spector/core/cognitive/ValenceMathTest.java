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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.ByteRange;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit and property-based tests for {@link ValenceMath}.
 */
class ValenceMathTest {

    @Test
    void canonicalBiologicalConstants() {
        assertThat(ValenceMath.STRONGLY_POSITIVE).isEqualTo((byte) 100);
        assertThat(ValenceMath.POSITIVE).isEqualTo((byte) 50);
        assertThat(ValenceMath.NEUTRAL).isEqualTo((byte) 0);
        assertThat(ValenceMath.NEGATIVE).isEqualTo((byte) -50);
        assertThat(ValenceMath.STRONGLY_NEGATIVE).isEqualTo((byte) -100);
    }

    @Test
    void clampingRange() {
        assertThat(ValenceMath.clamp(200)).isEqualTo(Byte.MAX_VALUE);
        assertThat(ValenceMath.clamp(-300)).isEqualTo(Byte.MIN_VALUE);
        assertThat(ValenceMath.clamp(42)).isEqualTo((byte) 42);
    }

    @Test
    void positivityAndNegativityThresholds() {
        assertThat(ValenceMath.isPositive((byte) 11)).isTrue();
        assertThat(ValenceMath.isPositive((byte) 10)).isFalse();
        assertThat(ValenceMath.isPositive((byte) 0)).isFalse();

        assertThat(ValenceMath.isNegative((byte) -11)).isTrue();
        assertThat(ValenceMath.isNegative((byte) -10)).isFalse();
        assertThat(ValenceMath.isNegative((byte) 0)).isFalse();
    }

    @Test
    void blendingFollowsExponentialWeighting() {
        assertThat(ValenceMath.blend((byte) 0, (byte) 100, 0.5f)).isEqualTo((byte) 50);
        assertThat(ValenceMath.blend((byte) 100, (byte) 0, 0.0f)).isEqualTo((byte) 100);
        assertThat(ValenceMath.blend((byte) 0, (byte) 100, 1.0f)).isEqualTo((byte) 100);
    }

    @Test
    void congruenceSymmetryAndBoundaries() {
        assertThat(ValenceMath.congruence((byte) 50, (byte) 50)).isEqualTo(1.0f);
        assertThat(ValenceMath.congruence(Byte.MAX_VALUE, Byte.MIN_VALUE)).isEqualTo(0.0f);
        assertThat(ValenceMath.congruence((byte) -30, (byte) 40))
                .isCloseTo(ValenceMath.congruence((byte) 40, (byte) -30), within(1e-6f));
    }

    @Property(tries = 200)
    void congruenceAlwaysNormalized(
            @ForAll @ByteRange(min = -128, max = 127) byte v1,
            @ForAll @ByteRange(min = -128, max = 127) byte v2
    ) {
        float c = ValenceMath.congruence(v1, v2);
        assertThat(c).isBetween(0.0f, 1.0f);
    }

    @Property(tries = 200)
    void blendStaysBounded(
            @ForAll @ByteRange(min = -128, max = 127) byte b1,
            @ForAll @ByteRange(min = -128, max = 127) byte b2,
            @ForAll @FloatRange(min = 0.0f, max = 1.0f) float alpha
    ) {
        byte res = ValenceMath.blend(b1, b2, alpha);
        int min = Math.min(b1, b2);
        int max = Math.max(b1, b2);
        assertThat(res).isBetween((byte) min, (byte) max);
    }
}
