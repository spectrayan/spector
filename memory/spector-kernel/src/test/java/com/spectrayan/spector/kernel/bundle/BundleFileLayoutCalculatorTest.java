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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.bundle.BundleFileLayout;

import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;

import org.junit.jupiter.api.Test;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BundleFileLayoutCalculatorTest {
    @Test
    void testCompute() {
        RegionSizeSpec spec1 = new RegionSizeSpec(
            RegionId.SEMANTIC, 1000, 100, 32, 1, 1, false
        );
        RegionSizeSpec spec2 = new RegionSizeSpec(
            RegionId.EPISODIC, 2000, 200, 64, 2, 1, true
        );
        RegionSizeSpec spec3 = new RegionSizeSpec(
            RegionId.PROCEDURAL, 3000, 300, 128, 3, 1, false
        );
        RegionSizeSpec spec4 = new RegionSizeSpec(
            RegionId.TEXT, 4000, 400, 256, 4, 1, true
        );
        
        BundleFileLayoutCalculator.BundleComputedLayout layout = BundleFileLayoutCalculator.compute(
            BundleSubHeader.MAGIC_PARTITION,
            List.of(spec1, spec2, spec3, spec4)
        );
        
        long expectedDataStart = BundleDirectory.dataStartOffset(4);
        long cursor = expectedDataStart;
        
        BundleDirectory dir = layout.directory();
        assertThat(dir.maxRegions()).isEqualTo(4);
        
        RegionEntry entry1 = dir.findRegion(RegionId.SEMANTIC);
        assertThat(entry1.offset()).isEqualTo(cursor);
        long size1 = BundleFileLayoutCalculator.alignToPage(RegionPreamble.PREAMBLE_BYTES + 1000);
        assertThat(entry1.allocatedSize()).isEqualTo(size1);
        assertThat(entry1.isGrowable()).isFalse();
        cursor += size1;
        
        RegionEntry entry2 = dir.findRegion(RegionId.EPISODIC);
        assertThat(entry2.offset()).isEqualTo(cursor);
        long size2 = BundleFileLayoutCalculator.alignToPage(RegionPreamble.PREAMBLE_BYTES + 2000);
        assertThat(entry2.allocatedSize()).isEqualTo(size2);
        assertThat(entry2.isGrowable()).isTrue();
        cursor += size2;
        
        RegionEntry entry3 = dir.findRegion(RegionId.PROCEDURAL);
        assertThat(entry3.offset()).isEqualTo(cursor);
        long size3 = BundleFileLayoutCalculator.alignToPage(RegionPreamble.PREAMBLE_BYTES + 3000);
        assertThat(entry3.allocatedSize()).isEqualTo(size3);
        cursor += size3;
        
        RegionEntry entry4 = dir.findRegion(RegionId.TEXT);
        assertThat(entry4.offset()).isEqualTo(cursor);
        long size4 = BundleFileLayoutCalculator.alignToPage(RegionPreamble.PREAMBLE_BYTES + 4000);
        assertThat(entry4.allocatedSize()).isEqualTo(size4);
        cursor += size4;
        
        assertThat(layout.totalFileSize()).isEqualTo(cursor);
    }
}
