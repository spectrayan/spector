package com.spectrayan.spector.memory.kernel.bundle;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegionIdTest {
    @Test
    void testValuesAndPartitions() {
        assertThat(RegionId.SEMANTIC.id()).isEqualTo(0);
        assertThat(RegionId.SEMANTIC.isPartitionRegion()).isTrue();
        assertThat(RegionId.SEMANTIC.isRuntimeRegion()).isFalse();

        assertThat(RegionId.WORKING.id()).isEqualTo(10);
        assertThat(RegionId.WORKING.isPartitionRegion()).isFalse();
        assertThat(RegionId.WORKING.isRuntimeRegion()).isTrue();
    }

    @Test
    void testFromId() {
        assertThat(RegionId.fromId(0)).isEqualTo(RegionId.SEMANTIC);
        assertThat(RegionId.fromId(10)).isEqualTo(RegionId.WORKING);
        assertThat(RegionId.fromId(23)).isEqualTo(RegionId.CHECKPOINT);
    }

    @Test
    void testFromIdThrows() {
        assertThatThrownBy(() -> RegionId.fromId(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegionId.fromId(99)).isInstanceOf(IllegalArgumentException.class);
    }
}
