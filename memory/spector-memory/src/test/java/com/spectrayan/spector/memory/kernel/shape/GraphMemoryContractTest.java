/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for the bundled {@link AdjacencyListGraphMemory} reference impl over the
 * kernel {@link AbstractGraphMemory} substrate.
 *
 * <p>Exercises the reference impl's adjacency-list mechanics (node/edge add + remove, adjacency
 * iteration, edge payloads, and the vertex/edge free-lists) through a minimal concrete
 * implementation.</p>
 */
class GraphMemoryContractTest {

    /** Real edge layout: base 8-byte prefix (target + next) followed by a 4-byte weight. */
    static final class TestGraphLayout implements MemoryLayout {
        static final int EDGE_OFF_WEIGHT = AdjacencyListLayout.EDGE_HEADER_BYTES; // 8

        @Override public int layoutId() { return 0x47525048; } // 'GRPH'
        @Override public int schemaVersion() { return 1; }
        @Override public int recordStride() { return 12; }     // 8 prefix + 4 weight
        @Override public boolean crcEnabled() { return false; }
        @Override public String name() { return "TestGraphLayout"; }
    }

    /** Minimal real graph over the adjacency-list reference base (no bespoke storage). */
    static final class SimpleGraphMemory extends AdjacencyListGraphMemory<TestGraphLayout> {
        SimpleGraphMemory(MemoryId id, TestGraphLayout layout, int vertexCap, int edgeCap) {
            super(id, layout, vertexCap, edgeCap);
        }
        SimpleGraphMemory(MemoryId id, TestGraphLayout layout, int vertexCap, int edgeCap, Path filePath) {
            super(id, layout, vertexCap, edgeCap, filePath);
        }

        int addWeightedEdge(int from, int to, float weight) {
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                MemorySegment payload = arena.allocate(4);
                payload.set(ValueLayout.JAVA_FLOAT, 0, weight);
                return addEdge(from, to, payload);
            }
        }

        List<Integer> neighborList(int node) {
            List<Integer> out = new ArrayList<>();
            PrimitiveIterator.OfInt it = neighbours(node);
            while (it.hasNext()) out.add(it.nextInt());
            return out;
        }
    }

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("graphMemoryShapeAndLifecycle")
    void graphMemoryShapeAndLifecycle() {
        TestGraphLayout layout = new TestGraphLayout();
        MemoryId id = MemoryId.of("test", "graph");

        try (var graph = new SimpleGraphMemory(id, layout, 100, 400)) {
            assertThat(graph.shape()).isEqualTo(MemoryShape.GRAPH);
            assertThat(graph.isPersistent()).isFalse();
            assertThat(graph.id()).isEqualTo(id);
            assertThat(graph.edgeCount()).isZero();
            assertThat(graph.nodeCount()).isZero();
            assertThat(graph.edgeCapacity()).isEqualTo(400);
        }

        Path file = tempDir.resolve("test_graph.dat");
        try (var graph2 = new SimpleGraphMemory(id, layout, 100, 400, file)) {
            assertThat(graph2.isPersistent()).isTrue();
            graph2.flush();
        }
    }

    @Test
    @DisplayName("csrAddEdgeNeighboursAndCounts")
    void csrAddEdgeNeighboursAndCounts() {
        TestGraphLayout layout = new TestGraphLayout();
        try (var g = new SimpleGraphMemory(MemoryId.of("test", "csr"), layout, 16, 64)) {
            g.addWeightedEdge(0, 1, 1.0f);
            g.addWeightedEdge(0, 2, 2.0f);
            g.addWeightedEdge(3, 4, 3.0f);

            assertThat(g.edgeCount()).isEqualTo(3);
            assertThat(g.nodeCount()).isEqualTo(2); // vertices 0 and 3 have out-edges
            // Adjacency of 0 is 2 then 1 (LIFO insertion into the linked list).
            assertThat(g.neighborList(0)).containsExactly(2, 1);
            assertThat(g.neighborList(3)).containsExactly(4);
            assertThat(g.neighborList(1)).isEmpty();
        }
    }

    @Test
    @DisplayName("csrRemoveEdgeUnlinksAndReclaims")
    void csrRemoveEdgeUnlinksAndReclaims() {
        TestGraphLayout layout = new TestGraphLayout();
        try (var g = new SimpleGraphMemory(MemoryId.of("test", "csr"), layout, 16, 64)) {
            int e01 = g.addWeightedEdge(0, 1, 1.0f);
            g.addWeightedEdge(0, 2, 2.0f);

            g.removeEdge(e01);
            assertThat(g.edgeCount()).isEqualTo(1);
            assertThat(g.neighborList(0)).containsExactly(2);

            // Removing the last edge of a vertex drops it from the node count.
            int e34 = g.addWeightedEdge(3, 4, 3.0f);
            assertThat(g.nodeCount()).isEqualTo(2);
            g.removeEdge(e34);
            assertThat(g.nodeCount()).isEqualTo(1);
            assertThat(g.neighborList(3)).isEmpty();
        }
    }

    @Test
    @DisplayName("csrNodeFreeListReusesSlots")
    void csrNodeFreeListReusesSlots() {
        TestGraphLayout layout = new TestGraphLayout();
        try (var g = new SimpleGraphMemory(MemoryId.of("test", "csr"), layout, 8, 32)) {
            int a = g.addNode();
            int b = g.addNode();
            assertThat(a).isEqualTo(0);
            assertThat(b).isEqualTo(1);
            g.removeNode(a);
            int c = g.addNode(); // should reuse slot 0 from the free-list
            assertThat(c).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("csrEdgeCapacityExhaustionReturnsMinusOne")
    void csrEdgeCapacityExhaustionReturnsMinusOne() {
        TestGraphLayout layout = new TestGraphLayout();
        try (var g = new SimpleGraphMemory(MemoryId.of("test", "csr"), layout, 8, 2)) {
            assertThat(g.addWeightedEdge(0, 1, 1.0f)).isNotNegative();
            assertThat(g.addWeightedEdge(0, 2, 1.0f)).isNotNegative();
            assertThat(g.addWeightedEdge(0, 3, 1.0f)).isEqualTo(-1); // slab full
            assertThat(g.edgeCount()).isEqualTo(2);
        }
    }
}
