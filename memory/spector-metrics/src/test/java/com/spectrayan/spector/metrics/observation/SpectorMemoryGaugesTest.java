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

import com.spectrayan.spector.memory.SpectorMemory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpectorMemoryGaugesTest {

    @Mock
    private SpectorMemory mockMemory;

    @Mock
    private SpectorMemory mockMemoryA;

    @Mock
    private SpectorMemory mockMemoryB;

    @Test
    @DisplayName("Gauges with namespace tag produce tagged meters")
    void gaugesWithNamespaceTag_producesTaggedMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SpectorMemoryGauges gauges = new SpectorMemoryGauges(mockMemory, "ns-alpha");
        
        when(mockMemory.totalMemories()).thenReturn(42);
        gauges.bindTo(registry);

        Gauge gauge = registry.find("spector.memory.count")
                .tag("spector.namespace", "ns-alpha")
                .gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42.0);
    }

    @Test
    @DisplayName("Gauges without namespace produce untagged meters")
    void gaugesWithoutNamespace_producesUntaggedMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SpectorMemoryGauges gauges = new SpectorMemoryGauges(mockMemory);
        
        when(mockMemory.totalMemories()).thenReturn(15);
        gauges.bindTo(registry);

        Gauge gauge = registry.find("spector.memory.count").gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getTags())
                .noneMatch(tag -> tag.getKey().equals("spector.namespace"));
        assertThat(gauge.value()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("Two namespaces produce distinct series")
    void twoNamespaces_producesDistinctSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        
        SpectorMemoryGauges gaugesA = new SpectorMemoryGauges(mockMemoryA, "ns-a");
        SpectorMemoryGauges gaugesB = new SpectorMemoryGauges(mockMemoryB, "ns-b");
        
        when(mockMemoryA.totalMemories()).thenReturn(100);
        when(mockMemoryB.totalMemories()).thenReturn(200);
        
        gaugesA.bindTo(registry);
        gaugesB.bindTo(registry);

        Gauge gaugeA = registry.find("spector.memory.count")
                .tag("spector.namespace", "ns-a")
                .gauge();
                
        Gauge gaugeB = registry.find("spector.memory.count")
                .tag("spector.namespace", "ns-b")
                .gauge();

        assertThat(gaugeA).isNotNull();
        assertThat(gaugeA.value()).isEqualTo(100.0);
        assertThat(gaugeB).isNotNull();
        assertThat(gaugeB.value()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("Host gauges are bound once and not duplicated on multiple namespace openings")
    void hostGauges_boundOnce_notDuplicated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        SpectorHostGauges.instance().resetForTesting();
        SpectorHostGauges.instance().bindTo(registry);
        // Second bind attempt should be a no-op
        SpectorHostGauges.instance().bindTo(registry);

        var pinnedGauges = registry.find("spector.memory.pinned.bytes").gauges();
        assertThat(pinnedGauges).hasSize(1);
    }

    @Test
    @DisplayName("Evicting a namespace removes only its meters via registry.remove")
    void evictNamespace_removesOnlyTargetMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        SpectorMemoryGauges gaugesA = new SpectorMemoryGauges(mockMemoryA, "ns-evict-a");
        SpectorMemoryGauges gaugesB = new SpectorMemoryGauges(mockMemoryB, "ns-evict-b");

        when(mockMemoryA.totalMemories()).thenReturn(10);
        when(mockMemoryB.totalMemories()).thenReturn(20);

        gaugesA.bindTo(registry);
        gaugesB.bindTo(registry);

        var gaugeA = registry.find("spector.memory.count").tag("spector.namespace", "ns-evict-a").gauge();
        var gaugeB = registry.find("spector.memory.count").tag("spector.namespace", "ns-evict-b").gauge();
        assertThat(gaugeA).isNotNull();
        assertThat(gaugeB).isNotNull();
        assertThat(gaugeA.value()).isEqualTo(10.0);
        assertThat(gaugeB.value()).isEqualTo(20.0);

        // Simulate NamespaceResolver.unbindNamespaceMeters("ns-evict-a")
        var toRemove = registry.getMeters().stream()
                .filter(m -> "ns-evict-a".equals(m.getId().getTag("spector.namespace")))
                .toList();
        for (var meter : toRemove) {
            registry.remove(meter);
        }

        // ns-evict-a should be gone, ns-evict-b remains
        assertThat(registry.find("spector.memory.count").tag("spector.namespace", "ns-evict-a").gauge()).isNull();
        assertThat(registry.find("spector.memory.count").tag("spector.namespace", "ns-evict-b").gauge()).isNotNull();
    }
}
