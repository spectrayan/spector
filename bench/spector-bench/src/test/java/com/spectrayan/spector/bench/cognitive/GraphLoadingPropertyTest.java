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
package com.spectrayan.spector.bench.cognitive;

import java.util.Set;
import java.util.stream.Collectors;

import com.spectrayan.spector.memory.graph.EntityType;
import com.spectrayan.spector.memory.graph.RelationType;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for graph loading invariants.
 *
 * <p><b>Validates: Requirements 5.1, 6.1, 7.1</b>
 *
 * <p>Properties 14, 16:
 * <ul>
 *   <li>14: Hebbian edges are bidirectional after loading</li>
 *   <li>16: Temporal chain is doubly-linked after loading</li>
 * </ul>
 */
class GraphLoadingPropertyTest {

    // ══════════════════════════════════════════════════════════════
    // Property 14: Hebbian bidirectional edges
    // ══════════════════════════════════════════════════════════════

    /**
     * Property 14: After strengthening a Hebbian edge between two nodes,
     * both nodes' neighbor lists contain the other.
     *
     * <p><b>Validates: Requirements 5.1</b>
     */
    @Property(tries = 100)
    void hebbianEdge_isBidirectional(
            @ForAll @IntRange(min = 0, max = 99) int nodeA,
            @ForAll @IntRange(min = 0, max = 99) int nodeB) {

        if (nodeA == nodeB) return; // skip self-loops

        HebbianGraph graph = new HebbianGraph(100);
        graph.strengthen(nodeA, nodeB, 1.0f);

        // Check bidirectionality
        Set<Integer> neighborsOfA = graph.neighbors(nodeA).stream()
                .map(e -> e.neighborIndex())
                .collect(Collectors.toSet());
        Set<Integer> neighborsOfB = graph.neighbors(nodeB).stream()
                .map(e -> e.neighborIndex())
                .collect(Collectors.toSet());

        assert neighborsOfA.contains(nodeB)
                : "Node B should be in neighbors of A after strengthen(A, B)";
        assert neighborsOfB.contains(nodeA)
                : "Node A should be in neighbors of B after strengthen(A, B)";

        graph.close();
    }

    // ══════════════════════════════════════════════════════════════
    // Property 16: Temporal chain doubly-linked list integrity
    // ══════════════════════════════════════════════════════════════

    /**
     * Property 16: After linking a temporal chain [m0, m1, ..., mn],
     * next(mi) == m(i+1) and prev(m(i+1)) == mi.
     *
     * <p><b>Validates: Requirements 6.1</b>
     */
    @Property(tries = 100)
    void temporalChain_isDoublyLinked(
            @ForAll @IntRange(min = 3, max = 15) int chainLength) {

        TemporalChainMemory chain = new TemporalChainMemory(100);

        // Link a chain of sequential indices
        int sessionId = 42;
        for (int i = 1; i < chainLength; i++) {
            chain.link(i, i - 1, sessionId);
        }

        // Verify forward traversal from the start
        int[] forward = chain.followForward(0, chainLength);
        for (int i = 0; i < forward.length; i++) {
            assert forward[i] == i + 1
                    : String.format("Forward from 0: expected %d at hop %d, got %d",
                    i + 1, i, forward[i]);
        }

        // Verify backward traversal from the end
        int[] backward = chain.followBackward(chainLength - 1, chainLength);
        for (int i = 0; i < backward.length; i++) {
            assert backward[i] == chainLength - 2 - i
                    : String.format("Backward from %d: expected %d at hop %d, got %d",
                    chainLength - 1, chainLength - 2 - i, i, backward[i]);
        }

        chain.close();
    }

    // ══════════════════════════════════════════════════════════════
    // Generators
    // ══════════════════════════════════════════════════════════════

    @Provide
    Arbitrary<String> relationTypes() {
        return Arbitraries.of(RelationType.SEED);
    }
}
