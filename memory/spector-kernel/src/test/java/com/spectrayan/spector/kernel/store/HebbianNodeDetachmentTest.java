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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.score.EdgeImportance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests node detachment across both Hebbian graph layouts — the dense V2 {@link HebbianGraph} and the sparse
 * CSR V3 {@link HebbianGraphMemory}.
 *
 * <p>Both are covered because both are reachable at runtime, and a purge that only detached from the
 * preferred implementation would silently leave associations intact on any store using the other. The
 * parameterised tests run identically against each.</p>
 */
@DisplayName("Hebbian node detachment (purge support)")
class HebbianNodeDetachmentTest {

    private static final int CAPACITY = 32;
    private static final int MAX_DEGREE = 8;

    private static final String DENSE = "dense-v2";
    private static final String CSR = "csr-v3";

    private static HebbianGraphBase newGraph(String flavour) {
        return switch (flavour) {
            case DENSE -> new HebbianGraph(CAPACITY, MAX_DEGREE);
            case CSR -> new HebbianGraphMemory(CAPACITY, CAPACITY * MAX_DEGREE, MAX_DEGREE, EdgeImportance.DEFAULT);
            default -> throw new IllegalArgumentException(flavour);
        };
    }

    private static void withGraph(String flavour, ThrowingConsumer<HebbianGraphBase> body) throws Throwable {
        HebbianGraphBase g = newGraph(flavour);
        try {
            body.accept(g);
        } finally {
            g.close();
        }
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {DENSE, CSR})
    @DisplayName("detaching a node removes its outgoing edges and every edge pointing back at it")
    void detachRemovesBothDirections(String flavour) throws Throwable {
        withGraph(flavour, g -> {
            // A hub: 5 is connected to 1..4. strengthen is symmetric, so each pair is two edges.
            g.strengthen(5, 1, 1.0f);
            g.strengthen(5, 2, 1.0f);
            g.strengthen(5, 3, 1.0f);
            g.strengthen(5, 4, 1.0f);
            // An unrelated pair that must survive untouched.
            g.strengthen(10, 11, 1.0f);

            int before = g.totalEdges();
            assertThat(g.degree(5)).isEqualTo(4);
            assertThat(g.degree(1)).isEqualTo(1);

            int removed = g.removeNode(5);

            // 4 outgoing + 4 incoming.
            assertThat(removed).isEqualTo(8);
            assertThat(g.degree(5)).isZero();
            // The peers must no longer point back. This is the half a naive implementation forgets, and
            // it is the half that matters: traversal follows the peers' rows, not the purged node's.
            for (int peer = 1; peer <= 4; peer++) {
                assertThat(g.degree(peer)).as("peer %d degree", peer).isZero();
                assertThat(g.neighbors(peer)).as("peer %d neighbours", peer).isEmpty();
            }
            assertThat(g.hasAnyEdge(5)).isFalse();

            // Edge counts reconcile, and the unrelated pair is intact.
            assertThat(g.totalEdges()).isEqualTo(before - removed);
            assertThat(g.degree(10)).isEqualTo(1);
            assertThat(g.neighbors(10)).singleElement()
                    .satisfies(e -> assertThat(e.neighborIndex()).isEqualTo(11));
        });
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {DENSE, CSR})
    @DisplayName("no surviving edge points at node 0 — the failure mode of zeroing an edge in place")
    void detachDoesNotFabricateEdgesToNodeZero(String flavour) throws Throwable {
        withGraph(flavour, g -> {
            // Node 0 is deliberately left with no edges at all, so any edge to 0 after the detach is
            // fabricated rather than pre-existing.
            g.strengthen(7, 1, 1.0f);
            g.strengthen(7, 2, 1.0f);
            g.strengthen(1, 2, 1.0f);

            g.removeNode(7);

            // Zeroing an edge slot in place would leave it counted toward degree and surface it as an edge
            // to node 0 — inventing an association instead of removing one. This is the assertion that
            // catches that "optimisation" if anyone reintroduces it.
            for (int node = 0; node < CAPACITY; node++) {
                assertThat(g.neighbors(node))
                        .as("node %d must not have gained an edge to node 0", node)
                        .noneSatisfy(e -> assertThat(e.neighborIndex()).isZero());
            }
            assertThat(g.degree(0)).isZero();
            assertThat(g.hasAnyEdge(0)).isFalse();
            // The untouched 1↔2 association survives.
            assertThat(g.totalEdges()).isEqualTo(2);
        });
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {DENSE, CSR})
    @DisplayName("surviving adjacency rows keep their weights, not just their shape")
    void detachPreservesSurvivingWeights(String flavour) throws Throwable {
        withGraph(flavour, g -> {
            g.strengthen(1, 2, 0.75f);
            g.strengthen(1, 9, 0.25f);
            float before = g.neighbors(1).stream()
                    .filter(e -> e.neighborIndex() == 2)
                    .findFirst().orElseThrow().weight();

            g.removeNode(9);

            assertThat(g.neighbors(1)).singleElement().satisfies(e -> {
                assertThat(e.neighborIndex()).isEqualTo(2);
                // A rebuild that shifted entries but lost their payload would pass a shape-only assertion.
                assertThat(e.weight()).isEqualTo(before);
            });
        });
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {DENSE, CSR})
    @DisplayName("detaching an unconnected or out-of-range node is a no-op, not an error")
    void detachIsSafeForAbsentNodes(String flavour) throws Throwable {
        withGraph(flavour, g -> {
            g.strengthen(1, 2, 1.0f);
            assertThat(g.removeNode(20)).isZero();
            assertThat(g.removeNode(-1)).isZero();
            assertThat(g.removeNode(CAPACITY)).isZero();
            assertThat(g.removeNode(CAPACITY + 100)).isZero();
            assertThat(g.totalEdges()).isEqualTo(2);
        });
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {DENSE, CSR})
    @DisplayName("detaching is idempotent")
    void detachTwiceRemovesNothingTheSecondTime(String flavour) throws Throwable {
        withGraph(flavour, g -> {
            g.strengthen(3, 4, 1.0f);
            assertThat(g.removeNode(3)).isEqualTo(2);
            assertThat(g.removeNode(3)).isZero();
            assertThat(g.totalEdges()).isZero();
        });
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {DENSE, CSR})
    @DisplayName("batch detachment removes every selected node in one pass")
    void batchDetach(String flavour) throws Throwable {
        withGraph(flavour, g -> {
            g.strengthen(1, 2, 1.0f);
            g.strengthen(3, 4, 1.0f);
            g.strengthen(5, 6, 1.0f);

            int removed = g.removeNode(1) + g.removeNode(3);

            assertThat(removed).isEqualTo(4);
            assertThat(g.hasAnyEdge(1)).isFalse();
            assertThat(g.hasAnyEdge(3)).isFalse();
            assertThat(g.hasAnyEdge(5)).isTrue();
            assertThat(g.totalEdges()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("CSR batch removal detaches many nodes in a single rebuild")
    void csrRemoveNodesBatch() {
        try (HebbianGraphMemory g = new HebbianGraphMemory(CAPACITY, CAPACITY * MAX_DEGREE, MAX_DEGREE, EdgeImportance.DEFAULT)) {
            g.strengthen(1, 2, 1.0f);
            g.strengthen(3, 4, 1.0f);
            g.strengthen(5, 6, 1.0f);

            int removed = g.removeNodes(n -> n == 1 || n == 3);

            assertThat(removed).isEqualTo(4);
            assertThat(g.totalEdges()).isEqualTo(2);
            assertThat(g.hasAnyEdge(1)).isFalse();
            assertThat(g.hasAnyEdge(2)).isFalse();
            assertThat(g.hasAnyEdge(5)).isTrue();
        }
    }

    @Test
    @DisplayName("CSR removeEdge(int) refuses rather than silently doing nothing")
    void csrRemoveEdgeThrows() {
        try (HebbianGraphMemory g = new HebbianGraphMemory(CAPACITY, CAPACITY * MAX_DEGREE, MAX_DEGREE, EdgeImportance.DEFAULT)) {
            g.strengthen(1, 2, 1.0f);
            // This used to be a no-op with an explanatory comment, which told callers an edge had been
            // removed when nothing had happened. CSR positions are renumbered by every rebuild, so there
            // is no id to honour — refusing is the only honest answer.
            assertThatThrownBy(() -> g.removeEdge(0))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("removeNode")
                    .hasMessageContaining("no stable edge ids");
            assertThat(g.totalEdges()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("CSR detachment survives edges that spilled into per-node overflow lists")
    void csrDetachmentCoversOverflow() {
        // maxDegree 2 forces overflow quickly, so the detach has to reach the overflow lists and not only
        // the CSR slab.
        try (HebbianGraphMemory g = new HebbianGraphMemory(CAPACITY, CAPACITY * 2, 2, EdgeImportance.DEFAULT)) {
            for (int peer = 1; peer <= 6; peer++) {
                g.strengthen(0, peer, 1.0f);
            }
            assertThat(g.degree(0) + countOverflowNeighbours(g)).isPositive();

            g.removeNode(0);

            assertThat(g.hasAnyEdge(0)).isFalse();
            for (int peer = 1; peer <= 6; peer++) {
                assertThat(g.neighbors(peer))
                        .as("peer %d must not still point at the detached node", peer)
                        .noneSatisfy(e -> assertThat(e.neighborIndex()).isZero());
            }
        }
    }

    private static int countOverflowNeighbours(HebbianGraphMemory g) {
        int n = 0;
        for (int node = 0; node < CAPACITY; node++) {
            n += g.neighbors(node).size();
        }
        return n;
    }

    /** Guards against the two implementations drifting apart in their reported edge accounting. */
    @Test
    @DisplayName("both layouts agree on how many edges a detach removed")
    void bothLayoutsAgree() {
        int denseRemoved;
        int csrRemoved;
        try (HebbianGraphBase dense = new HebbianGraph(CAPACITY, MAX_DEGREE)) {
            dense.strengthen(5, 1, 1.0f);
            dense.strengthen(5, 2, 1.0f);
            dense.strengthen(1, 2, 1.0f);
            denseRemoved = dense.removeNode(5);
        }
        try (HebbianGraphBase csr = new HebbianGraphMemory(CAPACITY, CAPACITY * MAX_DEGREE, MAX_DEGREE, EdgeImportance.DEFAULT)) {
            csr.strengthen(5, 1, 1.0f);
            csr.strengthen(5, 2, 1.0f);
            csr.strengthen(1, 2, 1.0f);
            csrRemoved = csr.removeNode(5);
        }
        assertThat(denseRemoved).isEqualTo(csrRemoved).isEqualTo(4);
    }
}
