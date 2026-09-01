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
package com.spectrayan.spector.memory.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryHeaderTest {

    @Test
    @DisplayName("writeAndReadRoundTrip")
    void writeAndReadRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            MemoryHeader.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            assertThat(MemoryHeader.readSchemaVersion(segment, 0)).isEqualTo(1);
            assertThat(MemoryHeader.readShape(segment, 0)).isEqualTo(MemoryShape.RECORD);
            assertThat(MemoryHeader.readCapacity(segment, 0)).isEqualTo(1000L);
            assertThat(MemoryHeader.readCount(segment, 0)).isEqualTo(500L);
        }
    }

    @Test
    @DisplayName("isValidReturnsTrueForValidHeader")
    void isValidReturnsTrueForValidHeader() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            MemoryHeader.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            assertThat(MemoryHeader.isValid(segment, 0)).isTrue();
        }
    }

    @Test
    @DisplayName("isValidReturnsFalseForBadMagic")
    void isValidReturnsFalseForBadMagic() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            MemoryHeader.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0, 0xDEADBEEF);
            
            assertThat(MemoryHeader.isValid(segment, 0)).isFalse();
        }
    }

    @Test
    @DisplayName("isValidReturnsFalseForCorruptedCrc")
    void isValidReturnsFalseForCorruptedCrc() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            MemoryHeader.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            // Corrupt a byte in the first 56 bytes
            byte b = segment.get(ValueLayout.JAVA_BYTE, 4);
            segment.set(ValueLayout.JAVA_BYTE, 4, (byte) (b + 1));
            
            assertThat(MemoryHeader.isValid(segment, 0)).isFalse();
        }
    }

    @Test
    @DisplayName("writeCountUpdatesCrcAndCount")
    void writeCountUpdatesCrcAndCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            MemoryHeader.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            MemoryHeader.writeCount(segment, 0, 600L);
            
            assertThat(MemoryHeader.readCount(segment, 0)).isEqualTo(600L);
            assertThat(MemoryHeader.isValid(segment, 0)).isTrue();
        }
    }

    @Test
    @DisplayName("readShapeReturnsCorrectShape")
    void readShapeReturnsCorrectShape() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            for (MemoryShape shape : MemoryShape.values()) {
                MemoryHeader.write(segment, 0, 1, shape, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
                assertThat(MemoryHeader.readShape(segment, 0)).isEqualTo(shape);
            }
        }
    }

    @Test
    @DisplayName("headerExactlySixtyFourBytes")
    void headerExactlySixtyFourBytes() {
        assertThat(MemoryHeader.HEADER_BYTES).isEqualTo(64);
    }

    @Test
    @DisplayName("magicConstantIsSMKM")
    void magicConstantIsSMKM() {
        assertThat(MemoryHeader.MAGIC).isEqualTo(0x534D4B4D);
    }
}
