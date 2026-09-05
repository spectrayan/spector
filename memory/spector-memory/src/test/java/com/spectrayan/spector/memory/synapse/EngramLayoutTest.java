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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EngramLayout} — versioned header read/write.
 */
class EngramLayoutTest {

    private static final int VECTOR_BYTES = 768; // 768 bytes for quantized vector
    private final EngramLayout layout = new EngramLayout(VECTOR_BYTES);

    @Test
    void layoutIdAndNamePinned() {
        assertThat(layout.layoutId()).isEqualTo(EngramLayout.LAYOUT_ID);
        assertThat(layout.layoutId()).isEqualTo(0x434F4700);
        assertThat(layout.name()).isEqualTo("EngramLayout");
    }

    @Test
    void strideIs64PlusVectorBytes() {
        assertThat(layout.stride()).isEqualTo(64 + VECTOR_BYTES);
    }

    @Test
    void vectorOffsetIs64() {
        assertThat(layout.vectorOffset(0)).isEqualTo(64);
        assertThat(layout.vectorOffset(832)).isEqualTo(896);
    }

    @Test
    void writeAndReadHeaderRoundtrip() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout.stride());

            long timestamp = System.currentTimeMillis();
            long tags = SynapticTagEncoder.encode("java", "performance");
            var header = new EncodingHeader(
                    timestamp, tags, 1.5f, 0.8f, 7,
                    (short) 42, (byte) -50, (byte) 0x12
            );

            layout.writeHeader(segment, 0, header);
            var readBack = layout.readHeader(segment, 0);

            assertThat(readBack.timestampMs()).isEqualTo(timestamp);
            assertThat(readBack.synapticTags()).isEqualTo(tags);
            assertThat(readBack.exactNorm()).isEqualTo(1.5f);
            assertThat(readBack.importance()).isEqualTo(0.8f);
            assertThat(readBack.centroidId()).isEqualTo((short) 42);
            assertThat(readBack.agentRecallCount()).isZero(); // Relocated to StrengthMemory
            assertThat(readBack.valence()).isEqualTo((byte) -50);
            assertThat(readBack.flags()).isEqualTo((byte) 0x12);
        }
    }

    @Test
    void fieldLevelAccessors() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout.stride());

            long timestamp = 12345L;
            long tags = 0xDEAD_BEEF_CAFE_BABEL;
            var header = EncodingHeader.create(
                    timestamp, tags, 2.0f, 5.0f, (short) 99, MemoryType.SEMANTIC
            );

            layout.writeHeader(segment, 0, header);

            assertThat(layout.readTimestamp(segment, 0)).isEqualTo(timestamp);
            assertThat(layout.readSynapticTags(segment, 0)).isEqualTo(tags);
            assertThat(layout.readImportance(segment, 0)).isEqualTo(5.0f);
            assertThat(layout.readAgentRecallCount(segment, 0)).isZero();
            assertThat(layout.readValence(segment, 0)).isZero();
        }
    }

    @Test
    void agentRecallCountRelocatedToStrengthMemory() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout.stride());

            var header = EncodingHeader.create(
                    System.currentTimeMillis(), 0L, 1.0f, 1.0f, (short) 0, MemoryType.EPISODIC
            );
            layout.writeHeader(segment, 0, header);

            // Under EncodingHeaderLayout (pure encoding header), recall counters live in StrengthMemory
            assertThat(layout.readAgentRecallCount(segment, 0)).isZero();
            assertThat(layout.incrementAgentRecallCount(segment, 0)).isZero();
            assertThat(layout.readAgentRecallCount(segment, 0)).isZero();
        }
    }

    @Test
    void tombstoneSetsFlagBit() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout.stride());

            var header = EncodingHeader.create(
                    System.currentTimeMillis(), 0L, 1.0f, 1.0f, (short) 0, MemoryType.EPISODIC
            );
            layout.writeHeader(segment, 0, header);

            assertThat(EncodingHeaderFields.isTombstoned(layout.readFlags(segment, 0))).isFalse();

            layout.tombstone(segment, 0);

            assertThat(EncodingHeaderFields.isTombstoned(layout.readFlags(segment, 0))).isTrue();
        }
    }

    @Test
    void markConsolidatedSetsFlagBit() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout.stride());

            var header = EncodingHeader.create(
                    System.currentTimeMillis(), 0L, 1.0f, 1.0f, (short) 0, MemoryType.EPISODIC
            );
            layout.writeHeader(segment, 0, header);

            layout.markConsolidated(segment, 0);

            assertThat(EncodingHeaderFields.isConsolidated(layout.readFlags(segment, 0))).isTrue();
        }
    }

    @Test
    void memoryTypeEncodedInFlags() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout.stride());

            for (MemoryType type : MemoryType.values()) {
                var header = EncodingHeader.create(
                        System.currentTimeMillis(), 0L, 1.0f, 1.0f, (short) 0, type
                );
                layout.writeHeader(segment, 0, header);

                byte flags = layout.readFlags(segment, 0);
                assertThat(EncodingHeaderFields.memoryTypeOrdinal(flags))
                        .as("MemoryType %s", type)
                        .isEqualTo(type.ordinal());
            }
        }
    }

    @Test
    void mergeSynapticTagsORsExisting() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout.stride());

            long initialTags = SynapticTagEncoder.encode("java");
            var header = EncodingHeader.create(
                    System.currentTimeMillis(), initialTags, 1.0f, 1.0f, (short) 0, MemoryType.SEMANTIC
            );
            layout.writeHeader(segment, 0, header);

            long additionalTags = SynapticTagEncoder.encode("performance");
            layout.mergeSynapticTags(segment, 0, additionalTags);

            long merged = layout.readSynapticTags(segment, 0);
            assertThat(merged).isEqualTo(initialTags | additionalTags);
        }
    }
}
