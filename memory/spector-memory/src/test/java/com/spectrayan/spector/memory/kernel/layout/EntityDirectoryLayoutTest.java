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

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntityDirectoryLayout Unit Tests")
class EntityDirectoryLayoutTest {

    private final EntityDirectoryLayout layout = new EntityDirectoryLayout();

    @Test
    @DisplayName("Verify Layout ID, Schema Version, and Header framing offsets")
    void testLayoutMetadataAndFraming() {
        assertThat(layout.layoutId()).isEqualTo(0x45444952); // 'EDIR'
        assertThat(layout.schemaVersion()).isEqualTo(1);
        assertThat(layout.name()).isEqualTo("EntityDirectory");
        assertThat(layout.crcEnabled()).isFalse();

        // 64B RegionPreamble + 16B Graph subheader = 80B DATA_START
        assertThat(EntityDirectoryLayout.GRAPH_SUBHEADER_BYTES).isEqualTo(16);
        assertThat(EntityDirectoryLayout.DATA_START).isEqualTo(80L);
        assertThat(EntityDirectoryLayout.DATA_START).isEqualTo(RegionPreamble.PREAMBLE_BYTES + EntityDirectoryLayout.GRAPH_SUBHEADER_BYTES);

        // Subheader offsets
        assertThat(EntityDirectoryLayout.SUB_OFF_ADJ_CAPACITY).isEqualTo(0);
        assertThat(EntityDirectoryLayout.SUB_OFF_ADJ_HWM).isEqualTo(4);
    }

    @Test
    @DisplayName("Verify Node and Adjacency stride and field alignment")
    void testStridesAndOffsets() {
        // Node stride: 64 bytes
        assertThat(EntityDirectoryLayout.ENTITY_NODE_BYTES).isEqualTo(64);
        assertThat(layout.recordStride()).isEqualTo(64);

        // Node fields
        assertThat(EntityDirectoryLayout.ENT_OFF_TYPE).isEqualTo(0);
        assertThat(EntityDirectoryLayout.ENT_OFF_NAME_HASH).isEqualTo(8);
        assertThat(EntityDirectoryLayout.ENT_OFF_ADJ_OFFSET).isEqualTo(16);
        assertThat(EntityDirectoryLayout.ENT_OFF_ADJ_COUNT).isEqualTo(20);
        assertThat(EntityDirectoryLayout.ENT_OFF_ADJ_CAPACITY).isEqualTo(24);
        assertThat(EntityDirectoryLayout.ENT_OFF_MERGED_INTO).isEqualTo(28);

        // Adjacency entry stride: 8 bytes
        assertThat(EntityDirectoryLayout.ADJ_ENTRY_BYTES).isEqualTo(8);
        assertThat(EntityDirectoryLayout.ADJ_OFF_MEM_IDX).isEqualTo(0);
        assertThat(EntityDirectoryLayout.ADJ_OFF_WEIGHT).isEqualTo(4);
    }

    @Test
    @DisplayName("Roundtrip write/read of Entity node and Adjacency entry in off-heap MemorySegment")
    void testNodeAndAdjacencySegmentRoundtrip() {
        try (Arena arena = Arena.ofConfined()) {
            long totalBytes = EntityDirectoryLayout.DATA_START + (2L * EntityDirectoryLayout.ENTITY_NODE_BYTES) + (4L * EntityDirectoryLayout.ADJ_ENTRY_BYTES);
            MemorySegment segment = arena.allocate(totalBytes);
            segment.fill((byte) 0);

            // 1. Write Sub-header
            long subHeaderOffset = RegionPreamble.PREAMBLE_BYTES;
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + EntityDirectoryLayout.SUB_OFF_ADJ_CAPACITY, 1024);
            segment.set(ValueLayout.JAVA_INT, subHeaderOffset + EntityDirectoryLayout.SUB_OFF_ADJ_HWM, 4);

            // 2. Write Node 0
            long node0Offset = EntityDirectoryLayout.DATA_START;
            segment.set(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_TYPE, 3);
            segment.set(ValueLayout.JAVA_LONG, node0Offset + EntityDirectoryLayout.ENT_OFF_NAME_HASH, 0x123456789ABCDEF0L);
            segment.set(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_ADJ_OFFSET, 0);
            segment.set(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_ADJ_COUNT, 2);
            segment.set(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_ADJ_CAPACITY, 4);
            segment.set(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_MERGED_INTO, -1);

            // 3. Write Adjacency entries
            long adjBaseOffset = EntityDirectoryLayout.DATA_START + (2L * EntityDirectoryLayout.ENTITY_NODE_BYTES);
            // Entry 0
            segment.set(ValueLayout.JAVA_INT, adjBaseOffset + (0L * EntityDirectoryLayout.ADJ_ENTRY_BYTES) + EntityDirectoryLayout.ADJ_OFF_MEM_IDX, 42);
            segment.set(ValueLayout.JAVA_FLOAT, adjBaseOffset + (0L * EntityDirectoryLayout.ADJ_ENTRY_BYTES) + EntityDirectoryLayout.ADJ_OFF_WEIGHT, 0.95f);
            // Entry 1
            segment.set(ValueLayout.JAVA_INT, adjBaseOffset + (1L * EntityDirectoryLayout.ADJ_ENTRY_BYTES) + EntityDirectoryLayout.ADJ_OFF_MEM_IDX, 99);
            segment.set(ValueLayout.JAVA_FLOAT, adjBaseOffset + (1L * EntityDirectoryLayout.ADJ_ENTRY_BYTES) + EntityDirectoryLayout.ADJ_OFF_WEIGHT, 0.80f);

            // 4. Read back and verify
            assertThat(segment.get(ValueLayout.JAVA_INT, subHeaderOffset + EntityDirectoryLayout.SUB_OFF_ADJ_CAPACITY)).isEqualTo(1024);
            assertThat(segment.get(ValueLayout.JAVA_INT, subHeaderOffset + EntityDirectoryLayout.SUB_OFF_ADJ_HWM)).isEqualTo(4);

            assertThat(segment.get(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_TYPE)).isEqualTo(3);
            assertThat(segment.get(ValueLayout.JAVA_LONG, node0Offset + EntityDirectoryLayout.ENT_OFF_NAME_HASH)).isEqualTo(0x123456789ABCDEF0L);
            assertThat(segment.get(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_ADJ_OFFSET)).isEqualTo(0);
            assertThat(segment.get(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_ADJ_COUNT)).isEqualTo(2);
            assertThat(segment.get(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_ADJ_CAPACITY)).isEqualTo(4);
            assertThat(segment.get(ValueLayout.JAVA_INT, node0Offset + EntityDirectoryLayout.ENT_OFF_MERGED_INTO)).isEqualTo(-1);

            assertThat(segment.get(ValueLayout.JAVA_INT, adjBaseOffset + EntityDirectoryLayout.ADJ_OFF_MEM_IDX)).isEqualTo(42);
            assertThat(segment.get(ValueLayout.JAVA_FLOAT, adjBaseOffset + EntityDirectoryLayout.ADJ_OFF_WEIGHT)).isEqualTo(0.95f);

            assertThat(segment.get(ValueLayout.JAVA_INT, adjBaseOffset + EntityDirectoryLayout.ADJ_ENTRY_BYTES + EntityDirectoryLayout.ADJ_OFF_MEM_IDX)).isEqualTo(99);
            assertThat(segment.get(ValueLayout.JAVA_FLOAT, adjBaseOffset + EntityDirectoryLayout.ADJ_ENTRY_BYTES + EntityDirectoryLayout.ADJ_OFF_WEIGHT)).isEqualTo(0.80f);
        }
    }
}
