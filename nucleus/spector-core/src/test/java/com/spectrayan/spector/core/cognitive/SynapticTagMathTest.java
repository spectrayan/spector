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

    @Test
    @DisplayName("128-bit encode tag produces K_128 bits bounded between 1 and 4")
    void encodeTag128BitBounds() {
        SynapticTag128 tag = SynapticTagMath.encodeTag128("neuroscience");
        int count = tag.popcount();
        assertThat(count).isBetween(1, 4);
        assertThat(tag.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("128-bit tag matches itself and handles non-matching tag")
    void tagMatchesSelf128() {
        SynapticTag128 filter = SynapticTagMath.encode128("memory", "episodic", "hippocampus");
        assertThat(SynapticTagMath.matches128(filter, "memory")).isTrue();
        assertThat(SynapticTagMath.matches128(filter, "episodic")).isTrue();
        assertThat(SynapticTagMath.matches128(filter, "hippocampus")).isTrue();
        assertThat(SynapticTagMath.matches128(filter, "nonexistent-tag")).isFalse();
    }

    @Test
    @DisplayName("128-bit overlap ratio is 1.0 on exact subset")
    void overlapRatio128FullMatch() {
        SynapticTag128 record = SynapticTagMath.encode128("alpha", "beta", "gamma", "delta");
        SynapticTag128 query = SynapticTagMath.encode128("alpha", "gamma");
        assertThat(SynapticTagMath.overlapRatio128(record.lo(), record.hi(), query.lo(), query.hi()))
                .isEqualTo(1.0f);
        assertThat(record.overlapRatio(query)).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("128-bit merge combines low and high words")
    void merge128() {
        SynapticTag128 t1 = SynapticTagMath.encodeTag128("tag1");
        SynapticTag128 t2 = SynapticTagMath.encodeTag128("tag2");
        SynapticTag128 merged = t1.merge(t2);
        assertThat(merged.lo()).isEqualTo(t1.lo() | t2.lo());
        assertThat(merged.hi()).isEqualTo(t1.hi() | t2.hi());
        assertThat(merged.matches(t1)).isTrue();
        assertThat(merged.matches(t2)).isTrue();
    }

    @Test
    @DisplayName("128-bit false positive probability is monotonic and lower than 64-bit for same tag count")
    void fpp128Bounds() {
        double p1 = SynapticTagMath.falsePositiveProbability128(5);
        double p2 = SynapticTagMath.falsePositiveProbability128(10);
        assertThat(p1).isLessThan(p2);
        assertThat(p1).isLessThan(SynapticTagMath.falsePositiveProbability(5));
    }
}
