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

import com.spectrayan.spector.kernel.region.RegionId;

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
