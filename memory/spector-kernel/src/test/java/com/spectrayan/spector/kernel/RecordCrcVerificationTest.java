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
package com.spectrayan.spector.kernel;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.layout.RegionLayout;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordCrcVerificationTest {

    static final class CrcLayout implements RegionLayout {
        @Override public int layoutId() { return 0x4352434C; }
        @Override public int schemaVersion() { return 1; }
        @Override public int recordStride() { return 16; } // 12B payload + 4B CRC
        @Override public boolean crcEnabled() { return true; }
        @Override public String name() { return "CrcLayout"; }
    }

    static final class CrcRecordMemory extends AbstractRecordMemory<CrcLayout> {
        CrcRecordMemory(MemoryId id, CrcLayout layout, int capacity, long bytes) {
            super(id, layout, capacity, bytes);
        }
    }

    @Test
    @DisplayName("recordCrcWriteAndVerification")
    void recordCrcWriteAndVerification() {
        CrcLayout layout = new CrcLayout();
        MemoryId id = MemoryId.of("test", "crc");

        try (var mem = new CrcRecordMemory(id, layout, 10, 160L)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment record = arena.allocate(16);
                record.set(ValueLayout.JAVA_INT, 0, 12345);
                mem.write(0, record);

                MemorySegment dest = arena.allocate(16);
                mem.read(0, dest);
                assertThat(dest.get(ValueLayout.JAVA_INT, 0)).isEqualTo(12345);

                // Corrupt payload byte to trigger CRC verification failure
                long recordOffset = mem.recordOffset(0);
                mem.segment().set(ValueLayout.JAVA_INT, recordOffset, 99999);

                assertThatThrownBy(() -> mem.read(0, dest))
                        .isInstanceOf(SpectorStorageException.class)
                        .satisfies(ex -> assertThat(((SpectorStorageException) ex).errorCode())
                                .isEqualTo(ErrorCode.RECORD_CRC_CORRUPTED));
            }
        }
    }
}
