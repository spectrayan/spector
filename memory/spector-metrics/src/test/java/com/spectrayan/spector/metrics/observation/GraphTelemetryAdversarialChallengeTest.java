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
package com.spectrayan.spector.metrics.observation;

import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.store.GraphStructureHealthSnapshot;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Adversarial Empirical Challenge: Graph Telemetry & Headroom Gauges (M4)")
class GraphTelemetryAdversarialChallengeTest {

    @Test
    @DisplayName("Challenge 1.1: Empty and uninitialized graph reports 100% headroom without NaN or div-by-zero across capacities")
    void emptyGraph_reportsFullHeadroomAcrossVariousCapacities() {
        int[] capacities = {1, 2, 10, 50, 500, 10_000};

        for (int cap : capacities) {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            HebbianGraphMemory hebbian = new HebbianGraphMemory(cap, cap * 4, 10, null);
            GraphMetricsBinder binder = new GraphMetricsBinder(hebbian, null, "ns-empty-" + cap);
            binder.bindTo(registry);

            Gauge headroom = registry.find("spector.graph.headroom").tag("graph", "hebbian").tag("spector.namespace", "ns-empty-" + cap).gauge();
            Gauge nodes = registry.find("spector.graph.nodes").tag("graph", "hebbian").tag("spector.namespace", "ns-empty-" + cap).gauge();
            Gauge edges = registry.find("spector.graph.edges").tag("graph", "hebbian").tag("spector.namespace", "ns-empty-" + cap).gauge();
            Gauge bytes = registry.find("spector.graph.bytes").tag("graph", "hebbian").tag("spector.namespace", "ns-empty-" + cap).gauge();
            Gauge liveBytes = registry.find("spector.graph.live_bytes").tag("graph", "hebbian").tag("spector.namespace", "ns-empty-" + cap).gauge();

            assertThat(headroom).isNotNull();
            assertThat(headroom.value()).isEqualTo(1.0);
            assertThat(Double.isNaN(headroom.value())).isFalse();
            assertThat(Double.isInfinite(headroom.value())).isFalse();

            assertThat(nodes).isNotNull();
            assertThat(nodes.value()).isEqualTo(-1.0);

            assertThat(edges).isNotNull();
            assertThat(edges.value()).isEqualTo(0.0);

            assertThat(liveBytes).isNotNull();
            assertThat(liveBytes.value()).isEqualTo(0.0);

            assertThat(bytes).isNotNull();
            long expectedBytes = (long) (cap + 1) * Integer.BYTES + (long) (cap * 4) * 12L;
            assertThat((long) bytes.value()).isEqualTo(expectedBytes);

            hebbian.close();
        }
    }

    @Test
    @DisplayName("Challenge 1.2: Degenerate snapshot with non-positive capacity returns 1.0 headroom safely")
    void degenerateSnapshot_returnsFullHeadroomSafely() {
        GraphStructureHealthSnapshot nonPositiveCap = new GraphStructureHealthSnapshot(
                "hebbian-csr", 1024L, 0L, 1.0f, Float.NaN, 0, 0.0f, 0L, 0L,
                0, -1, 0L, 0L
        );
        assertThat(Float.isNaN(nonPositiveCap.nodeSpaceHeadroom())).isTrue();

        GraphStructureHealthSnapshot negativeCap = new GraphStructureHealthSnapshot(
                "hebbian-csr", 1024L, 0L, 1.0f, Float.NaN, 0, 0.0f, 0L, 0L,
                -1, -1, 0L, 0L
        );
        assertThat(Float.isNaN(negativeCap.nodeSpaceHeadroom())).isTrue();
    }

    @Test
    @DisplayName("Challenge 1.3: Boundary case with minimal capacity = 1")
    void minimalCapacityOne_transitionsFromFullHeadroomToZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(1, 10, 5, null);
        GraphMetricsBinder binder = new GraphMetricsBinder(hebbian, null, "ns-cap-1");
        binder.bindTo(registry);

        Gauge headroom = registry.find("spector.graph.headroom").tag("spector.namespace", "ns-cap-1").gauge();
        Gauge nodes = registry.find("spector.graph.nodes").tag("spector.namespace", "ns-cap-1").gauge();
        assertThat(headroom.value()).isEqualTo(1.0);
        assertThat(nodes.value()).isEqualTo(-1.0);

        // Accessing node 0 consumes the single available slot
        hebbian.strengthen(0, 0, 1.0f);
        assertThat(nodes.value()).isEqualTo(0.0);
        assertThat(headroom.value()).isEqualTo(0.0);

        // Attempting out-of-range node 1
        hebbian.strengthen(0, 1, 1.0f);
        assertThat(headroom.value()).isEqualTo(0.0);
        assertThat(hebbian.structureHealthSnapshot().rejectedNodeOutOfRange()).isEqualTo(1L);

        hebbian.close();
    }

    @Test
    @DisplayName("Challenge 2.1: Saturated graph headroom decreases monotonically towards 0.0 without going negative")
    void saturatedGraph_headroomDecreasesMonotonicallyTowardsZero() {
        final int capacity = 50;
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(capacity, 500, 10, com.spectrayan.spector.kernel.score.EdgeImportance.DEFAULT);
        GraphMetricsBinder binder = new GraphMetricsBinder(hebbian, null, "ns-monotonic");
        binder.bindTo(registry);

        Gauge headroom = registry.find("spector.graph.headroom").tag("spector.namespace", "ns-monotonic").gauge();
        Gauge nodes = registry.find("spector.graph.nodes").tag("spector.namespace", "ns-monotonic").gauge();

        double prevHeadroom = 1.0;
        for (int i = 0; i < capacity; i++) {
            hebbian.strengthen(0, i, 1.0f);
            double currentHeadroom = headroom.value();
            double currentNode = nodes.value();

            assertThat(currentNode).isEqualTo((double) i);
            assertThat(currentHeadroom).isLessThanOrEqualTo(prevHeadroom);
            assertThat(currentHeadroom).isGreaterThanOrEqualTo(0.0);

            double expectedHeadroom = 1.0 - ((double) (i + 1) / capacity);
            assertThat(currentHeadroom).isCloseTo(expectedHeadroom, within(0.0001));

            prevHeadroom = currentHeadroom;
        }

        // At capacity, headroom must be exactly 0.0
        assertThat(headroom.value()).isEqualTo(0.0);

        // Further additions exceeding capacity must be rejected and headroom must NOT go negative
        for (int overshoot = capacity; overshoot < capacity + 20; overshoot++) {
            hebbian.strengthen(0, overshoot, 1.0f);
            assertThat(headroom.value()).isEqualTo(0.0);
            assertThat(headroom.value()).isGreaterThanOrEqualTo(0.0);
        }

        assertThat(hebbian.structureHealthSnapshot().rejectedNodeOutOfRange()).isEqualTo(20L);
        hebbian.close();
    }

    @Test
    @DisplayName("Challenge 3.1: Compaction and edge removal reflect reduced liveBytes and edges while allocatedBytes remains stable")
    void compactionAndEdgeRemoval_liveBytesAndEdgesReflectUsage_allocatedBytesStable() throws IOException {
        final int capacity = 40;
        final int edgeCapacity = 200;
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(capacity, edgeCapacity, 10, null);
        GraphMetricsBinder binder = new GraphMetricsBinder(hebbian, null, "ns-compaction");
        binder.bindTo(registry);

        Gauge edgesGauge = registry.find("spector.graph.edges").tag("spector.namespace", "ns-compaction").gauge();
        Gauge liveBytesGauge = registry.find("spector.graph.live_bytes").tag("spector.namespace", "ns-compaction").gauge();
        Gauge allocBytesGauge = registry.find("spector.graph.bytes").tag("spector.namespace", "ns-compaction").gauge();
        Gauge headroomGauge = registry.find("spector.graph.headroom").tag("spector.namespace", "ns-compaction").gauge();

        double initialAllocBytes = allocBytesGauge.value();
        assertThat(initialAllocBytes).isGreaterThan(0.0);
        assertThat(liveBytesGauge.value()).isEqualTo(0.0);
        assertThat(edgesGauge.value()).isEqualTo(0.0);

        // Add 4 association pairs: (1,2), (2,3), (3,4), (4,5) -> 8 directed edges
        hebbian.strengthen(1, 2, 1.0f);
        hebbian.strengthen(2, 3, 1.0f);
        hebbian.strengthen(3, 4, 1.0f);
        hebbian.strengthen(4, 5, 1.0f);

        assertThat(edgesGauge.value()).isEqualTo(8.0);
        assertThat(allocBytesGauge.value()).isEqualTo(initialAllocBytes);

        // Compact into static CSR slab via save
        Path tmpFile = Files.createTempFile("hebbian_compaction_test", ".dat");
        try {
            hebbian.save(tmpFile);
            assertThat(edgesGauge.value()).isEqualTo(8.0);
            assertThat(liveBytesGauge.value()).isEqualTo(8.0 * 12.0); // 96 bytes
            assertThat(allocBytesGauge.value()).isEqualTo(initialAllocBytes);
            double headroomBeforeRemoval = headroomGauge.value();

            // Remove node 3: incident edges are (2,3), (3,2), (3,4), (4,3) -> 4 edges removed
            int removed = hebbian.removeNode(3);
            assertThat(removed).isEqualTo(4);

            // Verifications:
            // 1. edges gauge reflects reduced count (8 - 4 = 4 edges remaining)
            assertThat(edgesGauge.value()).isEqualTo(4.0);
            // 2. live_bytes reflects reduced count (4 * 12 = 48 bytes)
            assertThat(liveBytesGauge.value()).isEqualTo(48.0);
            // 3. allocated_bytes remains 100% stable
            assertThat(allocBytesGauge.value()).isEqualTo(initialAllocBytes);
            // 4. headroom does NOT recover (slots are monotonic)
            assertThat(headroomGauge.value()).isEqualTo(headroomBeforeRemoval);

        } finally {
            Files.deleteIfExists(tmpFile);
            hebbian.close();
        }
    }

    @Test
    @DisplayName("Challenge 3.2: EntityDirectory compaction reflects reduced liveBytes and edges while allocatedBytes remains stable")
    void entityDirectoryCompaction_liveBytesAndEdgesReflectUsage_allocatedBytesStable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        EntityDirectory entityDir = new EntityDirectory(100, typeRegistry);

        GraphMetricsBinder binder = new GraphMetricsBinder(null, entityDir, "ns-entity-compaction");
        binder.bindTo(registry);

        Gauge nodesGauge = registry.find("spector.graph.nodes").tag("graph", "entity").gauge();
        Gauge edgesGauge = registry.find("spector.graph.edges").tag("graph", "entity").gauge();
        Gauge liveBytesGauge = registry.find("spector.graph.live_bytes").tag("graph", "entity").gauge();
        Gauge allocBytesGauge = registry.find("spector.graph.bytes").tag("graph", "entity").gauge();

        double initialAllocBytes = allocBytesGauge.value();
        assertThat(initialAllocBytes).isGreaterThan(0.0);
        assertThat(nodesGauge.value()).isEqualTo(0.0);
        assertThat(edgesGauge.value()).isEqualTo(0.0);
        assertThat(liveBytesGauge.value()).isEqualTo(0.0);

        // Intern 4 entities and create links
        int e1 = entityDir.intern("Alpha", "ORG");
        int e2 = entityDir.intern("Beta", "ORG");
        int e3 = entityDir.intern("Gamma", "ORG");
        int e4 = entityDir.intern("Delta", "ORG");

        for (int m = 1; m <= 10; m++) {
            entityDir.linkEntityToMemory(e1, m);
            entityDir.linkEntityToMemory(e2, m);
            entityDir.linkEntityToMemory(e3, m);
            entityDir.linkEntityToMemory(e4, m);
        }

        assertThat(nodesGauge.value()).isEqualTo(4.0);
        double populatedEdges = edgesGauge.value();
        assertThat(populatedEdges).isGreaterThanOrEqualTo(40.0);
        double populatedLiveBytes = liveBytesGauge.value();
        assertThat(populatedLiveBytes).isGreaterThan(0.0);

        // Unlink memories 1 through 5
        for (int m = 1; m <= 5; m++) {
            entityDir.unlinkMemory(m);
        }

        // Run compaction
        long reclaimed = entityDir.compactAdjacency();
        assertThat(reclaimed).isGreaterThan(0L);

        // After compaction:
        // edgesGauge (adjHighWaterMark) reflects reclaimed free entries and compacted layout
        assertThat(edgesGauge.value()).isLessThan(populatedEdges);
        // liveBytes is reduced
        assertThat(liveBytesGauge.value()).isLessThan(populatedLiveBytes);
        // allocatedBytes remains stable (not shrunk)
        assertThat(allocBytesGauge.value()).isGreaterThanOrEqualTo(initialAllocBytes);

        entityDir.close();
        typeRegistry.close();
    }

    @Test
    @DisplayName("Challenge 4.1: Concurrent updates and continuous gauge polling produce no race conditions, exceptions, or NaN values")
    void concurrentUpdatesAndContinuousGaugeReads_produceNoErrorsOrInconsistencies() throws Exception {
        final int capacity = 300;
        final int edgeCapacity = 2000;
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(capacity, edgeCapacity, 20, null);
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        EntityDirectory entityDir = new EntityDirectory(500, typeRegistry);

        GraphMetricsBinder binder = new GraphMetricsBinder(hebbian, entityDir, "ns-concurrent-stress");
        binder.bindTo(registry);

        final int durationMs = 2500;
        final int writerThreadsCount = 4;
        final int readerThreadsCount = 4;
        final ExecutorService executor = Executors.newFixedThreadPool(writerThreadsCount + readerThreadsCount);
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicInteger readCount = new AtomicInteger(0);
        final AtomicInteger writeCount = new AtomicInteger(0);
        final CountDownLatch startLatch = new CountDownLatch(1);

        // 4 writer threads: concurrent strengthen, remove, entity intern and links
        for (int w = 0; w < writerThreadsCount; w++) {
            final int writerId = w;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int step = 0;
                    while (running.get() && failure.get() == null) {
                        int nodeA = (writerId * 50 + (step % 50)) % capacity;
                        int nodeB = (nodeA + 1 + (step % 10)) % capacity;
                        hebbian.strengthen(nodeA, nodeB, 0.5f);

                        int ent = entityDir.intern("Ent-" + writerId + "-" + (step % 30), "CONCEPT");
                        entityDir.linkEntityToMemory(ent, step % 200);

                        if (step % 50 == 0) {
                            hebbian.removeNode(nodeA);
                            entityDir.unlinkMemory(step % 200);
                        }

                        writeCount.incrementAndGet();
                        step++;
                        Thread.yield();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
        }

        // 4 reader threads: poll all gauges continuously
        for (int r = 0; r < readerThreadsCount; r++) {
            executor.submit(() -> {
                try {
                    Gauge hebbianNodes = registry.find("spector.graph.nodes").tag("graph", "hebbian").gauge();
                    Gauge hebbianEdges = registry.find("spector.graph.edges").tag("graph", "hebbian").gauge();
                    Gauge hebbianBytes = registry.find("spector.graph.bytes").tag("graph", "hebbian").gauge();
                    Gauge hebbianLiveBytes = registry.find("spector.graph.live_bytes").tag("graph", "hebbian").gauge();
                    Gauge entityNodes = registry.find("spector.graph.nodes").tag("graph", "entity").gauge();
                    Gauge entityEdges = registry.find("spector.graph.edges").tag("graph", "entity").gauge();
                    Gauge entityBytes = registry.find("spector.graph.bytes").tag("graph", "entity").gauge();
                    Gauge entityLiveBytes = registry.find("spector.graph.live_bytes").tag("graph", "entity").gauge();
                    Gauge headroom = registry.find("spector.graph.headroom").tag("graph", "hebbian").gauge();

                    Gauge[] allGauges = new Gauge[] {
                            hebbianNodes, hebbianEdges, hebbianBytes, hebbianLiveBytes,
                            entityNodes, entityEdges, entityBytes, entityLiveBytes
                    };

                    startLatch.await();
                    while (running.get() && failure.get() == null) {
                        for (Gauge g : allGauges) {
                            if (g != null) {
                                double val = g.value();
                                assertThat(Double.isNaN(val)).isFalse();
                                if (g == hebbianNodes) {
                                    assertThat(val).isGreaterThanOrEqualTo(-1.0);
                                } else {
                                    assertThat(val).isGreaterThanOrEqualTo(0.0);
                                }
                            }
                        }

                        if (headroom != null) {
                            double hr = headroom.value();
                            assertThat(Double.isNaN(hr)).isFalse();
                            assertThat(hr).isBetween(0.0, 1.0);
                        }

                        readCount.incrementAndGet();
                        Thread.yield();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        Thread.sleep(durationMs);
        running.set(false);

        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(terminated).isTrue();

        if (failure.get() != null) {
            throw new AssertionError("Concurrent stress test encountered failure", failure.get());
        }

        assertThat(writeCount.get()).isGreaterThanOrEqualTo(50);
        assertThat(readCount.get()).isGreaterThanOrEqualTo(50);

        hebbian.close();
        entityDir.close();
        typeRegistry.close();
    }
}
