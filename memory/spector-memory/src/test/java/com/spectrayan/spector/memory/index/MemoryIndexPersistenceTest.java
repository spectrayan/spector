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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryIndexPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should save and load MemoryIndex in standard SMKM V5 format")
    void shouldSaveAndLoadV5() {
        MemoryIndex original = new MemoryIndex();
        original.register("mem-1", new MemoryLocation(MemoryType.WORKING, 128L, -1),
                "hello world", MemorySource.OBSERVED, new String[]{"greeting", "test"});
        original.register("mem-2", new MemoryLocation(MemoryType.EPISODIC, 1024L, 5, 200L, 11),
                "episodic text", MemorySource.INFERRED, new String[]{"episode"}, Map.of("key1", "val1"));

        Path indexFile = tempDir.resolve("index.midx");
        Path idPoolFile = tempDir.resolve("index.idpl");

        // Save
        original.save(indexFile);
        assertThat(Files.exists(indexFile)).isTrue();
        assertThat(Files.exists(idPoolFile)).isTrue();

        // Load
        MemoryIndex loaded = MemoryIndex.load(indexFile);
        assertThat(loaded.size()).isEqualTo(2);

        // Verify mem-1
        MemoryLocation loc1 = loaded.locate("mem-1");
        assertThat(loc1).isNotNull();
        assertThat(loc1.type()).isEqualTo(MemoryType.WORKING);
        assertThat(loc1.offset()).isEqualTo(128L);
        assertThat(loaded.text("mem-1")).isEqualTo("hello world");
        assertThat(loaded.source("mem-1")).isEqualTo(MemorySource.OBSERVED);
        assertThat(loaded.tags("mem-1")).containsExactlyInAnyOrder("greeting", "test");

        // Verify mem-2
        MemoryLocation loc2 = loaded.locate("mem-2");
        assertThat(loc2).isNotNull();
        assertThat(loc2.type()).isEqualTo(MemoryType.EPISODIC);
        assertThat(loc2.offset()).isEqualTo(1024L);
        assertThat(loc2.partitionIndex()).isEqualTo(5);
        assertThat(loc2.textOffset()).isEqualTo(200L);
        assertThat(loc2.textLength()).isEqualTo(11);
        assertThat(loaded.source("mem-2")).isEqualTo(MemorySource.INFERRED);
        assertThat(loaded.tags("mem-2")).containsExactly("episode");
        assertThat(loaded.metadata("mem-2")).containsEntry("key1", "val1");
    }

    @Test
    @DisplayName("should automatically migrate legacy V4 file on load")
    void shouldMigrateLegacyV4() throws IOException {
        Path legacyFile = tempDir.resolve("legacy.midx");

        // 1. Manually write a legacy V4 index file structure
        // Header: magic 'MIDX' (0x4D494458) + version 4 + count 1 + reserved 0
        try (FileChannel ch = FileChannel.open(legacyFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(16);
            header.putInt(0x4D494458); // 'MIDX'
            header.putInt(4);          // version 4
            header.putInt(1);          // count 1
            header.putInt(0);          // reserved
            header.flip();
            ch.write(header);

            // Write 1 legacy entry:
            // - [4B id_len] + N B id_bytes
            // - [4B type_ord] + [8B offset] + [4B partition_index]
            // - [4B text_len] + N B text_bytes (V4: text_len is 0)
            // - [4B source_ord]
            // - [4B tag_count]
            // - [4B metadata_count] + { [4B key_len] + N B key + [4B val_len] + N B val }
            // - [8B textOffset] + [4B textLength]
            String id = "legacy-mem";
            byte[] idBytes = id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            int entrySize = 4 + idBytes.length
                    + 4 + 8 + 4
                    + 4 // text_len (0)
                    + 4 // source_ord
                    + 4 // tag_count (0)
                    + 4 // metadata_count (1)
                    + 4 + 3 + 4 + 3 // key "foo" -> val "bar"
                    + 8 + 4; // textOffset + textLength
            
            ByteBuffer entry = ByteBuffer.allocate(entrySize);
            entry.putInt(idBytes.length);
            entry.put(idBytes);
            entry.putInt(MemoryType.SEMANTIC.ordinal());
            entry.putLong(512L);
            entry.putInt(-1);
            entry.putInt(0); // text_len
            entry.putInt(MemorySource.OBSERVED.ordinal());
            entry.putInt(0); // tags count
            entry.putInt(1); // metadata count
            entry.putInt(3); entry.put("foo".getBytes());
            entry.putInt(3); entry.put("bar".getBytes());
            entry.putLong(5000L);
            entry.putInt(25);
            entry.flip();
            ch.write(entry);
        }

        // 2. Load the legacy index
        MemoryIndex index = MemoryIndex.load(legacyFile);
        assertThat(index.size()).isEqualTo(1);

        MemoryLocation loc = index.locate("legacy-mem");
        assertThat(loc).isNotNull();
        assertThat(loc.type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(loc.offset()).isEqualTo(512L);
        assertThat(loc.textOffset()).isEqualTo(5000L);
        assertThat(loc.textLength()).isEqualTo(25);
        assertThat(index.metadata("legacy-mem")).containsEntry("foo", "bar");

        // 3. Save it to trigger automatic V5 rewrite
        index.save(legacyFile);

        // Verify slot table and pool are created
        Path idPoolFile = tempDir.resolve("legacy.idpl");
        assertThat(Files.exists(legacyFile)).isTrue();
        assertThat(Files.exists(idPoolFile)).isTrue();

        // Load again and verify standard V5 works
        MemoryIndex reloaded = MemoryIndex.load(legacyFile);
        assertThat(reloaded.size()).isEqualTo(1);
        assertThat(reloaded.locate("legacy-mem").offset()).isEqualTo(512L);
    }
}
