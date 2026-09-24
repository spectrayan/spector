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
package com.spectrayan.spector.memory.bootstrap;

import com.spectrayan.spector.config.properties.MemoryProperties;

/**
 * Single source of truth for the capacities of the namespace-global graph structures.
 *
 * <p>The Hebbian graph, temporal chain, entity directory and hypergraph are <b>namespace-global</b>: one
 * region each in {@code runtime.bundle}, opened once, never remapped when a partition rolls
 * ({@code PartitionRegistry} holds no reference to any of them). Their node index is the graph slot from
 * {@code IndexEntryMemory.allocateGraphSlot()}, which is allocated monotonically and <b>never reused</b>,
 * so it rises for the lifetime of the namespace across every roll and every deletion.</p>
 *
 * <h3>Why this class exists</h3>
 *
 * <p>Two things went wrong before issue #983, and this class fixes both structurally rather than by
 * convention:</p>
 *
 * <ol>
 *   <li><b>The capacity source was per-partition.</b> Hebbian capacity fell back to
 *       {@code episodicPartitionCapacity} (default 1,000) to size a namespace-global structure. Because
 *       slots are never reused, the 1,001st memory ever ingested received no associations — permanently,
 *       counting memories since deleted. Recall kept working and quietly degraded. The source is now the
 *       total-namespace {@code capacity} property (default 100,000).</li>
 *   <li><b>The expression was duplicated.</b> {@code CognitiveGraphBuilder} sized the in-memory object and
 *       {@code CognitiveCortexBuilder} sized the on-disk region, with the same expression copied in both.
 *       Changing one without the other makes the object and its region disagree about capacity. Both now
 *       call this class.</li>
 * </ol>
 *
 * <h3>What this does not fix</h3>
 *
 * <p>Raising the default reduces the blast radius; it does not remove the bound. A namespace that ingests
 * more than {@code nodeCapacity} memories over its lifetime still stops forming associations. No constant
 * can be correct for a monotonically unbounded index space, and the graph cannot grow — capacity, edge
 * capacity and the slab slices are fixed at construction, and on reopen capacity is pinned by the persisted
 * region preamble rather than by configuration.</p>
 *
 * <p>The real fix — dense slot remapping, region growth, or per-partition CSR with cross-partition stubs —
 * is owned by {@code spectrayan/.kiro/specs/namespace-scale-and-observability} §5.1. Exhaustion is now
 * counted and logged ({@code HebbianGraphMemory.rejectedNodeOutOfRangeCount()}, node headroom in
 * {@code GraphStructureHealthSnapshot}), so operators find out before recall quality tells them.</p>
 *
 * @param nodeCapacity     addressable graph slots for the Hebbian graph
 * @param maxDegree        maximum edges retained per node
 * @param edgeCapacity     edge slab slots, {@code nodeCapacity * maxDegree}
 * @param temporalCapacity addressable slots for the temporal chain
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
public record GraphCapacityPlan(
        int nodeCapacity,
        int maxDegree,
        int edgeCapacity,
        int temporalCapacity
) {

    /**
     * Fallback max-degree when none is configured.
     *
     * <p>References the authoritative constant rather than restating a literal. Both builders previously
     * hardcoded {@code 16} here — which is {@code DEFAULT_MEMORY_ENTITY_MAX_DEGREE}, the <i>entity</i>
     * graph's degree cap, evidently copy-pasted. It was never reached in practice, because
     * {@code HebbianProperties.maxDegree} defaults to 24 and the {@code > 0} guard therefore passes, so the
     * effective default has always been 24. A misleading dead fallback rather than an active bug, but it
     * would have become one the moment anyone configured the property to 0.</p>
     */
    public static final int DEFAULT_MAX_DEGREE =
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE;

    /**
     * Derives the plan from memory properties.
     *
     * <p>Precedence for node capacity: the explicit {@code hebbianGraphCapacity} override if positive,
     * otherwise the total-namespace {@code capacity}. {@code episodicPartitionCapacity} is deliberately
     * <b>not</b> consulted — it describes one partition, and these structures span all of them.</p>
     *
     * @param memProps memory properties; must not be {@code null}
     * @return the resolved capacity plan
     */
    public static GraphCapacityPlan from(MemoryProperties memProps) {
        int nodeCapacity = memProps.getHebbianGraphCapacity() > 0
                ? memProps.getHebbianGraphCapacity()
                : memProps.getCapacity();

        // Defensive: a non-positive total capacity would produce a zero-slot graph that silently refuses
        // every association, which is the failure mode this class exists to remove.
        if (nodeCapacity <= 0) {
            nodeCapacity = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_CAPACITY;
        }

        int maxDegree = resolveMaxDegree(memProps);

        int temporalCapacity = memProps.getTemporalChainCapacity() > 0
                ? memProps.getTemporalChainCapacity()
                : nodeCapacity;

        return new GraphCapacityPlan(
                nodeCapacity,
                maxDegree,
                nodeCapacity * maxDegree,
                temporalCapacity);
    }

    private static int resolveMaxDegree(MemoryProperties memProps) {
        int configured = (memProps.getGraph() != null && memProps.getGraph().getHebbian() != null)
                ? memProps.getGraph().getHebbian().getMaxDegree()
                : 0;
        return configured > 0 ? configured : DEFAULT_MAX_DEGREE;
    }

    /**
     * Byte size of the {@code RegionId.HEBBIAN} region for this plan.
     *
     * <p>Layout: region preamble, sub-header, offset slab of {@code nodeCapacity + 1} ints, then the edge
     * slab. Kept here so the region can never be sized from a different capacity than the object that
     * reads it.</p>
     *
     * @return region size in bytes
     */
    public long hebbianRegionBytes() {
        return 64L + 16L
                + (long) (nodeCapacity + 1) * Integer.BYTES
                + (long) nodeCapacity * maxDegree * 12L;
    }

    /**
     * Byte size of the {@code RegionId.TEMPORAL_CHAIN} region for this plan.
     *
     * @return region size in bytes
     */
    public long temporalChainRegionBytes() {
        return 64L + 24L * temporalCapacity;
    }
}
