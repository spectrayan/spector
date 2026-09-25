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
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Test
    @DisplayName("F12: Uninitialized/empty graphs report 1.0 (100% headroom) safely")
    void uninitializedEmptyGraph_reportsFullHeadroomSafely() {
        HebbianGraphMemory hebbian = new HebbianGraphMemory(50, 200, 10, null);

        var snapshot = hebbian.structureHealthSnapshot();
        assertThat(snapshot.highestNodeIndexSeen()).isEqualTo(-1);
        assertThat(snapshot.nodeCapacity()).isEqualTo(50);
        assertThat(snapshot.nodeSpaceHeadroom()).isEqualTo(1.0f);
        assertThat(snapshot.allocatedBytes()).isGreaterThan(0L);
        assertThat(snapshot.liveBytes()).isEqualTo(0L);
        assertThat(hebbian.totalEdges()).isEqualTo(0);

        hebbian.close();
    }

    @Test
    @DisplayName("F12: nodeSpaceHeadroom() moves accurately as node slots are consumed")
    void nodeSpaceHeadroom_movesAccuratelyAsSlotsConsumed() {
        HebbianGraphMemory hebbian = new HebbianGraphMemory(100, 500, 10, null);

        // Initially 100% headroom
        assertThat(hebbian.structureHealthSnapshot().nodeSpaceHeadroom()).isEqualTo(1.0f);

        // Consume 25 slots (highestNodeSeen = 24 -> used = 25)
        hebbian.strengthen(0, 24, 1.0f);
        var s1 = hebbian.structureHealthSnapshot();
        assertThat(s1.highestNodeIndexSeen()).isEqualTo(24);
        assertThat(s1.nodeSpaceHeadroom()).isEqualTo(1.0f - (25.0f / 100.0f)); // 0.75f

        // Consume 50 slots (highestNodeSeen = 49 -> used = 50)
        hebbian.strengthen(24, 49, 1.0f);
        var s2 = hebbian.structureHealthSnapshot();
        assertThat(s2.highestNodeIndexSeen()).isEqualTo(49);
        assertThat(s2.nodeSpaceHeadroom()).isEqualTo(1.0f - (50.0f / 100.0f)); // 0.50f

        // Consume 90 slots (highestNodeSeen = 89 -> used = 90)
        hebbian.strengthen(49, 89, 1.0f);
        var s3 = hebbian.structureHealthSnapshot();
        assertThat(s3.highestNodeIndexSeen()).isEqualTo(89);
        assertThat(s3.nodeSpaceHeadroom()).isEqualTo(1.0f - (90.0f / 100.0f)); // 0.10f

        // Saturated at 100 slots (highestNodeSeen = 99 -> used = 100)
        hebbian.strengthen(89, 99, 1.0f);
        var s4 = hebbian.structureHealthSnapshot();
        assertThat(s4.highestNodeIndexSeen()).isEqualTo(99);
        assertThat(s4.nodeSpaceHeadroom()).isEqualTo(0.0f);

        // Out-of-range node is rejected and counted
        hebbian.strengthen(99, 100, 1.0f);
        var s5 = hebbian.structureHealthSnapshot();
        assertThat(s5.rejectedNodeOutOfRange()).isEqualTo(1L);
        assertThat(s5.nodeSpaceHeadroom()).isEqualTo(0.0f);

        hebbian.close();
    }

    @Test
    @DisplayName("F11: As edges and nodes are added, Hebbian nodes, edges, and bytes reflect real allocations")
    void hebbianGraphAllocations_reflectRealNodesEdgesAndBytes() throws IOException {
        HebbianGraphMemory hebbian = new HebbianGraphMemory(50, 200, 10, null);

        long expectedAllocBytes = (long) (50 + 1) * Integer.BYTES + 200L * 12L; // 204 + 2400 = 2604
        assertThat(hebbian.structureHealthSnapshot().allocatedBytes()).isEqualTo(expectedAllocBytes);
        assertThat(hebbian.structureHealthSnapshot().liveBytes()).isEqualTo(0L);
        assertThat(hebbian.totalEdges()).isEqualTo(0);
        assertThat(hebbian.structureHealthSnapshot().highestNodeIndexSeen()).isEqualTo(-1);

        // Add first association edge (bidirectional 1<->2: 2 edges total in overflow)
        hebbian.strengthen(1, 2, 1.5f);
        var s1 = hebbian.structureHealthSnapshot();
        assertThat(s1.highestNodeIndexSeen()).isEqualTo(2);
        assertThat(hebbian.totalEdges()).isEqualTo(2);
        assertThat(s1.csrOverflowOccupancy()).isGreaterThan(0.0f);
        assertThat(s1.allocatedBytes()).isEqualTo(expectedAllocBytes);

        // Add second association edge (bidirectional 2<->5: 4 edges total in overflow)
        hebbian.strengthen(2, 5, 2.0f);
        var s2 = hebbian.structureHealthSnapshot();
        assertThat(s2.highestNodeIndexSeen()).isEqualTo(5);
        assertThat(hebbian.totalEdges()).isEqualTo(4);
        assertThat(s2.csrOverflowOccupancy()).isGreaterThan(s1.csrOverflowOccupancy());
        assertThat(s2.allocatedBytes()).isEqualTo(expectedAllocBytes);

        // Save / compact overflow into static CSR slab
        Path tempFile = Files.createTempFile("hebbian_health", ".dat");
        try {
            hebbian.save(tempFile);
            var sPost = hebbian.structureHealthSnapshot();
            assertThat(sPost.highestNodeIndexSeen()).isEqualTo(5);
            assertThat(hebbian.totalEdges()).isEqualTo(4);
            assertThat(sPost.liveBytes()).isEqualTo(4L * 12L); // 48 bytes
            assertThat(sPost.allocatedBytes()).isEqualTo(expectedAllocBytes);
            assertThat(sPost.csrOverflowOccupancy()).isEqualTo(0.0f);
        } finally {
            Files.deleteIfExists(tempFile);
            hebbian.close();
        }
    }

    @Test
    @DisplayName("F11: As entities and links are added, Entity Directory nodes, edges, and bytes reflect real allocations")
    void entityDirectoryAllocations_reflectRealNodesEdgesAndBytes() {
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        EntityDirectory dir = new EntityDirectory(100, typeRegistry);

        assertThat(dir.entityCount()).isEqualTo(0);
        assertThat(dir.adjHighWaterMark()).isEqualTo(0);
        assertThat(dir.structureHealthSnapshot().allocatedBytes()).isGreaterThan(0L);
        assertThat(dir.structureHealthSnapshot().liveBytes()).isEqualTo(0L);

        int e1 = dir.intern("Tesla", "COMPANY");
        int e2 = dir.intern("Elon", "PERSON");
        assertThat(dir.entityCount()).isEqualTo(2);
        var s1 = dir.structureHealthSnapshot();
        assertThat(s1.liveBytes()).isGreaterThan(0L);

        dir.linkEntityToMemory(e1, 100);
        dir.linkEntityToMemory(e2, 100);
        var s2 = dir.structureHealthSnapshot();
        assertThat(dir.adjHighWaterMark()).isGreaterThan(0);
        assertThat(s2.liveBytes()).isGreaterThan(s1.liveBytes());
        assertThat(s2.allocatedBytes()).isGreaterThan(0L);

        dir.close();
        typeRegistry.close();
    }
}
