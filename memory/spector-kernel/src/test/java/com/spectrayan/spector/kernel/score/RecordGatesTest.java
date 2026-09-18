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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordGatesTest {

    @Test
    @DisplayName("isTagGated128 returns false when query masks are 0")
    void isTagGated128_zeroMasks_passes() {
        boolean gated = RecordGates.isTagGated128(0x1234L, 0x5678L, 0L, 0L, 0L, 0L);
        assertThat(gated).isFalse();
    }

    @Test
    @DisplayName("isTagGated128 passes when record matches in low word only")
    void isTagGated128_matchLowOnly_passes() {
        long recordLo = 0b0001_0000L;
        long recordHi = 0L;
        long queryLo = 0b0001_0000L;
        long queryHi = 0L;

        boolean gated = RecordGates.isTagGated128(recordLo, recordHi, queryLo, queryHi, 0L, 0L);
        assertThat(gated).isFalse();
    }

    @Test
    @DisplayName("isTagGated128 passes when record matches in high word only (#795)")
    void isTagGated128_matchHighOnly_passes() {
        long recordLo = 0L;
        long recordHi = 0x8000_0000_0000_0001L;
        long queryLo = 0L;
        long queryHi = 0x8000_0000_0000_0001L;

        boolean gated = RecordGates.isTagGated128(recordLo, recordHi, queryLo, queryHi, 0L, 0L);
        assertThat(gated).isFalse();
    }

    @Test
    @DisplayName("isTagGated128 gates out when query shares no bits with either partition")
    void isTagGated128_noOverlap_gatedOut() {
        long recordLo = 0b0001L;
        long recordHi = 0b0010L;
        long queryLo = 0b0100L;
        long queryHi = 0b1000L;

        boolean gated = RecordGates.isTagGated128(recordLo, recordHi, queryLo, queryHi, 0L, 0L);
        assertThat(gated).isTrue();
    }

    @Test
    @DisplayName("isTagGated128 hyperfocus strict gating requires exact containment across both words")
    void isTagGated128_hyperfocusStrictContainment() {
        long hFocusLo = 0x1111L;
        long hFocusHi = 0x2222L;

        // Missing high word -> gated out
        assertThat(RecordGates.isTagGated128(0x1111L, 0L, 0L, 0L, hFocusLo, hFocusHi)).isTrue();

        // Missing low word -> gated out
        assertThat(RecordGates.isTagGated128(0L, 0x2222L, 0L, 0L, hFocusLo, hFocusHi)).isTrue();

        // Has both words with superset bits -> passes
        assertThat(RecordGates.isTagGated128(0x1111L | 0x4444L, 0x2222L | 0x8888L, 0L, 0L, hFocusLo, hFocusHi)).isFalse();
    }

    @Test
    @DisplayName("isTemporalGated respects causal horizon and min/max boundaries")
    void temporalGating() {
        long now = 10_000L;
        // Future timestamp when allowFuture is false
        assertThat(RecordGates.isTemporalGated(15_000L, null, null, now, false)).isTrue();
        // Future timestamp when allowFuture is true
        assertThat(RecordGates.isTemporalGated(15_000L, null, null, now, true)).isFalse();
        // Within bounds
        assertThat(RecordGates.isTemporalGated(5_000L, 1_000L, 8_000L, now, false)).isFalse();
        // Below min
        assertThat(RecordGates.isTemporalGated(500L, 1_000L, 8_000L, now, false)).isTrue();
    }

    @Test
    @DisplayName("isValenceGated enforces signed byte bounds")
    void valenceGating() {
        assertThat(RecordGates.isValenceGated((byte) 0, (byte) -10, (byte) 10)).isFalse();
        assertThat(RecordGates.isValenceGated((byte) 20, (byte) -10, (byte) 10)).isTrue();
        assertThat(RecordGates.isValenceGated((byte) -20, (byte) -10, (byte) 10)).isTrue();
    }
}
