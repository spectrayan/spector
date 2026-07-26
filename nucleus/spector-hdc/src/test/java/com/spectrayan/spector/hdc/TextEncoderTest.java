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

public class TextEncoderTest {

    @Test
    void testDeterminism() {
        TextEncoder encoder = new TextEncoder(10_000, 3);
        String text = "hello world";
        Hypervector v1 = encoder.encode(text);
        Hypervector v2 = encoder.encode(text);
        assertThat(v1).isEqualTo(v2);
    }

    @Test
    void testIdenticalTexts() {
        TextEncoder encoder = new TextEncoder(10_000, 3);
        Hypervector v1 = encoder.encode("spector database");
        Hypervector v2 = encoder.encode("spector database");
        assertThat(HammingDistance.similarity(v1, v2)).isEqualTo(1.0);
    }

    @Test
    void testSimilarTexts() {
        TextEncoder encoder = new TextEncoder(10_000, 3);
        Hypervector v1 = encoder.encode("hello world");
        Hypervector v2 = encoder.encode("hello there");
        assertThat(HammingDistance.similarity(v1, v2)).isGreaterThan(0.5);
    }

    @Test
    void testDifferentTexts() {
        TextEncoder encoder = new TextEncoder(10_000, 3);
        Hypervector v1 = encoder.encode("apple");
        Hypervector v2 = encoder.encode("motorcycle");
        assertThat(HammingDistance.similarity(v1, v2)).isBetween(0.4, 0.6);
    }
}
