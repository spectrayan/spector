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
package com.spectrayan.spector.kernel.store;

/**
 * Read-only telemetry snapshot of a graph structure's health.
 *
 * <p>The original nine fields are all byte- or edge-denominated, which made <b>node-space</b> exhaustion
 * invisible: {@code nodeCapacity} appeared only entangled inside {@code allocatedBytes} and
 * {@code csrOverflowOccupancy} and could not be recovered from either. Issue #983 added the four
 * node-space and rejection fields, because a graph that has run out of node slots keeps serving reads
 * and silently stops recording new associations.</p>
 *
 * @param structureName            stable identifier, e.g. {@code "hebbian-csr"}
 * @param allocatedBytes           bytes reserved by the structure
 * @param liveBytes                bytes currently holding live data
 * @param fragmentationRatio       {@code 1 - live/allocated}
 * @param hashLoadFactor           hash occupancy where applicable, else {@code NaN}
 * @param probeLength_p99          p99 probe length where applicable
 * @param csrOverflowOccupancy     heap spill pressure; not a hard capacity boundary
 * @param lastCompactionEpochMs    epoch millis of the last compaction, or 0
 * @param bytesReclaimedLastCycle  bytes reclaimed by the last compaction
 * @param nodeCapacity             maximum addressable node index, exclusive ({@code -1} if not applicable)
 * @param highestNodeIndexSeen     highest node index observed, or {@code -1} if none. Together with
 *                                 {@code nodeCapacity} this yields headroom
 * @param rejectedNodeOutOfRange   associations refused because a node index was at or beyond capacity.
 *                                 Non-zero means associations are being lost
 * @param truncatedEdgesAtCapacity edges discarded during compaction or decay because the edge slab was
 *                                 full. Non-zero means already-accepted associations are being lost
 */
public record GraphStructureHealthSnapshot(
        String structureName,
        long allocatedBytes,
        long liveBytes,
        float fragmentationRatio,
        float hashLoadFactor,
        int probeLength_p99,
        float csrOverflowOccupancy,
        long lastCompactionEpochMs,
        long bytesReclaimedLastCycle,
        int nodeCapacity,
        int highestNodeIndexSeen,
        long rejectedNodeOutOfRange,
        long truncatedEdgesAtCapacity
) {
    /** Sentinel for structures that do not address nodes by a bounded integer index. */
    public static final int NODE_SPACE_NOT_APPLICABLE = -1;

    public GraphStructureHealthSnapshot {
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException("structureName must not be blank");
        }
    }

    /**
     * Back-compatible constructor for structures with no node-space dimension.
     */
    public GraphStructureHealthSnapshot(
            String structureName,
            long allocatedBytes,
            long liveBytes,
            float fragmentationRatio,
            float hashLoadFactor,
            int probeLength_p99,
            float csrOverflowOccupancy,
            long lastCompactionEpochMs,
            long bytesReclaimedLastCycle
    ) {
        this(structureName, allocatedBytes, liveBytes, fragmentationRatio, hashLoadFactor,
                probeLength_p99, csrOverflowOccupancy, lastCompactionEpochMs, bytesReclaimedLastCycle,
                NODE_SPACE_NOT_APPLICABLE, NODE_SPACE_NOT_APPLICABLE, 0L, 0L);
    }

    /**
     * Fraction of the node index space still addressable, in {@code [0,1]}.
     *
     * <p>Returns {@code NaN} when node space does not apply. Note this measures the <b>index</b> space, not
     * live memory count: graph slots are allocated monotonically and never reused
     * ({@code IndexEntryMemory.allocateGraphSlot}), so headroom falls as memories are ingested over the
     * namespace's lifetime and does <b>not</b> recover when memories are deleted.</p>
     *
     * @return remaining node-index headroom, or {@code NaN} if not applicable
     */
    public float nodeSpaceHeadroom() {
        if (nodeCapacity <= 0) {
            return Float.NaN;
        }
        int used = Math.max(0, highestNodeIndexSeen + 1);
        if (used >= nodeCapacity) {
            return 0.0f;
        }
        return 1.0f - ((float) used / (float) nodeCapacity);
    }

    /**
     * Whether this structure is known to have lost associations.
     *
     * @return {@code true} if any node rejection or edge truncation has been counted
     */
    public boolean hasLostAssociations() {
        return rejectedNodeOutOfRange > 0 || truncatedEdgesAtCapacity > 0;
    }
}
