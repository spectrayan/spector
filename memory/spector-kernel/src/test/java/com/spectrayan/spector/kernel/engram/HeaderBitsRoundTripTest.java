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
package com.spectrayan.spector.kernel.engram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HeaderBits Round-Trip and Version Guard Tests (Task 6.6 / R7.4)")
class HeaderBitsRoundTripTest {

    @Test
    @DisplayName("Verify exact round-trip packing for all discrete and continuous fields")
    void testHeaderBitsRoundTrip() {
        byte flags = (byte) 0b0011_0101;
        byte valence = (byte) -42;
        byte arousal = (byte) 210;
        int agentRecallCount = 75;
        float importance = 7.85f;
        float storageStrength = 3.45f;
        int typeOrdinal = 2; // SEMANTIC

        long bits = HeaderBits.pack(flags, valence, arousal, agentRecallCount, importance, storageStrength, typeOrdinal);

        HeaderBits.checkVersion(bits);
        assertThat(HeaderBits.version(bits)).isEqualTo(HeaderBits.VERSION);
        assertThat(HeaderBits.flags(bits)).isEqualTo(flags);
        assertThat(HeaderBits.valence(bits)).isEqualTo(valence);
        assertThat(HeaderBits.arousal(bits)).isEqualTo(arousal);
        assertThat(HeaderBits.agentRecallCount(bits)).isEqualTo(agentRecallCount);
        assertThat(HeaderBits.typeOrdinal(bits)).isEqualTo(typeOrdinal);

        assertThat(HeaderBits.importance(bits)).isCloseTo(importance, org.assertj.core.data.Offset.offset(0.002f));
        assertThat(HeaderBits.storageStrength(bits)).isCloseTo(storageStrength, org.assertj.core.data.Offset.offset(0.02f));
    }

    @Test
    @DisplayName("Verify version guard throws on invalid version")
    void testVersionGuard() {
        long badBits = HeaderBits.pack(2, (byte) 0, (byte) 0, (byte) 0, 0, 1.0f, 1.0f, 0);
        assertThatThrownBy(() -> HeaderBits.checkVersion(badBits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported HeaderBits version: 2");
    }

    @Test
    @DisplayName("Verify boundary values for min and max bounds")
    void testBoundaryValues() {
        long minBits = HeaderBits.pack(
                (byte) 0, (byte) -128, (byte) 0, 0, 0.0f, 1.0f, 0
        );
        assertThat(HeaderBits.valence(minBits)).isEqualTo((byte) -128);
        assertThat(HeaderBits.arousal(minBits)).isEqualTo((byte) 0);
        assertThat(HeaderBits.importance(minBits)).isZero();
        assertThat(HeaderBits.storageStrength(minBits)).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.02f));

        long maxBits = HeaderBits.pack(
                (byte) 0xFF, (byte) 127, (byte) 255, 300, 15.0f, 6.0f, 15
        );
        assertThat(HeaderBits.valence(maxBits)).isEqualTo((byte) 127);
        assertThat(HeaderBits.arousal(maxBits)).isEqualTo((byte) 255);
        assertThat(HeaderBits.agentRecallCount(maxBits)).isEqualTo(255); // clamped
        assertThat(HeaderBits.importance(maxBits)).isCloseTo(10.0f, org.assertj.core.data.Offset.offset(0.02f)); // clamped to 10.0
        assertThat(HeaderBits.storageStrength(maxBits)).isCloseTo(5.0f, org.assertj.core.data.Offset.offset(0.02f)); // clamped to 5.0
        assertThat(HeaderBits.typeOrdinal(maxBits)).isEqualTo(15);
    }
}
