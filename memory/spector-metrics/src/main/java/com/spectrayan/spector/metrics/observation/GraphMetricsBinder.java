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

import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Exports Prometheus gauges for Hebbian and Entity graph structural telemetry and node-space headroom (R4 / F11, F12).
 *
 * <p>Metrics exported:
 * <ul>
 *   <li>{@code spector.graph.nodes} (tags: {@code graph="hebbian"|"entity"}, {@code spector.namespace})</li>
 *   <li>{@code spector.graph.edges} (tags: {@code graph="hebbian"|"entity"}, {@code spector.namespace})</li>
 *   <li>{@code spector.graph.bytes} (tags: {@code graph="hebbian"|"entity"}, {@code spector.namespace})</li>
 *   <li>{@code spector.graph.live_bytes} (tags: {@code graph="hebbian"|"entity"}, {@code spector.namespace})</li>
 *   <li>{@code spector.graph.headroom} (tags: {@code graph="hebbian"}, {@code spector.namespace})</li>
 * </ul>
 */
public class GraphMetricsBinder implements MeterBinder {

    private final SpectorMemory memory;
    private final HebbianGraphMemory directHebbian;
    private final EntityDirectory directEntityDirectory;
    private final String namespaceId;

    public GraphMetricsBinder(SpectorMemory memory) {
        this(memory, null);
    }

    public GraphMetricsBinder(SpectorMemory memory, String namespaceId) {
        this.memory = memory;
        this.directHebbian = null;
        this.directEntityDirectory = null;
        this.namespaceId = namespaceId;
    }

    public GraphMetricsBinder(HebbianGraphMemory hebbian, EntityDirectory entityDirectory) {
        this(hebbian, entityDirectory, null);
    }

    public GraphMetricsBinder(HebbianGraphMemory hebbian, EntityDirectory entityDirectory, String namespaceId) {
        this.memory = null;
        this.directHebbian = hebbian;
        this.directEntityDirectory = entityDirectory;
        this.namespaceId = namespaceId;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        HebbianGraphMemory hgm = resolveHebbianGraph();
        EntityDirectory entityDir = resolveEntityDirectory();

        if (hgm != null) {
            var nodesBuilder = Gauge.builder("spector.graph.nodes", hgm, h -> (double) h.structureHealthSnapshot().highestNodeIndexSeen())
                    .tag("graph", "hebbian")
                    .description("Highest node index observed in Hebbian CSR");
            if (namespaceId != null) nodesBuilder.tag("spector.namespace", namespaceId);
            nodesBuilder.register(registry);

            var edgesBuilder = Gauge.builder("spector.graph.edges", hgm, h -> (double) h.totalEdges())
                    .tag("graph", "hebbian")
                    .description("Total active Hebbian association edges");
            if (namespaceId != null) edgesBuilder.tag("spector.namespace", namespaceId);
            edgesBuilder.register(registry);

            var bytesBuilder = Gauge.builder("spector.graph.bytes", hgm, h -> (double) h.structureHealthSnapshot().allocatedBytes())
                    .tag("graph", "hebbian")
                    .description("Allocated bytes for Hebbian graph structures");
            if (namespaceId != null) bytesBuilder.tag("spector.namespace", namespaceId);
            bytesBuilder.register(registry);

            var liveBytesBuilder = Gauge.builder("spector.graph.live_bytes", hgm, h -> (double) h.structureHealthSnapshot().liveBytes())
                    .tag("graph", "hebbian")
                    .description("Live bytes currently utilized in Hebbian graph structures");
            if (namespaceId != null) liveBytesBuilder.tag("spector.namespace", namespaceId);
            liveBytesBuilder.register(registry);

            var headroomBuilder = Gauge.builder("spector.graph.headroom", hgm, h -> {
                float hr = h.structureHealthSnapshot().nodeSpaceHeadroom();
                return Float.isNaN(hr) ? 1.0 : (double) hr;
            })
                    .tag("graph", "hebbian")
                    .description("Remaining addressable node index headroom fraction [0..1]");
            if (namespaceId != null) headroomBuilder.tag("spector.namespace", namespaceId);
            headroomBuilder.register(registry);
        }

        if (entityDir != null) {
            var nodesBuilder = Gauge.builder("spector.graph.nodes", entityDir, e -> (double) e.entityCount())
                    .tag("graph", "entity")
                    .description("Active entity nodes in Entity Directory");
            if (namespaceId != null) nodesBuilder.tag("spector.namespace", namespaceId);
            nodesBuilder.register(registry);

            var edgesBuilder = Gauge.builder("spector.graph.edges", entityDir, e -> (double) e.adjHighWaterMark())
                    .tag("graph", "entity")
                    .description("Total active entity-to-memory adjacency links");
            if (namespaceId != null) edgesBuilder.tag("spector.namespace", namespaceId);
            edgesBuilder.register(registry);

            var bytesBuilder = Gauge.builder("spector.graph.bytes", entityDir, e -> (double) e.structureHealthSnapshot().allocatedBytes())
                    .tag("graph", "entity")
                    .description("Allocated bytes for Entity Directory structures");
            if (namespaceId != null) bytesBuilder.tag("spector.namespace", namespaceId);
            bytesBuilder.register(registry);

            var liveBytesBuilder = Gauge.builder("spector.graph.live_bytes", entityDir, e -> (double) e.structureHealthSnapshot().liveBytes())
                    .tag("graph", "entity")
                    .description("Live bytes currently utilized in Entity Directory structures");
            if (namespaceId != null) liveBytesBuilder.tag("spector.namespace", namespaceId);
            liveBytesBuilder.register(registry);
        }
    }

    private HebbianGraphMemory resolveHebbianGraph() {
        if (directHebbian != null) {
            return directHebbian;
        }
        if (memory != null) {
            try {
                if (memory.admin() != null && memory.admin().graph() != null) {
                    var raw = memory.admin().graph().rawHebbianGraph();
                    if (raw instanceof HebbianGraphMemory hgm) {
                        return hgm;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private EntityDirectory resolveEntityDirectory() {
        if (directEntityDirectory != null) {
            return directEntityDirectory;
        }
        if (memory != null) {
            try {
                if (memory.admin() != null) {
                    return memory.admin().entityDirectory();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
