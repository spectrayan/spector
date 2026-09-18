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
package com.spectrayan.spector.memory.neuromod.inhibition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuppressionSetTest {

    @Test
    void newSetIsEmpty() {
        var set = new SuppressionSet();
        assertThat(set.size()).isZero();
        assertThat(set.isSuppressed("memory-1")).isFalse();
    }

    @Test
    void suppressAndCheck() {
        var set = new SuppressionSet();
        set.suppress("memory-1", "incorrect answer");
        assertThat(set.isSuppressed("memory-1")).isTrue();
        assertThat(set.size()).isEqualTo(1);
    }

    @Test
    void unsuppressRemoves() {
        var set = new SuppressionSet();
        set.suppress("memory-1");
        set.unsuppress("memory-1");
        assertThat(set.isSuppressed("memory-1")).isFalse();
        assertThat(set.size()).isZero();
    }

    @Test
    void clearRemovesAll() {
        var set = new SuppressionSet();
        set.suppress("m1");
        set.suppress("m2");
        set.suppress("m3");
        assertThat(set.size()).isEqualTo(3);

        set.clear();
        assertThat(set.size()).isZero();
    }

    @Test
    void suppressedIdsReturnsUnmodifiableView() {
        var set = new SuppressionSet();
        set.suppress("m1");
        set.suppress("m2");

        var ids = set.suppressedIds();
        assertThat(ids).containsExactlyInAnyOrder("m1", "m2");
    }
}
