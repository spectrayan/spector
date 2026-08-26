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

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HebbianLayout Unit Tests")
class HebbianLayoutTest {

    private final HebbianLayout layout = new HebbianLayout();

    @Test
    @DisplayName("Verify Hebbian CSR layout constants, ID, and schema version")
    void testLayoutMetadataAndFraming() {
        assertThat(layout.layoutId()).isEqualTo(0x48435352); // 'HCSR'
        assertThat(layout.schemaVersion()).isEqualTo(1);
        assertThat(layout.name()).isEqualTo("HebbianGraphCsr");
        assertThat(layout.crcEnabled()).isFalse();

        // 64B MemoryHeader + 16B Graph subheader = 80B DATA_START
        assertThat(HebbianLayout.GRAPH_SUBHEADER_BYTES).isEqualTo(16);
        assertThat(HebbianLayout.DATA_START).isEqualTo(80L);
        assertThat(HebbianLayout.DATA_START).isEqualTo(MemoryHeader.HEADER_BYTES + HebbianLayout.GRAPH_SUBHEADER_BYTES);

        // Subheader offsets
        assertThat(HebbianLayout.SUB_OFF_EDGE_CAPACITY).isEqualTo(0);
        assertThat(HebbianLayout.SUB_OFF_CURRENT_CYCLE).isEqualTo(4);
    }

    @Test
    @DisplayName("Verify CSR Edge record stride and byte offsets")
    void testEdgeRecordStrideAndOffsets() {
        // Edge stride: 12 bytes
        assertThat(HebbianLayout.EDGE_BYTES).isEqualTo(12);
        assertThat(layout.recordStride()).isEqualTo(12);

        // Edge fields
        assertThat(HebbianLayout.EDGE_OFF_NEIGHBOR).isEqualTo(0);
        assertThat(HebbianLayout.EDGE_OFF_WEIGHT).isEqualTo(4);
        assertThat(HebbianLayout.EDGE_OFF_LAST_CYCLE).isEqualTo(8);
        assertThat(HebbianLayout.EDGE_OFF_BRIDGE_SCORE).isEqualTo(10);
        assertThat(HebbianLayout.EDGE_OFF_EDGE_FLAGS).isEqualTo(11);
    }

    @Test
    @DisplayName("Roundtrip write/read of CSR edge records in off-heap MemorySegment")
    void testEdgeRecordSegmentRoundtrip() {
        try (Arena arena = Arena.ofConfined()) {
            long totalBytes = HebbianLayout.DATA_START + (4L * HebbianLayout.EDGE_BYTES);
            MemorySegment segment = arena.allocate(totalBytes);
            segment.fill((byte) 0);

            // 1. Write Sub-header
            long subHeaderOffset = MemoryHeader.HEADER_BYTES;
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + HebbianLayout.SUB_OFF_EDGE_CAPACITY, 50000);
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + HebbianLayout.SUB_OFF_CURRENT_CYCLE, 12);

            // 2. Write Edge 0
            long edge0Offset = HebbianLayout.DATA_START;
            segment.set(ValueLayout.JAVA_INT, edge0Offset + HebbianLayout.EDGE_OFF_NEIGHBOR, 105);
            segment.set(ValueLayout.JAVA_FLOAT, edge0Offset + HebbianLayout.EDGE_OFF_WEIGHT, 0.88f);
            segment.set(ValueLayout.JAVA_SHORT, edge0Offset + HebbianLayout.EDGE_OFF_LAST_CYCLE, (short) 12);
            segment.set(ValueLayout.JAVA_BYTE, edge0Offset + HebbianLayout.EDGE_OFF_BRIDGE_SCORE, (byte) 7);
            segment.set(ValueLayout.JAVA_BYTE, edge0Offset + HebbianLayout.EDGE_OFF_EDGE_FLAGS, (byte) 0x01);

            // 3. Write Edge 1
            long edge1Offset = HebbianLayout.DATA_START + HebbianLayout.EDGE_BYTES;
            segment.set(ValueLayout.JAVA_INT, edge1Offset + HebbianLayout.EDGE_OFF_NEIGHBOR, 340);
            segment.set(ValueLayout.JAVA_FLOAT, edge1Offset + HebbianLayout.EDGE_OFF_WEIGHT, 0.45f);
            segment.set(ValueLayout.JAVA_SHORT, edge1Offset + HebbianLayout.EDGE_OFF_LAST_CYCLE, (short) 10);
            segment.set(ValueLayout.JAVA_BYTE, edge1Offset + HebbianLayout.EDGE_OFF_BRIDGE_SCORE, (byte) 0);
            segment.set(ValueLayout.JAVA_BYTE, edge1Offset + HebbianLayout.EDGE_OFF_EDGE_FLAGS, (byte) 0x00);

            // 4. Verify
            assertThat(segment.get(ValueLayout.JAVA_INT, subHeaderOffset + HebbianLayout.SUB_OFF_EDGE_CAPACITY)).isEqualTo(50000);
            assertThat(segment.get(ValueLayout.JAVA_INT, subHeaderOffset + HebbianLayout.SUB_OFF_CURRENT_CYCLE)).isEqualTo(12);

            assertThat(segment.get(ValueLayout.JAVA_INT, edge0Offset + HebbianLayout.EDGE_OFF_NEIGHBOR)).isEqualTo(105);
            assertThat(segment.get(ValueLayout.JAVA_FLOAT, edge0Offset + HebbianLayout.EDGE_OFF_WEIGHT)).isEqualTo(0.88f);
            assertThat(segment.get(ValueLayout.JAVA_SHORT, edge0Offset + HebbianLayout.EDGE_OFF_LAST_CYCLE)).isEqualTo((short) 12);
            assertThat(segment.get(ValueLayout.JAVA_BYTE, edge0Offset + HebbianLayout.EDGE_OFF_BRIDGE_SCORE)).isEqualTo((byte) 7);
            assertThat(segment.get(ValueLayout.JAVA_BYTE, edge0Offset + HebbianLayout.EDGE_OFF_EDGE_FLAGS)).isEqualTo((byte) 0x01);

            assertThat(segment.get(ValueLayout.JAVA_INT, edge1Offset + HebbianLayout.EDGE_OFF_NEIGHBOR)).isEqualTo(340);
            assertThat(segment.get(ValueLayout.JAVA_FLOAT, edge1Offset + HebbianLayout.EDGE_OFF_WEIGHT)).isEqualTo(0.45f);
            assertThat(segment.get(ValueLayout.JAVA_SHORT, edge1Offset + HebbianLayout.EDGE_OFF_LAST_CYCLE)).isEqualTo((short) 10);
            assertThat(segment.get(ValueLayout.JAVA_BYTE, edge1Offset + HebbianLayout.EDGE_OFF_BRIDGE_SCORE)).isEqualTo((byte) 0);
            assertThat(segment.get(ValueLayout.JAVA_BYTE, edge1Offset + HebbianLayout.EDGE_OFF_EDGE_FLAGS)).isEqualTo((byte) 0x00);
        }
    }
}
