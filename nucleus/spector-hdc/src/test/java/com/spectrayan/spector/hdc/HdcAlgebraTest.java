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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;

public class HdcAlgebraTest {

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testBindSelfInverse(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector result = HdcAlgebra.bind(a, a);
        assertThat(result.words()).containsOnly(0L);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testBindReversibility(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector b = Hypervector.random(dims, 43L);
        Hypervector result = HdcAlgebra.bind(HdcAlgebra.bind(a, b), b);
        assertThat(result).isEqualTo(a);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testBundle(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector b = Hypervector.random(dims, 43L);
        Hypervector c = Hypervector.random(dims, 44L);
        
        Hypervector bundled = HdcAlgebra.bundle(Arrays.asList(a, b, c));
        
        assertThat(HammingDistance.similarity(bundled, a)).isGreaterThan(0.5);
        assertThat(HammingDistance.similarity(bundled, b)).isGreaterThan(0.5);
        assertThat(HammingDistance.similarity(bundled, c)).isGreaterThan(0.5);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testPermuteIdentity(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector result = HdcAlgebra.permute(a, 0);
        assertThat(result).isEqualTo(a);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testPermuteDissimilarity(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector result = HdcAlgebra.permute(a, 50);
        double sim = HammingDistance.similarity(a, result);
        assertThat(sim).isBetween(0.4, 0.6); // ≈ 0.5 for orthogonal
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void testInverse(int dims) {
        Hypervector a = Hypervector.random(dims, 42L);
        Hypervector inv = HdcAlgebra.inverse(a);
        Hypervector bound = HdcAlgebra.bind(a, inv);
        
        // bound should be all ones up to dimensions
        // Actually, XOR with its complement yields all 1s
        for (int i = 0; i < bound.words().length - 1; i++) {
            assertThat(bound.words()[i]).isEqualTo(-1L);
        }
    }
}
