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
package com.spectrayan.spector.bench.cognitive;

import java.time.Instant;
import java.util.List;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.temporal.TemporalChain;

/**
 * Captures a static snapshot of graph state from a {@link SpectorMemory} instance.
 *
 * <p>Complements {@link com.spectrayan.spector.memory.graph.GraphHealthMetrics} which captures
 * <em>dynamic</em> per-cycle statistics during decay. This class captures the <em>static</em>
 * structural state: node counts, edge counts, degree distributions, and memory footprint.</p>
 *
 * <p><b>Thread safety:</b> Call during idle periods (between ingestion batches or reflection
 * cycles). The snapshot is approximate — concurrent writes may cause minor inconsistencies
 * in counts, which is acceptable for baseline measurement.</p>
 */
public final class GraphSnapshotCollector {

    private GraphSnapshotCollector() {
        // utility class
    }

    /**
     * Captures the current graph state from the given memory instance.
     *
     * @param memory the SpectorMemory instance to snapshot
     * @return an immutable snapshot of graph metrics
     */
    public static GraphSnapshot capture(SpectorMemory memory) {
        // ── Hebbian Graph Metrics ──
        int hebbianCapacity = 0;
        int hebbianTotalEdges = 0;
        int hebbianActiveNodes = 0;
        int hebbianMaxDegree = 0;
        long hebbianDegreeSum = 0;

        HebbianGraphBase hg = memory.admin().hebbianGraph();
        if (hg != null) {
            hebbianCapacity = hg.capacity();
            hebbianTotalEdges = hg.totalEdges();

            // Scan all slots to count active nodes and degree distribution
            for (int i = 0; i < hebbianCapacity; i++) {
                int deg = hg.degree(i);
                if (deg > 0) {
                    hebbianActiveNodes++;
                    hebbianDegreeSum += deg;
                    if (deg > hebbianMaxDegree) {
                        hebbianMaxDegree = deg;
                    }
                }
            }
        }
        float hebbianAvgDegree = hebbianActiveNodes > 0
                ? (float) hebbianDegreeSum / hebbianActiveNodes : 0f;

        // ── Entity Graph Metrics ──
        int entityCount = 0;
        int entityEdgeCount = 0;
        int entityMaxDegree = 0;
        long entityDegreeSum = 0;
        int entityAdjHighWaterMark = 0;

        EntityGraph eg = memory.admin().entityGraph();
        if (eg != null) {
            entityCount = eg.entityCount();
            entityEdgeCount = eg.edgeCount();
            entityAdjHighWaterMark = eg.adjHighWaterMark();

            // Scan entity edge degrees
            for (int i = 0; i < entityCount; i++) {
                int edgeSize = eg.edges(i).size();
                entityDegreeSum += edgeSize;
                if (edgeSize > entityMaxDegree) {
                    entityMaxDegree = edgeSize;
                }
            }
        }
        float entityAvgDegree = entityCount > 0
                ? (float) entityDegreeSum / entityCount : 0f;

        // ── Temporal Chain Metrics ──
        int temporalLinkedCount = 0;
        int temporalCapacity = 0;

        TemporalChain tc = memory.admin().temporalChain();
        if (tc != null) {
            temporalCapacity = tc.capacity();
            // Count how many slots are linked (have temporal connections)
            for (int i = 0; i < Math.min(temporalCapacity, memory.totalMemories()); i++) {
                if (tc.isLinked(i)) {
                    temporalLinkedCount++;
                }
            }
        }

        // ── Memory Counts ──
        int totalMemories = memory.totalMemories();
        int episodicCount = memory.memoryCount(MemoryType.EPISODIC);
        int semanticCount = memory.memoryCount(MemoryType.SEMANTIC);
        int proceduralCount = memory.memoryCount(MemoryType.PROCEDURAL);

        return new GraphSnapshot(
                hebbianActiveNodes, hebbianTotalEdges, hebbianMaxDegree, hebbianAvgDegree,
                entityCount, entityEdgeCount, entityMaxDegree, entityAvgDegree,
                entityAdjHighWaterMark,
                temporalLinkedCount, temporalCapacity,
                totalMemories, episodicCount, semanticCount, proceduralCount,
                Instant.now()
        );
    }

    /**
     * Immutable snapshot of graph structural state at a point in time.
     *
     * @param hebbianActiveNodes   nodes with ≥1 edge in the HebbianGraph
     * @param hebbianTotalEdges    total bidirectional edges (each pair counted once)
     * @param hebbianMaxDegree     highest degree among all nodes
     * @param hebbianAvgDegree     mean edges per active node
     * @param entityCount          unique entities registered in the EntityGraph
     * @param entityEdgeCount      entity↔entity edges
     * @param entityMaxDegree      highest entity edge degree
     * @param entityAvgDegree      mean edges per entity
     * @param entityAdjHighWater   high water mark of entity→memory adjacency entries
     * @param temporalLinkedCount  slots with temporal chain connections
     * @param temporalCapacity     total temporal chain capacity
     * @param totalMemories        total memories across all tiers
     * @param episodicCount        episodic memory count
     * @param semanticCount        semantic memory count
     * @param proceduralCount      procedural memory count
     * @param capturedAt           timestamp of this snapshot
     */
    public record GraphSnapshot(
            int hebbianActiveNodes,
            int hebbianTotalEdges,
            int hebbianMaxDegree,
            float hebbianAvgDegree,
            int entityCount,
            int entityEdgeCount,
            int entityMaxDegree,
            float entityAvgDegree,
            int entityAdjHighWater,
            int temporalLinkedCount,
            int temporalCapacity,
            int totalMemories,
            int episodicCount,
            int semanticCount,
            int proceduralCount,
            Instant capturedAt
    ) {

        /** Entity-to-memory ratio (higher = more entity explosion). */
        public float entityToMemoryRatio() {
            return totalMemories > 0 ? (float) entityCount / totalMemories : 0f;
        }

        /** Hebbian edge density: edges / active nodes. */
        public float hebbianEdgeDensity() {
            return hebbianActiveNodes > 0
                    ? (float) hebbianTotalEdges / hebbianActiveNodes : 0f;
        }

        /** Temporal coverage: % of memories with temporal links. */
        public float temporalCoverage() {
            return totalMemories > 0
                    ? (float) temporalLinkedCount / totalMemories : 0f;
        }

        @Override
        public String toString() {
            return String.format(
                    "GraphSnapshot{memories=%d(E=%d,S=%d,P=%d), hebbian[active=%d,edges=%d,maxDeg=%d,avgDeg=%.1f], "
                            + "entity[count=%d,edges=%d,maxDeg=%d,avgDeg=%.1f,adj=%d], "
                            + "temporal[linked=%d/%d,coverage=%.1f%%], entityRatio=%.3f}",
                    totalMemories, episodicCount, semanticCount, proceduralCount,
                    hebbianActiveNodes, hebbianTotalEdges, hebbianMaxDegree, hebbianAvgDegree,
                    entityCount, entityEdgeCount, entityMaxDegree, entityAvgDegree, entityAdjHighWater,
                    temporalLinkedCount, temporalCapacity, temporalCoverage() * 100f,
                    entityToMemoryRatio()
            );
        }
    }
}
