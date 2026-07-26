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
package com.spectrayan.spector.hdc;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;

public class HypervectorTest {

    @Test
    void testRandomProducesCorrectDimensionsAndWordCount() {
        int dims = 10_000;
        Hypervector v = Hypervector.random(dims, 42L);
        assertThat(v.dimensions()).isEqualTo(dims);
        assertThat(v.words()).hasSize((dims + 63) / 64);
    }

    @Test
    void testDefensiveCopy() {
        int dims = 64;
        long[] bits = new long[]{1L};
        Hypervector v = Hypervector.fromBits(bits, dims);
        
        bits[0] = 2L;
        assertThat(v.words()[0]).isEqualTo(1L);
    }

    @Test
    void testZeroHasAllZeroBits() {
        int dims = 128;
        Hypervector v = Hypervector.zero(dims);
        assertThat(v.words()).containsOnly(0L);
    }

    @Test
    void testEqualsAndHashCode() {
        long[] bits = new long[]{123L, 456L};
        Hypervector v1 = Hypervector.fromBits(bits, 100);
        Hypervector v2 = Hypervector.fromBits(bits, 100);
        Hypervector v3 = Hypervector.fromBits(new long[]{123L, 789L}, 100);
        
        assertThat(v1).isEqualTo(v2);
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
        assertThat(v1).isNotEqualTo(v3);
    }

    @Test
    void testFromBitsRoundtrip() {
        int dims = 100;
        long[] bits = new long[]{ -1L, 42L };
        Hypervector v = Hypervector.fromBits(bits, dims);
        
        assertThat(v.words()).isEqualTo(bits);
        assertThat(v.dimensions()).isEqualTo(dims);
    }
}
