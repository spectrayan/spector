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
package com.spectrayan.spector.kernel.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartitionSummaryHeaderTest {

    @Test
    @DisplayName("PartitionSummaryHeader should write and read back exact fields")
    void shouldWriteAndReadSummaryHeader() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(1024);

            PartitionSummaryHeader expected = new PartitionSummaryHeader(
                    42,
                    1_700_000_000_000L,
                    1_700_000_500_000L,
                    0x0123_4567_89AB_CDEFL,
                    0xFEDC_BA98_7654_3210L,
                    150,
                    75,
                    25
            );

            expected.write(seg);

            assertThat(PartitionSummaryHeader.isValid(seg)).isTrue();

            PartitionSummaryHeader actual = PartitionSummaryHeader.read(seg);
            assertThat(actual).isNotNull();
            assertThat(actual.seq()).isEqualTo(42);
            assertThat(actual.minTimestampMs()).isEqualTo(1_700_000_000_000L);
            assertThat(actual.maxTimestampMs()).isEqualTo(1_700_000_500_000L);
            assertThat(actual.synapticTagMaskLo()).isEqualTo(0x0123_4567_89AB_CDEFL);
            assertThat(actual.synapticTagMaskHi()).isEqualTo(0xFEDC_BA98_7654_3210L);
            assertThat(actual.semanticCount()).isEqualTo(150);
            assertThat(actual.episodicCount()).isEqualTo(75);
            assertThat(actual.proceduralCount()).isEqualTo(25);
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("Empty or zero-record summary header should write and read cleanly")
    void shouldHandleZeroRecordSummaryHeader() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(1024);

            PartitionSummaryHeader zero = new PartitionSummaryHeader(
                    0, 0L, 0L, 0L, 0L, 0, 0, 0
            );

            zero.write(seg);

            assertThat(PartitionSummaryHeader.isValid(seg)).isTrue();
            PartitionSummaryHeader actual = PartitionSummaryHeader.read(seg);
            assertThat(actual).isEqualTo(zero);
        }
    }

    @Test
    @DisplayName("Corrupt magic or version should fail validation")
    void shouldFailOnCorruptMagicOrVersion() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(1024);

            PartitionSummaryHeader header = new PartitionSummaryHeader(
                    1, 100L, 200L, 0x1L, 0x2L, 5, 10, 15
            );
            header.write(seg);

            // Corrupt magic
            seg.set(ValueLayout.JAVA_INT, PartitionSummaryHeader.OFFSET, 0x12345678);
            assertThat(PartitionSummaryHeader.isValid(seg)).isFalse();
            assertThat(PartitionSummaryHeader.read(seg)).isNull();

            // Restore magic, corrupt version
            seg.set(ValueLayout.JAVA_INT, PartitionSummaryHeader.OFFSET, PartitionSummaryHeader.MAGIC);
            seg.set(ValueLayout.JAVA_INT, PartitionSummaryHeader.OFFSET + 4, 99);
            assertThat(PartitionSummaryHeader.isValid(seg)).isFalse();
            assertThat(PartitionSummaryHeader.read(seg)).isNull();
        }
    }

    @Test
    @DisplayName("Corrupt payload or checksum byte should trigger CRC failure")
    void shouldFailOnCrcMismatch() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(1024);

            PartitionSummaryHeader header = new PartitionSummaryHeader(
                    7, 1000L, 2000L, 0xAAAL, 0xBBBL, 10, 20, 30
            );
            header.write(seg);

            // Corrupt a single byte in minTimestampMs (offset 512 + 16 = 528)
            byte originalByte = seg.get(ValueLayout.JAVA_BYTE, PartitionSummaryHeader.OFFSET + 16);
            seg.set(ValueLayout.JAVA_BYTE, PartitionSummaryHeader.OFFSET + 16, (byte) (originalByte ^ 0xFF));

            assertThat(PartitionSummaryHeader.isValid(seg)).isFalse();
            assertThat(PartitionSummaryHeader.read(seg)).isNull();

            // Restore byte, corrupt crc32c at offset 512 + 60 = 572
            seg.set(ValueLayout.JAVA_BYTE, PartitionSummaryHeader.OFFSET + 16, originalByte);
            assertThat(PartitionSummaryHeader.isValid(seg)).isTrue();

            int originalCrc = seg.get(ValueLayout.JAVA_INT, PartitionSummaryHeader.OFFSET + 60);
            seg.set(ValueLayout.JAVA_INT, PartitionSummaryHeader.OFFSET + 60, originalCrc ^ 0x01010101);
            assertThat(PartitionSummaryHeader.isValid(seg)).isFalse();
            assertThat(PartitionSummaryHeader.read(seg)).isNull();
        }
    }

    @Test
    @DisplayName("Too small segment should fail validation or throw on write")
    void shouldHandleUndersizedSegment() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment small = arena.allocate(256);

            assertThat(PartitionSummaryHeader.isValid(small)).isFalse();
            assertThat(PartitionSummaryHeader.read(small)).isNull();

            PartitionSummaryHeader header = new PartitionSummaryHeader(
                    1, 0, 0, 0, 0, 0, 0, 0
            );
            assertThatThrownBy(() -> header.write(small))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too small");
        }
    }
}
