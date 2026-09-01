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
package com.spectrayan.spector.memory.kernel.bundle;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import org.junit.jupiter.api.Test;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BundleDirectoryTest {
    @Test
    void testWriteReadVerify() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(4096 * 2);
            
            RegionEntry entry1 = new RegionEntry(RegionId.SEMANTIC, RegionEntry.FLAG_LIVE, 4096, 1024, 0, 100, 32, 1, 1);
            RegionEntry entry2 = new RegionEntry(RegionId.EPISODIC, RegionEntry.FLAG_LIVE, 8192, 1024, 0, 100, 32, 2, 1);
            RegionEntry entry3 = new RegionEntry(RegionId.PROCEDURAL, (short) 0, 12288, 1024, 0, 100, 32, 3, 1);
            RegionEntry entry4 = new RegionEntry(RegionId.TEXT, RegionEntry.FLAG_LIVE, 16384, 1024, 0, 100, 32, 4, 1);
            
            List<RegionEntry> entries = List.of(entry1, entry2, entry3, entry4);
            BundleDirectory dir = new BundleDirectory(BundleSubHeader.MAGIC_PARTITION, 4, entries);
            
            dir.write(segment);
            
            assertThat(MemoryHeader.isValid(segment, 0)).isTrue();
            
            BundleDirectory readDir = BundleDirectory.read(segment);
            assertThat(readDir.bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_PARTITION);
            assertThat(readDir.maxRegions()).isEqualTo(4);
            
            assertThat(readDir.findRegion(RegionId.SEMANTIC)).isNotNull();
            assertThat(readDir.findRegion(RegionId.EPISODIC)).isNotNull();
            
            assertThat(readDir.liveRegionCount()).isEqualTo(3);
            assertThat(readDir.liveRegions()).hasSize(3);
            
            assertThat(BundleDirectory.dataStartOffset(4)).isEqualTo(4096);
        }
    }
}
