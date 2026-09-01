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
package com.spectrayan.spector.memory.kernel.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CoActivationLayout Unit Tests")
class CoActivationLayoutTest {

    private final CoActivationLayout layout = new CoActivationLayout();

    @Test
    @DisplayName("Verify CoActivationLayout constants, strides, and schema version (3)")
    void testLayoutMetadata() {
        assertThat(layout.layoutId()).isEqualTo(0x434F4158); // 'COAX'
        assertThat(layout.schemaVersion()).isEqualTo(3);
        assertThat(layout.name()).isEqualTo("CoActivationLayout");
        assertThat(layout.crcEnabled()).isFalse();

        assertThat(CoActivationLayout.SUB_HEADER_BYTES).isEqualTo(8);
        assertThat(CoActivationLayout.PAIR_SLOT_BYTES).isEqualTo(32);
        assertThat(CoActivationLayout.EDGE_SLOT_BYTES).isEqualTo(40);
    }

    @Test
    @DisplayName("Verify table offset computation for Compound Off-Heap hash tables")
    void testTableOffsets() {
        int pairCap = 1000;
        int expectedPairOffset = 8;
        int expectedEdgeOffset = 8 + (1000 * 32); // 32008

        assertThat(layout.pairTableOffset()).isEqualTo(expectedPairOffset);
        assertThat(layout.edgeTableOffset(pairCap)).isEqualTo(expectedEdgeOffset);
    }
}
