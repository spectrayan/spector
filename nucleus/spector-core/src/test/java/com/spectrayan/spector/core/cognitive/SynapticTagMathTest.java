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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SynapticTagMathTest {

    @Test
    @DisplayName("Encode tag produces exactly K bits or fewer due to hash collision")
    void encodeTagBitBounds() {
        long filter = SynapticTagMath.encodeTag("neuroscience");
        int count = SynapticTagMath.bitCount(filter);
        assertThat(count).isBetween(1, 3);
    }

    @Test
    @DisplayName("Tag matches itself")
    void tagMatchesSelf() {
        long filter = SynapticTagMath.encodeTag("memory");
        assertThat(SynapticTagMath.matches(filter, "memory")).isTrue();
    }

    @Test
    @DisplayName("Overlap ratio is 1.0 when query is subset of record")
    void overlapRatioFullMatch() {
        long filter = SynapticTagMath.encode("alpha", "beta", "gamma");
        long query = SynapticTagMath.encode("alpha", "beta");
        assertThat(SynapticTagMath.overlapRatio(filter, query)).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("Merge performs bitwise OR")
    void mergeFilters() {
        long f1 = SynapticTagMath.encodeTag("tag1");
        long f2 = SynapticTagMath.encodeTag("tag2");
        long merged = SynapticTagMath.merge(f1, f2);
        assertThat(merged).isEqualTo(f1 | f2);
    }

    @Test
    @DisplayName("False positive probability monotonic with numTags")
    void fppMonotonic() {
        double p1 = SynapticTagMath.falsePositiveProbability(5);
        double p2 = SynapticTagMath.falsePositiveProbability(10);
        assertThat(p1).isLessThan(p2);
    }

    @Property
    void overlapRatioAlwaysBounded(
            @ForAll long record,
            @ForAll long query) {
        float ratio = SynapticTagMath.overlapRatio(record, query);
        assertThat(ratio).isBetween(0.0f, 1.0f);
    }
}
