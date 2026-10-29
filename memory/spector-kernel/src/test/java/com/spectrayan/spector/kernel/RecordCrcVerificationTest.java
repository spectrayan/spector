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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.layout.ProceduralLayout;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.layout.SemanticLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.layout.WorkingLayout;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.kernel.store.TextBlobMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordCrcVerificationTest {

    @TempDir
    Path tempDir;

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

    @Test
    @DisplayName("EngramLayout and TextBlobLayout enable CRC32C and bump schema version to 2")
    void engramAndTextBlobLayoutsEnableCrcAndBumpSchemaVersion() {
        EngramLayout engramLayout = new EngramLayout(64);
        SemanticLayout semanticLayout = new SemanticLayout(64);
        ProceduralLayout proceduralLayout = new ProceduralLayout(64);
        WorkingLayout workingLayout = new WorkingLayout(64);
        TextBlobLayout textBlobLayout = new TextBlobLayout();

        assertThat(engramLayout.crcEnabled()).isTrue();
        assertThat(semanticLayout.crcEnabled()).isTrue();
        assertThat(proceduralLayout.crcEnabled()).isTrue();
        assertThat(workingLayout.crcEnabled()).isTrue();
        assertThat(textBlobLayout.crcEnabled()).isTrue();

        assertThat(engramLayout.schemaVersion()).isEqualTo(2);
        assertThat(semanticLayout.schemaVersion()).isEqualTo(2);
        assertThat(proceduralLayout.schemaVersion()).isEqualTo(2);
        assertThat(workingLayout.schemaVersion()).isEqualTo(2);
        assertThat(textBlobLayout.schemaVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("EngramLayout records store valid CRC32C and point reads detect header or vector corruption")
    void engramLayoutStoresCrcAndDetectsHeaderAndVectorCorruption() {
        int vecBytes = 32;
        try (var semantic = new SemanticMemory(vecBytes, 8);
             var working = new WorkingMemory(vecBytes, 8)) {
            byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
            EncodingHeader header = new EncodingHeader(
                    1_700_000_000_000L, 0x123456789ABCDEFL, 1.5f, 0.7f,
                    0, (short) 3, (byte) 10, flags
            );
            byte[] vector = new byte[vecBytes];
            for (int i = 0; i < vecBytes; i++) {
                vector[i] = (byte) (i + 1);
            }

            long offset = semantic.write(header, vector);
            assertThat(semantic.readHeader(offset).timestampMs()).isEqualTo(1_700_000_000_000L);
            assertThat(semantic.readVector(offset)).containsExactly(vector);
            assertThat(semantic.readQuantizedVector(0)).containsExactly(vector);

            // Corrupt a vector byte and verify readVector and readQuantizedVector throw RECORD_CRC_CORRUPTED
            long vecOffset = semantic.layout().vectorOffset(offset);
            semantic.segment().set(ValueLayout.JAVA_BYTE, vecOffset + 5, (byte) 0x7F);

            assertThatThrownBy(() -> semantic.readVector(offset))
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(ex -> assertThat(((SpectorStorageException) ex).errorCode())
                            .isEqualTo(ErrorCode.RECORD_CRC_CORRUPTED));

            assertThatThrownBy(() -> semantic.readQuantizedVector(0))
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(ex -> assertThat(((SpectorStorageException) ex).errorCode())
                            .isEqualTo(ErrorCode.RECORD_CRC_CORRUPTED));

            // Restore vector byte, corrupt a header byte (exactNorm at +16) and verify readHeader throws
            semantic.segment().set(ValueLayout.JAVA_BYTE, vecOffset + 5, vector[5]);
            semantic.segment().set(ValueLayout.JAVA_FLOAT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_V2_EXACT_NORM, 99.9f);

            assertThatThrownBy(() -> semantic.readHeader(offset))
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(ex -> assertThat(((SpectorStorageException) ex).errorCode())
                            .isEqualTo(ErrorCode.RECORD_CRC_CORRUPTED));

            // Also verify WorkingMemory point reads
            long wmOffset = working.write(header, vector);
            assertThat(working.readVector(wmOffset)).containsExactly(vector);
            working.segment().set(ValueLayout.JAVA_BYTE, working.layout().vectorOffset(wmOffset), (byte) 0x55);
            assertThatThrownBy(() -> working.readVector(wmOffset))
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(ex -> assertThat(((SpectorStorageException) ex).errorCode())
                            .isEqualTo(ErrorCode.RECORD_CRC_CORRUPTED));
        }
    }

    @Test
    @DisplayName("Backward-compatible v1 EngramLayout records without CRC remain readable")
    void backwardCompatibleV1EngramRecordRemainsReadable() {
        int vecBytes = 16;
        try (var semantic = new SemanticMemory(vecBytes, 4)) {
            byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
            EncodingHeader header = new EncodingHeader(
                    1_700_000_000_000L, 0xCAFEBABEL, 1.0f, 0.5f,
                    0, (short) 1, (byte) 5, flags
            );
            byte[] vector = new byte[vecBytes];
            for (int i = 0; i < vecBytes; i++) {
                vector[i] = (byte) (i + 10);
            }
            long offset = semantic.write(header, vector);

            // Simulate a v1 record by zeroing the reserved CRC block (offsets 52..63)
            semantic.segment().asSlice(offset + EncodingHeaderFields.OFFSET_V2_RESERVED, 12).fill((byte) 0);

            // Mutating a byte in a v1 record (with zeroed CRC block) must remain readable for backward compatibility
            semantic.segment().set(ValueLayout.JAVA_BYTE, semantic.layout().vectorOffset(offset), (byte) 42);
            assertThat(semantic.readHeader(offset).timestampMs()).isEqualTo(1_700_000_000_000L);
            assertThat(semantic.readVector(offset)[0]).isEqualTo((byte) 42);
        }
    }

    @Test
    @DisplayName("TextBlobLayout records store valid CRC32C and detect corrupted text on point read and readAll")
    void textBlobLayoutStoresCrcAndDetectsCorruptedText() {
        Path textFile = tempDir.resolve("text-crc.dat");
        try (TextBlobMemory store = new TextBlobMemory(textFile)) {
            TextBlobMemory.TextPosition pos1 = store.write("mem-1", MemoryType.SEMANTIC, "First integrity protected blob");
            TextBlobMemory.TextPosition pos2 = store.write("mem-2", MemoryType.EPISODIC, "Second integrity protected blob");

            assertThat(store.readTextDirect(pos1.textOffset(), pos1.textLength()))
                    .isEqualTo("First integrity protected blob");
            assertThat(store.readAll()).hasSize(2);

            // Flip a byte inside the first text payload
            byte orig = store.segment().get(ValueLayout.JAVA_BYTE, pos1.textOffset() + 2);
            store.segment().set(ValueLayout.JAVA_BYTE, pos1.textOffset() + 2, (byte) (orig ^ 0xFF));

            assertThatThrownBy(() -> store.readTextDirect(pos1.textOffset(), pos1.textLength()))
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(ex -> assertThat(((SpectorStorageException) ex).errorCode())
                            .isEqualTo(ErrorCode.RECORD_CRC_CORRUPTED));

            assertThatThrownBy(store::readAll)
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(ex -> assertThat(((SpectorStorageException) ex).errorCode())
                            .isEqualTo(ErrorCode.RECORD_CRC_CORRUPTED));

            // Restore and verify second record and readAll work again
            store.segment().set(ValueLayout.JAVA_BYTE, pos1.textOffset() + 2, orig);
            assertThat(store.readTextDirect(pos2.textOffset(), pos2.textLength()))
                    .isEqualTo("Second integrity protected blob");
            assertThat(store.readAll()).hasSize(2);
        }
    }

    @Test
    @DisplayName("Backward-compatible v1 TextBlobMemory entries without CRC trailer remain readable")
    void backwardCompatibleV1TextBlobEntryRemainsReadable() {
        Path textFile = tempDir.resolve("text-v1.dat");
        try (TextBlobMemory store = new TextBlobMemory(textFile)) {
            // Manually append a v1-format entry (1B tier + 4B idLen + idBytes + 4B textLen + textBytes, no CRC trailer)
            byte[] idBytes = "legacy-v1".getBytes(StandardCharsets.UTF_8);
            byte[] textBytes = "Legacy V1 text entry without CRC".getBytes(StandardCharsets.UTF_8);
            int entrySize = 1 + 4 + idBytes.length + 4 + textBytes.length;
            MemorySegment v1Entry = MemorySegment.ofArray(new byte[entrySize]);
            v1Entry.set(ValueLayout.JAVA_BYTE, 0, (byte) MemoryType.SEMANTIC.ordinal());
            v1Entry.set(ValueLayout.JAVA_INT_UNALIGNED, 1, idBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(idBytes), 0, v1Entry, 5, idBytes.length);
            v1Entry.set(ValueLayout.JAVA_INT_UNALIGNED, 5 + idBytes.length, textBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(textBytes), 0, v1Entry, 9 + idBytes.length, textBytes.length);

            long payloadOffset = store.append(v1Entry);
            long textOffset = store.dataOffset() + payloadOffset + 9 + idBytes.length;

            assertThat(store.readTextDirect(textOffset, textBytes.length))
                    .isEqualTo("Legacy V1 text entry without CRC");
            Map<String, TextBlobMemory.TextEntry> all = store.readAll();
            assertThat(all).containsKey("legacy-v1");
            assertThat(all.get("legacy-v1").text()).isEqualTo("Legacy V1 text entry without CRC");
        }
    }
}
