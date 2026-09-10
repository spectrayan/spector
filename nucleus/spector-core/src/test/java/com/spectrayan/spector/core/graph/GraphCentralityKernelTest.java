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
package com.spectrayan.spector.core.graph;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphCentralityKernelTest {

    @Test
    @DisplayName("Single edge node is a critical bridge (score 255)")
    void singleEdgeIsCriticalBridge() {
        int score = GraphCentralityKernel.neighborOverlapBridgeScore(0, 1, 1);
        assertThat(score).isEqualTo(255);
    }

    @Test
    @DisplayName("Fully overlapping neighbors yields bridge score 0")
    void fullOverlapYieldsZero() {
        int score = GraphCentralityKernel.neighborOverlapBridgeScore(5, 5, 5);
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("Count shared neighbors identifies common elements")
    void countSharedNeighborsBasic() {
        int[] a = {1, 3, 5, 7};
        int[] b = {2, 3, 6, 7, 8};
        int count = GraphCentralityKernel.countSharedNeighbors(a, 4, b, 5);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Wilson spanning tree deterministic given seed")
    void wilsonDeterministic() {
        int[][] adj = new int[4][];
        adj[0] = new int[]{1, 2};
        adj[1] = new int[]{0, 3};
        adj[2] = new int[]{0, 3};
        adj[3] = new int[]{1, 2};

        int[][] scores1 = GraphCentralityKernel.computeWilsonBridgeScores(adj, 4, 20, 1000, 42L);
        int[][] scores2 = GraphCentralityKernel.computeWilsonBridgeScores(adj, 4, 20, 1000, 42L);

        assertThat(scores1).isDeepEqualTo(scores2);
    }

    @Property
    void bridgeScoreAlwaysInValidRange(
            @ForAll @IntRange(min = 0, max = 50) int shared,
            @ForAll @IntRange(min = 0, max = 50) int degA,
            @ForAll @IntRange(min = 0, max = 50) int degB) {
        int score = GraphCentralityKernel.neighborOverlapBridgeScore(shared, degA, degB);
        assertThat(score).isBetween(0, 255);
    }
}
