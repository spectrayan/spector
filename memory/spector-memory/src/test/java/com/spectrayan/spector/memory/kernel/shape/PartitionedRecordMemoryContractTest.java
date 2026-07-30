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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartitionedRecordMemoryContractTest {

    static final class TestLayout implements MemoryLayout {
        static final int STRIDE = 16;
        @Override public int layoutId() { return 0x50415254; }
        @Override public int schemaVersion() { return 1; }
        @Override public int recordStride() { return STRIDE; }
        @Override public boolean crcEnabled() { return false; }
        @Override public String name() { return "TestPartitionLayout"; }
    }

    static final class DummyPartitionedMemory implements PartitionedRecordMemory<TestLayout> {
        private final MemoryId id = MemoryId.of("test", "partitioned");
        private final TestLayout layout = new TestLayout();
        private final DefaultRecordMemory<TestLayout> active = new DefaultRecordMemory<>(id, layout, 10, 160L);

        @Override
        public RecordMemory<TestLayout> activePartition() {
            return active;
        }

        @Override
        public List<RecordMemory<TestLayout>> historicalPartitions() {
            return List.of();
        }

        @Override
        public RecordMemory<TestLayout> rollPartition() {
            return active;
        }

        @Override
        public MemoryId id() { return id; }

        @Override
        public MemoryShape shape() { return MemoryShape.PARTITIONED; }

        @Override
        public TestLayout layout() { return layout; }

        @Override
        public int size() { return active.size(); }

        @Override
        public int capacity() { return active.capacity(); }

        @Override
        public int schemaVersion() { return layout.schemaVersion(); }

        @Override
        public Arena arena() { return active.arena(); }

        @Override
        public MemorySegment segment() { return active.segment(); }

        @Override
        public void flush() {}

        @Override
        public void close() { active.close(); }

        @Override
        public long write(long recordId, MemorySegment recordBytes) {
            return active.write(recordId, recordBytes);
        }

        @Override
        public void read(long recordId, MemorySegment dest) {
            active.read(recordId, dest);
        }

        @Override
        public long recordOffset(long recordId) {
            return active.recordOffset(recordId);
        }
    }

    @Test
    @DisplayName("partitionedMemoryContractDelegation")
    void partitionedMemoryContractDelegation() {
        try (var pmem = new DummyPartitionedMemory()) {
            assertThat(pmem.shape()).isEqualTo(MemoryShape.PARTITIONED);
            assertThat(pmem.activePartition()).isNotNull();
            assertThat(pmem.historicalPartitions()).isEmpty();

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment record = arena.allocate(TestLayout.STRIDE);
                record.set(ValueLayout.JAVA_INT, 0, 777);
                pmem.write(0, record);

                MemorySegment dest = arena.allocate(TestLayout.STRIDE);
                pmem.read(0, dest);
                assertThat(dest.get(ValueLayout.JAVA_INT, 0)).isEqualTo(777);
            }
        }
    }
}
