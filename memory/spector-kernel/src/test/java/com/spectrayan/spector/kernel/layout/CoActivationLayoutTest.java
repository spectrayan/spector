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
package com.spectrayan.spector.kernel.layout;

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
