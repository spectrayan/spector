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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EncodingHeaderLayout Unit Tests")
class EncodingHeaderLayoutTest {

    private final EncodingHeaderLayout layout = EncodingHeaderLayout.INSTANCE;

    @Test
    @DisplayName("Verify EncodingHeaderLayout metadata, version (2), and 64-byte cache line alignment")
    void testMetadataAndDimensions() {
        assertThat(layout.version()).isEqualTo(2);
        assertThat(layout.headerBytes()).isEqualTo(64);
        assertThat(EncodingHeaderLayout.defaultLayout()).isSameAs(layout);
        assertThat(EncodingHeaderLayout.forVersion(2)).isSameAs(layout);
    }

    @Test
    @DisplayName("Verify pure encoding header read/write and 128-bit Bloom filter operations")
    void testEncodingHeaderAndBloomFilterRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            long offset = 0;

            long timestamp = 1716900000000L;
            float exactNorm = 1.05f;
            float importance = 4.25f;
            short centroidId = 42;
            byte valence = 15;
            byte flags = 0x01;
            long tagsLo = 0x0123456789ABCDEFL;
            long tagsHi = 0xFEDCBA9876543210L;
            EncodingHeader header = new EncodingHeader(
                    timestamp, tagsLo, exactNorm, importance, 0, centroidId, valence, flags
            );
            layout.writeHeader(segment, offset, header);
            layout.writeSynapticTags(segment, offset, tagsLo, tagsHi);

            assertThat(layout.readTimestamp(segment, offset)).isEqualTo(timestamp);
            assertThat(layout.readExactNorm(segment, offset)).isCloseTo(exactNorm, within(1e-5f));
            assertThat(layout.readImportance(segment, offset)).isCloseTo(importance, within(1e-5f));
            assertThat(layout.readCentroidId(segment, offset)).isEqualTo(centroidId);
            assertThat(layout.readValence(segment, offset)).isEqualTo(valence);
            assertThat(layout.readFlags(segment, offset)).isEqualTo(flags);

            // 128-bit Bloom tags
            assertThat(layout.readSynapticTagsLo(segment, offset)).isEqualTo(tagsLo);
            assertThat(layout.readSynapticTagsHi(segment, offset)).isEqualTo(tagsHi);
            assertThat(layout.readSynapticTags(segment, offset)).isEqualTo(tagsLo);

            // Merge additional bits into 128-bit Bloom filter
            layout.mergeSynapticTags128(segment, offset, 0x1000000000000000L, 0x0000000000000001L);
            assertThat(layout.readSynapticTagsLo(segment, offset)).isEqualTo(tagsLo | 0x1000000000000000L);
            assertThat(layout.readSynapticTagsHi(segment, offset)).isEqualTo(tagsHi | 0x0000000000000001L);
        }
    }
}
