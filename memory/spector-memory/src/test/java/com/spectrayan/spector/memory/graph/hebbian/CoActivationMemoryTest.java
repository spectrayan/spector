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
package com.spectrayan.spector.memory.graph.hebbian;
import com.spectrayan.spector.kernel.store.CoActivationMemory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoActivationMemoryTest {

    @Test
    void initialCountIsZero() {
        var tracker = new CoActivationMemory();
        assertThat(tracker.getCoActivation("java", "python")).isZero();
    }

    @Test
    void recordCoActivationIncrements() {
        var tracker = new CoActivationMemory();
        tracker.recordCoActivation("java", "performance");
        assertThat(tracker.getCoActivation("java", "performance")).isEqualTo(1);

        tracker.recordCoActivation("java", "performance");
        assertThat(tracker.getCoActivation("java", "performance")).isEqualTo(2);
    }

    @Test
    void pairKeyIsCanonical() {
        var tracker = new CoActivationMemory();
        tracker.recordCoActivation("java", "python");
        // Reverse order should access same pair
        assertThat(tracker.getCoActivation("python", "java")).isEqualTo(1);
    }

    @Test
    void getAssociatedTagsReturnsSorted() {
        var tracker = new CoActivationMemory();
        for (int i = 0; i < 5; i++) tracker.recordCoActivation("java", "performance");
        for (int i = 0; i < 3; i++) tracker.recordCoActivation("java", "gc");
        tracker.recordCoActivation("java", "concurrency");

        var associated = tracker.getAssociatedTags("java", 3);
        assertThat(associated).hasSize(3);
        assertThat(associated.getFirst()).isEqualTo("performance"); // highest count
    }

    @Test
    void singleTagDoesNotRecord() {
        var tracker = new CoActivationMemory();
        tracker.recordCoActivation("java");
        assertThat(tracker.pairCount()).isZero();
    }

    @Test
    void resetClearsAll() {
        var tracker = new CoActivationMemory();
        tracker.recordCoActivation("java", "python", "rust");
        assertThat(tracker.pairCount()).isGreaterThan(0);

        tracker.reset();
        assertThat(tracker.pairCount()).isZero();
    }
}
