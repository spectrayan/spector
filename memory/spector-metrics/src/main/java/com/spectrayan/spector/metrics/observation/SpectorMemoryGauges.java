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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

public class SpectorMemoryGauges implements MeterBinder {
    
    private final SpectorMemory memory;
    private final String namespaceId;
    
    public SpectorMemoryGauges(SpectorMemory memory) {
        this(memory, null);
    }
    
    public SpectorMemoryGauges(SpectorMemory memory, String namespaceId) {
        this.memory = memory;
        this.namespaceId = namespaceId;
    }
    
    @Override
    public void bindTo(MeterRegistry registry) {
        // Host-level gauges registered once if no namespace specified (legacy/shared mode)
        if (namespaceId == null) {
            SpectorHostGauges.instance().bindTo(registry);
        }

        // Gauges
        var countBuilder = Gauge.builder("spector.memory.count", memory, SpectorMemory::totalMemories)
                .description("Total number of memories across all tiers");
        if (namespaceId != null) countBuilder.tag("spector.namespace", namespaceId);
        countBuilder.register(registry);

        // Index Plane Gauges (ADR-0082)
        if (memory.indexPlaneCoordinator() != null) {
            for (com.spectrayan.spector.memory.index.ManagedIndex idx : memory.indexPlaneCoordinator().registeredIndexes()) {
                String name = idx.name();
                var b1 = Gauge.builder("spector.memory.index.entries", idx, i -> i.stats().entries())
                        .tag("index", name)
                        .description("Total entries in index");
                if (namespaceId != null) b1.tag("spector.namespace", namespaceId);
                b1.register(registry);
                
                var b2 = Gauge.builder("spector.memory.index.heap.bytes", idx, i -> i.stats().heapBytes())
                        .tag("index", name)
                        .description("Estimated heap bytes used by index");
                if (namespaceId != null) b2.tag("spector.namespace", namespaceId);
                b2.register(registry);
                
                var b3 = Gauge.builder("spector.memory.index.off_heap.bytes", idx, i -> i.stats().offHeapBytes())
                        .tag("index", name)
                        .description("Off-heap bytes used by index");
                if (namespaceId != null) b3.tag("spector.namespace", namespaceId);
                b3.register(registry);
                
                var b4 = Gauge.builder("spector.memory.index.generation", idx, com.spectrayan.spector.memory.index.ManagedIndex::generation)
                        .tag("index", name)
                        .description("Index generation number");
                if (namespaceId != null) b4.tag("spector.namespace", namespaceId);
                b4.register(registry);
            }
        }

        // Graph Telemetry Gauges (Milestone 4 / ADR-0083 / R4)
        new GraphMetricsBinder(memory, namespaceId).bindTo(registry);
    }
}
