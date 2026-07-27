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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordMemoryContractTest {

    static final class TestLayout implements MemoryLayout {
        static final int STRIDE = 32;
        @Override public int layoutId() { return 0x54455354; }
        @Override public int schemaVersion() { return 1; }
        @Override public int recordStride() { return STRIDE; }
        @Override public boolean crcEnabled() { return false; }
        @Override public String name() { return "TestLayout"; }
    }

    static final class TestRecordMemory extends AbstractRecordMemory<TestLayout> {
        TestRecordMemory(MemoryId id, TestLayout layout, int capacity) {
            super(id, layout, capacity, (long) capacity * layout.recordStride());
        }
        TestRecordMemory(MemoryId id, TestLayout layout, int capacity, Path filePath) {
            super(id, layout, capacity, (long) capacity * layout.recordStride(), filePath);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("writeAndReadRoundTrip")
    void writeAndReadRoundTrip() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "roundtrip");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment record = arena.allocate(TestLayout.STRIDE, 64);
                record.set(ValueLayout.JAVA_INT, 0, 999);
                mem.write(3, record);
                
                MemorySegment dest = arena.allocate(TestLayout.STRIDE, 64);
                mem.read(3, dest);
                
                assertThat(dest.get(ValueLayout.JAVA_INT, 0)).isEqualTo(999);
            }
        }
    }

    @Test
    @DisplayName("recordOffsetCalculation")
    void recordOffsetCalculation() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "offset");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            long dataOffset = mem.isPersistent() ? 64 : 0;
            assertThat(mem.recordOffset(2)).isEqualTo(dataOffset + 2L * TestLayout.STRIDE);
        }
    }

    @Test
    @DisplayName("writeUpdatesSize")
    void writeUpdatesSize() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "size");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment record = arena.allocate(TestLayout.STRIDE, 64);
                mem.write(0, record);
                assertThat(mem.size()).isEqualTo(1);
                mem.write(5, record);
                assertThat(mem.size()).isEqualTo(6);
            }
        }
    }

    @Test
    @DisplayName("writeAtCapacityThrows")
    void writeAtCapacityThrows() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "throws_cap");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment record = arena.allocate(TestLayout.STRIDE, 64);
                assertThatThrownBy(() -> mem.write(10, record))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            }
        }
    }

    @Test
    @DisplayName("negativeRecordIdThrows")
    void negativeRecordIdThrows() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "throws_neg");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment record = arena.allocate(TestLayout.STRIDE, 64);
                assertThatThrownBy(() -> mem.write(-1, record))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            }
        }
    }

    @Test
    @DisplayName("sizeStartsAtZero")
    void sizeStartsAtZero() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "starts_zero");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            assertThat(mem.size()).isZero();
        }
    }

    @Test
    @DisplayName("capacityMatchesConstructor")
    void capacityMatchesConstructor() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "cap_match");
        
        try (var mem = new TestRecordMemory(id, layout, 42)) {
            assertThat(mem.capacity()).isEqualTo(42);
        }
    }

    @Test
    @DisplayName("shapeIsRecord")
    void shapeIsRecord() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "shape");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            assertThat(mem.shape()).isEqualTo(MemoryShape.RECORD);
        }
    }

    @Test
    @DisplayName("volatileMemoryIsNotPersistent")
    void volatileMemoryIsNotPersistent() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "not_persistent");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            assertThat(mem.isPersistent()).isFalse();
        }
    }

    @Test
    @DisplayName("persistentWriteAndReload")
    void persistentWriteAndReload() {
        Path file = tempDir.resolve("test.mem");
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "persistent");
        
        // Write
        try (var mem = new TestRecordMemory(id, layout, 100, file)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment record = arena.allocate(TestLayout.STRIDE, 64);
                record.set(ValueLayout.JAVA_INT, 0, 42);
                mem.write(0, record);
            }
            assertThat(mem.size()).isEqualTo(1);
        }
        
        // Reopen
        try (var mem = new TestRecordMemory(id, layout, 100, file)) {
            assertThat(mem.size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("flushIsNoOpForVolatile")
    void flushIsNoOpForVolatile() {
        TestLayout layout = new TestLayout();
        MemoryId id = MemoryId.of("test", "flush_noop");
        
        try (var mem = new TestRecordMemory(id, layout, 10)) {
            mem.flush(); // Should not throw
        }
    }
}
