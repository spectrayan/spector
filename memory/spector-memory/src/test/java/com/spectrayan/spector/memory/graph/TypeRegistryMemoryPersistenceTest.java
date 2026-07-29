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
package com.spectrayan.spector.memory.graph;

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

class TypeRegistryMemoryPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should save and load TypeRegistryMemory in standard SMKM registry format")
    void shouldSaveAndLoadRegistry() throws Exception {
        TypeRegistryMemory original = TypeRegistryMemory.seeded("entity-type", "PERSON", "ORGANIZATION");
        
        // Intern dynamic ones
        int codeId = original.intern("CODE");
        int docId = original.intern("DOCUMENT");

        Path filePath = tempDir.resolve("entity-types.reg");

        // Save
        original.save(filePath);
        assertThat(Files.exists(filePath)).isTrue();

        // Load
        TypeRegistryMemory loaded = TypeRegistryMemory.load(filePath, "entity-type", "PERSON", "ORGANIZATION");
        
        assertThat(loaded.size()).isEqualTo(4);
        assertThat(loaded.nameOf(codeId)).isEqualTo("CODE");
        assertThat(loaded.nameOf(docId)).isEqualTo("DOCUMENT");
        assertThat(loaded.idOf("PERSON")).isEqualTo(0);
        assertThat(loaded.idOf("ORGANIZATION")).isEqualTo(1);
    }

    @Test
    @DisplayName("should transparently migrate legacy TREG registry files on load")
    void shouldMigrateLegacyRegistry() throws IOException {
        Path legacyFile = tempDir.resolve("legacy-entity-types.reg");

        // 1. Manually write a legacy TREG registry format file
        // Header: [magic:4B (0x54524547)][version:4B (1)][count:4B (2)]
        try (FileChannel ch = FileChannel.open(legacyFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(12);
            header.putInt(0x54524547); // 'TREG'
            header.putInt(1);          // version 1
            header.putInt(2);          // count 2
            header.flip();
            ch.write(header);

            // Entry 1: nameLen:4B, nameUtf8:varB, id:4B
            String name1 = "SOFTWARE";
            byte[] bytes1 = name1.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ByteBuffer entry1 = ByteBuffer.allocate(4 + bytes1.length + 4);
            entry1.putInt(bytes1.length);
            entry1.put(bytes1);
            entry1.putInt(10);
            entry1.flip();
            ch.write(entry1);

            // Entry 2: nameLen:4B, nameUtf8:varB, id:4B
            String name2 = "HARDWARE";
            byte[] bytes2 = name2.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ByteBuffer entry2 = ByteBuffer.allocate(4 + bytes2.length + 4);
            entry2.putInt(bytes2.length);
            entry2.put(bytes2);
            entry2.putInt(11);
            entry2.flip();
            ch.write(entry2);
        }

        // 2. Load the legacy registry file
        TypeRegistryMemory registry = TypeRegistryMemory.load(legacyFile, "entity-type", "PERSON");
        
        // size should be 3 (PERSON seed + 2 migrated entries)
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.idOf("SOFTWARE")).isEqualTo(10);
        assertThat(registry.idOf("HARDWARE")).isEqualTo(11);
        assertThat(registry.idOf("PERSON")).isEqualTo(12); // seed auto-assigned next sequential ID

        // 3. Save it to trigger automatic V2 standard rewrite
        registry.save(legacyFile);

        // Load again and verify standard SMKM loading
        TypeRegistryMemory reloaded = TypeRegistryMemory.load(legacyFile, "entity-type", "PERSON");
        assertThat(reloaded.size()).isEqualTo(3);
        assertThat(reloaded.idOf("SOFTWARE")).isEqualTo(10);
        assertThat(reloaded.idOf("HARDWARE")).isEqualTo(11);
    }
}
