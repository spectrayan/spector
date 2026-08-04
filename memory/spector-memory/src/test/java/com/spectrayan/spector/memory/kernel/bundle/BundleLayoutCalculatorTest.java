package com.spectrayan.spector.memory.kernel.bundle;

import org.junit.jupiter.api.Test;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BundleLayoutCalculatorTest {
    @Test
    void testCompute() {
        BundleLayoutCalculator.RegionSizeSpec spec1 = new BundleLayoutCalculator.RegionSizeSpec(
            RegionId.SEMANTIC, 1000, 100, 32, 1, 1, false
        );
        BundleLayoutCalculator.RegionSizeSpec spec2 = new BundleLayoutCalculator.RegionSizeSpec(
            RegionId.EPISODIC, 2000, 200, 64, 2, 1, true
        );
        BundleLayoutCalculator.RegionSizeSpec spec3 = new BundleLayoutCalculator.RegionSizeSpec(
            RegionId.PROCEDURAL, 3000, 300, 128, 3, 1, false
        );
        BundleLayoutCalculator.RegionSizeSpec spec4 = new BundleLayoutCalculator.RegionSizeSpec(
            RegionId.TEXT, 4000, 400, 256, 4, 1, true
        );
        
        BundleLayoutCalculator.BundleComputedLayout layout = BundleLayoutCalculator.compute(
            BundleSubHeader.MAGIC_PARTITION,
            List.of(spec1, spec2, spec3, spec4)
        );
        
        long expectedDataStart = BundleDirectory.dataStartOffset(4);
        long cursor = expectedDataStart;
        
        BundleDirectory dir = layout.directory();
        assertThat(dir.maxRegions()).isEqualTo(4);
        
        RegionEntry entry1 = dir.findRegion(RegionId.SEMANTIC);
        assertThat(entry1.offset()).isEqualTo(cursor);
        long size1 = BundleLayoutCalculator.alignToPage(MemoryHeader.HEADER_BYTES + 1000);
        assertThat(entry1.allocatedSize()).isEqualTo(size1);
        assertThat(entry1.isGrowable()).isFalse();
        cursor += size1;
        
        RegionEntry entry2 = dir.findRegion(RegionId.EPISODIC);
        assertThat(entry2.offset()).isEqualTo(cursor);
        long size2 = BundleLayoutCalculator.alignToPage(MemoryHeader.HEADER_BYTES + 2000);
        assertThat(entry2.allocatedSize()).isEqualTo(size2);
        assertThat(entry2.isGrowable()).isTrue();
        cursor += size2;
        
        RegionEntry entry3 = dir.findRegion(RegionId.PROCEDURAL);
        assertThat(entry3.offset()).isEqualTo(cursor);
        long size3 = BundleLayoutCalculator.alignToPage(MemoryHeader.HEADER_BYTES + 3000);
        assertThat(entry3.allocatedSize()).isEqualTo(size3);
        cursor += size3;
        
        RegionEntry entry4 = dir.findRegion(RegionId.TEXT);
        assertThat(entry4.offset()).isEqualTo(cursor);
        long size4 = BundleLayoutCalculator.alignToPage(MemoryHeader.HEADER_BYTES + 4000);
        assertThat(entry4.allocatedSize()).isEqualTo(size4);
        cursor += size4;
        
        assertThat(layout.totalFileSize()).isEqualTo(cursor);
    }
}
