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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import static org.assertj.core.api.Assertions.*;

class RegionEntryTest {
    @Test
    void testWriteReadVerify() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64);
            
            RegionEntry entry = new RegionEntry(
                RegionId.SEMANTIC,
                (short) (RegionEntry.FLAG_LIVE | RegionEntry.FLAG_GROWABLE),
                1024L,
                2048L,
                100L,
                5000,
                32,
                42,
                2
            );
            
            RegionEntry.write(segment, 0, entry);
            
            RegionEntry readEntry = RegionEntry.read(segment, 0);
            assertThat(readEntry.regionId()).isEqualTo(RegionId.SEMANTIC);
            assertThat(readEntry.isLive()).isTrue();
            assertThat(readEntry.isGrowable()).isTrue();
            assertThat(readEntry.offset()).isEqualTo(1024L);
            assertThat(readEntry.allocatedSize()).isEqualTo(2048L);
            assertThat(readEntry.usedSize()).isEqualTo(100L);
            assertThat(readEntry.capacity()).isEqualTo(5000);
            assertThat(readEntry.stride()).isEqualTo(32);
            assertThat(readEntry.layoutId()).isEqualTo(42);
            assertThat(readEntry.schemaVersion()).isEqualTo(2);
            
            RegionEntry updated = readEntry.withOffset(4096L).withAllocatedSize(8192L);
            assertThat(updated.offset()).isEqualTo(4096L);
            assertThat(updated.allocatedSize()).isEqualTo(8192L);
        }
    }
}
