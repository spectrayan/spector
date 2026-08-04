package com.spectrayan.spector.memory.kernel.bundle;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BundleLayoutTest {
    @Test
    void testBundleLayoutValues() {
        BundleLayout layout = BundleLayout.SINGLETON;
        assertThat(layout.layoutId()).isEqualTo(0x42554E44);
        assertThat(layout.recordStride()).isEqualTo(64);
        assertThat(layout.schemaVersion()).isEqualTo(1);
        assertThat(layout.crcEnabled()).isTrue();
        assertThat(layout.name()).isEqualTo("Bundle");
    }
}
