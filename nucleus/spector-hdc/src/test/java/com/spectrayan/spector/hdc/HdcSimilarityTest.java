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

public class HdcSimilarityTest {

    @Test
    void testEndToEndIdentical() {
        HdcSimilarity sim = new HdcSimilarity();
        double score = sim.similarity("spector", "spector");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void testEndToEndDifferent() {
        HdcSimilarity sim = new HdcSimilarity();
        double score = sim.similarity("spector", "database");
        assertThat(score).isLessThan(1.0);
    }

    @Test
    void testBuilderPattern() {
        HdcSimilarity sim = HdcSimilarity.builder()
                .dimensions(5_000)
                .ngramSize(4)
                .build();
        
        double score = sim.similarity("java", "java");
        assertThat(score).isEqualTo(1.0);
    }
}
