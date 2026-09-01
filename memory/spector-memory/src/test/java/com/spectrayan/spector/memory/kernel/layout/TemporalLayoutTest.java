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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemporalLayout Unit Tests")
class TemporalLayoutTest {

    private final TemporalLayout layout = new TemporalLayout();

    @Test
    @DisplayName("Verify TemporalLayout metadata, stride, and version")
    void testLayoutMetadata() {
        assertThat(layout.layoutId()).isEqualTo(0x54504348); // 'TPCH'
        assertThat(layout.schemaVersion()).isEqualTo(2);
        assertThat(layout.name()).isEqualTo("TemporalChain");
        assertThat(layout.recordStride()).isEqualTo(16);
        assertThat(layout.crcEnabled()).isFalse();
    }

    @Test
    @DisplayName("Roundtrip write/read of 16-byte temporal chain nodes")
    void testTemporalNodeRoundtrip() {
        try (Arena arena = Arena.ofConfined()) {
            int nodeCount = 3;
            MemorySegment segment = arena.allocate((long) layout.recordStride() * nodeCount);
            segment.fill((byte) 0);

            // Node 0: prev=-1, next=1, sessionId=1001, epochSec=1700000000
            long node0 = 0L;
            segment.set(ValueLayout.JAVA_INT, node0, -1);
            segment.set(ValueLayout.JAVA_INT, node0 + 4, 1);
            segment.set(ValueLayout.JAVA_INT, node0 + 8, 1001);
            segment.set(ValueLayout.JAVA_INT, node0 + 12, 1700000000);

            // Node 1: prev=0, next=2, sessionId=1001, epochSec=1700000060
            long node1 = 16L;
            segment.set(ValueLayout.JAVA_INT, node1, 0);
            segment.set(ValueLayout.JAVA_INT, node1 + 4, 2);
            segment.set(ValueLayout.JAVA_INT, node1 + 8, 1001);
            segment.set(ValueLayout.JAVA_INT, node1 + 12, 1700000060);

            // Node 2: prev=1, next=-1, sessionId=1001, epochSec=1700000120
            long node2 = 32L;
            segment.set(ValueLayout.JAVA_INT, node2, 1);
            segment.set(ValueLayout.JAVA_INT, node2 + 4, -1);
            segment.set(ValueLayout.JAVA_INT, node2 + 8, 1001);
            segment.set(ValueLayout.JAVA_INT, node2 + 12, 1700000120);

            // Verify Forward Chain
            int curr = 0;
            int count = 0;
            while (curr != -1) {
                long offset = (long) curr * 16L;
                int next = segment.get(ValueLayout.JAVA_INT, offset + 4);
                assertThat(segment.get(ValueLayout.JAVA_INT, offset + 8)).isEqualTo(1001);
                curr = next;
                count++;
            }
            assertThat(count).isEqualTo(3);

            // Verify Backward Chain
            curr = 2;
            count = 0;
            while (curr != -1) {
                long offset = (long) curr * 16L;
                int prev = segment.get(ValueLayout.JAVA_INT, offset);
                assertThat(segment.get(ValueLayout.JAVA_INT, offset + 8)).isEqualTo(1001);
                curr = prev;
                count++;
            }
            assertThat(count).isEqualTo(3);
        }
    }
}
