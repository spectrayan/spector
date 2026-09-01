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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.model.MemoryType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Global Graph Index Correctness (graphSlot High-Water & Tombstoning)")
class GlobalGraphIndexTest {

    private static final String[] EMPTY_TAGS = new String[0];

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("graphSlot is monotonically increasing and never reused across deletions")
    void monotonicSlotAllocationAcrossDeletions() {
        IndexRecordMemory index = new IndexRecordMemory();

        int slot0 = index.allocateGraphSlot();
        int slot1 = index.allocateGraphSlot();
        int slot2 = index.allocateGraphSlot();

        assertThat(slot0).isEqualTo(0);
        assertThat(slot1).isEqualTo(1);
        assertThat(slot2).isEqualTo(2);

        index.register("mem-0", new MemoryLocation(MemoryType.EPISODIC, 100L, slot0), "text 0", MemorySource.USER_STATED, EMPTY_TAGS);
        index.register("mem-1", new MemoryLocation(MemoryType.SEMANTIC, 200L, slot1), "text 1", MemorySource.USER_STATED, EMPTY_TAGS);
        index.register("mem-2", new MemoryLocation(MemoryType.PROCEDURAL, 300L, slot2), "text 2", MemorySource.USER_STATED, EMPTY_TAGS);

        assertThat(index.idAt(slot0)).isEqualTo("mem-0");
        assertThat(index.idAt(slot1)).isEqualTo("mem-1");
        assertThat(index.idAt(slot2)).isEqualTo("mem-2");

        // Remove mem-1 (tombstone slot 1)
        index.remove("mem-1");

        // Tombstoned slot should return null for idAt
        assertThat(index.idAt(slot1)).isNull();

        // Allocating a new slot after deletion MUST produce 3 (monotonic high-water mark), NOT reuse slot 1
        int slot3 = index.allocateGraphSlot();
        assertThat(slot3).isEqualTo(3);

        index.register("mem-3", new MemoryLocation(MemoryType.EPISODIC, 400L, slot3), "text 3", MemorySource.USER_STATED, EMPTY_TAGS);
        assertThat(index.idAt(slot3)).isEqualTo("mem-3");
    }

    @Test
    @DisplayName("MIDX v7 header persists graphSlotHighWater across save/load")
    void headerPersistsGraphSlotHighWater() {
        Path midxPath = tempDir.resolve("test_highwater.midx");
        IndexRecordMemory index = new IndexRecordMemory();

        int slot0 = index.allocateGraphSlot(); // 0
        int slot1 = index.allocateGraphSlot(); // 1
        int slot2 = index.allocateGraphSlot(); // 2

        index.register("mem-0", new MemoryLocation(MemoryType.EPISODIC, 100L, slot0), "text 0", MemorySource.USER_STATED, EMPTY_TAGS);
        index.register("mem-1", new MemoryLocation(MemoryType.SEMANTIC, 200L, slot1), "text 1", MemorySource.USER_STATED, EMPTY_TAGS);
        index.register("mem-2", new MemoryLocation(MemoryType.PROCEDURAL, 300L, slot2), "text 2", MemorySource.USER_STATED, EMPTY_TAGS);

        index.remove("mem-1"); // high-water remains 3
        index.save(midxPath);

        IndexRecordMemory loaded = IndexRecordMemory.load(midxPath);
        assertThat(loaded.graphSlotHighWater()).isEqualTo(3);
        assertThat(loaded.idAt(0)).isEqualTo("mem-0");
        assertThat(loaded.idAt(1)).isNull();
        assertThat(loaded.idAt(2)).isEqualTo("mem-2");

        int nextSlot = loaded.allocateGraphSlot();
        assertThat(nextSlot).isEqualTo(3);
    }
}
