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

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryContractTest {

    static final class TestGraphLayout implements MemoryLayout {
        @Override public int layoutId() { return 0x47525048; }
        @Override public int schemaVersion() { return 1; }
        @Override public int recordStride() { return -1; }
        @Override public boolean crcEnabled() { return false; }
        @Override public String name() { return "TestGraphLayout"; }
    }

    static final class DummyGraphMemory extends AbstractGraphMemory<TestGraphLayout> {
        DummyGraphMemory(MemoryId id, TestGraphLayout layout, int capacity, long bytes) {
            super(id, layout, capacity, bytes);
        }
        DummyGraphMemory(MemoryId id, TestGraphLayout layout, int capacity, long bytes, Path filePath) {
            super(id, layout, capacity, bytes, filePath);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("graphMemoryShapeAndLifecycle")
    void graphMemoryShapeAndLifecycle() {
        TestGraphLayout layout = new TestGraphLayout();
        MemoryId id = MemoryId.of("test", "graph");

        try (var graph = new DummyGraphMemory(id, layout, 100, 4096L)) {
            assertThat(graph.shape()).isEqualTo(MemoryShape.GRAPH);
            assertThat(graph.isPersistent()).isFalse();
            assertThat(graph.id()).isEqualTo(id);
            assertThat(graph.edgeCount()).isEqualTo(0);
            assertThat(graph.nodeCount()).isEqualTo(0);
        }

        Path file = tempDir.resolve("test_graph.dat");
        try (var graph2 = new DummyGraphMemory(id, layout, 100, 4096L, file)) {
            assertThat(graph2.isPersistent()).isTrue();
            graph2.flush();
        }
    }
}
