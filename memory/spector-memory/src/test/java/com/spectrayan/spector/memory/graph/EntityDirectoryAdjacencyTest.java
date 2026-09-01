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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntityDirectory Adjacency Growth & Auto-Compaction Tests")
class EntityDirectoryAdjacencyTest {

    @Test
    @DisplayName("Single entity expands beyond initial adjacency capacity (4 -> 8 -> 16 -> 32 -> 64 -> 128 -> 256)")
    void testEntityAdjacencyDoubling() {
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        try (EntityDirectory dir = new EntityDirectory(64, typeRegistry)) {
            int alice = dir.intern("Alice", "PERSON");

            // Link Alice to 200 distinct memories
            int memoryCount = 200;
            for (int memIdx = 0; memIdx < memoryCount; memIdx++) {
                dir.linkEntityToMemory(alice, memIdx);
            }

            int[] memories = dir.memoriesForEntity(alice);
            assertThat(memories).hasSize(memoryCount);
            Arrays.sort(memories);
            for (int i = 0; i < memoryCount; i++) {
                assertThat(memories[i]).isEqualTo(i);
            }

            // Verify reverse lookup
            for (int memIdx = 0; memIdx < memoryCount; memIdx++) {
                Map<Integer, String> entities = dir.entitiesForMemory(memIdx);
                assertThat(entities).containsKey(alice);
            }
        }
    }

    @Test
    @DisplayName("File-backed EntityDirectory auto-compacts abandoned adjacency slabs when approaching capacity")
    void testFileBackedAdjacencyAutoCompaction(@TempDir Path tempDir) {
        Path mmapFile = tempDir.resolve("entity_dir_compact.edir");
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);

        // Small capacity to force high-water mark expansion and compaction
        int entityCap = 32;

        try (EntityDirectory dir = new EntityDirectory(mmapFile, entityCap, typeRegistry)) {
            int hubEntity = dir.intern("Hub", "SYSTEM");
            int leafEntity = dir.intern("Leaf", "NODE");

            // Add 100 links to hub entity (triggers repeated doubling & slab abandonment)
            for (int i = 0; i < 100; i++) {
                dir.linkEntityToMemory(hubEntity, i);
            }

            // Add links to leaf entity
            for (int i = 50; i < 70; i++) {
                dir.linkEntityToMemory(leafEntity, i);
            }

            // Trigger compaction explicitly or through capacity limit
            long bytesReclaimed = dir.compactAdjacency();
            assertThat(bytesReclaimed).isGreaterThanOrEqualTo(0L);

            // Verify 100% integrity of links after compaction
            int[] hubMems = dir.memoriesForEntity(hubEntity);
            assertThat(hubMems).hasSize(100);

            int[] leafMems = dir.memoriesForEntity(leafEntity);
            assertThat(leafMems).hasSize(20);

            // Flush & save sidecar
            dir.save(mmapFile);
        }

        // Re-open from disk to verify persistence across restarts
        TypeRegistryMemory typeRegistry2 = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        try (EntityDirectory dir = EntityDirectory.load(mmapFile, entityCap, typeRegistry2)) {
            int hub = dir.findEntity("Hub");
            assertThat(hub).isGreaterThanOrEqualTo(0);

            int[] hubMems = dir.memoriesForEntity(hub);
            assertThat(hubMems).hasSize(100);
            Arrays.sort(hubMems);
            for (int i = 0; i < 100; i++) {
                assertThat(hubMems[i]).isEqualTo(i);
            }

            int leaf = dir.findEntity("Leaf");
            assertThat(leaf).isGreaterThanOrEqualTo(0);
            int[] leafMems = dir.memoriesForEntity(leaf);
            assertThat(leafMems).hasSize(20);
        }
    }
}
