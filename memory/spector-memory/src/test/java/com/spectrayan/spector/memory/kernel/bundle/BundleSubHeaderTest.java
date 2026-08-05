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

import org.junit.jupiter.api.Test;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import static org.assertj.core.api.Assertions.*;

class BundleSubHeaderTest {
    @Test
    void testWriteReadVerify() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128);
            
            BundleSubHeader.write(segment, BundleSubHeader.MAGIC_PARTITION, 1, 1024L, 42L, 0, 4, 128L);
            
            assertThat(BundleSubHeader.isValid(segment)).isTrue();
            assertThat(BundleSubHeader.readBundleMagic(segment)).isEqualTo(BundleSubHeader.MAGIC_PARTITION);
            assertThat(BundleSubHeader.readBundleVersion(segment)).isEqualTo(1);
            assertThat(BundleSubHeader.readTotalFileSize(segment)).isEqualTo(1024L);
            assertThat(BundleSubHeader.readDirChecksum(segment)).isEqualTo(42L);
            assertThat(BundleSubHeader.readCapacityConfig(segment)).isEqualTo(0);
            assertThat(BundleSubHeader.readRegionCount(segment)).isEqualTo(4);
            assertThat(BundleSubHeader.readDataStartOffset(segment)).isEqualTo(128L);
            
            // Corrupt data
            segment.set(ValueLayout.JAVA_INT, BundleSubHeader.OFFSET, 0);
            assertThat(BundleSubHeader.isValid(segment)).isFalse();
        }
    }
}
