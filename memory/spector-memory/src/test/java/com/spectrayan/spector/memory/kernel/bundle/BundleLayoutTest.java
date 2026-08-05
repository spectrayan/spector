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
