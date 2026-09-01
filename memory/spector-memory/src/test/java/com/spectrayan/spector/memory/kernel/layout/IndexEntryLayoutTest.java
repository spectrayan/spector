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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IndexEntryLayout Unit Tests")
class IndexEntryLayoutTest {

    private final IndexEntryLayout layout = new IndexEntryLayout();

    @Test
    @DisplayName("Verify IndexEntryLayout metadata, stride (48B), and version (7)")
    void testLayoutMetadata() {
        assertThat(layout.layoutId()).isEqualTo(0x4D494458); // 'MIDX'
        assertThat(layout.schemaVersion()).isEqualTo(7);
        assertThat(layout.name()).isEqualTo("IndexEntryLayout");
        assertThat(layout.recordStride()).isEqualTo(48);
        assertThat(layout.crcEnabled()).isFalse();
    }

    @Test
    @DisplayName("Roundtrip write/read of 48-byte index entry slot table record")
    void testSlotEntryRoundtrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(48L);
            segment.fill((byte) 0);

            // [0:8]   idPoolOffset (long)
            // [8:4]   idPoolLength (int)
            // [12:4]  typeOrdinal (int)
            // [16:8]  offset (long)
            // [24:4]  graphSlot (int)
            // [28:8]  textOffset (long, unaligned at 28)
            // [36:4]  textLength (int)
            // [40:4]  colocatedPartition (int)
            // [44:4]  reserved (int)

            segment.set(ValueLayout.JAVA_LONG, 0, 1024L);
            segment.set(ValueLayout.JAVA_INT, 8, 36);
            segment.set(ValueLayout.JAVA_INT, 12, 2); // SEMANTIC
            segment.set(ValueLayout.JAVA_LONG, 16, 2048L);
            segment.set(ValueLayout.JAVA_INT, 24, 42); // graphSlot
            segment.set(ValueLayout.JAVA_LONG_UNALIGNED, 28, 4096L);
            segment.set(ValueLayout.JAVA_INT, 36, 128);
            segment.set(ValueLayout.JAVA_INT, 40, 1);  // colocatedPartition
            segment.set(ValueLayout.JAVA_INT, 44, 0);  // reserved

            // Verify
            assertThat(segment.get(ValueLayout.JAVA_LONG, 0)).isEqualTo(1024L);
            assertThat(segment.get(ValueLayout.JAVA_INT, 8)).isEqualTo(36);
            assertThat(segment.get(ValueLayout.JAVA_INT, 12)).isEqualTo(2);
            assertThat(segment.get(ValueLayout.JAVA_LONG, 16)).isEqualTo(2048L);
            assertThat(segment.get(ValueLayout.JAVA_INT, 24)).isEqualTo(42);
            assertThat(segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 28)).isEqualTo(4096L);
            assertThat(segment.get(ValueLayout.JAVA_INT, 36)).isEqualTo(128);
            assertThat(segment.get(ValueLayout.JAVA_INT, 40)).isEqualTo(1);
            assertThat(segment.get(ValueLayout.JAVA_INT, 44)).isEqualTo(0);
        }
    }
}
