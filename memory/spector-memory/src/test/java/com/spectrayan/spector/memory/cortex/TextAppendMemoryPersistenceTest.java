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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.cortex.TextAppendMemory.TextEntry;
import com.spectrayan.spector.memory.cortex.TextAppendMemory.TextPosition;
import com.spectrayan.spector.memory.kernel.MemoryHeader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextAppendMemoryPersistenceTest {

    @TempDir
    Path tempDir;

    private Path textFile;

    @BeforeEach
    void setUp() {
        textFile = tempDir.resolve("text.dat");
    }

    @Test
    void testLegacyFormatMigration() throws IOException {
        // 1. Manually write a legacy V2 format file
        try (FileChannel ch = FileChannel.open(textFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(16);
            header.putInt(StorageLayout.TEXT_DAT_MAGIC); // 0x54585444
            header.putInt(StorageLayout.TEXT_DAT_VERSION); // 2
            header.putInt(2); // entry_count
            header.putInt(0); // reserved
            header.flip();
            ch.write(header);

            // Write entry 1
            byte[] id1Bytes = "mem-1".getBytes(StandardCharsets.UTF_8);
            byte[] text1Bytes = "Hello Legacy World 1".getBytes(StandardCharsets.UTF_8);
            int size1 = 1 + 4 + id1Bytes.length + 4 + text1Bytes.length;
            ByteBuffer entry1 = ByteBuffer.allocate(size1);
            entry1.put((byte) MemoryType.SEMANTIC.ordinal());
            entry1.putInt(id1Bytes.length);
            entry1.put(id1Bytes);
            entry1.putInt(text1Bytes.length);
            entry1.put(text1Bytes);
            entry1.flip();
            ch.write(entry1);

            // Write entry 2
            byte[] id2Bytes = "mem-2".getBytes(StandardCharsets.UTF_8);
            byte[] text2Bytes = "Hello Legacy World 2".getBytes(StandardCharsets.UTF_8);
            int size2 = 1 + 4 + id2Bytes.length + 4 + text2Bytes.length;
            ByteBuffer entry2 = ByteBuffer.allocate(size2);
            entry2.put((byte) MemoryType.EPISODIC.ordinal());
            entry2.putInt(id2Bytes.length);
            entry2.put(id2Bytes);
            entry2.putInt(text2Bytes.length);
            entry2.put(text2Bytes);
            entry2.flip();
            ch.write(entry2);
        }

        // 2. Open via TextAppendMemory (which triggers migration on readAll)
        try (TextAppendMemory store = new TextAppendMemory(textFile)) {
            Map<String, TextEntry> entries = store.readAll();

            assertThat(entries).hasSize(2);
            assertThat(entries.get("mem-1").text()).isEqualTo("Hello Legacy World 1");
            assertThat(entries.get("mem-1").tier()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(entries.get("mem-2").text()).isEqualTo("Hello Legacy World 2");
            assertThat(entries.get("mem-2").tier()).isEqualTo(MemoryType.EPISODIC);

            // Verify standard SMKM header is present now
            try (FileChannel ch = FileChannel.open(textFile, StandardOpenOption.READ)) {
                ByteBuffer magicBuf = ByteBuffer.allocate(4);
                ch.read(magicBuf);
                magicBuf.flip();
                int magic = magicBuf.getInt();
                assertThat(magic).isIn(MemoryHeader.MAGIC, 0x4D4B4D53);
            }
        }
    }

    @Test
    void testXxHashDeduplication() {
        try (TextAppendMemory store = new TextAppendMemory(textFile)) {
            // Write distinct strings
            TextPosition pos1 = store.write("mem-1", MemoryType.SEMANTIC, "Unique text content");
            TextPosition pos2 = store.write("mem-2", MemoryType.SEMANTIC, "Another unique content");

            // Write duplicate string
            TextPosition pos3 = store.write("mem-3", MemoryType.SEMANTIC, "Unique text content");

            assertThat(pos1.textLength()).isEqualTo(pos3.textLength());
            assertThat(pos1.textOffset()).isEqualTo(pos3.textOffset());
            assertThat(pos2.textOffset()).isNotEqualTo(pos1.textOffset());

            Map<String, TextEntry> entries = store.readAll();
            // deduplicated entry is not written to disk again, so 2 entries physically
            assertThat(entries).hasSize(2);
            assertThat(entries.get("mem-1").text()).isEqualTo("Unique text content");
            assertThat(entries.get("mem-2").text()).isEqualTo("Another unique content");
        }
    }
}
