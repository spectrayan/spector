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
package com.spectrayan.spector.memory.temporal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalChainMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should automatically migrate legacy TPCH file to SMKM")
    void shouldMigrateLegacyTpchToSmkm() throws IOException {
        Path legacyFile = tempDir.resolve("legacy.chain");
        int capacity = 10;

        // 1. Manually write a legacy TPCH file structure (16-byte header)
        // [0:4] magic 'TPCH'
        // [4:4] version 2
        // [8:4] capacity 10
        // [12:4] count 3
        try (FileChannel ch = FileChannel.open(legacyFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(16);
            header.putInt(0x54504348); // 'TPCH'
            header.putInt(2);          // version
            header.putInt(capacity);   // capacity
            header.putInt(3);          // count
            header.flip();
            ch.write(header);

            // Extend file to 16 + 10 * 16 bytes
            long totalBytes = 16 + capacity * 16;
            ch.position(totalBytes - 1);
            ch.write(ByteBuffer.wrap(new byte[]{0}));

            // Let's write node data: link 0 -> 1 -> 2
            // Node size is 16 bytes: prevIdx(4) + nextIdx(4) + sessionId(4) + epochSec(4)
            ByteBuffer data = ByteBuffer.allocate(capacity * 16);
            data.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            
            // Node 0: prev=-1, next=1, session=42
            data.putInt(-1); data.putInt(1); data.putInt(42); data.putInt(9999);
            // Node 1: prev=0, next=2, session=42
            data.putInt(0); data.putInt(2); data.putInt(42); data.putInt(9999);
            // Node 2: prev=1, next=-1, session=42
            data.putInt(1); data.putInt(-1); data.putInt(42); data.putInt(9999);
            
            // Fill remaining nodes with -1 sentinels
            for (int i = 3; i < capacity; i++) {
                data.putInt(-1); data.putInt(-1); data.putInt(0); data.putInt(0);
            }
            data.flip();
            ch.position(16);
            ch.write(data);
        }

        // 2. Open using the refactored TemporalChainMemory (which reads SMKM or migrates TPCH)
        try (TemporalChainMemory memory = new TemporalChainMemory(legacyFile, capacity)) {
            // Verify capacity and size
            assertThat(memory.capacity()).isEqualTo(capacity);

            // Verify links are intact
            assertThat(memory.isLinked(2)).isTrue();
            assertThat(memory.isLinked(3)).isFalse();

            assertThat(memory.getNextIndex(0)).isEqualTo(1);
            assertThat(memory.getPrevIndex(1)).isEqualTo(0);
            assertThat(memory.getNextIndex(1)).isEqualTo(2);
            assertThat(memory.getPrevIndex(2)).isEqualTo(1);
            assertThat(memory.getNextIndex(2)).isEqualTo(-1);

            assertThat(memory.getSessionId(0)).isEqualTo(42);
            assertThat(memory.getSessionId(1)).isEqualTo(42);
            assertThat(memory.getSessionId(2)).isEqualTo(42);

            assertThat(memory.getEpochSec(0)).isEqualTo(9999);
            assertThat(memory.getEpochSec(1)).isEqualTo(9999);
            assertThat(memory.getEpochSec(2)).isEqualTo(9999);

            // Verify new ChainMemory methods
            assertThat(memory.head()).isEqualTo(0);
            assertThat(memory.tail()).isEqualTo(2);
            assertThat(memory.chainLength()).isEqualTo(3);
        }

        // 3. Verify file is now in SMKM format (64-byte header)
        try (FileChannel ch = FileChannel.open(legacyFile, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(64);
            header.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            ch.read(header);
            header.flip();
            int magic = header.getInt();
            assertThat(magic).isEqualTo(0x534D4B4D); // 'SMKM'
            
            int schemaVersion = header.getInt();
            assertThat(schemaVersion).isEqualTo(2);
            
            int shape = header.getInt();
            assertThat(shape).isEqualTo(3); // MemoryShape.CHAIN ordinal
        }
    }
}
