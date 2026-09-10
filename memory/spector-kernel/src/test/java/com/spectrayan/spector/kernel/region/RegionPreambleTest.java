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
package com.spectrayan.spector.kernel.region;

import com.spectrayan.spector.kernel.shape.MemoryShape;

import com.spectrayan.spector.kernel.region.RegionPreamble;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

class RegionPreambleTest {

    @Test
    @DisplayName("writeAndReadRoundTrip")
    void writeAndReadRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            RegionPreamble.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            assertThat(RegionPreamble.readSchemaVersion(segment, 0)).isEqualTo(1);
            assertThat(RegionPreamble.readShape(segment, 0)).isEqualTo(MemoryShape.RECORD);
            assertThat(RegionPreamble.readCapacity(segment, 0)).isEqualTo(1000L);
            assertThat(RegionPreamble.readCount(segment, 0)).isEqualTo(500L);
        }
    }

    @Test
    @DisplayName("isValidReturnsTrueForValidHeader")
    void isValidReturnsTrueForValidHeader() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            RegionPreamble.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            assertThat(RegionPreamble.isValid(segment, 0)).isTrue();
        }
    }

    @Test
    @DisplayName("isValidReturnsFalseForBadMagic")
    void isValidReturnsFalseForBadMagic() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            RegionPreamble.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0, 0xDEADBEEF);
            
            assertThat(RegionPreamble.isValid(segment, 0)).isFalse();
        }
    }

    @Test
    @DisplayName("isValidReturnsFalseForCorruptedCrc")
    void isValidReturnsFalseForCorruptedCrc() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            RegionPreamble.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            // Corrupt a byte in the first 56 bytes
            byte b = segment.get(ValueLayout.JAVA_BYTE, 4);
            segment.set(ValueLayout.JAVA_BYTE, 4, (byte) (b + 1));
            
            assertThat(RegionPreamble.isValid(segment, 0)).isFalse();
        }
    }

    @Test
    @DisplayName("writeCountUpdatesCrcAndCount")
    void writeCountUpdatesCrcAndCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            RegionPreamble.write(segment, 0, 1, MemoryShape.RECORD, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
            
            RegionPreamble.writeCount(segment, 0, 600L);
            
            assertThat(RegionPreamble.readCount(segment, 0)).isEqualTo(600L);
            assertThat(RegionPreamble.isValid(segment, 0)).isTrue();
        }
    }

    @Test
    @DisplayName("readShapeReturnsCorrectShape")
    void readShapeReturnsCorrectShape() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128, 64);
            for (MemoryShape shape : MemoryShape.values()) {
                RegionPreamble.write(segment, 0, 1, shape, 3, 1000L, 500L, 32, 42, 1000000L, 2000000L);
                assertThat(RegionPreamble.readShape(segment, 0)).isEqualTo(shape);
            }
        }
    }

    @Test
    @DisplayName("headerExactlySixtyFourBytes")
    void headerExactlySixtyFourBytes() {
        assertThat(RegionPreamble.PREAMBLE_BYTES).isEqualTo(64);
    }

    @Test
    @DisplayName("magicConstantIsSMKM")
    void magicConstantIsSMKM() {
        assertThat(RegionPreamble.MAGIC).isEqualTo(0x534D4B4D);
    }
}
