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
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GraphMetricsBinder & SpectorMemoryGauges: Prometheus Graph Telemetry (R4 / F11, F12)")
class GraphMetricsBinderTest {

    @Mock
    private SpectorMemory mockMemory;

    @Test
    @DisplayName("F11/F12: Gauges dynamically query Hebbian and Entity health snapshots with namespace tag")
    void gaugesDynamicallyQuerySnapshotsWithNamespaceTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(50, 200, 10, null);
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        EntityDirectory entityDir = new EntityDirectory(100, typeRegistry);

        GraphMetricsBinder binder = new GraphMetricsBinder(hebbian, entityDir, "ns-graph-test");
        binder.bindTo(registry);

        Gauge hebbianNodes = registry.find("spector.graph.nodes").tag("graph", "hebbian").tag("spector.namespace", "ns-graph-test").gauge();
        Gauge hebbianEdges = registry.find("spector.graph.edges").tag("graph", "hebbian").tag("spector.namespace", "ns-graph-test").gauge();
        Gauge hebbianBytes = registry.find("spector.graph.bytes").tag("graph", "hebbian").tag("spector.namespace", "ns-graph-test").gauge();
        Gauge hebbianHeadroom = registry.find("spector.graph.headroom").tag("graph", "hebbian").tag("spector.namespace", "ns-graph-test").gauge();

        Gauge entityNodes = registry.find("spector.graph.nodes").tag("graph", "entity").tag("spector.namespace", "ns-graph-test").gauge();
        Gauge entityEdges = registry.find("spector.graph.edges").tag("graph", "entity").tag("spector.namespace", "ns-graph-test").gauge();
        Gauge entityBytes = registry.find("spector.graph.bytes").tag("graph", "entity").tag("spector.namespace", "ns-graph-test").gauge();

        assertThat(hebbianNodes).isNotNull();
        assertThat(hebbianEdges).isNotNull();
        assertThat(hebbianBytes).isNotNull();
        assertThat(hebbianHeadroom).isNotNull();
        assertThat(entityNodes).isNotNull();
        assertThat(entityEdges).isNotNull();
        assertThat(entityBytes).isNotNull();

        // Initial state before additions
        assertThat(hebbianNodes.value()).isEqualTo(-1.0);
        assertThat(hebbianEdges.value()).isEqualTo(0.0);
        assertThat(hebbianBytes.value()).isGreaterThan(0.0);
        assertThat(hebbianHeadroom.value()).isEqualTo(1.0); // 100% headroom for uninitialized graph

        assertThat(entityNodes.value()).isEqualTo(0.0);
        assertThat(entityEdges.value()).isEqualTo(0.0);
        assertThat(entityBytes.value()).isGreaterThan(0.0);

        // Dynamically mutate Hebbian graph
        hebbian.strengthen(1, 4, 1.5f);
        hebbian.strengthen(4, 9, 2.0f);

        // Gauges must reflect new state dynamically on next read without re-registering
        assertThat(hebbianNodes.value()).isEqualTo(9.0);
        // Each strengthen call adds bidirectional edges (A->B and B->A), so 2 calls = 4 edges
        assertThat(hebbianEdges.value()).isEqualTo(4.0);
        // highestNodeIndexSeen is 9 -> used = 10 -> headroom = 1.0 - (10 / 50) = 0.8
        assertThat(hebbianHeadroom.value()).isCloseTo(0.8, within(0.001));

        // Dynamically mutate Entity directory
        int e1 = entityDir.intern("OpenAI", "COMPANY");
        int e2 = entityDir.intern("Sam", "PERSON");
        entityDir.linkEntityToMemory(e1, 100);
        entityDir.linkEntityToMemory(e2, 100);

        assertThat(entityNodes.value()).isEqualTo(2.0);
        assertThat(entityEdges.value()).isGreaterThan(0.0);
        assertThat(entityBytes.value()).isGreaterThan(0.0);

        hebbian.close();
        entityDir.close();
        typeRegistry.close();
    }

    @Test
    @DisplayName("F11/F12: SpectorMemoryGauges automatically binds graph gauges via SpectorMemory admin view")
    void spectorMemoryGauges_bindsGraphGaugesFromMemoryInstance() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(50, 200, 10, null);
        TypeRegistryMemory typeRegistry = new TypeRegistryMemory(SystemMemoryId.ENTITY_TYPE);
        EntityDirectory entityDir = new EntityDirectory(100, typeRegistry);

        hebbian.strengthen(2, 5, 1.0f);
        int e = entityDir.intern("Spectrayan", "ORG");
        entityDir.linkEntityToMemory(e, 42);

        SpectorMemoryAdmin mockAdmin = mock(SpectorMemoryAdmin.class);
        CognitiveGraphFacade mockFacade = mock(CognitiveGraphFacade.class);

        when(mockMemory.totalMemories()).thenReturn(10);
        when(mockMemory.admin()).thenReturn(mockAdmin);
        when(mockAdmin.graph()).thenReturn(mockFacade);
        when(mockFacade.rawHebbianGraph()).thenReturn(hebbian);
        when(mockAdmin.entityDirectory()).thenReturn(entityDir);

        SpectorMemoryGauges memoryGauges = new SpectorMemoryGauges(mockMemory, "ns-e2e");
        memoryGauges.bindTo(registry);

        Gauge memCount = registry.find("spector.memory.count").tag("spector.namespace", "ns-e2e").gauge();
        Gauge hNodes = registry.find("spector.graph.nodes").tag("graph", "hebbian").tag("spector.namespace", "ns-e2e").gauge();
        Gauge hEdges = registry.find("spector.graph.edges").tag("graph", "hebbian").tag("spector.namespace", "ns-e2e").gauge();
        Gauge hHeadroom = registry.find("spector.graph.headroom").tag("graph", "hebbian").tag("spector.namespace", "ns-e2e").gauge();
        Gauge eNodes = registry.find("spector.graph.nodes").tag("graph", "entity").tag("spector.namespace", "ns-e2e").gauge();

        assertThat(memCount).isNotNull();
        assertThat(memCount.value()).isEqualTo(10.0);

        assertThat(hNodes).isNotNull();
        assertThat(hNodes.value()).isEqualTo(5.0);

        assertThat(hEdges).isNotNull();
        // Bidirectional edge: 2->5 and 5->2
        assertThat(hEdges.value()).isEqualTo(2.0);

        // highestNodeIndexSeen is 5 -> used = 6 -> headroom = 1.0 - (6 / 50) = 0.88
        assertThat(hHeadroom).isNotNull();
        assertThat(hHeadroom.value()).isCloseTo(0.88, within(0.001));

        assertThat(eNodes).isNotNull();
        assertThat(eNodes.value()).isEqualTo(1.0);

        hebbian.close();
        entityDir.close();
        typeRegistry.close();
    }

    @Test
    @DisplayName("F12: Uninitialized or empty Hebbian graph safely reports 1.0 (100% headroom)")
    void uninitializedGraph_reportsFullHeadroomSafely() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(100, 400, 10, null);

        GraphMetricsBinder binder = new GraphMetricsBinder(hebbian, null, "ns-empty");
        binder.bindTo(registry);

        Gauge headroom = registry.find("spector.graph.headroom").tag("graph", "hebbian").gauge();
        assertThat(headroom).isNotNull();
        assertThat(headroom.value()).isEqualTo(1.0);

        Gauge nodes = registry.find("spector.graph.nodes").tag("graph", "hebbian").gauge();
        assertThat(nodes).isNotNull();
        assertThat(nodes.value()).isEqualTo(-1.0);

        Gauge edges = registry.find("spector.graph.edges").tag("graph", "hebbian").gauge();
        assertThat(edges).isNotNull();
        assertThat(edges.value()).isEqualTo(0.0);

        hebbian.close();
    }

    @Test
    @DisplayName("Null admin or graph safely skips registration without throwing")
    void nullAdminOrGraph_skipsCleanly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(mockMemory.admin()).thenReturn(null);

        GraphMetricsBinder binder = new GraphMetricsBinder(mockMemory, "ns-null");
        binder.bindTo(registry);

        assertThat(registry.find("spector.graph.nodes").gauges()).isEmpty();
        assertThat(registry.find("spector.graph.edges").gauges()).isEmpty();
        assertThat(registry.find("spector.graph.bytes").gauges()).isEmpty();
        assertThat(registry.find("spector.graph.headroom").gauges()).isEmpty();
    }
}
