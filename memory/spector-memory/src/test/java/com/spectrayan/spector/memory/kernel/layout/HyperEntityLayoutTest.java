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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HyperEntityLayout Unit Tests")
class HyperEntityLayoutTest {

    private final HyperEntityLayout layout = new HyperEntityLayout();

    @Test
    @DisplayName("Verify HyperEntityLayout metadata, layout ID, and header framing")
    void testLayoutMetadataAndFraming() {
        assertThat(layout.layoutId()).isEqualTo(0x48594547); // 'HYEG'
        assertThat(layout.schemaVersion()).isEqualTo(2);
        assertThat(layout.name()).isEqualTo("HyperEntityGraph");
        assertThat(layout.crcEnabled()).isFalse();
        assertThat(layout.recordStride()).isEqualTo(32);

        // 64B MemoryHeader + 16B Graph subheader = 80B DATA_START
        assertThat(HyperEntityLayout.GRAPH_SUBHEADER_BYTES).isEqualTo(16);
        assertThat(HyperEntityLayout.DATA_START).isEqualTo(80L);
        assertThat(HyperEntityLayout.DATA_START).isEqualTo(MemoryHeader.HEADER_BYTES + HyperEntityLayout.GRAPH_SUBHEADER_BYTES);

        // Subheader offsets
        assertThat(HyperEntityLayout.SUB_OFF_ENTITY_CAP).isEqualTo(0);
        assertThat(HyperEntityLayout.SUB_OFF_NEXT_HYPEREDGE_ID).isEqualTo(4);
        assertThat(HyperEntityLayout.SUB_OFF_NEXT_VERTEX_OFFSET).isEqualTo(8);
        assertThat(HyperEntityLayout.SUB_OFF_TOTAL_HYPEREDGES).isEqualTo(12);
    }

    @Test
    @DisplayName("Verify Hyperedge and Vertex record stride and byte offsets")
    void testStridesAndOffsets() {
        // Hyperedge stride: 32 bytes
        assertThat(HyperEntityLayout.HEDGE_BYTES).isEqualTo(32);
        assertThat(HyperEntityLayout.HEDGE_OFF_EDGE_ID).isEqualTo(0);
        assertThat(HyperEntityLayout.HEDGE_OFF_TYPE).isEqualTo(4);
        assertThat(HyperEntityLayout.HEDGE_OFF_WEIGHT).isEqualTo(8);
        assertThat(HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT).isEqualTo(12);
        assertThat(HyperEntityLayout.HEDGE_OFF_VERTEX_OFFSET).isEqualTo(16);
        assertThat(HyperEntityLayout.HEDGE_OFF_MEMORY_IDX).isEqualTo(20);
        assertThat(HyperEntityLayout.HEDGE_OFF_TIMESTAMP).isEqualTo(24);

        // Vertex stride: 8 bytes
        assertThat(HyperEntityLayout.VERTEX_BYTES).isEqualTo(8);
        assertThat(HyperEntityLayout.VERTEX_OFF_ENTITY_ID).isEqualTo(0);
        assertThat(HyperEntityLayout.VERTEX_OFF_ROLE_ID).isEqualTo(4);
    }

    @Test
    @DisplayName("Roundtrip write/read of Hyperedge and Vertex records in off-heap MemorySegment")
    void testHyperedgeAndVertexSegmentRoundtrip() {
        try (Arena arena = Arena.ofConfined()) {
            long totalBytes = HyperEntityLayout.DATA_START + (2L * HyperEntityLayout.HEDGE_BYTES) + (4L * HyperEntityLayout.VERTEX_BYTES);
            MemorySegment segment = arena.allocate(totalBytes);
            segment.fill((byte) 0);

            // 1. Write Sub-header
            long subHeaderOffset = MemoryHeader.HEADER_BYTES;
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + HyperEntityLayout.SUB_OFF_ENTITY_CAP, 10000);
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + HyperEntityLayout.SUB_OFF_NEXT_HYPEREDGE_ID, 2);
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + HyperEntityLayout.SUB_OFF_NEXT_VERTEX_OFFSET, 4);
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + HyperEntityLayout.SUB_OFF_TOTAL_HYPEREDGES, 2);

            // 2. Write Hyperedge 0
            long hedge0Offset = HyperEntityLayout.DATA_START;
            segment.set(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_EDGE_ID, 1);
            segment.set(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_TYPE, 5);
            segment.set(ValueLayout.JAVA_FLOAT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_WEIGHT, 0.92f);
            segment.set(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT, 2);
            segment.set(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_VERTEX_OFFSET, 0);
            segment.set(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_MEMORY_IDX, 77);
            segment.set(ValueLayout.JAVA_LONG, hedge0Offset + HyperEntityLayout.HEDGE_OFF_TIMESTAMP, 1700000500L);

            // 3. Write Vertices
            long vertexBaseOffset = HyperEntityLayout.DATA_START + (2L * HyperEntityLayout.HEDGE_BYTES);
            // Vertex 0: Entity 10, Role 1
            segment.set(ValueLayout.JAVA_INT, vertexBaseOffset + (0L * HyperEntityLayout.VERTEX_BYTES) + HyperEntityLayout.VERTEX_OFF_ENTITY_ID, 10);
            segment.set(ValueLayout.JAVA_INT, vertexBaseOffset + (0L * HyperEntityLayout.VERTEX_BYTES) + HyperEntityLayout.VERTEX_OFF_ROLE_ID, 1);
            // Vertex 1: Entity 20, Role 2
            segment.set(ValueLayout.JAVA_INT, vertexBaseOffset + (1L * HyperEntityLayout.VERTEX_BYTES) + HyperEntityLayout.VERTEX_OFF_ENTITY_ID, 20);
            segment.set(ValueLayout.JAVA_INT, vertexBaseOffset + (1L * HyperEntityLayout.VERTEX_BYTES) + HyperEntityLayout.VERTEX_OFF_ROLE_ID, 2);

            // 4. Verify
            assertThat(segment.get(ValueLayout.JAVA_INT, subHeaderOffset + HyperEntityLayout.SUB_OFF_ENTITY_CAP)).isEqualTo(10000);
            assertThat(segment.get(ValueLayout.JAVA_INT, subHeaderOffset + HyperEntityLayout.SUB_OFF_TOTAL_HYPEREDGES)).isEqualTo(2);

            assertThat(segment.get(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_EDGE_ID)).isEqualTo(1);
            assertThat(segment.get(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_TYPE)).isEqualTo(5);
            assertThat(segment.get(ValueLayout.JAVA_FLOAT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_WEIGHT)).isEqualTo(0.92f);
            assertThat(segment.get(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT)).isEqualTo(2);
            assertThat(segment.get(ValueLayout.JAVA_INT, hedge0Offset + HyperEntityLayout.HEDGE_OFF_MEMORY_IDX)).isEqualTo(77);
            assertThat(segment.get(ValueLayout.JAVA_LONG, hedge0Offset + HyperEntityLayout.HEDGE_OFF_TIMESTAMP)).isEqualTo(1700000500L);

            assertThat(segment.get(ValueLayout.JAVA_INT, vertexBaseOffset + HyperEntityLayout.VERTEX_OFF_ENTITY_ID)).isEqualTo(10);
            assertThat(segment.get(ValueLayout.JAVA_INT, vertexBaseOffset + HyperEntityLayout.VERTEX_OFF_ROLE_ID)).isEqualTo(1);

            assertThat(segment.get(ValueLayout.JAVA_INT, vertexBaseOffset + HyperEntityLayout.VERTEX_BYTES + HyperEntityLayout.VERTEX_OFF_ENTITY_ID)).isEqualTo(20);
            assertThat(segment.get(ValueLayout.JAVA_INT, vertexBaseOffset + HyperEntityLayout.VERTEX_BYTES + HyperEntityLayout.VERTEX_OFF_ROLE_ID)).isEqualTo(2);
        }
    }
}
