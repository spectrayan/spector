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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
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

        GraphStructureHealthSnapshot metrics = hebbian.structureHealthSnapshot();
        assertThat(metrics.structureName()).isEqualTo("hebbian-csr");
        assertThat(metrics.allocatedBytes()).isGreaterThan(0L);
        assertThat(metrics.liveBytes()).isGreaterThanOrEqualTo(0L);
        assertThat(metrics.csrOverflowOccupancy()).isGreaterThanOrEqualTo(0.0f);
        assertThat(metrics.fragmentationRatio()).isBetween(0.0f, 1.0f);

        hebbian.close();
    }
}
