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
package com.spectrayan.spector.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScoredResult} verifying ranking behavior and
 * Valhalla / JEP 390 value-based class certification.
 */
class ScoredResultTest {

    @Test
    @DisplayName("ScoredResult records components accurately")
    void recordComponents() {
        ScoredResult r = new ScoredResult("doc-1", 42, 0.95f);
        assertThat(r.id()).isEqualTo("doc-1");
        assertThat(r.index()).isEqualTo(42);
        assertThat(r.score()).isEqualTo(0.95f);
    }

    @Test
    @DisplayName("Natural ordering sorts descending by score (highest first)")
    void naturalOrderingDescending() {
        ScoredResult low = new ScoredResult("a", 0, 0.1f);
        ScoredResult mid = new ScoredResult("b", 1, 0.5f);
        ScoredResult high = new ScoredResult("c", 2, 0.9f);

        List<ScoredResult> list = new ArrayList<>(List.of(mid, low, high));
        Collections.sort(list);

        assertThat(list).containsExactly(high, mid, low);
    }

    @Test
    @DisplayName("compareAscending sorts ascending by score (lowest first for distance)")
    void compareAscendingOrdering() {
        ScoredResult near = new ScoredResult("a", 0, 0.1f);
        ScoredResult far = new ScoredResult("b", 1, 0.8f);

        assertThat(ScoredResult.compareAscending(near, far)).isNegative();
        assertThat(ScoredResult.compareAscending(far, near)).isPositive();
        assertThat(ScoredResult.compareAscending(near, near)).isZero();
    }

    @Test
    @DisplayName("Valhalla / JEP 390 value-based class certification")
    void valhallaValueBasedCertification() {
        // Must be an immutable record
        assertThat(ScoredResult.class.isRecord()).isTrue();

        // Separate instances with identical values must be substitutable
        ScoredResult a = new ScoredResult("doc-99", 10, 0.88f);
        ScoredResult b = new ScoredResult("doc-99", 10, 0.88f);

        assertThat(a).isNotSameAs(b);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).isEqualTo(b.toString());

        // Different field yields inequality
        ScoredResult c = new ScoredResult("doc-99", 10, 0.89f);
        assertThat(a).isNotEqualTo(c);
    }
}
