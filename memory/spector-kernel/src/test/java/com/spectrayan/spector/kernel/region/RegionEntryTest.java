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
package com.spectrayan.spector.kernel.region;

import com.spectrayan.spector.kernel.region.RegionEntry;

import com.spectrayan.spector.kernel.region.RegionId;

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
