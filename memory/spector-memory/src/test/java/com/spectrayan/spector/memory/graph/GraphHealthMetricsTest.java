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
package com.spectrayan.spector.memory.graph;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.kernel.store.GraphStructureHealthSnapshot;
import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphStructureHealthSnapshot: Telemetry and Compaction Tests (MR-08)")
class GraphHealthMetricsTest {

    @Test
    @DisplayName("MR-08: EntityDirectory exports valid health and fragmentation metrics")
    void entityDirectoryHealthMetrics() {
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        EntityDirectory dir = new EntityDirectory(100, typeRegistry);

        int e1 = dir.intern("Apple", "COMPANY");
        int e2 = dir.intern("Google", "COMPANY");
        dir.linkEntityToMemory(e1, 10);
        dir.linkEntityToMemory(e2, 10);

        GraphStructureHealthSnapshot metrics = dir.structureHealthSnapshot();
        assertThat(metrics.structureName()).isEqualTo("entity-directory");
        assertThat(metrics.allocatedBytes()).isGreaterThan(0L);
        assertThat(metrics.liveBytes()).isGreaterThan(0L);
        assertThat(metrics.fragmentationRatio()).isBetween(0.0f, 1.0f);
        assertThat(metrics.hashLoadFactor()).isEqualTo(2.0f / 100.0f);

        long reclaimed = dir.compactAdjacency();
        GraphStructureHealthSnapshot postMetrics = dir.structureHealthSnapshot();
        assertThat(postMetrics.lastCompactionEpochMs()).isGreaterThan(0L);
        assertThat(postMetrics.bytesReclaimedLastCycle()).isEqualTo(reclaimed);

        dir.close();
        typeRegistry.close();
    }

    @Test
    @DisplayName("MR-08: HebbianGraphMemory exports valid CSR health and overflow metrics")
    void hebbianGraphHealthMetrics() {
        HebbianGraphMemory hebbian = new HebbianGraphMemory(50, 200, 10, null);

        hebbian.strengthen(1, 2, 1.5f);
        hebbian.strengthen(2, 3, 2.0f);

        var metrics = hebbian.structureHealthSnapshot();
        assertThat(metrics.structureName()).isEqualTo("hebbian-csr");
        assertThat(metrics.allocatedBytes()).isGreaterThan(0L);
        assertThat(metrics.liveBytes()).isGreaterThanOrEqualTo(0L);
        assertThat(metrics.csrOverflowOccupancy()).isGreaterThanOrEqualTo(0.0f);
        assertThat(metrics.fragmentationRatio()).isBetween(0.0f, 1.0f);

        hebbian.close();
    }
}
