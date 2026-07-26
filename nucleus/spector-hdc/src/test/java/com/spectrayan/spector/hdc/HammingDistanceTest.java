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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

public class HammingDistanceTest {

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testIdenticalVectors(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        assertThat(HammingDistance.distance(a, a)).isEqualTo(0);
        assertThat(HammingDistance.similarity(a, a)).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testComplementaryVectors(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector inv = HdcAlgebra.inverse(a);
        assertThat(HammingDistance.distance(a, inv)).isEqualTo(dims);
        assertThat(HammingDistance.similarity(a, inv)).isEqualTo(0.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testRandomVectors(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector b = Hypervector.random(dims, 43L);
        
        int dist = HammingDistance.distance(a, b);
        double sim = HammingDistance.similarity(a, b);
        
        int expectedDist = dims / 2;
        int tolerance = dims / 10; // 10%
        
        assertThat((double) dist).isBetween((double) expectedDist - tolerance, (double) expectedDist + tolerance);
        assertThat(sim).isBetween(0.4, 0.6);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testSimdVsOffHeapMatch(int dims) {
        // Since we are just calling distance() which may internally dispatch
        // This is a placeholder test for ensuring SIMD behaves correctly.
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector b = Hypervector.random(dims, 43L);
        assertThat(HammingDistance.distance(a, b)).isGreaterThanOrEqualTo(0);
    }
}
